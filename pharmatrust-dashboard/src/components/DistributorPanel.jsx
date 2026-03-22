import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { transferAPI, verifyAPI, complaintAPI, authAPI } from '../services/api'
import AuthModal from './AuthModal'
import QRScanner from './QRScanner'

export default function DistributorPanel() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [activeTab, setActiveTab] = useState('receive')
  const [verificationStatus, setVerificationStatus] = useState(null)

  const [myStock, setMyStock] = useState(null)
  const [myTransfers, setMyTransfers] = useState([])
  const [loading, setLoading] = useState(false)

  // Receive stock
  const [receiveSerial, setReceiveSerial] = useState('')
  const [receiveNotes, setReceiveNotes] = useState('')
  const [receiveLoading, setReceiveLoading] = useState(false)
  const [receiveResult, setReceiveResult] = useState(null)
  const [receiveError, setReceiveError] = useState('')
  const [receiveInputMode, setReceiveInputMode] = useState('manual')

  // Forward transfer
  const [fwdSerial, setFwdSerial] = useState('')
  const [fwdToEmail, setFwdToEmail] = useState('')
  const [fwdNotes, setFwdNotes] = useState('')
  const [fwdLoading, setFwdLoading] = useState(false)
  const [fwdResult, setFwdResult] = useState(null)
  const [fwdInputMode, setFwdInputMode] = useState('manual')

  // Inspect & Report
  const [inspectSerial, setInspectSerial] = useState('')
  const [inspectInfo, setInspectInfo] = useState(null)
  const [inspectLoading, setInspectLoading] = useState(false)
  const [inspectError, setInspectError] = useState('')
  const [issueType, setIssueType] = useState('DAMAGED_BOX')
  const [issueDesc, setIssueDesc] = useState('')
  const [complaintLoading, setComplaintLoading] = useState(false)
  const [complaintResult, setComplaintResult] = useState(null)
  const [myComplaints, setMyComplaints] = useState([])

  useEffect(() => {
    const token = localStorage.getItem('authToken')
    const storedRole = localStorage.getItem('userRole')
    // Only auto-login if token is valid JWT AND role matches DISTRIBUTOR
    if (token && token.split('.').length === 3 && storedRole === 'DISTRIBUTOR') {
      setIsAuthenticated(true)
      loadData()
    } else {
      localStorage.removeItem('authToken')
      localStorage.removeItem('userRole')
      localStorage.removeItem('username')
      setShowAuthModal(true)
    }
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [stockRes, transfersRes, complaintsRes, verifyRes] = await Promise.allSettled([
        transferAPI.getMyStock(),
        transferAPI.getMyTransfers(),
        complaintAPI.getMyComplaints(),
        authAPI.getMyVerificationStatus(),
      ])
      if (stockRes.status === 'fulfilled') setMyStock(stockRes.value)
      if (transfersRes.status === 'fulfilled') setMyTransfers(Array.isArray(transfersRes.value) ? transfersRes.value : [])
      if (complaintsRes.status === 'fulfilled') setMyComplaints(Array.isArray(complaintsRes.value) ? complaintsRes.value : [])
      if (verifyRes.status === 'fulfilled') setVerificationStatus(verifyRes.value)
    } finally { setLoading(false) }
  }

  const handleAuthSuccess = (response) => {
    if (response?.role) localStorage.setItem('userRole', response.role)
    if (response?.email) localStorage.setItem('username', response.email)
    setShowAuthModal(false)
    setIsAuthenticated(true)
    loadData()
  }
  const handleLogout = () => {
    localStorage.removeItem('authToken'); localStorage.removeItem('userRole'); localStorage.removeItem('username'); localStorage.removeItem('userEmail')
    setIsAuthenticated(false); setShowAuthModal(true)
  }

  const handleReceive = async (e) => {
    e.preventDefault()
    if (!receiveSerial.trim()) { setReceiveError('Enter a serial number'); return }
    setReceiveLoading(true); setReceiveResult(null); setReceiveError('')
    try {
      const data = await transferAPI.receiveStock(receiveSerial.trim(), receiveNotes)
      setReceiveResult(data)
      setReceiveSerial(''); setReceiveNotes('')
      // Immediately refresh stock data
      await loadData()
    } catch (err) {
      setReceiveError(err.response?.data?.error || 'Receive failed')
    } finally { setReceiveLoading(false) }
  }

  const handleReceiveAnother = () => {
    setReceiveResult(null)
    setReceiveSerial('')
    setReceiveNotes('')
    setReceiveError('')
  }

  const handleInspectLookup = async (e) => {
    e.preventDefault()
    if (!inspectSerial.trim()) return
    setInspectLoading(true); setInspectInfo(null); setInspectError(''); setComplaintResult(null)
    try {
      const data = await verifyAPI.verifyUnit(inspectSerial.trim())
      setInspectInfo(data)
    } catch (err) {
      setInspectError(err.response?.data?.error || 'Serial number not found')
    } finally { setInspectLoading(false) }
  }

  const handleRaiseComplaint = async (e) => {
    e.preventDefault()
    if (!issueDesc.trim()) return
    setComplaintLoading(true); setComplaintResult(null)
    try {
      const result = await complaintAPI.raiseComplaint({
        serialNumber: inspectSerial.trim(),
        batchNumber: inspectInfo?.batchNumber || '',
        medicineName: inspectInfo?.medicineName || '',
        issueType,
        description: issueDesc,
      })
      setComplaintResult(result)
      setIssueDesc('')
      loadData()
    } catch (err) {
      setComplaintResult({ error: err.response?.data?.error || 'Failed to submit complaint' })
    } finally { setComplaintLoading(false) }
  }

  const handleForwardTransfer = async (e) => {
    e.preventDefault()
    setFwdLoading(true); setFwdResult(null)
    try {
      const result = await transferAPI.initiateTransfer({
        serialNumber: fwdSerial,
        toUserEmail: fwdToEmail,
        notes: fwdNotes,
        transferType: 'DISTRIBUTOR_TO_PHARMACY',
      })
      setFwdResult({ success: true, data: result })
      setFwdSerial(''); setFwdToEmail(''); setFwdNotes('')
      await loadData()
    } catch (err) {
      setFwdResult({ success: false, error: err.response?.data?.error || 'Transfer failed' })
    } finally { setFwdLoading(false) }
  }

  return (
    <div className="w-full">
      {showAuthModal && <AuthModal onAuthSuccess={handleAuthSuccess} allowedRoles={['DISTRIBUTOR']} />}

      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        <div className="flex justify-between items-center mb-6">
          <div>
            <h2 className="text-3xl font-bold neon-text-blue">🚚 Distributor Dashboard</h2>
            {myStock?.shopName && (
              <p className="text-gray-500 text-sm mt-1 font-mono">🏪 {myStock.shopName} · {myStock.ownerEmail}</p>
            )}
            {verificationStatus?.isVerified ? (
              <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
                className="mt-2 inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-neon-green/10 border border-neon-green/40"
                style={{ boxShadow: '0 0 12px rgba(0,255,136,0.15)' }}>
                <div className="w-2 h-2 rounded-full bg-neon-green animate-pulse" />
                <span className="text-neon-green text-sm font-semibold">✅ Verified by PharmaTrust</span>
                {verificationStatus.verifiedAt && (
                  <span className="text-gray-400 text-xs">· {new Date(verificationStatus.verifiedAt).toLocaleDateString()}</span>
                )}
              </motion.div>
            ) : (
              <div className="mt-2 inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-yellow-500/10 border border-yellow-500/30">
                <div className="w-2 h-2 rounded-full bg-yellow-400" />
                <span className="text-yellow-400 text-sm">⏳ Pending Regulator Verification</span>
              </div>
            )}
          </div>
          <div className="flex gap-3">
            <button onClick={loadData} disabled={loading}
              className="px-4 py-2 bg-electric-blue/20 text-electric-blue border border-electric-blue/50 rounded-lg hover:bg-electric-blue/30 transition-all text-sm">
              {loading ? '⏳' : '🔄'} Refresh
            </button>
            {isAuthenticated && (
              <button onClick={handleLogout} className="px-4 py-2 bg-red-500/20 text-red-400 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-all text-sm">
                Logout
              </button>
            )}
          </div>
        </div>

        {/* My Stock Summary */}
        {myStock && (
          <div className="grid grid-cols-3 gap-4 mb-6">
            <div className="bg-dark-bg p-4 rounded-lg border border-electric-blue/30 text-center">
              <p className="text-gray-400 text-xs mb-1">Total Units in Stock</p>
              <p className="text-3xl font-bold text-electric-blue">{myStock.totalUnits || 0}</p>
            </div>
            <div className="bg-dark-bg p-4 rounded-lg border border-neon-green/30 text-center">
              <p className="text-gray-400 text-xs mb-1">Batches</p>
              <p className="text-3xl font-bold text-neon-green">{myStock.stockByBatch?.length || 0}</p>
            </div>
            <div className="bg-dark-bg p-4 rounded-lg border border-yellow-500/30 text-center">
              <p className="text-gray-400 text-xs mb-1">Transfers Made</p>
              <p className="text-3xl font-bold text-yellow-400">{myTransfers.length}</p>
            </div>
          </div>
        )}

        {/* Tabs */}
        <div className="flex gap-1.5 mb-6 p-1 bg-dark-bg/60 rounded-xl border border-white/5 flex-wrap">
          {[
            { id: 'receive', label: '📥 Receive' },
            { id: 'stock', label: '📦 My Stock' },
            { id: 'forward', label: '➡️ Forward' },
            { id: 'inspect', label: '🔍 Inspect' },
            { id: 'history', label: '📋 History' },
          ].map(tab => (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-electric-blue/20 to-neon-purple/20 text-white border border-electric-blue/30'
                  : 'text-gray-500 hover:text-gray-300'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* RECEIVE STOCK TAB */}
        {activeTab === 'receive' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-electric-blue/30">
              <h3 className="text-lg font-semibold text-white mb-1">📥 Receive Medicine Stock</h3>
              <p className="text-gray-400 text-sm mb-4">
                Receive a box/carton from the manufacturer. Use QR scanner or enter serial number manually.
              </p>

              {/* Input Mode Toggle */}
              <div className="flex gap-1 mb-4 p-1 bg-black/30 rounded-lg border border-electric-blue/20 w-fit">
                <button
                  onClick={() => setReceiveInputMode('manual')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    receiveInputMode === 'manual'
                      ? 'bg-electric-blue/20 text-electric-blue border border-electric-blue/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>
                  ⌨️ Manual Entry
                </button>
                <button
                  onClick={() => setReceiveInputMode('scanner')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    receiveInputMode === 'scanner'
                      ? 'bg-electric-blue/20 text-electric-blue border border-electric-blue/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>
                  📷 QR Scanner
                </button>
              </div>

              <form onSubmit={handleReceive} className="space-y-3">
                {receiveInputMode === 'scanner' ? (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Scan QR Code Image</label>
                    <QRScanner
                      onScan={sn => { setReceiveSerial(sn); setReceiveError('') }}
                      color="#00D9FF"
                      label="Upload Box QR Code Image"
                    />
                    {receiveSerial && (
                      <div className="mt-2 px-3 py-2 bg-electric-blue/10 border border-electric-blue/30 rounded-lg">
                        <p className="text-xs text-gray-400">Scanned Serial:</p>
                        <p className="text-electric-blue font-mono text-sm">{receiveSerial}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Box / Carton Serial Number *</label>
                    <input
                      type="text"
                      value={receiveSerial}
                      onChange={e => { setReceiveSerial(e.target.value); setReceiveError('') }}
                      className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
                      placeholder="BOX-PAR-20240313-..."
                      disabled={receiveLoading}
                      required
                    />
                  </div>
                )}
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Notes (optional)</label>
                  <input
                    type="text"
                    value={receiveNotes}
                    onChange={e => setReceiveNotes(e.target.value)}
                    className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                    placeholder="e.g. Received from Delhi warehouse"
                    disabled={receiveLoading}
                  />
                </div>
                {receiveError && <p className="text-red-400 text-sm">{receiveError}</p>}
                <button type="submit" disabled={receiveLoading || !receiveSerial.trim()}
                  className="w-full bg-gradient-to-r from-electric-blue to-purple-600 text-white py-3 rounded-lg font-semibold hover:shadow-lg transition-all disabled:opacity-50">
                  {receiveLoading ? '⏳ Processing...' : '✅ Confirm Receipt'}
                </button>
              </form>
            </div>

            {receiveResult && (
              <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
                className="bg-neon-green/10 border border-neon-green/30 rounded-lg p-5">
                <p className="text-neon-green font-bold text-lg mb-3">✅ Stock Received Successfully</p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Medicine</p>
                    <p className="text-white font-semibold">{receiveResult.medicineName}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Batch</p>
                    <p className="text-white font-mono text-xs">{receiveResult.batchNumber}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Unit Type</p>
                    <p className="text-white">{receiveResult.unitType}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Total Units Received</p>
                    <p className="text-2xl font-bold text-neon-green">{receiveResult.totalUnitsReceived}</p>
                  </div>
                </div>
                <p className="text-gray-400 text-xs mt-3">
                  Registered under: <span className="text-white">{receiveResult.shopName || receiveResult.receivedBy}</span>
                </p>
                <button onClick={handleReceiveAnother}
                  className="mt-4 w-full py-2.5 rounded-lg font-semibold text-sm transition-all"
                  style={{ background: 'rgba(0,217,255,0.1)', border: '1px solid rgba(0,217,255,0.3)', color: '#00D9FF' }}>
                  📥 Receive Another Product
                </button>
              </motion.div>
            )}
          </div>
        )}

        {/* MY STOCK TAB */}
        {activeTab === 'stock' && (
          <div>
            {!myStock || myStock.stockByBatch?.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No stock yet. Receive boxes to see them here.</div>
            ) : (
              <>
                {/* Group by medicine name */}
                {(() => {
                  const grouped = {}
                  myStock.stockByBatch.forEach(b => {
                    const key = b.medicineName || 'Unknown'
                    if (!grouped[key]) grouped[key] = []
                    grouped[key].push(b)
                  })
                  return Object.entries(grouped).map(([medicine, batches]) => (
                    <div key={medicine} className="mb-6">
                      <div className="flex items-center gap-2 mb-3">
                        <span className="text-lg">💊</span>
                        <h3 className="text-white font-bold text-base">{medicine}</h3>
                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-electric-blue/10 text-electric-blue border border-electric-blue/20">
                          {batches.reduce((sum, b) => sum + (b.unitCount || 0), 0)} units total
                        </span>
                      </div>
                      <div className="space-y-2 pl-2 border-l-2 border-electric-blue/20">
                        {batches.map((b, i) => (
                          <motion.div key={i} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.05 }}
                            className="glass-card p-4 rounded-xl border border-white/5 metric-card">
                            <div className="flex justify-between items-start">
                              <div>
                                <p className="text-gray-400 text-xs font-mono">{b.batchNumber}</p>
                                <p className="text-gray-500 text-xs mt-0.5">Expires: {b.expiryDate}</p>
                              </div>
                              <div className="text-right">
                                <p className="text-2xl font-bold text-electric-blue">{b.unitCount}</p>
                                <p className="text-gray-600 text-xs">units</p>
                                <span className={`text-xs px-2 py-0.5 rounded mt-1 inline-block ${
                                  b.batchStatus === 'ACTIVE' ? 'bg-neon-green/10 text-neon-green border border-neon-green/20'
                                  : 'bg-red-500/10 text-red-400 border border-red-500/20'
                                }`}>{b.batchStatus}</span>
                              </div>
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  ))
                })()}
              </>
            )}
          </div>
        )}

        {/* FORWARD TO RETAILER TAB */}
        {activeTab === 'forward' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-yellow-500/30">
              <h3 className="text-lg font-semibold text-white mb-1">➡️ Forward Stock to Retailer</h3>
              <p className="text-gray-400 text-sm mb-4">Transfer a box/unit to a retailer or pharmacy. Use QR scanner or enter serial number manually.</p>

              {/* Input Mode Toggle */}
              <div className="flex gap-1 mb-4 p-1 bg-black/30 rounded-lg border border-yellow-500/20 w-fit">
                <button
                  onClick={() => setFwdInputMode('manual')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    fwdInputMode === 'manual'
                      ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>
                  ⌨️ Manual Entry
                </button>
                <button
                  onClick={() => setFwdInputMode('scanner')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    fwdInputMode === 'scanner'
                      ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>
                  📷 QR Scanner
                </button>
              </div>

              <form onSubmit={handleForwardTransfer} className="space-y-3">
                {fwdInputMode === 'scanner' ? (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Scan QR Code Image</label>
                    <QRScanner
                      onScan={sn => setFwdSerial(sn)}
                      color="#EAB308"
                      label="Upload Box/Unit QR Code Image"
                    />
                    {fwdSerial && (
                      <div className="mt-2 px-3 py-2 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                        <p className="text-xs text-gray-400">Scanned Serial:</p>
                        <p className="text-yellow-400 font-mono text-sm">{fwdSerial}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Serial Number *</label>
                    <input type="text" value={fwdSerial}
                      onChange={e => setFwdSerial(e.target.value)}
                      className="w-full bg-dark-bg border border-yellow-500/30 rounded-lg px-4 py-3 text-white focus:border-yellow-500 focus:outline-none font-mono"
                      placeholder="BOX-PAR-..." required disabled={fwdLoading} />
                  </div>
                )}
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Retailer Email *</label>
                  <input type="email" value={fwdToEmail}
                    onChange={e => setFwdToEmail(e.target.value)}
                    className="w-full bg-dark-bg border border-yellow-500/30 rounded-lg px-4 py-3 text-white focus:border-yellow-500 focus:outline-none"
                    placeholder="retailer@example.com" required disabled={fwdLoading} />
                </div>
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Notes (optional)</label>
                  <input type="text" value={fwdNotes}
                    onChange={e => setFwdNotes(e.target.value)}
                    className="w-full bg-dark-bg border border-yellow-500/30 rounded-lg px-4 py-3 text-white focus:border-yellow-500 focus:outline-none"
                    placeholder="Delivery notes..." disabled={fwdLoading} />
                </div>
                <button type="submit" disabled={fwdLoading}
                  className="w-full bg-yellow-500 text-black font-bold py-3 rounded-lg hover:bg-yellow-400 transition-all disabled:opacity-50">
                  {fwdLoading ? '⏳ Transferring...' : '➡️ Transfer to Retailer'}
                </button>
              </form>
              {fwdResult && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                  className={`mt-4 p-3 rounded-lg ${fwdResult.success ? 'bg-neon-green/10 border border-neon-green/30' : 'bg-red-500/10 border border-red-500/30'}`}>
                  {fwdResult.success
                    ? <p className="text-neon-green text-sm font-semibold">✅ Transfer initiated successfully</p>
                    : <p className="text-red-400 text-sm">❌ {fwdResult.error}</p>}
                </motion.div>
              )}
            </div>
          </div>
        )}

        {/* INSPECT & REPORT TAB */}
        {activeTab === 'inspect' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-orange-500/30">
              <h3 className="text-lg font-semibold text-white mb-1">🔍 Manual Inspection</h3>
              <p className="text-gray-400 text-sm mb-4">Enter the box serial number to inspect it, then report any issues found.</p>
              <div className="mb-3">
                <label className="block text-sm text-gray-300 mb-2">Scan QR Code (optional)</label>
                <QRScanner
                  onScan={sn => setInspectSerial(sn)}
                  color="#F97316"
                  label="Upload QR Code to Inspect"
                />
              </div>
              <form onSubmit={handleInspectLookup} className="flex gap-3">
                <input type="text" value={inspectSerial} onChange={e => setInspectSerial(e.target.value)}
                  className="flex-1 bg-dark-bg border border-orange-500/30 rounded-lg px-4 py-3 text-white focus:border-orange-500 focus:outline-none font-mono"
                  placeholder="BOX-PAR-... or UNIT-..." disabled={inspectLoading} />
                <button type="submit" disabled={inspectLoading}
                  className="px-6 py-3 bg-orange-500 text-black font-bold rounded-lg hover:bg-orange-400 transition-all disabled:opacity-50">
                  {inspectLoading ? '⏳' : '🔍 Lookup'}
                </button>
              </form>
              {inspectError && <p className="text-red-400 text-sm mt-2">{inspectError}</p>}
            </div>

            {inspectInfo && (
              <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                className="bg-dark-bg p-5 rounded-lg border border-electric-blue/30 space-y-4">
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Medicine</p>
                    <p className="text-white font-semibold">{inspectInfo.medicineName || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Batch</p>
                    <p className="text-white font-mono text-xs">{inspectInfo.batchNumber || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Status</p>
                    <p className={`font-semibold ${inspectInfo.status === 'ACTIVE' ? 'text-neon-green' : 'text-red-400'}`}>{inspectInfo.status}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-3 rounded">
                    <p className="text-gray-400 text-xs">Expiry</p>
                    <p className="text-white">{inspectInfo.expiryDate || 'N/A'}</p>
                  </div>
                </div>

                <div className="border-t border-orange-500/20 pt-4">
                  <p className="text-orange-400 font-semibold text-sm mb-3">⚠️ Report an Issue</p>
                  <form onSubmit={handleRaiseComplaint} className="space-y-3">
                    <div>
                      <label className="block text-sm text-gray-300 mb-1">Issue Type *</label>
                      <select value={issueType} onChange={e => setIssueType(e.target.value)}
                        className="w-full bg-dark-bg border border-orange-500/30 rounded-lg px-4 py-2 text-white focus:border-orange-500 focus:outline-none">
                        <option value="DAMAGED_BOX">📦 Damaged Box / Packaging</option>
                        <option value="TEMPERATURE_ISSUE">🌡️ Temperature / Storage Issue</option>
                        <option value="SEAL_BROKEN">🔓 Seal Broken / Tampered</option>
                        <option value="WRONG_MEDICINE">💊 Wrong Medicine</option>
                        <option value="EXPIRED_STOCK">⏰ Expired Stock</option>
                        <option value="SUSPICIOUS_APPEARANCE">🚨 Suspicious Appearance</option>
                        <option value="QUANTITY_MISMATCH">🔢 Quantity Mismatch</option>
                        <option value="OTHER">📝 Other Issue</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-sm text-gray-300 mb-1">Describe the Issue *</label>
                      <textarea value={issueDesc} onChange={e => setIssueDesc(e.target.value)} rows={4}
                        className="w-full bg-dark-bg border border-orange-500/30 rounded-lg px-4 py-3 text-white focus:border-orange-500 focus:outline-none resize-none"
                        placeholder="Describe what you found in detail. e.g. Box arrived with broken seal, medicine tablets appear discolored and have unusual smell..."
                        required />
                      <p className="text-xs text-gray-500 mt-1">AI will analyze your description and alert the regulator automatically.</p>
                    </div>
                    <button type="submit" disabled={complaintLoading || !issueDesc.trim()}
                      className="w-full bg-orange-500 text-black font-bold py-3 rounded-lg hover:bg-orange-400 transition-all disabled:opacity-50">
                      {complaintLoading ? '⏳ Submitting...' : '🚨 Submit Complaint to Regulator'}
                    </button>
                  </form>
                </div>

                {complaintResult && (
                  <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                    className={`p-4 rounded-lg ${complaintResult.error ? 'bg-red-500/10 border border-red-500/30' : 'bg-orange-500/10 border border-orange-500/30'}`}>
                    {complaintResult.error ? (
                      <p className="text-red-400 text-sm">❌ {complaintResult.error}</p>
                    ) : (
                      <div className="space-y-2 text-sm">
                        <p className="text-orange-400 font-bold">✅ Complaint Submitted</p>
                        <p className="text-gray-300">{complaintResult.analysis}</p>
                        <div className="flex gap-4 text-xs mt-2">
                          <span className={`px-2 py-1 rounded font-semibold ${
                            complaintResult.severity === 'CRITICAL' ? 'bg-red-500/20 text-red-400' :
                            complaintResult.severity === 'HIGH' ? 'bg-orange-500/20 text-orange-400' :
                            complaintResult.severity === 'MEDIUM' ? 'bg-yellow-500/20 text-yellow-400' :
                            'bg-gray-500/20 text-gray-400'
                          }`}>Severity: {complaintResult.severity}</span>
                          <span className="text-gray-400">Status: {complaintResult.status}</span>
                        </div>
                        <p className="text-gray-500 text-xs">Complaint ID: {complaintResult.complaintId}</p>
                      </div>
                    )}
                  </motion.div>
                )}
              </motion.div>
            )}

            {/* My past complaints */}
            {myComplaints.length > 0 && (
              <div>
                <p className="text-gray-400 text-sm font-semibold mb-2">📋 My Previous Complaints</p>
                <div className="space-y-2">
                  {myComplaints.slice(0, 5).map((c, i) => (
                    <div key={c.id || i} className="bg-dark-bg p-3 rounded-lg border border-orange-500/20 text-sm">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="text-white font-mono text-xs">{c.serialNumber}</p>
                          <p className="text-gray-400 text-xs mt-0.5">{c.issueType?.replace(/_/g, ' ')} — {c.medicineName}</p>
                          <p className="text-gray-500 text-xs">{c.createdAt ? new Date(c.createdAt).toLocaleString() : ''}</p>
                        </div>
                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          c.aiSeverity === 'CRITICAL' ? 'bg-red-500/20 text-red-400' :
                          c.aiSeverity === 'HIGH' ? 'bg-orange-500/20 text-orange-400' :
                          c.aiSeverity === 'MEDIUM' ? 'bg-yellow-500/20 text-yellow-400' :
                          'bg-gray-500/20 text-gray-400'
                        }`}>{c.aiSeverity || 'LOW'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* TRANSFER HISTORY TAB */}
        {activeTab === 'history' && (
          <div className="space-y-3">
            {myTransfers.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No transfer history</div>
            ) : myTransfers.map((t, i) => (
              <div key={t.id || i} className="bg-dark-bg p-4 rounded-lg border border-electric-blue/20">
                <div className="flex justify-between items-center">
                  <div>
                    <p className="text-white font-mono text-sm">{t.serialNumber}</p>
                    <p className="text-gray-400 text-xs mt-1">{t.fromUsername || t.from} → {t.toUsername || t.to}</p>
                    <p className="text-gray-500 text-xs">{t.createdAt ? new Date(t.createdAt).toLocaleString() : ''}</p>
                  </div>
                  <span className={`px-2 py-1 rounded border text-xs font-semibold ${
                    t.status === 'COMPLETED' || t.status === 'ACCEPTED' ? 'text-green-400 border-green-400/40 bg-green-400/10'
                    : t.status === 'PENDING' ? 'text-yellow-400 border-yellow-400/40 bg-yellow-400/10'
                    : 'text-red-400 border-red-400/40 bg-red-400/10'
                  }`}>{t.status || 'DONE'}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </motion.div>
    </div>
  )
}
