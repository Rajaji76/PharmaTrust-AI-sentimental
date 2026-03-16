import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { transferAPI, verifyAPI, complaintAPI } from '../services/api'
import AuthModal from './AuthModal'
import QRScanner from './QRScanner'

export default function RetailerPanel() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [activeTab, setActiveTab] = useState('receive')

  const [myStock, setMyStock] = useState(null)
  const [myTransfers, setMyTransfers] = useState([])
  const [loading, setLoading] = useState(false)

  // Receive stock from distributor
  const [receiveSerial, setReceiveSerial] = useState('')
  const [receiveNotes, setReceiveNotes] = useState('')
  const [receiveLoading, setReceiveLoading] = useState(false)
  const [receiveResult, setReceiveResult] = useState(null)
  const [receiveError, setReceiveError] = useState('')

  // Sell / dispense to patient
  const [sellSerial, setSellSerial] = useState('')
  const [sellPatientEmail, setSellPatientEmail] = useState('')
  const [sellNotes, setSellNotes] = useState('')
  const [sellLoading, setSellLoading] = useState(false)
  const [sellResult, setSellResult] = useState(null)

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
    // Only auto-login if token is valid JWT AND role matches RETAILER or PHARMACIST
    if (token && token.split('.').length === 3 && (storedRole === 'RETAILER' || storedRole === 'PHARMACIST')) {
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
      const [stockRes, transfersRes, complaintsRes] = await Promise.allSettled([
        transferAPI.getMyStock(),
        transferAPI.getMyTransfers(),
        complaintAPI.getMyComplaints(),
      ])
      if (stockRes.status === 'fulfilled') setMyStock(stockRes.value)
      if (transfersRes.status === 'fulfilled') setMyTransfers(Array.isArray(transfersRes.value) ? transfersRes.value : [])
      if (complaintsRes.status === 'fulfilled') setMyComplaints(Array.isArray(complaintsRes.value) ? complaintsRes.value : [])
    } finally { setLoading(false) }
  }

  const handleAuthSuccess = () => { setIsAuthenticated(true); setShowAuthModal(false); loadData() }
  const handleLogout = () => {
    localStorage.removeItem('authToken'); localStorage.removeItem('userRole'); localStorage.removeItem('username')
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
      setTimeout(loadData, 500)
    } catch (err) {
      setReceiveError(err.response?.data?.error || 'Receive failed')
    } finally { setReceiveLoading(false) }
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

  const handleSellToPatient = async (e) => {
    e.preventDefault()
    setSellLoading(true); setSellResult(null)
    try {
      const result = await transferAPI.initiateTransfer({
        serialNumber: sellSerial,
        toUserEmail: sellPatientEmail,
        notes: sellNotes,
        transferType: 'PHARMACY_TO_PATIENT',
      })
      setSellResult({ success: true, data: result })
      setSellSerial(''); setSellPatientEmail(''); setSellNotes('')
      setTimeout(loadData, 500)
    } catch (err) {
      setSellResult({ success: false, error: err.response?.data?.error || 'Transfer failed' })
    } finally { setSellLoading(false) }
  }

  return (
    <div className="w-full">
      {showAuthModal && <AuthModal onAuthSuccess={handleAuthSuccess} allowedRoles={['RETAILER', 'PHARMACIST']} />}

      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        <div className="flex justify-between items-center mb-6">
          <div>
            <h2 className="text-3xl font-bold neon-text-blue">🏪 Retailer / Pharmacy Dashboard</h2>
            {myStock?.shopName && (
              <p className="text-gray-500 text-sm mt-1 font-mono">🏪 {myStock.shopName} · {myStock.ownerEmail}</p>
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

        {/* Stock Summary */}
        {myStock && (
          <div className="grid grid-cols-3 gap-4 mb-6">
            {[
              { label: 'Units in Stock', value: myStock.totalUnits || 0, color: 'electric-blue', icon: '💊' },
              { label: 'Medicine Types', value: myStock.stockByBatch?.length || 0, color: 'neon-green', icon: '🧪' },
              { label: 'Dispensed', value: myTransfers.length, color: 'cyber-teal', icon: '🏪' },
            ].map(s => (
              <motion.div key={s.label} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                className={`glass-card p-4 rounded-xl border border-${s.color}/20 metric-card text-center`}>
                <p className="text-lg mb-1">{s.icon}</p>
                <p className="text-gray-500 text-xs mb-1">{s.label}</p>
                <p className={`text-3xl font-bold text-${s.color}`}>{s.value}</p>
              </motion.div>
            ))}
          </div>
        )}

        {/* Tabs */}
        <div className="flex gap-1.5 mb-6 p-1 bg-dark-bg/60 rounded-xl border border-white/5 flex-wrap">
          {[
            { id: 'receive', label: '📥 Receive' },
            { id: 'stock', label: '📦 My Stock' },
            { id: 'sell', label: '💊 Sell' },
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
              <h3 className="text-lg font-semibold text-white mb-1">📥 Receive Stock from Distributor</h3>
              <p className="text-gray-400 text-sm mb-4">
                Scan the QR code of a box you received from the distributor. This registers the stock under your shop.
              </p>
              <form onSubmit={handleReceive} className="space-y-3">
                <div>
                  <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 font-semibold">Scan Box QR Code</p>
                  <QRScanner onScan={(sn) => setReceiveSerial(sn)} color="#00D9FF" label="Upload Box QR Code" />
                </div>
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Or enter Box / Unit Serial Number *</label>
                  <input
                    type="text"
                    value={receiveSerial}
                    onChange={e => setReceiveSerial(e.target.value)}
                    className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
                    placeholder="BOX-PAR-20240313-... or UNIT-..."
                    disabled={receiveLoading}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Notes (optional)</label>
                  <input
                    type="text"
                    value={receiveNotes}
                    onChange={e => setReceiveNotes(e.target.value)}
                    className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                    placeholder="e.g. Received from XYZ Distributors"
                    disabled={receiveLoading}
                  />
                </div>
                {receiveError && <p className="text-red-400 text-sm">{receiveError}</p>}
                <button type="submit" disabled={receiveLoading}
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
              </motion.div>
            )}
          </div>
        )}

        {/* MY STOCK TAB */}
        {activeTab === 'stock' && (
          <div>
            {!myStock || myStock.stockByBatch?.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No stock yet. Receive boxes from distributor to see them here.</div>
            ) : (
              <div className="space-y-3">
                {myStock.stockByBatch.map((b, i) => (
                  <div key={i} className="bg-dark-bg p-4 rounded-lg border border-electric-blue/20">
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="text-white font-semibold">{b.medicineName}</p>
                        <p className="text-gray-400 text-xs font-mono mt-0.5">{b.batchNumber}</p>
                        <p className="text-gray-500 text-xs mt-0.5">Expires: {b.expiryDate}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-2xl font-bold text-electric-blue">{b.unitCount}</p>
                        <p className="text-gray-500 text-xs">units</p>
                        <span className={`text-xs px-2 py-0.5 rounded mt-1 inline-block ${
                          b.batchStatus === 'ACTIVE' ? 'bg-neon-green/20 text-neon-green' : 'bg-red-500/20 text-red-400'
                        }`}>{b.batchStatus}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* SELL / DISPENSE TAB */}
        {activeTab === 'sell' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-neon-green/30">
              <h3 className="text-lg font-semibold text-white mb-1">💊 Sell / Dispense to Patient</h3>
              <p className="text-gray-400 text-sm mb-4">
                Transfer a unit to a patient by their registered email. This records the final sale in the supply chain.
              </p>
              <form onSubmit={handleSellToPatient} className="space-y-3">
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Unit Serial Number *</label>
                  <input type="text" value={sellSerial}
                    onChange={e => setSellSerial(e.target.value)}
                    className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-3 text-white focus:border-neon-green focus:outline-none font-mono"
                    placeholder="UNIT-PAR-..." required disabled={sellLoading} />
                </div>
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Patient Email *</label>
                  <input type="email" value={sellPatientEmail}
                    onChange={e => setSellPatientEmail(e.target.value)}
                    className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-3 text-white focus:border-neon-green focus:outline-none"
                    placeholder="patient@example.com" required disabled={sellLoading} />
                </div>
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Notes (optional)</label>
                  <input type="text" value={sellNotes}
                    onChange={e => setSellNotes(e.target.value)}
                    className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-3 text-white focus:border-neon-green focus:outline-none"
                    placeholder="e.g. Prescription no. RX-12345" disabled={sellLoading} />
                </div>
                <button type="submit" disabled={sellLoading}
                  className="w-full bg-neon-green text-black font-bold py-3 rounded-lg hover:bg-green-400 transition-all disabled:opacity-50">
                  {sellLoading ? '⏳ Processing...' : '💊 Dispense to Patient'}
                </button>
              </form>
              {sellResult && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                  className={`mt-4 p-3 rounded-lg ${sellResult.success ? 'bg-neon-green/10 border border-neon-green/30' : 'bg-red-500/10 border border-red-500/30'}`}>
                  {sellResult.success
                    ? <p className="text-neon-green text-sm font-semibold">✅ Dispensed successfully to patient</p>
                    : <p className="text-red-400 text-sm">❌ {sellResult.error}</p>}
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
              <p className="text-gray-400 text-sm mb-4">Enter the serial number of a unit/box to inspect, then report any issues found.</p>
              <div className="mb-3">
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 font-semibold">Scan QR Code</p>
                <QRScanner onScan={(sn) => setInspectSerial(sn)} color="#FF6B35" label="Upload Unit/Box QR Code" />
              </div>
              <form onSubmit={handleInspectLookup} className="flex gap-3">
                <input type="text" value={inspectSerial} onChange={e => setInspectSerial(e.target.value)}
                  className="flex-1 bg-dark-bg border border-orange-500/30 rounded-lg px-4 py-3 text-white focus:border-orange-500 focus:outline-none font-mono"
                  placeholder="Or type: BOX-PAR-... or UNIT-..." disabled={inspectLoading} />
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
                        placeholder="Describe what you found. e.g. Tablets are discolored, box was wet, seal was already broken on arrival..."
                        required />
                      <p className="text-xs text-gray-500 mt-1">AI will analyze your description and alert the regulator with your shop identity.</p>
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
                            'bg-yellow-500/20 text-yellow-400'
                          }`}>Severity: {complaintResult.severity}</span>
                          <span className="text-gray-400">Status: {complaintResult.status}</span>
                        </div>
                      </div>
                    )}
                  </motion.div>
                )}
              </motion.div>
            )}

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
                          'bg-yellow-500/20 text-yellow-400'
                        }`}>{c.aiSeverity || 'LOW'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* HISTORY TAB */}
        {activeTab === 'history' && (
          <div className="space-y-3">
            {myTransfers.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No transfer history yet</div>
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
