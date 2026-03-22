import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { batchAPI, jobAPI, authAPI } from '../services/api'
import AuthModal from './AuthModal'

function JobStatusBadge({ status }) {
  const map = {
    PENDING: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/30',
    PROCESSING: 'text-electric-blue bg-electric-blue/10 border-electric-blue/30',
    COMPLETED: 'text-neon-green bg-neon-green/10 border-neon-green/30',
    FAILED: 'text-red-400 bg-red-400/10 border-red-400/30',
    CANCELLED: 'text-gray-400 bg-gray-400/10 border-gray-400/30',
  }
  return (
    <span className={`px-2 py-1 rounded border text-xs font-semibold ${map[status] || map.PENDING}`}>
      {status}
    </span>
  )
}

const StatCard = ({ label, value, color = 'electric-blue', icon }) => (
  <motion.div
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    className={`glass-card p-4 rounded-xl border border-${color}/20 metric-card`}
  >
    <div className="flex items-center gap-2 mb-1">
      <span className="text-lg">{icon}</span>
      <p className="text-gray-400 text-xs">{label}</p>
    </div>
    <p className={`text-2xl font-bold text-${color}`}>{value}</p>
  </motion.div>
)

export default function ManufacturerPanel({ onBatchCreated }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [isVerified, setIsVerified] = useState(null) // null = loading, false = pending, true = verified
  const [verificationInfo, setVerificationInfo] = useState(null)
  const [activeTab, setActiveTab] = useState('create')

  const today = new Date().toISOString().split('T')[0]
  const nextYear = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

  const [formData, setFormData] = useState({
    medicineName: '',
    totalUnits: '1000',
    manufacturingDate: today,
    expiryDate: nextYear,
    testOfficerSignature: 'MOCK_SIGNATURE_12345',
    labReportContent: '',
  })
  const [labReportFile, setLabReportFile] = useState(null)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [uploadProgress, setUploadProgress] = useState(0)

  const [jobs, setJobs] = useState([])
  const [jobsLoading, setJobsLoading] = useState(false)
  const [pollingJobId, setPollingJobId] = useState(null)

  const [pendingApprovals, setPendingApprovals] = useState([])
  const [approvalsLoading, setApprovalsLoading] = useState(false)
  const [approvalSignature, setApprovalSignature] = useState('MOCK_APPROVAL_SIG')

  useEffect(() => {
    const token = localStorage.getItem('authToken')
    const storedRole = localStorage.getItem('userRole')
    if (token && token.split('.').length === 3 && storedRole === 'MANUFACTURER') {
      setIsAuthenticated(true)
      setShowAuthModal(false)
      checkVerificationStatus()
    } else {
      if (token) {
        localStorage.removeItem('authToken')
        localStorage.removeItem('userRole')
        localStorage.removeItem('username')
        localStorage.removeItem('userEmail')
      }
      setShowAuthModal(true)
    }
  }, [])

  const checkVerificationStatus = async () => {
    try {
      const data = await authAPI.getMyVerificationStatus()
      setIsVerified(data.isVerified)
      setVerificationInfo(data)
    } catch {
      setIsVerified(false)
    }
  }

  useEffect(() => {
    if (isAuthenticated && activeTab === 'jobs') fetchJobs()
    if (isAuthenticated && activeTab === 'approvals') fetchPendingApprovals()
  }, [activeTab, isAuthenticated])

  useEffect(() => {
    if (!pollingJobId) return
    const interval = setInterval(async () => {
      try {
        const job = await jobAPI.getJobStatus(pollingJobId)
        setJobs(prev => prev.map(j => j.jobId === pollingJobId ? job : j))
        if (job.status === 'COMPLETED' || job.status === 'FAILED') {
          setPollingJobId(null)
          clearInterval(interval)
        }
      } catch { clearInterval(interval) }
    }, 2000)
    return () => clearInterval(interval)
  }, [pollingJobId])

  const fetchJobs = async () => {
    setJobsLoading(true)
    try {
      const data = await jobAPI.getMyJobs()
      setJobs(Array.isArray(data) ? data : [])
    } catch { setJobs([]) }
    finally { setJobsLoading(false) }
  }

  const fetchPendingApprovals = async () => {
    setApprovalsLoading(true)
    try {
      const data = await batchAPI.getPendingApprovals()
      setPendingApprovals(Array.isArray(data) ? data : [])
    } catch { setPendingApprovals([]) }
    finally { setApprovalsLoading(false) }
  }

  const handleAuthSuccess = (response) => {
    if (response?.role) localStorage.setItem('userRole', response.role)
    if (response?.email) localStorage.setItem('username', response.email)
    setShowAuthModal(false)
    setIsAuthenticated(true)
    checkVerificationStatus()
  }
  const handleLogout = () => {
    localStorage.removeItem('authToken')
    localStorage.removeItem('userRole')
    localStorage.removeItem('username')
    localStorage.removeItem('userEmail')
    setIsAuthenticated(false)
    setShowAuthModal(true)
  }

  const handleFileChange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    if (file.type !== 'application/pdf') { alert('Only PDF files allowed'); return }
    if (file.size > 10 * 1024 * 1024) { alert('Max 10MB'); return }
    setLabReportFile(file)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!labReportFile) { alert('Please upload a lab report PDF'); return }
    setLoading(true); setResult(null); setUploadProgress(0)
    try {
      const fd = new FormData()
      Object.entries(formData).forEach(([k, v]) => fd.append(k, v))
      fd.append('labReportFile', labReportFile)
      fd.set('labReportContent', formData.labReportContent || '')

      const progressInterval = setInterval(() => {
        setUploadProgress(p => { if (p >= 90) { clearInterval(progressInterval); return 90 } return p + 10 })
      }, 200)

      const response = await batchAPI.createBatchComplete(fd)
      clearInterval(progressInterval)
      setUploadProgress(100)
      setResult({ success: true, data: response })
      if (onBatchCreated) onBatchCreated()
      if (response.jobId) { setPollingJobId(response.jobId); setActiveTab('jobs') }
      setFormData({ medicineName: '', totalUnits: '1000', manufacturingDate: today, expiryDate: nextYear, testOfficerSignature: 'MOCK_SIGNATURE_12345', labReportContent: '' })
      setLabReportFile(null)
    } catch (error) {
      setResult({ success: false, error: error.response?.data?.error || error.message || 'Failed to create batch' })
    } finally {
      setLoading(false)
      setTimeout(() => setUploadProgress(0), 2000)
    }
  }

  const handleApprove = async (batchId) => {
    try {
      await batchAPI.approveBatch(batchId, approvalSignature, 'PRODUCTION_HEAD')
      fetchPendingApprovals()
    } catch (err) { alert(err.response?.data?.error || 'Approval failed') }
  }

  const handleRetryJob = async (jobId) => {
    try { await jobAPI.retryJob(jobId); fetchJobs() }
    catch (err) { alert(err.response?.data?.error || 'Retry failed') }
  }

  const handleCancelJob = async (jobId) => {
    try { await jobAPI.cancelJob(jobId); fetchJobs() }
    catch (err) { alert(err.response?.data?.error || 'Cancel failed') }
  }

  const tabs = [
    { id: 'create', label: '➕ Create Batch', color: 'electric-blue' },
    { id: 'jobs', label: '⚙️ Job Status', color: 'neon-purple' },
    { id: 'approvals', label: '✅ Approvals', color: 'neon-green' },
  ]

  return (
    <div className="w-full">
      {showAuthModal && <AuthModal onAuthSuccess={handleAuthSuccess} allowedRoles={['MANUFACTURER']} />}

      {/* ── PENDING VERIFICATION SCREEN ── */}
      {isAuthenticated && isVerified === false && (
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
          className="glass-panel p-8 glow-border">
          <div className="max-w-lg mx-auto text-center py-8">
            <motion.div
              animate={{ scale: [1, 1.05, 1] }}
              transition={{ duration: 2, repeat: Infinity }}
              className="w-24 h-24 mx-auto mb-6 rounded-full flex items-center justify-center text-5xl"
              style={{ background: 'rgba(255,165,0,0.1)', border: '2px solid rgba(255,165,0,0.4)' }}>
              ⏳
            </motion.div>
            <h2 className="text-2xl font-bold text-yellow-400 mb-3">Verification Pending</h2>
            <p className="text-gray-400 text-sm mb-6 leading-relaxed">
              Your manufacturer account has been registered successfully. A <span className="text-electric-blue font-semibold">PharmaTrust Regulator</span> needs to verify your company identity before you can access the dashboard.
            </p>

            {verificationInfo && (
              <div className="text-left space-y-3 mb-6 p-4 rounded-xl"
                style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)' }}>
                <p className="text-xs text-gray-500 uppercase tracking-wider font-semibold mb-2">Your Registered Details</p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <p className="text-gray-500 text-xs">Full Name</p>
                    <p className="text-white">{verificationInfo.fullName || '—'}</p>
                  </div>
                  <div>
                    <p className="text-gray-500 text-xs">Company</p>
                    <p className="text-white">{verificationInfo.shopName || verificationInfo.organization || '—'}</p>
                  </div>
                  <div>
                    <p className="text-gray-500 text-xs">Role</p>
                    <p className="text-electric-blue font-semibold">🏭 MANUFACTURER</p>
                  </div>
                  <div>
                    <p className="text-gray-500 text-xs">Status</p>
                    <span className="px-2 py-0.5 rounded text-xs font-bold bg-yellow-500/20 text-yellow-400 border border-yellow-500/30">
                      ⏳ PENDING VERIFICATION
                    </span>
                  </div>
                </div>
              </div>
            )}

            <div className="p-4 rounded-xl mb-6"
              style={{ background: 'rgba(0,217,255,0.05)', border: '1px solid rgba(0,217,255,0.15)' }}>
              <p className="text-electric-blue text-xs font-semibold mb-2">📋 What happens next?</p>
              <ol className="text-gray-400 text-xs space-y-1.5 text-left list-decimal list-inside">
                <li>Regulator reviews your company details and license</li>
                <li>Once approved, you'll be able to login and access the dashboard</li>
                <li>You'll see a green "✅ Verified by PharmaTrust" badge on your account</li>
              </ol>
            </div>

            <div className="flex gap-3 justify-center">
              <button onClick={checkVerificationStatus}
                className="px-5 py-2.5 rounded-lg text-sm font-semibold transition-all"
                style={{ background: 'rgba(0,217,255,0.15)', border: '1px solid rgba(0,217,255,0.4)', color: '#00D9FF' }}>
                🔄 Check Status
              </button>
              <button onClick={handleLogout}
                className="px-5 py-2.5 rounded-lg text-sm font-semibold transition-all"
                style={{ background: 'rgba(255,45,120,0.1)', border: '1px solid rgba(255,45,120,0.3)', color: '#ff6b9d' }}>
                Logout
              </button>
            </div>
          </div>
        </motion.div>
      )}

      {/* ── MAIN DASHBOARD (only if verified) ── */}
      {(!isAuthenticated || isVerified !== true) ? null : (
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        {/* Header */}
        <div className="flex justify-between items-center mb-8">
          <div>
            <h2 className="text-3xl font-bold neon-text-blue">🏭 Manufacturer Dashboard</h2>
            <p className="text-gray-500 text-sm mt-1 font-mono">Lab Report → AI Verify → Blockchain → QR Codes</p>
          </div>
          {isAuthenticated && (
            <button onClick={handleLogout} className="px-4 py-2 bg-red-500/10 text-red-400 border border-red-500/20 rounded-lg hover:bg-red-500/20 transition-all text-sm">
              Logout
            </button>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-8 p-1 bg-dark-bg/60 rounded-xl border border-white/5">
          {tabs.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 px-4 py-2.5 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-electric-blue/20 to-neon-purple/20 text-white border border-electric-blue/30 shadow-neon-blue'
                  : 'text-gray-500 hover:text-gray-300'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* CREATE BATCH TAB */}
        {activeTab === 'create' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Medicine Name *</label>
                  <input type="text" value={formData.medicineName}
                    onChange={e => setFormData({ ...formData, medicineName: e.target.value })}
                    className="w-full bg-dark-bg/80 border border-electric-blue/20 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none focus:shadow-neon-blue transition-all"
                    placeholder="Paracetamol 500mg" required />
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Total Units *</label>
                  <input type="number" value={formData.totalUnits}
                    onChange={e => setFormData({ ...formData, totalUnits: e.target.value })}
                    className="w-full bg-dark-bg/80 border border-electric-blue/20 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none transition-all"
                    min="1" max="10000" required />
                  <p className="text-xs text-gray-600 mt-1">{formData.totalUnits} doses + {Math.ceil(formData.totalUnits / 10)} boxes</p>
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Manufacturing Date *</label>
                  <input type="date" value={formData.manufacturingDate}
                    onChange={e => setFormData({ ...formData, manufacturingDate: e.target.value })}
                    className="w-full bg-dark-bg/80 border border-electric-blue/20 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none transition-all" required />
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Expiry Date *</label>
                  <input type="date" value={formData.expiryDate}
                    onChange={e => setFormData({ ...formData, expiryDate: e.target.value })}
                    className="w-full bg-dark-bg/80 border border-electric-blue/20 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none transition-all" required />
                </div>
              </div>

              {/* Lab Report Upload */}
              <div className="gradient-border p-5">
                <label className="block text-xs text-gray-400 mb-3 uppercase tracking-wider">📄 Lab Report (PDF) *</label>
                <input type="file" accept=".pdf" onChange={handleFileChange}
                  className="w-full text-gray-300 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-electric-blue/20 file:text-electric-blue hover:file:bg-electric-blue/30 cursor-pointer transition-all" required />
                {labReportFile && (
                  <p className="text-sm text-neon-green mt-2 flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-neon-green inline-block"></span>
                    {labReportFile.name} ({(labReportFile.size / 1024).toFixed(2)} KB)
                  </p>
                )}
                <div className="mt-4">
                  <label className="block text-xs text-gray-500 mb-1">📋 Paste Lab Report Text (backup — used if PDF text extraction fails)</label>
                  <textarea
                    value={formData.labReportContent}
                    onChange={e => setFormData({ ...formData, labReportContent: e.target.value })}
                    rows={4}
                    className="w-full bg-dark-bg/60 border border-white/5 rounded-lg px-3 py-2 text-gray-300 text-xs focus:border-electric-blue/50 focus:outline-none font-mono resize-none transition-all"
                    placeholder="Paste the text from your lab report here..."
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Test Officer Signature</label>
                <input type="text" value={formData.testOfficerSignature}
                  onChange={e => setFormData({ ...formData, testOfficerSignature: e.target.value })}
                  className="w-full bg-dark-bg/80 border border-electric-blue/20 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none transition-all font-mono text-sm" />
              </div>

              {uploadProgress > 0 && uploadProgress < 100 && (
                <div className="space-y-2">
                  <div className="flex justify-between text-xs text-gray-400">
                    <span className="font-mono">Processing...</span><span>{uploadProgress}%</span>
                  </div>
                  <div className="w-full bg-dark-bg rounded-full h-1.5 overflow-hidden">
                    <motion.div
                      className="bg-gradient-to-r from-electric-blue to-neon-purple h-1.5 rounded-full progress-glow"
                      initial={{ width: 0 }}
                      animate={{ width: `${uploadProgress}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                </div>
              )}

              <button type="submit" disabled={loading}
                className="cyber-btn w-full bg-gradient-to-r from-electric-blue to-neon-purple text-white py-4 rounded-xl font-bold hover:shadow-neon-blue transition-all disabled:opacity-40 disabled:cursor-not-allowed text-sm uppercase tracking-wider">
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Creating Batch...
                  </span>
                ) : '🚀 Create Batch with Complete Workflow'}
              </button>
            </form>

            <AnimatePresence>
              {result && (
                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                  className={`p-6 rounded-xl ${result.success ? 'bg-neon-green/5 border border-neon-green/20' : 'bg-red-500/5 border border-red-500/20'}`}>
                  {result.success ? (
                    <div className="space-y-4">
                      <h3 className="text-lg font-bold text-neon-green flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-neon-green animate-pulse"></span>
                        Batch Created Successfully
                      </h3>
                      <div className="grid grid-cols-2 gap-3 text-sm">
                        <div className="glass-card p-3 rounded-lg">
                          <p className="text-gray-500 text-xs">Batch Number</p>
                          <p className="text-white font-mono text-xs mt-1">{result.data.batchNumber}</p>
                        </div>
                        <div className="glass-card p-3 rounded-lg">
                          <p className="text-gray-500 text-xs">Total Units</p>
                          <p className="text-electric-blue font-bold text-lg">{result.data.totalUnits}</p>
                        </div>
                        <div className="glass-card p-3 rounded-lg">
                          <p className="text-gray-500 text-xs">AI Score</p>
                          <p className="text-neon-green font-bold text-lg">{result.data.aiVerificationScore?.toFixed(1)}/100</p>
                          <p className="text-xs text-gray-600 mt-0.5">≥70 required — PASSED</p>
                        </div>
                        <div className="glass-card p-3 rounded-lg col-span-1">
                          <p className="text-gray-500 text-xs">Blockchain Token</p>
                          <p className="text-neon-purple font-mono text-xs mt-1 break-all">{result.data.blockchainTokenId?.slice(0, 24)}...</p>
                        </div>
                      </div>

                      {result.data.units && result.data.units.filter(u => u.unitType === 'TABLET').length > 0 && (
                        <div>
                          <p className="text-gray-400 text-xs uppercase tracking-wider mb-3">💊 Sample Unit QR Codes</p>
                          <div className="grid grid-cols-5 gap-2">
                            {result.data.units.filter(u => u.unitType === 'TABLET').slice(0, 10).map((unit, i) => (
                              <div key={i} className="glass-card p-1.5 rounded-lg text-center border border-electric-blue/10">
                                {unit.qrCodeData?.startsWith('data:image') ? (
                                  <img src={unit.qrCodeData} alt={unit.serialNumber} className="w-full rounded" />
                                ) : (
                                  <div className="w-full h-16 flex items-center justify-center text-gray-600 text-xs">No QR</div>
                                )}
                                <p className="text-gray-600 text-xs mt-1 truncate">{unit.serialNumber?.slice(-8)}</p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {result.data.units && result.data.units.filter(u => u.unitType === 'BOX').length > 0 && (
                        <div>
                          <p className="text-gray-400 text-xs uppercase tracking-wider mb-3">📦 Box QR Codes</p>
                          <div className="grid grid-cols-3 gap-3">
                            {result.data.units.filter(u => u.unitType === 'BOX').slice(0, 3).map((box, i) => (
                              <div key={i} className="glass-card p-2 rounded-lg text-center border border-neon-purple/20">
                                {box.qrCodeData?.startsWith('data:image') ? (
                                  <img src={box.qrCodeData} alt={box.serialNumber} className="w-full rounded" />
                                ) : (
                                  <div className="w-full h-20 flex items-center justify-center text-gray-600 text-xs">No QR</div>
                                )}
                                <p className="text-neon-purple text-xs mt-1">📦 Box</p>
                                <p className="text-gray-600 text-xs truncate">{box.serialNumber?.slice(-8)}</p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div>
                      <h3 className="text-lg font-bold text-red-400 flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-red-400"></span>
                        Batch Creation Failed
                      </h3>
                      <p className="text-red-300 mt-2 text-sm">{result.error}</p>
                      {result.error?.includes('AI verification failed') && (
                        <div className="mt-3 p-3 bg-dark-bg/60 border border-red-500/20 rounded-lg text-xs text-gray-300 space-y-1">
                          <p className="text-yellow-400 font-semibold">💡 Lab report must include:</p>
                          <ul className="list-disc list-inside space-y-0.5 text-gray-400">
                            <li>Active ingredient name and chemical formula</li>
                            <li>Dosage strength (e.g. 500mg)</li>
                            <li>Purity/assay percentage (≥99%)</li>
                            <li>Pharmacopoeia standard (IP/BP/USP)</li>
                          </ul>
                        </div>
                      )}
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}

        {/* JOBS TAB */}
        {activeTab === 'jobs' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">Background Jobs</h3>
              <button onClick={fetchJobs} className="px-3 py-1.5 text-xs bg-electric-blue/10 text-electric-blue border border-electric-blue/20 rounded-lg hover:bg-electric-blue/20 transition-all">
                🔄 Refresh
              </button>
            </div>
            {jobsLoading ? (
              <div className="flex justify-center py-12">
                <div className="w-8 h-8 border-2 border-electric-blue border-t-transparent rounded-full animate-spin" />
              </div>
            ) : jobs.length === 0 ? (
              <div className="text-center py-16 text-gray-600 font-mono text-sm">No jobs found</div>
            ) : (
              <div className="space-y-3">
                {jobs.map(job => (
                  <div key={job.jobId} className="glass-card p-4 rounded-xl border border-white/5">
                    <div className="flex justify-between items-start mb-3">
                      <div>
                        <p className="text-white font-semibold text-sm">{job.jobType || 'UNIT_GENERATION'}</p>
                        <p className="text-gray-600 text-xs font-mono mt-0.5">{job.jobId}</p>
                      </div>
                      <JobStatusBadge status={job.status} />
                    </div>
                    {job.progress !== undefined && (
                      <div className="mb-3">
                        <div className="flex justify-between text-xs text-gray-500 mb-1">
                          <span>Progress</span><span>{job.progress}%</span>
                        </div>
                        <div className="w-full bg-dark-bg rounded-full h-1">
                          <div className="bg-gradient-to-r from-electric-blue to-neon-purple h-1 rounded-full transition-all duration-500" style={{ width: `${job.progress}%` }} />
                        </div>
                      </div>
                    )}
                    {job.errorMessage && <p className="text-red-400 text-xs">{job.errorMessage}</p>}
                    <div className="flex gap-2 mt-2">
                      {job.status === 'FAILED' && (
                        <button onClick={() => handleRetryJob(job.jobId)}
                          className="px-3 py-1 text-xs bg-yellow-500/10 text-yellow-400 border border-yellow-500/20 rounded-lg hover:bg-yellow-500/20 transition-all">
                          🔁 Retry
                        </button>
                      )}
                      {(job.status === 'PENDING' || job.status === 'PROCESSING') && (
                        <button onClick={() => handleCancelJob(job.jobId)}
                          className="px-3 py-1 text-xs bg-red-500/10 text-red-400 border border-red-500/20 rounded-lg hover:bg-red-500/20 transition-all">
                          ✕ Cancel
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        )}

        {/* APPROVALS TAB */}
        {activeTab === 'approvals' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">Pending Multi-Sig Approvals</h3>
              <button onClick={fetchPendingApprovals} className="px-3 py-1.5 text-xs bg-neon-green/10 text-neon-green border border-neon-green/20 rounded-lg hover:bg-neon-green/20 transition-all">
                🔄 Refresh
              </button>
            </div>
            <div className="mb-4">
              <label className="block text-xs text-gray-400 mb-2 uppercase tracking-wider">Your Approval Signature</label>
              <input type="text" value={approvalSignature} onChange={e => setApprovalSignature(e.target.value)}
                className="w-full bg-dark-bg/80 border border-neon-green/20 rounded-lg px-4 py-2.5 text-white focus:border-neon-green focus:outline-none text-sm font-mono transition-all" />
            </div>
            {approvalsLoading ? (
              <div className="flex justify-center py-12">
                <div className="w-8 h-8 border-2 border-neon-green border-t-transparent rounded-full animate-spin" />
              </div>
            ) : pendingApprovals.length === 0 ? (
              <div className="text-center py-16 text-gray-600 font-mono text-sm">No pending approvals</div>
            ) : (
              <div className="space-y-3">
                {pendingApprovals.map(batch => (
                  <div key={batch.id} className="glass-card p-4 rounded-xl border border-yellow-500/20">
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="text-white font-semibold">{batch.medicineName}</p>
                        <p className="text-gray-500 text-xs font-mono mt-0.5">{batch.batchNumber}</p>
                        <p className="text-gray-500 text-xs mt-1">Units: {batch.totalUnits} · Created: {new Date(batch.createdAt).toLocaleDateString()}</p>
                        <p className="text-yellow-400 text-xs mt-1">Approvals: {batch.approvalCount || 0} / {batch.requiredApprovals || 2}</p>
                      </div>
                      <button onClick={() => handleApprove(batch.id)}
                        className="px-4 py-2 bg-neon-green/10 text-neon-green border border-neon-green/20 rounded-lg hover:bg-neon-green/20 transition-all text-sm font-semibold">
                        ✅ Approve
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        )}
      </motion.div>
      )}
    </div>
  )
}
