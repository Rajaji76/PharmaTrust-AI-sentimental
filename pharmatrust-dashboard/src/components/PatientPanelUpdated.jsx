import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { scanAPI, complaintAPI, batchAPI } from '../services/api'
import QRScanner from './QRScanner'
import AuthModal from './AuthModal'

const SCAN_HISTORY_KEY = 'pharmatrust_scan_history'
const PENDING_SERIAL_KEY = 'pharmatrust_pending_serial'

const EMERGENCY_CONTACTS = [
  { name: 'National Health Helpline', number: '104', desc: 'Free 24x7 health advice & medicine complaints', icon: '🏥' },
  { name: 'Drug Controller General (CDSCO)', number: '1800-11-0707', desc: 'Report counterfeit / substandard medicines', icon: '💊' },
  { name: 'Ayushman Bharat Helpline', number: '14555', desc: 'PM-JAY scheme support & health queries', icon: '💛' },
  { name: 'National Poison Control', number: '1800-116-117', desc: 'Emergency if consumed harmful medicine', icon: '☠️' },
  { name: 'Emergency Ambulance', number: '108', desc: 'Medical emergency — free ambulance service', icon: '🚑' },
]

function loadHistory() {
  try { return JSON.parse(localStorage.getItem(SCAN_HISTORY_KEY) || '[]') } catch { return [] }
}
function saveHistory(h) { localStorage.setItem(SCAN_HISTORY_KEY, JSON.stringify(h.slice(0, 20))) }

function isValidToken(token) {
  return token && token.split('.').length === 3
}

// ─── Lab Report Modal ────────────────────────────────────────────────────────
function LabReportModal({ batchNumber, onClose }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    batchAPI.getLabReport(batchNumber)
      .then(d => setData(d)).catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [batchNumber])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(4px)' }} onClick={onClose}>
      <div className="rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto border border-slate-700" style={{ background: '#1e293b' }} onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-700">
          <div className="flex items-center gap-3">
            <span className="text-xl">📄</span>
            <h2 className="text-white font-bold text-lg">Lab Report</h2>
          </div>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:bg-slate-700">✕</button>
        </div>
        <div className="p-6 space-y-4">
          {loading ? (
            <p className="text-slate-400 text-sm text-center py-8">Loading lab report...</p>
          ) : !data ? (
            <p className="text-slate-500 text-sm text-center py-8">Lab report not available for this batch.</p>
          ) : (
            <>
              <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                <p className="text-emerald-400 text-xs font-bold uppercase tracking-wider mb-3">✅ AI Verified Batch</p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div><p className="text-slate-500 text-xs">Medicine</p><p className="text-white font-semibold">{data.medicineName}</p></div>
                  <div><p className="text-slate-500 text-xs">Batch</p><p className="text-slate-300 font-mono text-xs">{data.batchNumber}</p></div>
                  <div><p className="text-slate-500 text-xs">Mfg Date</p><p className="text-slate-300">{data.manufacturingDate}</p></div>
                  <div><p className="text-slate-500 text-xs">Expiry</p><p className="text-slate-300">{data.expiryDate}</p></div>
                  <div><p className="text-slate-500 text-xs">Total Units</p><p className="text-slate-300">{data.totalUnits}</p></div>
                  <div><p className="text-slate-500 text-xs">Status</p>
                    <span className={`px-2 py-0.5 rounded text-xs font-bold ${data.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-yellow-500/20 text-yellow-400'}`}>{data.status}</span>
                  </div>
                </div>
              </div>

              {data.manufacturer && (
                <div className="p-4 rounded-xl bg-blue-500/10 border border-blue-500/20">
                  <p className="text-blue-400 text-xs font-bold uppercase tracking-wider mb-2">🏭 Manufacturer</p>
                  <p className="text-white font-semibold text-sm">{data.manufacturer.company || data.manufacturer.name}</p>
                  {data.manufacturer.city && <p className="text-slate-400 text-xs">{data.manufacturer.city}</p>}
                </div>
              )}

              <div className="p-4 rounded-xl bg-slate-800 border border-slate-700">
                <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-2">🔐 Integrity</p>
                <div className="space-y-2">
                  <div>
                    <p className="text-slate-500 text-xs">Lab Report Hash (SHA-256)</p>
                    <p className="text-slate-300 font-mono text-xs break-all mt-0.5">{data.labReportHash || 'Not available'}</p>
                  </div>
                  {data.blockchainTxId && (
                    <div>
                      <p className="text-slate-500 text-xs">Blockchain TX</p>
                      <p className="text-purple-400 font-mono text-xs break-all mt-0.5">{data.blockchainTxId}</p>
                    </div>
                  )}
                  <div className="flex items-center gap-2 mt-1">
                    <span className={`w-2 h-2 rounded-full ${data.blockchainConfirmed ? 'bg-emerald-400' : 'bg-yellow-400'}`} />
                    <span className="text-xs text-slate-400">{data.blockchainConfirmed ? 'Blockchain Confirmed' : 'Pending Confirmation'}</span>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
        <div className="px-6 py-4 border-t border-slate-700 flex justify-end">
          <button onClick={onClose} className="px-5 py-2 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-200 text-sm font-semibold">Close</button>
        </div>
      </div>
    </div>
  )
}

// ─── Scan Result Card ────────────────────────────────────────────────────────
function ScanResultCard({ result }) {
  const [showLabReport, setShowLabReport] = useState(false)
  if (!result) return null
  const { data } = result
  if (data?.isRecalled || data?.isBlocked) return (
    <motion.div animate={{ boxShadow: ['0 0 20px rgba(239,68,68,0.5)', '0 0 60px rgba(239,68,68,0.8)', '0 0 20px rgba(239,68,68,0.5)'] }}
      transition={{ duration: 1, repeat: Infinity }}
      className="bg-red-600/20 border-2 border-red-500 rounded-xl p-6 mt-6">
      <div className="flex items-center mb-4">
        <motion.span animate={{ scale: [1, 1.2, 1] }} transition={{ duration: 0.5, repeat: Infinity }} className="text-6xl mr-4">🚨</motion.span>
        <div>
          <h3 className="text-2xl font-bold text-red-400">DANGER — DO NOT CONSUME!</h3>
          <p className="text-red-300 text-sm mt-1">This batch has been recalled</p>
        </div>
      </div>
      <p className="text-white font-semibold">{data.message}</p>
      {data.recallReason && <p className="text-red-200 mt-2 text-sm">Reason: {data.recallReason}</p>}
    </motion.div>
  )
  if (data?.isCounterfeit) return (
    <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-6 mt-6">
      <h3 className="text-2xl font-bold text-red-400 mb-3">⚠️ COUNTERFEIT DETECTED!</h3>
      <p className="text-red-300 text-sm">{data.message}</p>
      <p className="text-white mt-2 text-sm">Scan Count: {data.scanCount} / {data.maxScanLimit}</p>
    </div>
  )
  if (result.success) return (
    <motion.div animate={{ boxShadow: ['0 0 20px rgba(57,255,20,0.2)', '0 0 40px rgba(57,255,20,0.4)', '0 0 20px rgba(57,255,20,0.2)'] }}
      transition={{ duration: 2, repeat: Infinity }}
      className="bg-neon-green/5 border border-neon-green/30 rounded-xl p-6 mt-6">
      <h3 className="text-xl font-bold text-neon-green mb-4 flex items-center gap-2">
        <motion.span animate={{ scale: [1, 1.1, 1] }} transition={{ duration: 1, repeat: Infinity }}>✅</motion.span>
        {data.unitType === 'BOX' || data.unitType === 'CARTON' ? 'AUTHENTIC BOX / PACKAGE' : 'AUTHENTIC MEDICINE'}
      </h3>
      <div className="grid grid-cols-2 gap-3 text-sm">
        {[
          ['💊 Medicine', data.medicineName],
          ['🏭 Manufacturer', data.manufacturerName || 'PharmaCorp'],
          ['📦 Batch Number', data.batchNumber],
          ['📅 Mfg Date', data.manufacturingDate],
          ['⏰ Expiry Date', data.expiryDate],
          ['🔢 Scan Count', `${data.scanCount} / ${data.maxScanLimit}`],
        ].map(([label, value]) => (
          <div key={label} className="glass-card p-3 rounded-lg">
            <p className="text-gray-500 text-xs">{label}</p>
            <p className="text-white font-semibold mt-0.5">{value || '—'}</p>
          </div>
        ))}
        <div className="glass-card p-3 rounded-lg col-span-2">
          <p className="text-gray-500 text-xs">⛓️ Blockchain TX</p>
          <p className="text-electric-blue font-mono text-xs break-all mt-0.5">{data.blockchainTxId || 'Pending'}</p>
        </div>
      </div>
      {data.batchNumber && (
        <button onClick={() => setShowLabReport(true)}
          className="mt-3 w-full py-2 rounded-lg text-sm font-semibold transition-colors border border-blue-500/30 bg-blue-500/10 text-blue-400 hover:bg-blue-500/20">
          📄 View Lab Report & AI Verification
        </button>
      )}
      {showLabReport && data.batchNumber && (
        <LabReportModal batchNumber={data.batchNumber} onClose={() => setShowLabReport(false)} />
      )}
      {data.expiryDate && (() => {
        const daysLeft = Math.ceil((new Date(data.expiryDate) - new Date()) / (1000 * 60 * 60 * 24))
        if (daysLeft < 90) return (
          <div className={`mt-3 p-3 rounded-lg ${daysLeft < 30 ? 'bg-red-500/10 border border-red-500/20' : 'bg-yellow-500/10 border border-yellow-500/20'}`}>
            <p className={`text-sm font-semibold ${daysLeft < 30 ? 'text-red-400' : 'text-yellow-400'}`}>
              ⚠️ {daysLeft < 0 ? 'EXPIRED!' : `Expires in ${daysLeft} days`}
            </p>
          </div>
        )
        return null
      })()}
    </motion.div>
  )
  return (
    <div className="bg-red-500/10 border border-red-500/20 rounded-xl p-6 mt-6">
      <h3 className="text-lg font-bold text-red-400">Verification Failed</h3>
      <p className="text-red-300 mt-2 text-sm">{result.error || 'Unknown error'}</p>
    </div>
  )
}

// ─── Guest Mode: Public Scan + CTA ──────────────────────────────────────────
function GuestMode({ autoScanSerial, onAuthSuccess }) {
  const [serialNumber, setSerialNumber] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [scannedSerial, setScannedSerial] = useState('')
  const [showAuth, setShowAuth] = useState(false)
  const [authIntent, setAuthIntent] = useState('login') // 'login' | 'register'

  useEffect(() => {
    if (autoScanSerial) {
      setSerialNumber(autoScanSerial)
      doScan(autoScanSerial)
    }
  }, [autoScanSerial])

  const doScan = async (sn) => {
    setLoading(true); setResult(null)
    try {
      const response = await scanAPI.scanUnit(sn)
      setResult({ success: response.isValid && !response.isRecalled && !response.isBlocked, data: response })
      setScannedSerial(sn)
    } catch (error) {
      setResult({ success: false, error: error.response?.data?.message || error.message || 'Scan failed' })
      setScannedSerial(sn)
    } finally { setLoading(false) }
  }

  const handleScan = (e) => {
    e.preventDefault()
    if (serialNumber.trim()) doScan(serialNumber.trim())
  }

  const handleCTA = (mode) => {
    // Save serial so after login/register it auto-fills
    if (scannedSerial) localStorage.setItem(PENDING_SERIAL_KEY, scannedSerial)
    setAuthIntent(mode)
    setShowAuth(true)
  }

  const handleAuthSuccess = (response) => {
    setShowAuth(false)
    if (onAuthSuccess) onAuthSuccess(response)
  }

  return (
    <div className="w-full">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        <h2 className="text-3xl font-bold neon-text-blue mb-1">🔍 Verify Medicine Authenticity</h2>
        <p className="text-gray-500 text-sm mb-6 font-mono">Scan QR code or enter serial number — no login required</p>

        {/* Scan Form */}
        <form onSubmit={handleScan} className="space-y-5">
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 font-semibold">Scan QR Code Image</p>
            <QRScanner onScan={(sn) => setSerialNumber(sn)} color="#00D9FF" label="Upload QR Code Photo" />
          </div>
          <div>
            <label className="block text-sm text-gray-300 mb-2">Or enter Serial Number manually</label>
            <input type="text" value={serialNumber} onChange={e => setSerialNumber(e.target.value)}
              className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
              placeholder="PAR-20240313-ABC123-D-000001-HASH" required />
          </div>
          <button type="submit" disabled={loading}
            className="cyber-btn w-full bg-gradient-to-r from-electric-blue to-neon-purple text-white py-4 rounded-xl font-bold hover:shadow-neon-blue transition-all disabled:opacity-40 text-sm uppercase tracking-wider">
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                Verifying...
              </span>
            ) : '🔍 Scan Medicine'}
          </button>
        </form>

        {/* Scan Result */}
        <AnimatePresence>
          {result && (
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
              <ScanResultCard result={result} />

              {/* Issue CTA Card */}
              <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
                className="mt-5 p-5 rounded-xl border"
                style={{ background: 'rgba(255,107,53,0.06)', border: '1px solid rgba(255,107,53,0.25)' }}>
                <div className="flex items-center gap-2 mb-3">
                  <span className="text-xl">⚠️</span>
                  <p className="text-orange-300 font-semibold text-sm">Facing any issue with this medicine?</p>
                </div>
                <div className="flex items-center gap-2 mb-4 p-2 rounded-lg bg-black/20">
                  <span className="text-xs text-gray-500">Serial:</span>
                  <span className="text-xs text-electric-blue font-mono truncate">{scannedSerial}</span>
                  {result.data?.batchNumber && (
                    <>
                      <span className="text-xs text-gray-600">·</span>
                      <span className="text-xs text-gray-500">Batch:</span>
                      <span className="text-xs text-neon-purple font-mono">{result.data.batchNumber}</span>
                    </>
                  )}
                </div>
                <p className="text-gray-500 text-xs mb-4">
                  Register or sign in to report this issue. Your complaint will be verified using your Govt ID and escalated to the Drug Regulator.
                </p>
                <div className="grid grid-cols-2 gap-3">
                  <button onClick={() => handleCTA('register')}
                    className="py-3 rounded-xl font-bold text-sm transition-all"
                    style={{ background: 'linear-gradient(135deg, rgba(255,107,53,0.2), rgba(255,107,53,0.1))', border: '1px solid rgba(255,107,53,0.4)', color: '#FF6B35' }}>
                    📝 New User? Register
                  </button>
                  <button onClick={() => handleCTA('login')}
                    className="py-3 rounded-xl font-bold text-sm transition-all"
                    style={{ background: 'rgba(0,217,255,0.08)', border: '1px solid rgba(0,217,255,0.3)', color: '#00D9FF' }}>
                    🔐 Already Registered? Sign In
                  </button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      {/* Inline Auth Modal */}
      <AnimatePresence>
        {showAuth && (
          <motion.div key="inline-auth" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-50" style={{ background: 'rgba(5,8,22,0.95)', backdropFilter: 'blur(12px)' }}>
            <button onClick={() => setShowAuth(false)}
              className="fixed top-6 left-6 z-50 flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all hover:scale-105"
              style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', color: '#9CA3AF' }}>
              ← Back
            </button>
            <AuthModal
              onAuthSuccess={handleAuthSuccess}
              allowedRoles={['PATIENT']}
              defaultMode={authIntent}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Logged-in Mode: Full Dashboard ─────────────────────────────────────────
function LoggedInMode({ onLogout }) {
  const [activeTab, setActiveTab] = useState('scan')
  const [serialNumber, setSerialNumber] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [scanHistory, setScanHistory] = useState(loadHistory)

  // Report Issue state
  const [reportSerial, setReportSerial] = useState('')
  const [reportIssueType, setReportIssueType] = useState('SIDE_EFFECT')
  const [reportDesc, setReportDesc] = useState('')
  const [reportLoading, setReportLoading] = useState(false)
  const [reportResult, setReportResult] = useState(null)
  const [reportError, setReportError] = useState('')

  // On mount: check pending serial from localStorage (set before login)
  useEffect(() => {
    const pending = localStorage.getItem(PENDING_SERIAL_KEY)
    if (pending) {
      setReportSerial(pending)
      setActiveTab('report')
      localStorage.removeItem(PENDING_SERIAL_KEY)
    }
  }, [])

  const doScan = async (sn) => {
    setLoading(true); setResult(null)
    try {
      const response = await scanAPI.scanUnit(sn)
      setResult({ success: response.isValid && !response.isRecalled && !response.isBlocked, data: response })
      setReportSerial(sn)
      const entry = {
        serialNumber: sn,
        medicineName: response.medicineName || 'Unknown',
        status: response.isRecalled ? 'RECALLED' : response.isBlocked ? 'BLOCKED' : response.isCounterfeit ? 'COUNTERFEIT' : response.isValid ? 'AUTHENTIC' : 'INVALID',
        scannedAt: new Date().toISOString(),
      }
      const updated = [entry, ...scanHistory]
      setScanHistory(updated); saveHistory(updated)
    } catch (error) {
      setResult({ success: false, error: error.response?.data?.message || error.message || 'Scan failed' })
    } finally { setLoading(false) }
  }

  const handleScan = (e) => {
    e.preventDefault()
    if (serialNumber.trim()) doScan(serialNumber.trim())
  }

  const handleReportIssue = async (e) => {
    e.preventDefault()
    if (!reportDesc.trim()) return
    setReportLoading(true); setReportResult(null); setReportError('')
    try {
      const res = await complaintAPI.raiseComplaint({
        serialNumber: reportSerial.trim(),
        batchNumber: '', medicineName: '',
        issueType: reportIssueType,
        description: reportDesc,
      })
      setReportResult(res); setReportDesc('')
    } catch (err) {
      setReportError(err.response?.data?.error || 'Failed to submit. Please try again.')
    } finally { setReportLoading(false) }
  }

  const historyStatusColor = (status) => ({
    AUTHENTIC: 'text-green-400 bg-green-400/10 border-green-400/30',
    RECALLED: 'text-red-400 bg-red-400/10 border-red-400/30',
    BLOCKED: 'text-red-400 bg-red-400/10 border-red-400/30',
    COUNTERFEIT: 'text-orange-400 bg-orange-400/10 border-orange-400/30',
    INVALID: 'text-gray-400 bg-gray-400/10 border-gray-400/30',
  }[status] || 'text-gray-400 bg-gray-400/10 border-gray-400/30')

  const userEmail = localStorage.getItem('userEmail') || ''

  return (
    <div className="w-full">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        {/* Header with logout */}
        <div className="flex items-start justify-between mb-6">
          <div>
            <h2 className="text-3xl font-bold neon-text-blue mb-1">👤 Patient Dashboard</h2>
            <p className="text-gray-500 text-sm font-mono">{userEmail}</p>
          </div>
          <button onClick={onLogout}
            className="px-4 py-2 rounded-lg text-xs font-semibold transition-all hover:scale-105"
            style={{ background: 'rgba(255,45,120,0.1)', border: '1px solid rgba(255,45,120,0.2)', color: '#FF2D78' }}>
            🚪 Logout
          </button>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 p-1 bg-dark-bg/60 rounded-xl border border-white/5">
          {[
            { id: 'scan', label: '🔍 Scan' },
            { id: 'report', label: '🚨 Report Issue' },
            { id: 'history', label: `📋 History (${scanHistory.length})` },
          ].map(tab => (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`flex-1 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.id
                  ? tab.id === 'report'
                    ? 'bg-gradient-to-r from-red-500/20 to-orange-500/20 text-orange-400 border border-orange-500/30'
                    : 'bg-gradient-to-r from-electric-blue/20 to-neon-purple/20 text-white border border-electric-blue/30'
                  : 'text-gray-500 hover:text-gray-300'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* SCAN TAB */}
        {activeTab === 'scan' && (
          <>
            <form onSubmit={handleScan} className="space-y-5">
              <div>
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 font-semibold">Scan QR Code Image</p>
                <QRScanner onScan={(sn) => setSerialNumber(sn)} color="#00D9FF" label="Upload QR Code Photo" />
              </div>
              <div>
                <label className="block text-sm text-gray-300 mb-2">Or enter Serial Number manually</label>
                <input type="text" value={serialNumber} onChange={e => setSerialNumber(e.target.value)}
                  className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
                  placeholder="PAR-20240313-ABC123-D-000001-HASH" required />
              </div>
              <button type="submit" disabled={loading}
                className="cyber-btn w-full bg-gradient-to-r from-electric-blue to-neon-purple text-white py-4 rounded-xl font-bold hover:shadow-neon-blue transition-all disabled:opacity-40 text-sm uppercase tracking-wider">
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Verifying...
                  </span>
                ) : '🔍 Scan Medicine'}
              </button>
            </form>
            <AnimatePresence>
              {result && (
                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                  <ScanResultCard result={result} />
                  {result && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
                      className="mt-4 p-4 rounded-xl border border-orange-500/20 bg-orange-500/5 flex items-center justify-between">
                      <p className="text-orange-300 text-sm">⚠️ Issue with this medicine?</p>
                      <button onClick={() => { setReportSerial(serialNumber); setActiveTab('report') }}
                        className="px-4 py-2 rounded-lg text-xs font-bold transition-all"
                        style={{ background: 'rgba(255,107,53,0.15)', border: '1px solid rgba(255,107,53,0.4)', color: '#FF6B35' }}>
                        🚨 Report Issue
                      </button>
                    </motion.div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </>
        )}

        {/* REPORT ISSUE TAB */}
        {activeTab === 'report' && (
          <div className="space-y-5">
            <div className="p-4 rounded-xl" style={{ background: 'rgba(255,107,53,0.06)', border: '1px solid rgba(255,107,53,0.2)' }}>
              <div className="flex items-start gap-3">
                <span className="text-2xl">💙</span>
                <div>
                  <p className="text-orange-300 font-semibold text-sm mb-1">We're sorry you're facing this issue</p>
                  <p className="text-gray-400 text-xs leading-relaxed">
                    PharmaTrust deeply regrets any inconvenience caused. Your health and safety is our highest priority.
                    We sincerely wish you a speedy recovery and good health. 🙏
                  </p>
                  <p className="text-gray-500 text-xs mt-2">
                    Your complaint will be reviewed by our AI system and immediately escalated to the Drug Regulator.
                    Your Govt ID ensures your complaint is taken seriously and acted upon.
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-dark-bg p-5 rounded-xl border border-red-500/20">
              <h3 className="text-white font-semibold mb-1">🚨 Report a Medicine Problem</h3>
              <p className="text-gray-500 text-xs mb-4">AI will analyze your complaint and alert the Drug Regulator automatically.</p>
              <form onSubmit={handleReportIssue} className="space-y-4">
                <div>
                  <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider font-semibold">Medicine Serial Number</label>
                  <input type="text" value={reportSerial} onChange={e => setReportSerial(e.target.value)}
                    className="w-full bg-black/30 border border-red-500/20 rounded-lg px-4 py-3 text-white focus:border-red-400 focus:outline-none font-mono text-sm"
                    placeholder="UNIT-PAR-... (auto-filled after scan)" />
                  <p className="text-xs text-gray-600 mt-1">Scan a medicine first to auto-fill this field</p>
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider font-semibold">Issue Type *</label>
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      { value: 'SIDE_EFFECT', label: '😷 Side Effect / Reaction' },
                      { value: 'WRONG_MEDICINE', label: '💊 Wrong Medicine Given' },
                      { value: 'SEAL_BROKEN', label: '🔓 Seal Broken / Tampered' },
                      { value: 'SUSPICIOUS_APPEARANCE', label: '🚨 Looks Suspicious / Fake' },
                      { value: 'EXPIRED_STOCK', label: '⏰ Expired Medicine' },
                      { value: 'OTHER', label: '📝 Other Problem' },
                    ].map(opt => (
                      <button key={opt.value} type="button" onClick={() => setReportIssueType(opt.value)}
                        className={`px-3 py-2 rounded-lg text-xs font-medium transition-all text-left ${
                          reportIssueType === opt.value
                            ? 'bg-red-500/20 text-red-300 border border-red-500/40'
                            : 'bg-black/20 text-gray-500 border border-white/5 hover:text-gray-300'
                        }`}>
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider font-semibold">Describe Your Problem *</label>
                  <textarea value={reportDesc} onChange={e => setReportDesc(e.target.value)} rows={4}
                    className="w-full bg-black/30 border border-red-500/20 rounded-lg px-4 py-3 text-white focus:border-red-400 focus:outline-none resize-none text-sm"
                    placeholder="e.g. After taking this tablet I felt dizzy and had rashes. The tablet color also looked different from usual..."
                    required />
                  <p className="text-xs text-gray-600 mt-1">🤖 AI will detect severity and alert regulator with your verified identity</p>
                </div>
                {reportError && <p className="text-red-400 text-sm">❌ {reportError}</p>}
                <button type="submit" disabled={reportLoading || !reportDesc.trim()}
                  className="w-full py-3 rounded-xl font-bold text-sm transition-all disabled:opacity-50"
                  style={{ background: 'linear-gradient(135deg, #ef4444, #f97316)', color: 'white' }}>
                  {reportLoading ? '⏳ Submitting to Regulator...' : '🚨 Submit Complaint to Drug Regulator'}
                </button>
              </form>
              {reportResult && (
                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                  className="mt-4 p-4 rounded-xl border border-orange-500/30 bg-orange-500/10">
                  <p className="text-orange-400 font-bold mb-2">✅ Complaint Submitted Successfully</p>
                  <p className="text-gray-300 text-sm">{reportResult.analysis}</p>
                  {reportResult.severity && (
                    <span className={`mt-2 inline-block px-3 py-1 rounded-full text-xs font-bold ${
                      reportResult.severity === 'CRITICAL' ? 'bg-red-500/20 text-red-400' :
                      reportResult.severity === 'HIGH' ? 'bg-orange-500/20 text-orange-400' :
                      'bg-yellow-500/20 text-yellow-400'
                    }`}>Severity: {reportResult.severity}</span>
                  )}
                  <p className="text-gray-500 text-xs mt-2">Regulator has been notified. You may be contacted for follow-up.</p>
                </motion.div>
              )}
            </div>

            {/* Emergency Contacts */}
            <div className="bg-dark-bg p-5 rounded-xl border border-red-500/20">
              <div className="flex items-center gap-2 mb-4">
                <span className="text-xl">📞</span>
                <div>
                  <h3 className="text-white font-semibold text-sm">Emergency Health Contacts</h3>
                  <p className="text-gray-500 text-xs">For serious issues, contact these government helplines immediately</p>
                </div>
              </div>
              <div className="space-y-3">
                {EMERGENCY_CONTACTS.map((c, i) => (
                  <motion.div key={i} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.05 }}
                    className="flex items-center justify-between p-3 rounded-lg"
                    style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                    <div className="flex items-center gap-3">
                      <span className="text-xl">{c.icon}</span>
                      <div>
                        <p className="text-white text-sm font-semibold">{c.name}</p>
                        <p className="text-gray-500 text-xs">{c.desc}</p>
                      </div>
                    </div>
                    <a href={`tel:${c.number}`}
                      className="px-4 py-2 rounded-lg font-bold text-sm transition-all"
                      style={{ background: 'rgba(239,68,68,0.15)', color: '#f87171', border: '1px solid rgba(239,68,68,0.3)' }}>
                      📞 {c.number}
                    </a>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Disclaimer */}
            <div className="p-4 rounded-xl text-center" style={{ background: 'rgba(0,217,255,0.04)', border: '1px solid rgba(0,217,255,0.1)' }}>
              <p className="text-gray-500 text-xs leading-relaxed">
                ⚠️ <span className="text-gray-400 font-semibold">Disclaimer:</span> PharmaTrust is a medicine tracking platform.
                In case of medical emergency, please call <span className="text-red-400 font-bold">108</span> immediately.
                Do not rely solely on this app for medical decisions. Always consult a qualified doctor.
              </p>
              <p className="text-gray-600 text-xs mt-2">
                Your complaint is verified using your registered Govt ID to prevent misuse and ensure genuine reports reach the regulator.
              </p>
            </div>
          </div>
        )}

        {/* HISTORY TAB */}
        {activeTab === 'history' && (
          <div>
            <div className="flex justify-between items-center mb-4">
              <p className="text-gray-400 text-sm">Last 20 scans (stored locally)</p>
              {scanHistory.length > 0 && (
                <button onClick={() => { setScanHistory([]); saveHistory([]) }}
                  className="px-3 py-1 text-xs bg-red-500/20 text-red-400 border border-red-500/30 rounded hover:bg-red-500/30 transition-all">
                  🗑️ Clear History
                </button>
              )}
            </div>
            {scanHistory.length === 0 ? (
              <div className="text-center py-12 text-gray-600 font-mono text-sm">No scan history yet</div>
            ) : (
              <div className="space-y-2">
                {scanHistory.map((entry, i) => (
                  <motion.div key={i} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.03 }}
                    className="glass-card p-3 rounded-xl border border-white/5 flex justify-between items-center">
                    <div>
                      <p className="text-white text-sm font-semibold">{entry.medicineName}</p>
                      <p className="text-gray-500 text-xs font-mono">{entry.serialNumber}</p>
                      <p className="text-gray-600 text-xs">{new Date(entry.scannedAt).toLocaleString()}</p>
                    </div>
                    <span className={`px-2 py-1 rounded border text-xs font-semibold ${historyStatusColor(entry.status)}`}>
                      {entry.status}
                    </span>
                  </motion.div>
                ))}
              </div>
            )}
          </div>
        )}
      </motion.div>
    </div>
  )
}

// ─── Main Export: Dual-mode Patient Panel ───────────────────────────────────
export default function PatientPanel({ autoScanSerial }) {
  const [isLoggedIn, setIsLoggedIn] = useState(() => isValidToken(localStorage.getItem('authToken')))

  const handleAuthSuccess = () => {
    setIsLoggedIn(true)
  }

  const handleLogout = () => {
    localStorage.removeItem('authToken')
    localStorage.removeItem('userRole')
    localStorage.removeItem('userEmail')
    setIsLoggedIn(false)
  }

  return isLoggedIn
    ? <LoggedInMode onLogout={handleLogout} />
    : <GuestMode autoScanSerial={autoScanSerial} onAuthSuccess={handleAuthSuccess} />
}
