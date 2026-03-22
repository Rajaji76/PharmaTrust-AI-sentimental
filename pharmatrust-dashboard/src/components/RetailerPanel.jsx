import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { transferAPI, verifyAPI, complaintAPI, authAPI, govtSchemeAPI } from '../services/api'
import AuthModal from './AuthModal'
import QRScanner from './QRScanner'

export default function RetailerPanel() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [activeTab, setActiveTab] = useState('receive')
  const [verificationStatus, setVerificationStatus] = useState(null)

  const [myStock, setMyStock] = useState(null)
  const [myTransfers, setMyTransfers] = useState([])
  const [loading, setLoading] = useState(false)

  // Receive stock from distributor
  const [receiveSerial, setReceiveSerial] = useState('')
  const [receiveNotes, setReceiveNotes] = useState('')
  const [receiveLoading, setReceiveLoading] = useState(false)
  const [receiveResult, setReceiveResult] = useState(null)
  const [receiveError, setReceiveError] = useState('')
  const [receiveInputMode, setReceiveInputMode] = useState('manual')

  // Sell / dispense to patient
  const [sellSerial, setSellSerial] = useState('')
  const [sellPatientEmail, setSellPatientEmail] = useState('')
  const [sellNotes, setSellNotes] = useState('')
  const [sellLoading, setSellLoading] = useState(false)
  const [sellResult, setSellResult] = useState(null)
  const [sellInputMode, setSellInputMode] = useState('manual')

  // Govt Scheme Dispense state
  const [govtAbhaId, setGovtAbhaId] = useState('')
  const [govtPhone, setGovtPhone] = useState('')
  const [govtPatientName, setGovtPatientName] = useState('')
  const [govtPatientProfile, setGovtPatientProfile] = useState(null)
  const [govtLookupLoading, setGovtLookupLoading] = useState(false)
  const [govtLookupError, setGovtLookupError] = useState('')
  const [govtSelectedMedicine, setGovtSelectedMedicine] = useState('')
  const [govtSelectedBatch, setGovtSelectedBatch] = useState('')
  const [govtQuantity, setGovtQuantity] = useState(1)
  const [govtScheme, setGovtScheme] = useState('PMJAY')
  const [govtNotes, setGovtNotes] = useState('')
  const [govtSafetyResult, setGovtSafetyResult] = useState(null)
  const [govtSafetyLoading, setGovtSafetyLoading] = useState(false)
  const [govtDispenseLoading, setGovtDispenseLoading] = useState(false)
  const [govtDispenseResult, setGovtDispenseResult] = useState(null)
  const [govtDispenseHistory, setGovtDispenseHistory] = useState([])

  // Inspect & Report
  const [inspectSerial, setInspectSerial] = useState('')
  const [inspectInfo, setInspectInfo] = useState(null)
  const [inspectLoading, setInspectLoading] = useState(false)
  const [inspectError, setInspectError] = useState('')
  const [inspectInputMode, setInspectInputMode] = useState('manual')
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
      const [stockRes, transfersRes, complaintsRes, verifyRes, dispenseRes] = await Promise.allSettled([
        transferAPI.getMyStock(),
        transferAPI.getMyTransfers(),
        complaintAPI.getMyComplaints(),
        authAPI.getMyVerificationStatus(),
        govtSchemeAPI.getMyDispenseHistory(),
      ])
      if (stockRes.status === 'fulfilled') setMyStock(stockRes.value)
      if (transfersRes.status === 'fulfilled') setMyTransfers(Array.isArray(transfersRes.value) ? transfersRes.value : [])
      if (complaintsRes.status === 'fulfilled') setMyComplaints(Array.isArray(complaintsRes.value) ? complaintsRes.value : [])
      if (verifyRes.status === 'fulfilled') setVerificationStatus(verifyRes.value)
      if (dispenseRes.status === 'fulfilled') setGovtDispenseHistory(Array.isArray(dispenseRes.value) ? dispenseRes.value : [])
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

  const handleGovtLookup = async () => {
    if (!govtAbhaId.trim() && !govtPhone.trim()) { setGovtLookupError('ABHA ID ya phone number daalna zaroori hai'); return }
    setGovtLookupLoading(true); setGovtLookupError(''); setGovtPatientProfile(null); setGovtSafetyResult(null); setGovtDispenseResult(null)
    try {
      const data = await govtSchemeAPI.lookupPatient({ abhaId: govtAbhaId.trim(), phone: govtPhone.trim(), patientName: govtPatientName.trim() })
      setGovtPatientProfile(data)
      if (data.patientName && !govtPatientName) setGovtPatientName(data.patientName)
    } catch (err) {
      setGovtLookupError(err.response?.data?.error || 'Patient lookup failed')
    } finally { setGovtLookupLoading(false) }
  }

  const handleGovtSafetyCheck = async () => {
    if (!govtSelectedMedicine) { return }
    setGovtSafetyLoading(true); setGovtSafetyResult(null)
    try {
      const result = await govtSchemeAPI.checkSafety(govtPatientProfile.abhaId, govtSelectedMedicine)
      setGovtSafetyResult(result)
    } catch (err) {
      setGovtSafetyResult({ riskLevel: 'UNKNOWN', hasRisk: false, message: 'Safety check failed — proceed with caution' })
    } finally { setGovtSafetyLoading(false) }
  }

  const handleGovtDispense = async () => {
    if (!govtSelectedMedicine || !govtPatientProfile) return
    setGovtDispenseLoading(true); setGovtDispenseResult(null)
    try {
      const result = await govtSchemeAPI.recordDispense({
        abhaId: govtPatientProfile.abhaId,
        patientName: govtPatientName || govtPatientProfile.patientName,
        patientPhone: govtPhone || govtPatientProfile.phoneNumber,
        medicineName: govtSelectedMedicine,
        batchNumber: govtSelectedBatch,
        quantity: govtQuantity,
        govtScheme: govtScheme,
        riskLevel: govtSafetyResult?.riskLevel || 'UNCHECKED',
        notes: govtNotes,
      })
      setGovtDispenseResult(result)
      setGovtDispenseHistory(prev => [{ ...result, medicineName: govtSelectedMedicine, patientName: govtPatientName, quantity: govtQuantity, govtScheme }, ...prev])
      // Reset form
      setGovtSelectedMedicine(''); setGovtSelectedBatch(''); setGovtQuantity(1); setGovtNotes(''); setGovtSafetyResult(null)
    } catch (err) {
      setGovtDispenseResult({ success: false, error: err.response?.data?.error || 'Dispense failed' })
    } finally { setGovtDispenseLoading(false) }
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
      await loadData()
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
            { id: 'govt', label: '🏥 Govt Scheme' },
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
                Receive a box from the distributor. Use QR scanner or enter serial number manually.
              </p>

              {/* Input Mode Toggle */}
              <div className="flex gap-1 mb-4 p-1 bg-black/30 rounded-lg border border-electric-blue/20 w-fit">
                <button onClick={() => setReceiveInputMode('manual')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    receiveInputMode === 'manual'
                      ? 'bg-electric-blue/20 text-electric-blue border border-electric-blue/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>⌨️ Manual Entry</button>
                <button onClick={() => setReceiveInputMode('scanner')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    receiveInputMode === 'scanner'
                      ? 'bg-electric-blue/20 text-electric-blue border border-electric-blue/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>📷 QR Scanner</button>
              </div>

              <form onSubmit={handleReceive} className="space-y-3">
                {receiveInputMode === 'scanner' ? (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Scan QR Code Image</label>
                    <QRScanner onScan={sn => { setReceiveSerial(sn); setReceiveError('') }} color="#00D9FF" label="Upload Box QR Code" />
                    {receiveSerial && (
                      <div className="mt-2 px-3 py-2 bg-electric-blue/10 border border-electric-blue/30 rounded-lg">
                        <p className="text-xs text-gray-400">Scanned Serial:</p>
                        <p className="text-electric-blue font-mono text-sm">{receiveSerial}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Box / Unit Serial Number *</label>
                    <input type="text" value={receiveSerial}
                      onChange={e => { setReceiveSerial(e.target.value); setReceiveError('') }}
                      className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
                      placeholder="BOX-PAR-20240313-... or UNIT-..."
                      disabled={receiveLoading} required />
                  </div>
                )}
                <div>
                  <label className="block text-sm text-gray-300 mb-2">Notes (optional)</label>
                  <input type="text" value={receiveNotes}
                    onChange={e => setReceiveNotes(e.target.value)}
                    className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                    placeholder="e.g. Received from XYZ Distributors"
                    disabled={receiveLoading} />
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
              <div className="text-center py-12 text-gray-500">No stock yet. Receive boxes from distributor to see them here.</div>
            ) : (
              <>
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
                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-neon-green/10 text-neon-green border border-neon-green/20">
                          {batches.reduce((sum, b) => sum + (b.unitCount || 0), 0)} units total
                        </span>
                      </div>
                      <div className="space-y-2 pl-2 border-l-2 border-neon-green/20">
                        {batches.map((b, i) => (
                          <div key={i} className="bg-dark-bg p-4 rounded-lg border border-electric-blue/20">
                            <div className="flex justify-between items-start">
                              <div>
                                <p className="text-gray-400 text-xs font-mono">{b.batchNumber}</p>
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
                    </div>
                  ))
                })()}
              </>
            )}
          </div>
        )}

        {/* SELL / DISPENSE TAB */}
        {activeTab === 'sell' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-neon-green/30">
              <h3 className="text-lg font-semibold text-white mb-1">💊 Sell / Dispense to Patient</h3>
              <p className="text-gray-400 text-sm mb-4">
                Transfer a unit to a patient. Use QR scanner or enter serial number manually.
              </p>

              {/* Input Mode Toggle */}
              <div className="flex gap-1 mb-4 p-1 bg-black/30 rounded-lg border border-neon-green/20 w-fit">
                <button onClick={() => setSellInputMode('manual')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    sellInputMode === 'manual'
                      ? 'bg-neon-green/20 text-neon-green border border-neon-green/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>⌨️ Manual Entry</button>
                <button onClick={() => setSellInputMode('scanner')}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                    sellInputMode === 'scanner'
                      ? 'bg-neon-green/20 text-neon-green border border-neon-green/40'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}>📷 QR Scanner</button>
              </div>

              <form onSubmit={handleSellToPatient} className="space-y-3">
                {sellInputMode === 'scanner' ? (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Scan Unit QR Code Image</label>
                    <QRScanner onScan={sn => setSellSerial(sn)} color="#00FF88" label="Upload Unit QR Code" />
                    {sellSerial && (
                      <div className="mt-2 px-3 py-2 bg-neon-green/10 border border-neon-green/30 rounded-lg">
                        <p className="text-xs text-gray-400">Scanned Serial:</p>
                        <p className="text-neon-green font-mono text-sm">{sellSerial}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div>
                    <label className="block text-sm text-gray-300 mb-2">Unit Serial Number *</label>
                    <input type="text" value={sellSerial}
                      onChange={e => setSellSerial(e.target.value)}
                      className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-3 text-white focus:border-neon-green focus:outline-none font-mono"
                      placeholder="UNIT-PAR-..." required disabled={sellLoading} />
                  </div>
                )}
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
                <button type="submit" disabled={sellLoading || !sellSerial.trim()}
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

        {/* GOVT SCHEME DISPENSE TAB */}
        {activeTab === 'govt' && (
          <div className="space-y-5">
            {/* Header */}
            <div className="flex items-center gap-3 p-4 rounded-xl border border-neon-purple/30 bg-neon-purple/5">
              <span className="text-3xl">🏥</span>
              <div>
                <p className="text-white font-bold text-base">Government Scheme Medicine Dispense</p>
                <p className="text-gray-400 text-xs">PMJAY / Ayushman Bharat · ABHA Health ID se patient ka health record check karke safe dispense karo</p>
              </div>
              <div className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-neon-green/10 border border-neon-green/30">
                <div className="w-1.5 h-1.5 rounded-full bg-neon-green animate-pulse" />
                <span className="text-neon-green text-xs font-semibold">ABHA Connected</span>
              </div>
            </div>

            {/* STEP 1: Patient Lookup */}
            <div className="bg-dark-bg rounded-xl border border-electric-blue/30 overflow-hidden">
              <div className="px-5 py-3 border-b border-electric-blue/20 bg-electric-blue/5 flex items-center gap-2">
                <span className="w-6 h-6 rounded-full bg-electric-blue text-black text-xs font-bold flex items-center justify-center">1</span>
                <span className="text-electric-blue font-semibold text-sm">Patient Identity — ABHA / Phone</span>
              </div>
              <div className="p-5 space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-gray-400 mb-1.5">ABHA ID (Ayushman Bharat Health Account)</label>
                    <input
                      type="text"
                      value={govtAbhaId}
                      onChange={e => setGovtAbhaId(e.target.value)}
                      placeholder="14-digit ABHA number (e.g. 12-3456-7890-1234)"
                      className="w-full bg-dark-bg/80 border border-electric-blue/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-electric-blue focus:outline-none font-mono"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-gray-400 mb-1.5">Mobile Number</label>
                    <input
                      type="tel"
                      value={govtPhone}
                      onChange={e => setGovtPhone(e.target.value)}
                      placeholder="10-digit mobile number"
                      className="w-full bg-dark-bg/80 border border-electric-blue/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-electric-blue focus:outline-none"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-gray-400 mb-1.5">Patient Name (optional — override)</label>
                  <input
                    type="text"
                    value={govtPatientName}
                    onChange={e => setGovtPatientName(e.target.value)}
                    placeholder="Patient ka naam"
                    className="w-full bg-dark-bg/80 border border-electric-blue/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-electric-blue focus:outline-none"
                  />
                </div>
                {govtLookupError && <p className="text-red-400 text-xs">⚠️ {govtLookupError}</p>}
                <button
                  onClick={handleGovtLookup}
                  disabled={govtLookupLoading || (!govtAbhaId.trim() && !govtPhone.trim())}
                  className="w-full py-2.5 rounded-lg text-sm font-bold transition-all disabled:opacity-50"
                  style={{ background: 'rgba(0,217,255,0.15)', border: '1px solid rgba(0,217,255,0.4)', color: '#00D9FF' }}>
                  {govtLookupLoading ? '⏳ ABHA se fetch ho raha hai...' : '🔍 Patient Health Profile Fetch Karo'}
                </button>
              </div>
            </div>

            {/* Patient Health Profile Card */}
            {govtPatientProfile && (
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                className="bg-dark-bg rounded-xl border border-neon-purple/30 overflow-hidden">
                <div className="px-5 py-3 border-b border-neon-purple/20 bg-neon-purple/5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-lg">👤</span>
                    <span className="text-neon-purple font-semibold text-sm">Patient Health Profile</span>
                    {govtPatientProfile.isAbhaVerified && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-neon-green/15 text-neon-green border border-neon-green/30 font-bold">✅ ABHA Verified</span>
                    )}
                  </div>
                  <span className="text-xs text-gray-600 italic">{govtPatientProfile.dataSource}</span>
                </div>
                <div className="p-5 space-y-4">
                  {/* Basic info */}
                  <div className="grid grid-cols-4 gap-3 text-sm">
                    <div className="bg-dark-bg/60 p-3 rounded-lg border border-white/5">
                      <p className="text-gray-500 text-xs">Name</p>
                      <p className="text-white font-semibold">{govtPatientName || govtPatientProfile.patientName}</p>
                    </div>
                    <div className="bg-dark-bg/60 p-3 rounded-lg border border-white/5">
                      <p className="text-gray-500 text-xs">Age</p>
                      <p className="text-white font-semibold">{govtPatientProfile.age > 0 ? `${govtPatientProfile.age} yrs` : '—'}</p>
                    </div>
                    <div className="bg-dark-bg/60 p-3 rounded-lg border border-white/5">
                      <p className="text-gray-500 text-xs">Blood Group</p>
                      <p className="text-red-400 font-bold">{govtPatientProfile.bloodGroup || '—'}</p>
                    </div>
                    <div className="bg-dark-bg/60 p-3 rounded-lg border border-white/5">
                      <p className="text-gray-500 text-xs">Phone</p>
                      <p className="text-white text-xs">{govtPhone || govtPatientProfile.phoneNumber || '—'}</p>
                    </div>
                  </div>

                  {/* Allergies */}
                  {govtPatientProfile.knownAllergies?.length > 0 && (
                    <div className="p-3 rounded-lg bg-red-500/8 border border-red-500/25">
                      <p className="text-red-400 text-xs font-bold uppercase tracking-wider mb-2">⚠️ Known Allergies</p>
                      <div className="flex flex-wrap gap-2">
                        {govtPatientProfile.knownAllergies.map((a, i) => (
                          <span key={i} className="px-2 py-1 rounded-full text-xs font-semibold bg-red-500/20 text-red-300 border border-red-500/30">{a}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Chronic conditions */}
                  {govtPatientProfile.chronicConditions?.length > 0 && (
                    <div className="p-3 rounded-lg bg-yellow-500/8 border border-yellow-500/25">
                      <p className="text-yellow-400 text-xs font-bold uppercase tracking-wider mb-2">🏥 Chronic Conditions</p>
                      <div className="flex flex-wrap gap-2">
                        {govtPatientProfile.chronicConditions.map((c, i) => (
                          <span key={i} className="px-2 py-1 rounded-full text-xs bg-yellow-500/15 text-yellow-300 border border-yellow-500/25">{c}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Current medications */}
                  {govtPatientProfile.currentMedications?.length > 0 && (
                    <div className="p-3 rounded-lg bg-electric-blue/8 border border-electric-blue/20">
                      <p className="text-electric-blue text-xs font-bold uppercase tracking-wider mb-2">💊 Current Medications</p>
                      <div className="flex flex-wrap gap-2">
                        {govtPatientProfile.currentMedications.map((m, i) => (
                          <span key={i} className="px-2 py-1 rounded-full text-xs bg-electric-blue/15 text-electric-blue border border-electric-blue/25">{m}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {govtPatientProfile.knownAllergies?.length === 0 && govtPatientProfile.chronicConditions?.length === 0 && (
                    <div className="p-3 rounded-lg bg-neon-green/8 border border-neon-green/20 flex items-center gap-2">
                      <span className="text-neon-green">✅</span>
                      <p className="text-neon-green text-sm font-semibold">No known allergies or chronic conditions on record</p>
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {/* STEP 2: Medicine Selection */}
            {govtPatientProfile && (
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                className="bg-dark-bg rounded-xl border border-neon-green/30 overflow-hidden">
                <div className="px-5 py-3 border-b border-neon-green/20 bg-neon-green/5 flex items-center gap-2">
                  <span className="w-6 h-6 rounded-full bg-neon-green text-black text-xs font-bold flex items-center justify-center">2</span>
                  <span className="text-neon-green font-semibold text-sm">Medicine Select Karo (Apni Inventory Se)</span>
                </div>
                <div className="p-5 space-y-4">
                  {/* Medicine dropdown from stock */}
                  {!myStock || myStock.stockByBatch?.length === 0 ? (
                    <p className="text-gray-500 text-sm text-center py-4">Koi stock available nahi. Pehle distributor se receive karo.</p>
                  ) : (
                    <>
                      <div>
                        <label className="block text-xs text-gray-400 mb-1.5">Medicine Select Karo *</label>
                        <select
                          value={govtSelectedMedicine}
                          onChange={e => {
                            setGovtSelectedMedicine(e.target.value)
                            setGovtSafetyResult(null)
                            // Auto-select batch
                            const batch = myStock.stockByBatch?.find(b => b.medicineName === e.target.value)
                            if (batch) setGovtSelectedBatch(batch.batchNumber)
                          }}
                          className="w-full bg-dark-bg/80 border border-neon-green/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-neon-green focus:outline-none">
                          <option value="">-- Medicine choose karo --</option>
                          {[...new Set(myStock.stockByBatch?.map(b => b.medicineName))].map((med, i) => {
                            const totalUnits = myStock.stockByBatch?.filter(b => b.medicineName === med).reduce((s, b) => s + (b.unitCount || 0), 0)
                            return <option key={i} value={med}>{med} ({totalUnits} units available)</option>
                          })}
                        </select>
                      </div>

                      {govtSelectedMedicine && (
                        <div className="grid grid-cols-3 gap-3">
                          <div>
                            <label className="block text-xs text-gray-400 mb-1.5">Batch Number</label>
                            <select
                              value={govtSelectedBatch}
                              onChange={e => setGovtSelectedBatch(e.target.value)}
                              className="w-full bg-dark-bg/80 border border-neon-green/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-neon-green focus:outline-none">
                              {myStock.stockByBatch?.filter(b => b.medicineName === govtSelectedMedicine).map((b, i) => (
                                <option key={i} value={b.batchNumber}>{b.batchNumber} ({b.unitCount} units)</option>
                              ))}
                            </select>
                          </div>
                          <div>
                            <label className="block text-xs text-gray-400 mb-1.5">Quantity *</label>
                            <input
                              type="number"
                              min={1}
                              value={govtQuantity}
                              onChange={e => setGovtQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                              className="w-full bg-dark-bg/80 border border-neon-green/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-neon-green focus:outline-none"
                            />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-400 mb-1.5">Govt Scheme</label>
                            <select
                              value={govtScheme}
                              onChange={e => setGovtScheme(e.target.value)}
                              className="w-full bg-dark-bg/80 border border-neon-green/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-neon-green focus:outline-none">
                              <option value="PMJAY">PMJAY (Ayushman Bharat)</option>
                              <option value="CGHS">CGHS</option>
                              <option value="ESIC">ESIC</option>
                              <option value="STATE_SCHEME">State Health Scheme</option>
                              <option value="FREE_MEDICINE">Free Medicine Scheme</option>
                            </select>
                          </div>
                        </div>
                      )}

                      {govtSelectedMedicine && (
                        <div>
                          <label className="block text-xs text-gray-400 mb-1.5">Notes (optional)</label>
                          <input
                            type="text"
                            value={govtNotes}
                            onChange={e => setGovtNotes(e.target.value)}
                            placeholder="e.g. Doctor prescription no. RX-12345"
                            className="w-full bg-dark-bg/80 border border-neon-green/30 rounded-lg px-3 py-2.5 text-white text-sm focus:border-neon-green focus:outline-none"
                          />
                        </div>
                      )}

                      {/* AI Safety Check Button */}
                      {govtSelectedMedicine && (
                        <button
                          onClick={handleGovtSafetyCheck}
                          disabled={govtSafetyLoading}
                          className="w-full py-2.5 rounded-lg text-sm font-bold transition-all disabled:opacity-50"
                          style={{ background: 'rgba(191,95,255,0.15)', border: '1px solid rgba(191,95,255,0.4)', color: '#BF5FFF' }}>
                          {govtSafetyLoading ? '⏳ AI check ho raha hai...' : '🤖 AI Safety Check Karo (ABHA Health Data Se)'}
                        </button>
                      )}
                    </>
                  )}
                </div>
              </motion.div>
            )}

            {/* STEP 3: Safety Check Result */}
            {govtSafetyResult && (
              <motion.div initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }}
                className={`rounded-xl border-2 overflow-hidden ${
                  govtSafetyResult.riskLevel === 'SAFE'
                    ? 'border-neon-green/50 bg-neon-green/5'
                    : govtSafetyResult.riskLevel === 'CRITICAL'
                    ? 'border-red-500/60 bg-red-500/8'
                    : 'border-yellow-500/50 bg-yellow-500/5'
                }`}>
                <div className={`px-5 py-3 border-b flex items-center justify-between ${
                  govtSafetyResult.riskLevel === 'SAFE' ? 'border-neon-green/20 bg-neon-green/8'
                  : govtSafetyResult.riskLevel === 'CRITICAL' ? 'border-red-500/30 bg-red-500/10'
                  : 'border-yellow-500/20 bg-yellow-500/8'
                }`}>
                  <div className="flex items-center gap-2">
                    <span className="text-lg">
                      {govtSafetyResult.riskLevel === 'SAFE' ? '✅' : govtSafetyResult.riskLevel === 'CRITICAL' ? '🚨' : '⚠️'}
                    </span>
                    <span className={`font-bold text-sm ${
                      govtSafetyResult.riskLevel === 'SAFE' ? 'text-neon-green'
                      : govtSafetyResult.riskLevel === 'CRITICAL' ? 'text-red-400'
                      : 'text-yellow-400'
                    }`}>
                      AI Safety Result: {govtSafetyResult.riskLevel}
                    </span>
                  </div>
                  <span className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold"
                    style={{
                      background: govtSafetyResult.riskLevel === 'SAFE' ? 'rgba(57,255,20,0.2)' : govtSafetyResult.riskLevel === 'CRITICAL' ? 'rgba(239,68,68,0.2)' : 'rgba(234,179,8,0.2)',
                      color: govtSafetyResult.riskLevel === 'SAFE' ? '#39FF14' : govtSafetyResult.riskLevel === 'CRITICAL' ? '#f87171' : '#facc15'
                    }}>3</span>
                </div>
                <div className="p-5 space-y-3">
                  <p className={`text-sm font-medium ${
                    govtSafetyResult.riskLevel === 'SAFE' ? 'text-neon-green'
                    : govtSafetyResult.riskLevel === 'CRITICAL' ? 'text-red-300'
                    : 'text-yellow-300'
                  }`}>{govtSafetyResult.message}</p>

                  {/* SMS Alert sent notification */}
                  {govtSafetyResult.smsAlertSent && (
                    <div className="p-3 rounded-lg bg-orange-500/10 border border-orange-500/30">
                      <p className="text-orange-400 text-xs font-bold mb-1">📱 SMS Alert Bheja Gaya — Bharat Sarkar ki taraf se</p>
                      <p className="text-gray-400 text-xs">To: {govtSafetyResult.smsAlertPhone}</p>
                      <p className="text-gray-300 text-xs mt-1 italic">"{govtSafetyResult.smsPreview?.slice(0, 120)}..."</p>
                    </div>
                  )}

                  {/* Dispense button */}
                  {govtSafetyResult.riskLevel === 'SAFE' ? (
                    <button
                      onClick={handleGovtDispense}
                      disabled={govtDispenseLoading}
                      className="w-full py-3 rounded-lg text-sm font-bold transition-all disabled:opacity-50"
                      style={{ background: 'rgba(57,255,20,0.2)', border: '1px solid rgba(57,255,20,0.5)', color: '#39FF14' }}>
                      {govtDispenseLoading ? '⏳ Record ho raha hai...' : '✅ Dispense Confirm Karo & Record Save Karo'}
                    </button>
                  ) : (
                    <div className="space-y-2">
                      <p className="text-xs text-gray-400">⚠️ Risk detected hai. Phir bhi dispense karna chahte ho? (Doctor ki salah ke baad hi karo)</p>
                      <div className="flex gap-2">
                        <button
                          onClick={handleGovtDispense}
                          disabled={govtDispenseLoading}
                          className="flex-1 py-2.5 rounded-lg text-xs font-bold transition-all disabled:opacity-50"
                          style={{ background: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.4)', color: '#f87171' }}>
                          {govtDispenseLoading ? '⏳...' : '⚠️ Override — Phir Bhi Dispense Karo'}
                        </button>
                        <button
                          onClick={() => { setGovtSafetyResult(null); setGovtSelectedMedicine('') }}
                          className="flex-1 py-2.5 rounded-lg text-xs font-bold transition-all"
                          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', color: '#9CA3AF' }}>
                          ✕ Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {/* Dispense Success */}
            {govtDispenseResult?.success && (
              <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
                className="p-4 rounded-xl bg-neon-green/10 border border-neon-green/40">
                <p className="text-neon-green font-bold mb-1">✅ Dispense Record Saved</p>
                <p className="text-gray-400 text-xs">ID: {govtDispenseResult.dispenseId}</p>
                <p className="text-gray-400 text-xs">{govtDispenseResult.dispensedAt}</p>
                <button
                  onClick={() => { setGovtPatientProfile(null); setGovtAbhaId(''); setGovtPhone(''); setGovtPatientName(''); setGovtDispenseResult(null) }}
                  className="mt-3 w-full py-2 rounded-lg text-xs font-semibold transition-all"
                  style={{ background: 'rgba(0,217,255,0.1)', border: '1px solid rgba(0,217,255,0.3)', color: '#00D9FF' }}>
                  🔄 Naya Patient
                </button>
              </motion.div>
            )}

            {/* Dispense History */}
            {govtDispenseHistory.length > 0 && (
              <div>
                <p className="text-gray-400 text-sm font-semibold mb-3">📋 Aaj ke Dispense Records ({govtDispenseHistory.length})</p>
                <div className="space-y-2">
                  {govtDispenseHistory.slice(0, 10).map((d, i) => (
                    <div key={d.id || i} className="bg-dark-bg p-3 rounded-lg border border-neon-purple/20 flex justify-between items-center">
                      <div>
                        <p className="text-white text-sm font-semibold">{d.medicineName} × {d.quantity}</p>
                        <p className="text-gray-400 text-xs">{d.patientName || d.abhaId} · {d.govtScheme}</p>
                        <p className="text-gray-600 text-xs">{d.dispensedAt ? new Date(d.dispensedAt).toLocaleString() : ''}</p>
                      </div>
                      <span className={`text-xs px-2 py-1 rounded font-bold border ${
                        d.riskLevel === 'SAFE' || d.riskLevel === 'UNCHECKED'
                          ? 'bg-neon-green/10 text-neon-green border-neon-green/30'
                          : d.riskLevel === 'CRITICAL'
                          ? 'bg-red-500/10 text-red-400 border-red-500/30'
                          : 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30'
                      }`}>{d.riskLevel || 'DONE'}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* INSPECT & REPORT TAB */}
        {activeTab === 'inspect' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-orange-500/30">
              <h3 className="text-lg font-semibold text-white mb-1">🔍 Manual Inspection</h3>
              <p className="text-gray-400 text-sm mb-4">Inspect a unit/box and report any issues found.</p>

              {/* Input Mode Toggle */}
              <div className="flex gap-1 mb-4 p-1 bg-black/30 rounded-lg border border-orange-500/20 w-fit">
                {['manual', 'scanner'].map(mode => (
                  <button key={mode}
                    onClick={() => { setInspectInputMode(mode); setInspectSerial(''); setInspectInfo(null); setInspectError('') }}
                    className={`px-4 py-2 rounded-md text-sm font-semibold transition-all ${
                      inspectInputMode === mode
                        ? 'bg-orange-500/20 text-orange-400 border border-orange-500/40'
                        : 'text-gray-500 hover:text-gray-300'
                    }`}>
                    {mode === 'manual' ? '⌨️ Manual Entry' : '📷 QR Scanner'}
                  </button>
                ))}
              </div>

              {inspectInputMode === 'scanner' ? (
                <div className="mb-3">
                  <label className="block text-sm text-gray-300 mb-2">Scan QR Code Image</label>
                  <QRScanner onScan={sn => { setInspectSerial(sn); setInspectInfo(null); setInspectError('') }} color="#FF6B35" label="Upload Unit/Box QR Code" />
                  {inspectSerial && (
                    <div className="mt-2 px-3 py-2 bg-orange-500/10 border border-orange-500/30 rounded-lg">
                      <p className="text-xs text-gray-400">Scanned Serial:</p>
                      <p className="text-orange-400 font-mono text-sm">{inspectSerial}</p>
                    </div>
                  )}
                </div>
              ) : null}

              <form onSubmit={handleInspectLookup} className="flex gap-3">
                <input type="text" value={inspectSerial} onChange={e => setInspectSerial(e.target.value)}
                  className="flex-1 bg-dark-bg border border-orange-500/30 rounded-lg px-4 py-3 text-white focus:border-orange-500 focus:outline-none font-mono"
                  placeholder={inspectInputMode === 'scanner' ? 'Scanned serial will appear here...' : 'BOX-PAR-... or UNIT-...'}
                  disabled={inspectLoading} />
                <button type="submit" disabled={inspectLoading || !inspectSerial.trim()}
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
