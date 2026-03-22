import { useState, useEffect, useRef } from 'react'
import { regulatorAPI, securityAPI, complaintAPI, verifyAPI } from '../services/api'
import AuthModal from './AuthModal'
import QRScanner from './QRScanner'

const SEV = (s) => {
  const t = (s || '').toUpperCase()
  if (t.includes('CRITICAL') || t.includes('COUNTERFEIT') || t.includes('DANGER'))
    return { bg: 'bg-red-500/20', text: 'text-red-400', border: 'border-red-500/40', dot: 'bg-red-400', left: 'border-l-red-400' }
  if (t.includes('HIGH') || t.includes('SUSPICIOUS'))
    return { bg: 'bg-orange-500/20', text: 'text-orange-400', border: 'border-orange-500/40', dot: 'bg-orange-400', left: 'border-l-orange-400' }
  if (t.includes('MEDIUM'))
    return { bg: 'bg-yellow-500/20', text: 'text-yellow-400', border: 'border-yellow-500/40', dot: 'bg-yellow-400', left: 'border-l-yellow-400' }
  return { bg: 'bg-blue-500/20', text: 'text-blue-400', border: 'border-blue-500/40', dot: 'bg-blue-400', left: 'border-l-blue-400' }
}

// ─── Alert Detail Modal ───────────────────────────────────────────────────────
function AlertDetailModal({ item, onClose }) {
  const [unitHistory, setUnitHistory] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [trackResult, setTrackResult] = useState(null)
  const [trackLoading, setTrackLoading] = useState(false)
  const isComplaint = !!(item?.issueType)
  const serialNumber = item?.serialNumber
  const batchNumber = item?.batchNumber
  const sev = SEV(item?.aiSeverity || item?.alertType || 'INFO')

  useEffect(() => {
    if (!serialNumber) return
    setHistoryLoading(true)
    verifyAPI.getUnitHistory(serialNumber)
      .then(d => setUnitHistory(d)).catch(() => setUnitHistory(null))
      .finally(() => setHistoryLoading(false))
  }, [serialNumber])

  useEffect(() => {
    if (!batchNumber) return
    setTrackLoading(true)
    regulatorAPI.getBatchLocation(batchNumber)
      .then(d => setTrackResult(d)).catch(() => setTrackResult(null))
      .finally(() => setTrackLoading(false))
  }, [batchNumber])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }} onClick={onClose}>
      <div className="rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto border border-slate-700" style={{ background: '#1e293b' }} onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-700">
          <div className="flex items-center gap-3">
            <span className="text-xl">🔍</span>
            <h2 className="text-white font-bold text-lg">Alert Investigation</h2>
            <span className={`px-2 py-0.5 rounded-full text-xs font-bold border ${sev.bg} ${sev.text} ${sev.border}`}>
              {(item?.aiSeverity || item?.alertType || 'INFO').toUpperCase()}
            </span>
          </div>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:bg-slate-700 transition-colors">✕</button>
        </div>

        <div className="p-6 space-y-4">
          <div className={`p-4 rounded-xl border-l-4 ${sev.left} bg-slate-800/60`}>
            <p className="text-slate-400 text-xs uppercase tracking-wider mb-1">{isComplaint ? 'Issue Type' : 'Alert Type'}</p>
            <p className={`font-bold text-base ${sev.text}`}>
              {isComplaint ? (item.issueType?.replace(/_/g, ' ') || 'Unknown') : (item.alertType || item.type || 'ALERT')}
            </p>
            {(item?.description || item?.message) && (
              <p className="text-slate-300 text-sm mt-2 italic">"{item.description || item.message}"</p>
            )}
          </div>

          <div className="p-4 rounded-xl bg-blue-500/10 border border-blue-500/20">
            <p className="text-blue-400 text-xs font-bold uppercase tracking-wider mb-3">💊 Medicine Details</p>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div><p className="text-slate-500 text-xs">Medicine</p><p className="text-white font-semibold">{item?.medicineName || '—'}</p></div>
              <div><p className="text-slate-500 text-xs">Batch</p><p className="text-slate-300 font-mono text-xs">{item?.batchNumber || '—'}</p></div>
              <div className="col-span-2"><p className="text-slate-500 text-xs">Serial Number</p><p className="text-slate-300 font-mono text-xs break-all">{item?.serialNumber || '—'}</p></div>
            </div>
          </div>

          {isComplaint && item?.reporter && (
            <div className="p-4 rounded-xl bg-purple-500/10 border border-purple-500/20">
              <p className="text-purple-400 text-xs font-bold uppercase tracking-wider mb-3">👤 Reporter</p>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div><p className="text-slate-500 text-xs">Name</p><p className="text-white font-semibold">{item.reporter.fullName || item.reporter.name || '—'}</p></div>
                <div><p className="text-slate-500 text-xs">Email</p><p className="text-slate-300 text-xs">{item.reporter.email || '—'}</p></div>
                <div><p className="text-slate-500 text-xs">Shop</p><p className="text-slate-300">{item.reporter.shopName || '—'}</p></div>
                <div><p className="text-slate-500 text-xs">Location</p><p className="text-slate-300">{item.reporter.cityState || '—'}</p></div>
              </div>
            </div>
          )}

          {item?.aiAnalysis && (
            <div className="p-4 rounded-xl bg-slate-800 border border-slate-700">
              <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-2">🤖 AI Analysis</p>
              <p className="text-slate-300 text-sm leading-relaxed">{item.aiAnalysis}</p>
            </div>
          )}

          <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
            <p className="text-amber-400 text-xs font-bold uppercase tracking-wider mb-3">📍 Scan History</p>
            {!serialNumber ? <p className="text-slate-500 text-sm">No serial number</p>
              : historyLoading ? <p className="text-slate-500 text-sm">Loading...</p>
              : !unitHistory ? <p className="text-slate-500 text-sm">Not available</p>
              : unitHistory.totalScans > 0 ? (
                <div className="space-y-2">
                  <p className="text-amber-300 text-xs mb-2">Total scans: <span className="font-bold">{unitHistory.totalScans}</span></p>
                  {(unitHistory.history || []).slice(0, 5).map((step, idx) => (
                    <div key={idx} className="flex items-start gap-3 p-3 bg-slate-800 rounded-lg border border-slate-700">
                      <div className="w-6 h-6 rounded-full bg-amber-500/20 flex items-center justify-center text-xs font-bold text-amber-400 flex-shrink-0">{idx + 1}</div>
                      <div>
                        <p className={`text-xs font-bold ${step.result === 'VALID' ? 'text-emerald-400' : step.result === 'COUNTERFEIT' ? 'text-red-400' : 'text-amber-400'}`}>{step.result}</p>
                        {step.scannedAt && <p className="text-slate-500 text-xs">{new Date(step.scannedAt).toLocaleString()}</p>}
                        {step.location && step.location !== 'unknown' && <p className="text-slate-500 text-xs">📍 {step.location}</p>}
                        {step.autoFlagged && <p className="text-red-400 text-xs">⚠️ Auto-flagged</p>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : <p className="text-slate-500 text-sm">No scan history</p>}
          </div>

          {/* Supply Chain Tracking */}
          {batchNumber && (
            <div className="p-4 rounded-xl bg-cyan-500/10 border border-cyan-500/20">
              <p className="text-cyan-400 text-xs font-bold uppercase tracking-wider mb-3">🗺️ Supply Chain Location</p>
              {trackLoading ? <p className="text-slate-500 text-sm">Loading supply chain data...</p>
              : !trackResult ? <p className="text-slate-500 text-sm">Tracking data unavailable</p>
              : (
                <div className="space-y-3">
                  {trackResult.summary && Object.keys(trackResult.summary).length > 0 && (
                    <div className="grid grid-cols-2 gap-2">
                      {Object.entries(trackResult.summary).map(([role, count]) => (
                        <div key={role} className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-center">
                          <p className="text-cyan-400 text-xs font-bold">{role}</p>
                          <p className="text-white text-lg font-black">{count}</p>
                          <p className="text-slate-500 text-xs">units</p>
                        </div>
                      ))}
                    </div>
                  )}
                  {trackResult.distributors?.length > 0 && (
                    <div>
                      <p className="text-slate-400 text-xs font-semibold mb-1">🚚 Distributors</p>
                      {trackResult.distributors.slice(0, 3).map((d, i) => (
                        <div key={i} className="flex justify-between items-center p-2 rounded bg-slate-800 mb-1">
                          <div>
                            <p className="text-slate-200 text-xs font-semibold">{d.shopName || d.email}</p>
                            {d.cityState && <p className="text-slate-500 text-xs">{d.cityState}</p>}
                          </div>
                          <span className="text-purple-400 font-bold text-sm">{d.unitCount}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  {trackResult.retailers?.length > 0 && (
                    <div>
                      <p className="text-slate-400 text-xs font-semibold mb-1">🏪 Retailers</p>
                      {trackResult.retailers.slice(0, 3).map((r, i) => (
                        <div key={i} className="flex justify-between items-center p-2 rounded bg-slate-800 mb-1">
                          <div>
                            <p className="text-slate-200 text-xs font-semibold">{r.shopName || r.email}</p>
                            {r.cityState && <p className="text-slate-500 text-xs">{r.cityState}</p>}
                          </div>
                          <span className="text-teal-400 font-bold text-sm">{r.unitCount}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-slate-700 flex justify-end">
          <button onClick={onClose} className="px-5 py-2 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-200 text-sm font-semibold transition-colors">Close</button>
        </div>
      </div>
    </div>
  )
}

// ─── Critical Alert Overlay ───────────────────────────────────────────────────
function CriticalAlertOverlay({ alerts, complaints, onDismiss, onViewAlert }) {
  const critical = [
    ...alerts.filter(a => { const t = (a.alertType || '').toUpperCase(); return t.includes('CRITICAL') || t.includes('COUNTERFEIT') || t.includes('DANGER') }),
    ...complaints.filter(c => c.aiSeverity === 'CRITICAL' || c.aiSeverity === 'HIGH'),
  ].slice(0, 5)
  if (critical.length === 0) return null

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(6px)' }}>
      <div className="rounded-2xl shadow-2xl w-full max-w-lg border border-red-500/30" style={{ background: '#1e293b' }}>
        <div className="p-5 border-b border-red-500/20 bg-red-500/10 rounded-t-2xl flex items-center gap-3">
          <span className="text-2xl">🚨</span>
          <div>
            <h2 className="text-red-400 font-bold text-lg">Critical Alerts Detected</h2>
            <p className="text-red-300/70 text-sm">{critical.length} alert{critical.length > 1 ? 's' : ''} require immediate attention</p>
          </div>
        </div>
        <div className="p-4 space-y-2 max-h-72 overflow-y-auto">
          {critical.map((item, i) => {
            const isC = !!(item?.issueType)
            return (
              <div key={i} className="flex items-start gap-3 p-3 rounded-xl border border-red-500/20 bg-red-500/10 cursor-pointer hover:bg-red-500/20 transition-colors"
                onClick={() => { onViewAlert(item); onDismiss() }}>
                <span className="text-red-400 mt-0.5">⚠️</span>
                <div className="flex-1 min-w-0">
                  <p className="text-white font-semibold text-sm truncate">
                    {isC ? `${item.issueType?.replace(/_/g, ' ')} — ${item.medicineName || 'Unknown'}` : (item.message || item.description || 'Alert')}
                  </p>
                  {item.serialNumber && <p className="text-slate-400 text-xs font-mono">Serial: {item.serialNumber}</p>}
                </div>
                <span className="text-red-400 text-xs font-semibold flex-shrink-0">View →</span>
              </div>
            )
          })}
        </div>
        <div className="p-4 border-t border-slate-700 flex gap-3">
          <button onClick={onDismiss} className="flex-1 py-2.5 rounded-xl bg-slate-700 hover:bg-slate-600 text-slate-200 text-sm font-semibold transition-colors">
            Acknowledge & Continue
          </button>
          <button onClick={onDismiss} className="px-5 py-2.5 rounded-xl bg-red-600 hover:bg-red-700 text-white text-sm font-bold transition-colors">
            View All Alerts
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Main RegulatorPanel ──────────────────────────────────────────────────────
export default function RegulatorPanel() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [activeTab, setActiveTab] = useState('overview')
  const [loading, setLoading] = useState(false)

  const [stats, setStats] = useState({ totalUnits: 0, activeUnits: 0, recalledUnits: 0, flaggedUnits: 0, suspiciousScans: 0, unitsByOwner: {} })
  const [alerts, setAlerts] = useState([])
  const [recalls, setRecalls] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [complaints, setComplaints] = useState([])
  const [batches, setBatches] = useState([])
  const [partners, setPartners] = useState({ manufacturers: [], distributors: [], retailers: [], totalManufacturers: 0, totalDistributors: 0, totalRetailers: 0 })

  const [showAlertOverlay, setShowAlertOverlay] = useState(false)
  const overlayShownRef = useRef(false)
  const [selectedAlert, setSelectedAlert] = useState(null)

  const [killSerial, setKillSerial] = useState('')
  const [killReason, setKillReason] = useState('STOLEN')
  const [killLoading, setKillLoading] = useState(false)
  const [killResult, setKillResult] = useState(null)
  const [killInputMode, setKillInputMode] = useState('manual')
  const [killPreview, setKillPreview] = useState(null)       // unit + history + lab report data
  const [killPreviewLoading, setKillPreviewLoading] = useState(false)

  const [trackerBatch, setTrackerBatch] = useState('')
  const [trackerSerial, setTrackerSerial] = useState('')
  const [trackerMode, setTrackerMode] = useState('batch')
  const [trackerLoading, setTrackerLoading] = useState(false)
  const [trackerResult, setTrackerResult] = useState(null)
  const [trackerError, setTrackerError] = useState('')

  useEffect(() => {
    const token = localStorage.getItem('authToken')
    if (token && token.split('.').length === 3) { setIsAuthenticated(true); loadAll(true) }
    else { localStorage.removeItem('authToken'); setShowAuthModal(true) }
  }, [])

  useEffect(() => {
    if (!isAuthenticated) return
    const iv = setInterval(() => loadAll(false), 15000)
    return () => clearInterval(iv)
  }, [isAuthenticated])

  const loadAll = async (firstLoad = false) => {
    setLoading(true)
    try {
      const [sD, aD, rD, lD, cD, pD, bD] = await Promise.allSettled([
        regulatorAPI.getStats(), regulatorAPI.getAlerts(), regulatorAPI.getRecallEvents(),
        regulatorAPI.getAuditLogs(), complaintAPI.getAllComplaints(),
        regulatorAPI.getRegisteredPartners(), regulatorAPI.getAllBatches(),
      ])
      if (sD.status === 'fulfilled') setStats(sD.value)
      if (aD.status === 'fulfilled') {
        const list = Array.isArray(aD.value) ? aD.value : (aD.value?.alerts || [])
        const norm = list.map(a => ({ ...a, alertType: a.type || a.alertType || a.severity || 'INFO', message: a.message || a.description || '' }))
        setAlerts(norm)
        if (firstLoad && !overlayShownRef.current) {
          if (norm.some(a => { const t = (a.alertType || '').toUpperCase(); return t.includes('CRITICAL') || t.includes('COUNTERFEIT') || t.includes('DANGER') })) {
            setShowAlertOverlay(true); overlayShownRef.current = true
          }
        }
      }
      if (rD.status === 'fulfilled') {
        const list = Array.isArray(rD.value) ? rD.value : (rD.value?.recalls || [])
        setRecalls(list.map(r => ({ ...r, medicineName: r.medicineName || r.batchNumber || 'Unknown' })))
      }
      if (lD.status === 'fulfilled') { const raw = lD.value; setAuditLogs(Array.isArray(raw) ? raw : (raw?.logs || [])) }
      if (cD.status === 'fulfilled') {
        const cList = Array.isArray(cD.value) ? cD.value : []
        setComplaints(cList)
        if (firstLoad && !overlayShownRef.current && cList.some(c => c.aiSeverity === 'CRITICAL' || c.aiSeverity === 'HIGH')) {
          setShowAlertOverlay(true); overlayShownRef.current = true
        }
      }
      if (pD.status === 'fulfilled') setPartners(pD.value || { manufacturers: [], distributors: [], retailers: [], totalManufacturers: 0, totalDistributors: 0, totalRetailers: 0 })
      if (bD.status === 'fulfilled') setBatches(Array.isArray(bD.value) ? bD.value : [])
    } finally { setLoading(false) }
  }

  const handleAuthSuccess = (r) => {
    if (r?.role) localStorage.setItem('userRole', r.role)
    if (r?.email) localStorage.setItem('username', r.email)
    setShowAuthModal(false); setIsAuthenticated(true); loadAll(true)
  }
  const handleLogout = () => {
    ['authToken', 'userRole', 'username', 'userEmail'].forEach(k => localStorage.removeItem(k))
    setIsAuthenticated(false); setShowAuthModal(true)
  }
  const handleVerifyPartner = async (id) => {
    try { await regulatorAPI.verifyPartner(id); loadAll() } catch (e) { alert(e.response?.data?.error || 'Failed') }
  }
  const handleUnverifyPartner = async (id) => {
    try { await regulatorAPI.unverifyPartner(id); loadAll() } catch (e) { alert(e.response?.data?.error || 'Failed') }
  }
  const handleKill = async () => {
    if (!killSerial.trim()) { setKillResult({ success: false, error: 'Enter serial number' }); return }
    setKillLoading(true); setKillResult(null)
    try { const r = await securityAPI.killHierarchy(killSerial, killReason); setKillResult({ success: true, data: r }); setKillSerial(''); setKillPreview(null); setTimeout(() => loadAll(), 1000) }
    catch (e) { setKillResult({ success: false, error: e.response?.data?.error || 'Kill switch failed' }) }
    finally { setKillLoading(false) }
  }
  const handleKillInvestigate = async () => {
    if (!killSerial.trim()) return
    setKillPreviewLoading(true); setKillPreview(null); setKillResult(null)
    try {
      const [unitData, historyData] = await Promise.allSettled([
        verifyAPI.verifyUnit(killSerial.trim()),
        verifyAPI.getUnitHistory(killSerial.trim()),
      ])
      const unit = unitData.status === 'fulfilled' ? unitData.value : null
      const history = historyData.status === 'fulfilled' ? historyData.value : null
      let labReport = null
      const batchNum = unit?.batchNumber || unit?.batch?.batchNumber
      if (batchNum) {
        try { labReport = await regulatorAPI.getBatchLocation(batchNum) } catch {}
      }
      setKillPreview({ unit, history, labReport, batchNumber: batchNum })
    } catch (e) {
      setKillPreview({ error: 'Could not load unit details' })
    } finally { setKillPreviewLoading(false) }
  }
  const handleTrackBatch = async () => {
    if (!trackerBatch.trim()) { setTrackerError('Enter batch number'); return }
    setTrackerLoading(true); setTrackerResult(null); setTrackerError('')
    try { setTrackerResult(await regulatorAPI.getBatchLocation(trackerBatch.trim())) }
    catch (e) { setTrackerError(e.response?.status === 404 ? 'Batch not found' : 'Failed') }
    finally { setTrackerLoading(false) }
  }
  const handleTrackSerial = async () => {
    if (!trackerSerial.trim()) { setTrackerError('Enter serial number'); return }
    setTrackerLoading(true); setTrackerResult(null); setTrackerError('')
    try {
      const u = await verifyAPI.verifyUnit(trackerSerial.trim())
      const batchNum = u?.batchNumber || u?.batch?.batchNumber
      if (!batchNum) { setTrackerError('Batch not found for this serial'); return }
      setTrackerBatch(batchNum)
      setTrackerResult(await regulatorAPI.getBatchLocation(batchNum))
    } catch (e) { setTrackerError(e.response?.data?.error || 'Failed to track serial') }
    finally { setTrackerLoading(false) }
  }

  const TABS = [
    { id: 'overview',   icon: '🗺️',  label: 'Overview',    color: 'text-indigo-400' },
    { id: 'alerts',     icon: '🚨',  label: 'Alerts',      color: 'text-red-400',    badge: alerts.length || null },
    { id: 'complaints', icon: '📝',  label: 'Complaints',  color: 'text-orange-400', badge: complaints.length || null },
    { id: 'batches',    icon: '💊',  label: 'Batches',     color: 'text-blue-400',   badge: batches.length || null },
    { id: 'recalls',    icon: '📋',  label: 'Recalls',     color: 'text-red-400',    badge: recalls.length || null },
    { id: 'audit',      icon: '📜',  label: 'Audit Logs',  color: 'text-purple-400' },
    { id: 'partners',   icon: '🤝',  label: 'Partners',    color: 'text-teal-400' },
    { id: 'tracker',    icon: '📍',  label: 'Tracker',     color: 'text-cyan-400' },
    { id: 'killswitch', icon: '⚡',  label: 'Kill Switch', color: 'text-red-400' },
  ]

  const activeTabColor = TABS.find(t => t.id === activeTab)?.color || 'text-indigo-400'

  return (
    <div className="w-full min-h-screen" style={{ background: '#0f172a' }}>
      {showAuthModal && <AuthModal onAuthSuccess={handleAuthSuccess} allowedRoles={['REGULATOR']} />}
      {selectedAlert && <AlertDetailModal item={selectedAlert} onClose={() => setSelectedAlert(null)} />}
      {showAlertOverlay && (
        <CriticalAlertOverlay alerts={alerts} complaints={complaints}
          onDismiss={() => { setShowAlertOverlay(false); setActiveTab('alerts') }}
          onViewAlert={(item) => setSelectedAlert(item)} />
      )}

      <div className="max-w-7xl mx-auto p-6 space-y-5">

        {/* Header */}
        <div className="flex justify-between items-center rounded-2xl border border-slate-700/60 px-6 py-4" style={{ background: '#1e293b' }}>
          <div className="flex items-center gap-4">
            <div className="w-11 h-11 rounded-xl bg-indigo-600 flex items-center justify-center text-white text-xl shadow-lg shadow-indigo-500/20">👮</div>
            <div>
              <h1 className="text-white font-bold text-xl">Regulator Control Center</h1>
              <p className="text-slate-400 text-xs">PharmaTrust · National Drug Safety Authority</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/15 border border-emerald-500/30">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-emerald-400 text-xs font-semibold">Live</span>
            </div>
            <button onClick={() => loadAll()} disabled={loading}
              className="px-4 py-2 rounded-xl bg-slate-700 hover:bg-slate-600 text-slate-200 text-sm font-semibold transition-colors">
              {loading ? '⏳' : '🔄'} Refresh
            </button>
            {isAuthenticated && (
              <button onClick={handleLogout}
                className="px-4 py-2 rounded-xl bg-red-500/15 hover:bg-red-500/25 text-red-400 text-sm font-semibold border border-red-500/30 transition-colors">
                🚪 Logout
              </button>
            )}
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { icon: '📦', label: 'Total Units',      value: stats.totalUnits,      sub: 'In system',      bg: 'bg-indigo-500/15', border: 'border-indigo-500/30', text: 'text-indigo-400' },
            { icon: '✅', label: 'Active Units',     value: stats.activeUnits,     sub: 'Circulating',    bg: 'bg-emerald-500/15', border: 'border-emerald-500/30', text: 'text-emerald-400' },
            { icon: '⚠️', label: 'Suspicious Scans', value: stats.suspiciousScans, sub: 'Need review',    bg: 'bg-amber-500/15',   border: 'border-amber-500/30',   text: 'text-amber-400' },
            { icon: '🚨', label: 'Flagged Units',    value: stats.flaggedUnits,    sub: 'Blocked',        bg: 'bg-red-500/15',     border: 'border-red-500/30',     text: 'text-red-400' },
          ].map(s => (
            <div key={s.label} className={`rounded-2xl border p-5 ${s.bg} ${s.border}`}>
              <div className="flex items-center justify-between mb-3">
                <span className="text-2xl">{s.icon}</span>
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${s.bg} ${s.text} border ${s.border}`}>{s.sub}</span>
              </div>
              <p className={`text-3xl font-black mb-1 ${s.text}`}>{(s.value || 0).toLocaleString()}</p>
              <p className="text-slate-400 text-sm">{s.label}</p>
            </div>
          ))}
        </div>

        {/* Tabs + Content */}
        <div className="rounded-2xl border border-slate-700/60 overflow-hidden" style={{ background: '#1e293b' }}>
          <div className="flex overflow-x-auto border-b border-slate-700/60" style={{ background: '#162032' }}>
            {TABS.map(tab => (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2 px-5 py-3.5 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
                  activeTab === tab.id
                    ? `border-current ${tab.color} bg-white/5`
                    : 'border-transparent text-slate-500 hover:text-slate-300 hover:bg-white/3'
                }`}>
                <span>{tab.icon}</span>
                <span>{tab.label}</span>
                {tab.badge ? (
                  <span className="px-1.5 py-0.5 rounded-full text-xs font-bold bg-red-500/25 text-red-400">{tab.badge}</span>
                ) : null}
              </button>
            ))}
          </div>

          <div className="p-6">

            {/* OVERVIEW */}
            {activeTab === 'overview' && (
              <div className="space-y-5">
                <h3 className="text-slate-200 font-semibold">Supply Chain Distribution</h3>
                {Object.keys(stats.unitsByOwner || {}).length === 0 ? (
                  <div className="text-center py-16 text-slate-500">
                    <span className="text-4xl block mb-3">📊</span>
                    <p className="text-sm">No supply chain data available</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {Object.entries(stats.unitsByOwner || {}).map(([role, count], i) => {
                      const pct = stats.totalUnits > 0 ? (count / stats.totalUnits * 100) : 0
                      const colors = [
                        { bar: 'bg-indigo-500', text: 'text-indigo-400', bg: 'bg-indigo-500/10', border: 'border-indigo-500/25' },
                        { bar: 'bg-purple-500', text: 'text-purple-400', bg: 'bg-purple-500/10', border: 'border-purple-500/25' },
                        { bar: 'bg-teal-500',   text: 'text-teal-400',   bg: 'bg-teal-500/10',   border: 'border-teal-500/25' },
                        { bar: 'bg-cyan-500',   text: 'text-cyan-400',   bg: 'bg-cyan-500/10',   border: 'border-cyan-500/25' },
                      ]
                      const c = colors[i % colors.length]
                      return (
                        <div key={role} className={`p-4 rounded-xl border ${c.bg} ${c.border}`}>
                          <p className="text-slate-400 text-xs uppercase tracking-wider mb-1">{role}</p>
                          <p className={`text-2xl font-black mb-1 ${c.text}`}>{(count || 0).toLocaleString()}</p>
                          <p className="text-slate-500 text-xs mb-2">{pct.toFixed(1)}%</p>
                          <div className="h-1.5 bg-slate-700 rounded-full overflow-hidden">
                            <div className={`h-full rounded-full ${c.bar}`} style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
                <div className="grid grid-cols-3 gap-4">
                  <div className="p-4 rounded-xl border border-red-500/25 bg-red-500/10">
                    <p className="text-red-400 text-xs font-semibold uppercase mb-1">Active Recalls</p>
                    <p className="text-2xl font-black text-red-400">{recalls.length}</p>
                  </div>
                  <div className="p-4 rounded-xl border border-orange-500/25 bg-orange-500/10">
                    <p className="text-orange-400 text-xs font-semibold uppercase mb-1">Open Complaints</p>
                    <p className="text-2xl font-black text-orange-400">{complaints.filter(c => c.status === 'OPEN' || c.status === 'ESCALATED').length}</p>
                  </div>
                  <div className="p-4 rounded-xl border border-blue-500/25 bg-blue-500/10">
                    <p className="text-blue-400 text-xs font-semibold uppercase mb-1">Total Batches</p>
                    <p className="text-2xl font-black text-blue-400">{batches.length}</p>
                  </div>
                </div>
              </div>
            )}

            {/* ALERTS */}
            {activeTab === 'alerts' && (
              <div className="space-y-3">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-slate-200 font-semibold">🚨 Active Alerts</h3>
                  <span className="text-xs px-2 py-1 rounded-full bg-red-500/20 text-red-400 font-semibold">{alerts.length} total</span>
                </div>
                {alerts.length === 0 ? (
                  <div className="text-center py-16 text-slate-500">
                    <span className="text-4xl block mb-3">✅</span>
                    <p className="text-sm">No active alerts</p>
                  </div>
                ) : alerts.map((alert, i) => {
                  const sev = SEV(alert.alertType || alert.type)
                  return (
                    <div key={alert.id || i}
                      className={`flex items-start gap-4 p-4 rounded-xl border-l-4 border cursor-pointer hover:bg-white/5 transition-colors ${sev.left} border-slate-700/50`}
                      style={{ background: 'rgba(255,255,255,0.03)' }}
                      onClick={() => setSelectedAlert(alert)}>
                      <div className={`w-2.5 h-2.5 rounded-full mt-1.5 flex-shrink-0 ${sev.dot}`} />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                          <span className={`text-xs px-2 py-0.5 rounded-full font-bold border ${sev.bg} ${sev.text} ${sev.border}`}>
                            {(alert.alertType || 'ALERT').toUpperCase()}
                          </span>
                          {alert.createdAt && <span className="text-slate-500 text-xs">{new Date(alert.createdAt).toLocaleString()}</span>}
                        </div>
                        <p className="text-slate-200 font-semibold text-sm">{alert.message || alert.description}</p>
                        {alert.serialNumber && <p className="text-slate-500 text-xs font-mono mt-0.5">Serial: {alert.serialNumber}</p>}
                      </div>
                      <span className="text-slate-400 text-xs font-semibold flex-shrink-0">View →</span>
                    </div>
                  )
                })}
              </div>
            )}

            {/* COMPLAINTS */}
            {activeTab === 'complaints' && (
              <div className="space-y-3">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-slate-200 font-semibold">📝 Patient Complaints</h3>
                  <span className="text-xs px-2 py-1 rounded-full bg-orange-500/20 text-orange-400 font-semibold">{complaints.length} total</span>
                </div>
                {complaints.length === 0 ? (
                  <div className="text-center py-16 text-slate-500">
                    <span className="text-4xl block mb-3">📭</span>
                    <p className="text-sm">No complaints filed yet</p>
                  </div>
                ) : complaints.map((c, i) => {
                  const sev = SEV(c.aiSeverity)
                  const statusCls = { ESCALATED: 'bg-red-500/20 text-red-400 border-red-500/40', OPEN: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40', UNDER_REVIEW: 'bg-blue-500/20 text-blue-400 border-blue-500/40', RESOLVED: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' }[c.status] || 'bg-slate-500/20 text-slate-400 border-slate-500/40'
                  return (
                    <div key={c.id || i}
                      className="flex items-start gap-4 p-4 rounded-xl border border-slate-700/50 cursor-pointer hover:bg-white/5 transition-colors"
                      style={{ background: 'rgba(255,255,255,0.03)' }}
                      onClick={() => setSelectedAlert(c)}>
                      <div className={`w-2.5 h-2.5 rounded-full mt-1.5 flex-shrink-0 ${sev.dot}`} />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                          <span className={`text-xs px-2 py-0.5 rounded-full font-bold border ${statusCls}`}>{c.status}</span>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-bold border ${sev.bg} ${sev.text} ${sev.border}`}>{c.aiSeverity}</span>
                          {c.createdAt && <span className="text-slate-500 text-xs">{new Date(c.createdAt).toLocaleString()}</span>}
                        </div>
                        <p className="text-slate-200 font-semibold text-sm">{c.issueType?.replace(/_/g, ' ')} — {c.medicineName || 'Unknown'}</p>
                        {c.description && <p className="text-slate-400 text-xs mt-0.5 italic truncate">"{c.description}"</p>}
                        <p className="text-slate-500 text-xs mt-0.5">Reporter: {c.reporter?.shopName || c.reporter?.fullName || c.reporter?.email || 'Unknown'}</p>
                      </div>
                      <span className="text-slate-400 text-xs font-semibold flex-shrink-0">View →</span>
                    </div>
                  )
                })}
              </div>
            )}

            {/* BATCHES */}
            {activeTab === 'batches' && (
              <div className="space-y-3">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-slate-200 font-semibold">💊 All Batches</h3>
                  <span className="text-xs px-2 py-1 rounded-full bg-blue-500/20 text-blue-400 font-semibold">{batches.length} total</span>
                </div>
                {batches.length === 0 ? (
                  <div className="text-center py-16 text-slate-500"><span className="text-4xl block mb-3">💊</span><p className="text-sm">No batches found</p></div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {batches.map((b, i) => {
                      const sc = { APPROVED: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40', RECALLED: 'bg-red-500/20 text-red-400 border-red-500/40', PENDING: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40' }[b.status] || 'bg-slate-500/20 text-slate-400 border-slate-500/40'
                      const isExpired = b.expiryDate && new Date(b.expiryDate) < new Date()
                      return (
                        <div key={b.id || i} className="p-4 rounded-xl border border-slate-700/50" style={{ background: 'rgba(255,255,255,0.03)' }}>
                          <div className="flex justify-between items-start mb-3">
                            <div>
                              <p className="text-slate-200 font-semibold text-sm">{b.medicineName || 'Unknown'}</p>
                              <p className="text-slate-500 text-xs font-mono">{b.batchNumber}</p>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-bold border ${sc}`}>{b.status || 'UNKNOWN'}</span>
                          </div>
                          <div className="grid grid-cols-2 gap-2 text-xs">
                            <div><p className="text-slate-500">Manufacturer</p><p className="text-slate-300">{b.manufacturerEmail || b.manufacturer || '—'}</p></div>
                            <div><p className="text-slate-500">Quantity</p><p className="text-slate-300">{b.quantity || '—'}</p></div>
                            <div><p className="text-slate-500">Mfg Date</p><p className="text-slate-300">{b.manufacturingDate ? new Date(b.manufacturingDate).toLocaleDateString() : '—'}</p></div>
                            <div><p className="text-slate-500">Expiry</p><p className={isExpired ? 'text-red-400 font-bold' : 'text-slate-300'}>{b.expiryDate ? new Date(b.expiryDate).toLocaleDateString() : '—'}{isExpired ? ' ⚠️' : ''}</p></div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}

            {/* RECALLS */}
            {activeTab === 'recalls' && (
              <div className="space-y-3">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-slate-200 font-semibold">📋 Recall Events</h3>
                  <span className="text-xs px-2 py-1 rounded-full bg-red-500/20 text-red-400 font-semibold">{recalls.length} active</span>
                </div>
                {recalls.length === 0 ? (
                  <div className="text-center py-16 text-slate-500"><span className="text-4xl block mb-3">✅</span><p className="text-sm">No active recalls</p></div>
                ) : recalls.map((r, i) => (
                  <div key={r.id || i} className="p-4 rounded-xl border-l-4 border-l-red-400 border border-slate-700/50" style={{ background: 'rgba(239,68,68,0.05)' }}>
                    <div className="flex justify-between items-start mb-2">
                      <div>
                        <p className="text-slate-200 font-semibold text-sm">{r.medicineName}</p>
                        <p className="text-slate-500 text-xs font-mono">Batch: {r.batchNumber}</p>
                      </div>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 font-bold">{r.recallType || r.reason || 'RECALL'}</span>
                    </div>
                    <p className="text-slate-400 text-sm">{r.reason || r.description || '—'}</p>
                    <div className="flex justify-between mt-2 text-xs text-slate-500">
                      <span>Units affected: <span className="text-red-400 font-bold">{r.affectedUnits || r.unitsAffected || '—'}</span></span>
                      <span>{r.createdAt ? new Date(r.createdAt).toLocaleString() : ''}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* AUDIT LOGS */}
            {activeTab === 'audit' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-slate-200 font-semibold">📜 Audit Logs</h3>
                  <span className="text-xs px-2 py-1 rounded-full bg-purple-500/20 text-purple-400 font-semibold">{auditLogs.length} entries</span>
                </div>
                {auditLogs.length === 0 ? (
                  <div className="text-center py-16 text-slate-500"><span className="text-4xl block mb-3">📜</span><p className="text-sm">No audit logs</p></div>
                ) : auditLogs.map((log, i) => (
                  <div key={log.id || i} className="flex items-start gap-3 p-3 rounded-xl border border-slate-700/40 hover:bg-white/3 transition-colors" style={{ background: 'rgba(255,255,255,0.02)' }}>
                    <div className="w-2 h-2 rounded-full bg-purple-400 mt-2 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-start gap-2">
                        <p className="text-slate-200 text-sm font-semibold">{log.action || log.event || log.type || 'Action'}</p>
                        <p className="text-slate-500 text-xs flex-shrink-0">{log.timestamp || log.createdAt ? new Date(log.timestamp || log.createdAt).toLocaleString() : ''}</p>
                      </div>
                      {(log.details || log.description || log.message) && <p className="text-slate-400 text-xs mt-0.5">{log.details || log.description || log.message}</p>}
                      {log.performedBy && <p className="text-purple-400 text-xs mt-0.5">By: {log.performedBy}</p>}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* PARTNERS */}
            {activeTab === 'partners' && (
              <div className="space-y-5">
                <h3 className="text-slate-200 font-semibold">🤝 Registered Partners</h3>
                {[
                  { key: 'manufacturers', label: 'Manufacturers', icon: '🏭', color: 'text-cyan-400',  bg: 'bg-cyan-500/10',  border: 'border-cyan-500/25',  total: partners.totalManufacturers },
                  { key: 'distributors',  label: 'Distributors',  icon: '🚚', color: 'text-purple-400', bg: 'bg-purple-500/10', border: 'border-purple-500/25', total: partners.totalDistributors },
                  { key: 'retailers',     label: 'Retailers',     icon: '🏪', color: 'text-teal-400',  bg: 'bg-teal-500/10',  border: 'border-teal-500/25',  total: partners.totalRetailers },
                ].map(({ key, label, icon, color, bg, border, total }) => {
                  const list = partners[key] || []
                  const unverified = list.filter(p => !p.verified && !p.isVerified)
                  const verified   = list.filter(p =>  p.verified ||  p.isVerified)
                  return (
                    <div key={key} className={`rounded-xl border overflow-hidden ${border}`} style={{ background: 'rgba(255,255,255,0.02)' }}>
                      <div className={`flex items-center justify-between px-5 py-3 border-b ${border} ${bg}`}>
                        <div className="flex items-center gap-2">
                          <span className="text-lg">{icon}</span>
                          <span className={`font-semibold text-sm ${color}`}>{label}</span>
                          <span className="text-slate-500 text-xs">({total || list.length} registered)</span>
                        </div>
                        <div className="flex gap-3 text-xs">
                          <span className="text-yellow-400">{unverified.length} pending</span>
                          <span className="text-emerald-400">{verified.length} verified</span>
                        </div>
                      </div>

                      {unverified.length > 0 && (
                        <div className="p-4 border-b border-slate-700/40">
                          <p className="text-yellow-400 text-xs font-bold uppercase tracking-wider mb-3">⏳ Awaiting Verification</p>
                          <div className="space-y-2">
                            {unverified.map((p, i) => (
                              <div key={p.id || i} className="flex items-center justify-between p-3 rounded-lg border border-yellow-500/20 bg-yellow-500/8">
                                <div>
                                  <p className="text-slate-200 font-semibold text-sm">{p.shopName || p.companyName || p.email}</p>
                                  <p className="text-slate-400 text-xs">{p.email}</p>
                                  {p.cityState && <p className="text-slate-500 text-xs">{p.cityState}</p>}
                                  {p.licenseNumber && <p className="text-yellow-400 text-xs font-mono">License: {p.licenseNumber}</p>}
                                </div>
                                <button onClick={() => handleVerifyPartner(p.id)}
                                  className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition-colors">
                                  ✅ Verify
                                </button>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {verified.length > 0 && (
                        <div className="p-4">
                          <p className="text-emerald-400 text-xs font-bold uppercase tracking-wider mb-3">✅ Verified & Active</p>
                          <div className="space-y-2">
                            {verified.map((p, i) => (
                              <div key={p.id || i} className="flex items-center justify-between p-3 rounded-lg border border-emerald-500/20 bg-emerald-500/8">
                                <div>
                                  <div className="flex items-center gap-2">
                                    <p className="text-slate-200 font-semibold text-sm">{p.shopName || p.companyName || p.email}</p>
                                    <span className="text-xs px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">✓</span>
                                  </div>
                                  <p className="text-slate-400 text-xs">{p.email}</p>
                                  {p.cityState && <p className="text-slate-500 text-xs">{p.cityState}</p>}
                                </div>
                                <button onClick={() => handleUnverifyPartner(p.id)}
                                  className="px-3 py-1.5 rounded-lg bg-red-500/15 hover:bg-red-500/25 text-red-400 text-xs font-semibold border border-red-500/30 transition-colors">
                                  Revoke
                                </button>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {list.length === 0 && (
                        <div className="text-center py-8 text-slate-500">
                          <span className="text-2xl block mb-2">{icon}</span>
                          <p className="text-sm">No {label.toLowerCase()} registered yet</p>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}

            {/* TRACKER */}
            {activeTab === 'tracker' && (
              <div className="space-y-5 max-w-2xl">
                <h3 className="text-slate-200 font-semibold">📍 Batch & Unit Tracker</h3>
                <div className="flex gap-2 p-1 rounded-xl border border-slate-700 bg-slate-800/60 w-fit">
                  {[{ id: 'batch', label: '📦 By Batch' }, { id: 'serial', label: '🔍 By Serial' }, { id: 'qr', label: '📷 Scan QR' }].map(m => (
                    <button key={m.id} onClick={() => { setTrackerMode(m.id); setTrackerResult(null); setTrackerError('') }}
                      className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors ${trackerMode === m.id ? 'bg-cyan-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'}`}>
                      {m.label}
                    </button>
                  ))}
                </div>

                {trackerMode === 'batch' && (
                  <div className="flex gap-3">
                    <input value={trackerBatch} onChange={e => setTrackerBatch(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleTrackBatch()}
                      placeholder="Enter batch number..." className="flex-1 px-4 py-2.5 rounded-xl border border-slate-600 bg-slate-800 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 text-sm" />
                    <button onClick={handleTrackBatch} disabled={trackerLoading} className="px-5 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-semibold transition-colors">
                      {trackerLoading ? '⏳' : '🔍 Track'}
                    </button>
                  </div>
                )}
                {trackerMode === 'serial' && (
                  <div className="flex gap-3">
                    <input value={trackerSerial} onChange={e => setTrackerSerial(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleTrackSerial()}
                      placeholder="Enter serial number..." className="flex-1 px-4 py-2.5 rounded-xl border border-slate-600 bg-slate-800 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 text-sm" />
                    <button onClick={handleTrackSerial} disabled={trackerLoading} className="px-5 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-semibold transition-colors">
                      {trackerLoading ? '⏳' : '🔍 Track'}
                    </button>
                  </div>
                )}
                {trackerMode === 'qr' && (
                  <div className="rounded-xl border border-slate-700 overflow-hidden">
                    <QRScanner onScan={(val) => { setTrackerSerial(val); setTrackerMode('serial'); setTimeout(handleTrackSerial, 100) }} />
                  </div>
                )}

                {trackerError && <div className="p-3 rounded-xl border border-red-500/30 bg-red-500/10 text-red-400 text-sm">{trackerError}</div>}

                {trackerResult && (
                  <div className="rounded-xl border border-slate-700 p-5 space-y-4" style={{ background: 'rgba(255,255,255,0.03)' }}>
                    <p className="text-cyan-400 font-semibold text-sm">📍 Tracking Result</p>

                    {/* Batch Summary */}
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-3 text-sm">
                      {[
                        { label: 'Batch Number', value: trackerResult.batchNumber },
                        { label: 'Medicine', value: trackerResult.medicineName },
                        { label: 'Status', value: trackerResult.batchStatus || trackerResult.status },
                        { label: 'Total Units', value: trackerResult.totalUnits },
                        { label: 'Expiry Date', value: trackerResult.expiryDate ? new Date(trackerResult.expiryDate).toLocaleDateString() : null },
                      ].filter(f => f.value != null).map(({ label, value }) => (
                        <div key={label} className="p-3 rounded-lg border border-slate-700 bg-slate-800/60">
                          <p className="text-slate-500 text-xs mb-1">{label}</p>
                          <p className="text-slate-200 font-semibold text-sm">{value}</p>
                        </div>
                      ))}
                    </div>

                    {/* Supply Chain Summary */}
                    {trackerResult.summary && Object.keys(trackerResult.summary).length > 0 && (
                      <div>
                        <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-3">📊 Units by Role</p>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                          {Object.entries(trackerResult.summary).map(([role, count]) => (
                            <div key={role} className="p-3 rounded-lg border border-cyan-500/20 bg-cyan-500/8 text-center">
                              <p className="text-cyan-400 text-xs font-bold">{role}</p>
                              <p className="text-white text-xl font-black">{count}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Distributors */}
                    {trackerResult.distributors?.length > 0 && (
                      <div>
                        <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-3">🚚 Distributors Holding Stock</p>
                        <div className="space-y-2">
                          {trackerResult.distributors.map((d, idx) => (
                            <div key={idx} className="flex items-start justify-between p-3 rounded-lg border border-purple-500/20 bg-purple-500/8">
                              <div>
                                <p className="text-white font-semibold text-sm">{d.shopName || d.name || d.email}</p>
                                <p className="text-slate-400 text-xs">{d.email}</p>
                                {d.cityState && <p className="text-slate-500 text-xs">{d.cityState}</p>}
                                {d.phoneNumber && d.phoneNumber !== 'N/A' && <p className="text-slate-500 text-xs">📞 {d.phoneNumber}</p>}
                              </div>
                              <span className="text-purple-400 font-black text-lg">{d.unitCount}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Retailers */}
                    {trackerResult.retailers?.length > 0 && (
                      <div>
                        <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-3">🏪 Retailers Holding Stock</p>
                        <div className="space-y-2">
                          {trackerResult.retailers.map((r, idx) => (
                            <div key={idx} className="flex items-start justify-between p-3 rounded-lg border border-teal-500/20 bg-teal-500/8">
                              <div>
                                <p className="text-white font-semibold text-sm">{r.shopName || r.name || r.email}</p>
                                <p className="text-slate-400 text-xs">{r.email}</p>
                                {r.cityState && <p className="text-slate-500 text-xs">{r.cityState}</p>}
                                {r.phoneNumber && r.phoneNumber !== 'N/A' && <p className="text-slate-500 text-xs">📞 {r.phoneNumber}</p>}
                              </div>
                              <span className="text-teal-400 font-black text-lg">{r.unitCount}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Individual Units (collapsed, show first 10) */}
                    {trackerResult.units?.length > 0 && (
                      <div>
                        <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-3">
                          📦 Unit Details (showing {Math.min(10, trackerResult.units.length)} of {trackerResult.units.length})
                        </p>
                        <div className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
                          {trackerResult.units.slice(0, 10).map((u, idx) => (
                            <div key={idx} className="flex items-center justify-between p-2.5 rounded-lg border border-slate-700/50 bg-slate-800/40 text-xs">
                              <div className="flex items-center gap-2 min-w-0">
                                <span className={`w-2 h-2 rounded-full flex-shrink-0 ${u.status === 'ACTIVE' ? 'bg-emerald-400' : u.status === 'RECALLED' ? 'bg-red-400' : 'bg-slate-400'}`} />
                                <span className="text-slate-300 font-mono truncate">{u.serialNumber}</span>
                              </div>
                              <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                                <span className="text-slate-500">{u.unitType}</span>
                                <span className="text-cyan-400 font-semibold">{u.currentOwnerRole}</span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* KILL SWITCH */}
            {activeTab === 'killswitch' && (
              <div className="space-y-5 max-w-2xl">
                <div>
                  <h3 className="text-slate-200 font-semibold">⚡ Hierarchy Kill Switch</h3>
                  <p className="text-slate-400 text-sm mt-1">Investigate a unit first, then confirm deactivation.</p>
                </div>

                {/* STEP 1 — Serial Input */}
                <div className="rounded-xl border border-slate-700 p-5 space-y-4 bg-slate-800/40">
                  <p className="text-slate-300 text-xs font-bold uppercase tracking-wider">Step 1 — Enter or Scan Serial Number</p>
                  <div className="flex gap-2 mb-2">
                    <button onClick={() => setKillInputMode('manual')}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-semibold transition-colors border ${killInputMode === 'manual' ? 'bg-indigo-600 border-indigo-500 text-white' : 'bg-slate-800 border-slate-600 text-slate-400 hover:text-slate-200'}`}>
                      ⌨️ Manual Entry
                    </button>
                    <button onClick={() => setKillInputMode('scanner')}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-semibold transition-colors border ${killInputMode === 'scanner' ? 'bg-indigo-600 border-indigo-500 text-white' : 'bg-slate-800 border-slate-600 text-slate-400 hover:text-slate-200'}`}>
                      📷 QR Scanner
                    </button>
                  </div>
                  {killInputMode === 'manual' ? (
                    <input value={killSerial} onChange={e => { setKillSerial(e.target.value); setKillPreview(null); setKillResult(null) }}
                      placeholder="Enter serial number..."
                      className="w-full px-4 py-2.5 rounded-xl border border-slate-600 bg-slate-800 text-white placeholder-slate-500 focus:outline-none focus:border-indigo-400 text-sm font-mono" />
                  ) : (
                    <div className="space-y-2">
                      <QRScanner onScan={sn => { setKillSerial(sn); setKillInputMode('manual'); setKillPreview(null); setKillResult(null) }} color="#6366f1" label="Scan Unit QR Code" />
                      {killSerial && (
                        <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 border border-indigo-500/30">
                          <span className="text-emerald-400 text-xs">✓ Scanned:</span>
                          <span className="text-white text-xs font-mono truncate">{killSerial}</span>
                        </div>
                      )}
                    </div>
                  )}
                  <button onClick={handleKillInvestigate} disabled={!killSerial.trim() || killPreviewLoading}
                    className="w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white font-bold text-sm transition-colors">
                    {killPreviewLoading ? '⏳ Loading details...' : '🔍 Investigate Unit'}
                  </button>
                </div>

                {/* STEP 2 — Preview + Confirm */}
                {killPreview && (
                  <div className="space-y-4">
                    {killPreview.error ? (
                      <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10">
                        <p className="text-red-400 text-sm">❌ {killPreview.error}</p>
                      </div>
                    ) : (
                      <>
                        {/* Unit Details */}
                        {killPreview.unit && (
                          <div className="p-4 rounded-xl bg-blue-500/10 border border-blue-500/20">
                            <p className="text-blue-400 text-xs font-bold uppercase tracking-wider mb-3">💊 Unit Details</p>
                            <div className="grid grid-cols-2 gap-3 text-sm">
                              <div><p className="text-slate-500 text-xs">Medicine</p><p className="text-white font-semibold">{killPreview.unit.medicineName || '—'}</p></div>
                              <div><p className="text-slate-500 text-xs">Batch</p><p className="text-slate-300 font-mono text-xs">{killPreview.unit.batchNumber || '—'}</p></div>
                              <div><p className="text-slate-500 text-xs">Status</p>
                                <span className={`px-2 py-0.5 rounded text-xs font-bold ${killPreview.unit.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                                  {killPreview.unit.status || '—'}
                                </span>
                              </div>
                              <div><p className="text-slate-500 text-xs">Expiry</p><p className="text-slate-300">{killPreview.unit.expiryDate || '—'}</p></div>
                              <div><p className="text-slate-500 text-xs">Manufacturer</p><p className="text-slate-300">{killPreview.unit.manufacturerName || '—'}</p></div>
                              <div><p className="text-slate-500 text-xs">Unit Type</p><p className="text-slate-300">{killPreview.unit.unitType || '—'}</p></div>
                            </div>
                          </div>
                        )}

                        {/* Scan History */}
                        {killPreview.history && (
                          <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
                            <p className="text-amber-400 text-xs font-bold uppercase tracking-wider mb-3">📍 Scan History ({killPreview.history.totalScans || 0} scans)</p>
                            {(killPreview.history.history || []).length === 0 ? (
                              <p className="text-slate-500 text-sm">No scan history</p>
                            ) : (
                              <div className="space-y-2 max-h-40 overflow-y-auto">
                                {(killPreview.history.history || []).slice(0, 6).map((step, idx) => (
                                  <div key={idx} className="flex items-start gap-3 p-2 bg-slate-800 rounded-lg border border-slate-700">
                                    <div className="w-5 h-5 rounded-full bg-amber-500/20 flex items-center justify-center text-xs font-bold text-amber-400 flex-shrink-0">{idx + 1}</div>
                                    <div className="flex-1 min-w-0">
                                      <p className={`text-xs font-bold ${step.result === 'VALID' ? 'text-emerald-400' : step.result === 'COUNTERFEIT' ? 'text-red-400' : 'text-amber-400'}`}>{step.result}</p>
                                      {step.scannedAt && <p className="text-slate-500 text-xs">{new Date(step.scannedAt).toLocaleString()}</p>}
                                      {step.location && step.location !== 'unknown' && <p className="text-slate-500 text-xs">📍 {step.location}</p>}
                                      {step.autoFlagged && <p className="text-red-400 text-xs">⚠️ Auto-flagged</p>}
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        )}

                        {/* Supply Chain / Lab Report */}
                        {killPreview.labReport && (
                          <div className="p-4 rounded-xl bg-cyan-500/10 border border-cyan-500/20">
                            <p className="text-cyan-400 text-xs font-bold uppercase tracking-wider mb-3">🗺️ Supply Chain (Batch: {killPreview.batchNumber})</p>
                            {killPreview.labReport.summary && Object.keys(killPreview.labReport.summary).length > 0 && (
                              <div className="grid grid-cols-3 gap-2 mb-2">
                                {Object.entries(killPreview.labReport.summary).map(([role, count]) => (
                                  <div key={role} className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-center">
                                    <p className="text-cyan-400 text-xs font-bold">{role}</p>
                                    <p className="text-white text-lg font-black">{count}</p>
                                    <p className="text-slate-500 text-xs">units</p>
                                  </div>
                                ))}
                              </div>
                            )}
                            {killPreview.labReport.distributors?.length > 0 && (
                              <p className="text-slate-400 text-xs">🚚 {killPreview.labReport.distributors.length} distributor(s) holding units</p>
                            )}
                            {killPreview.labReport.retailers?.length > 0 && (
                              <p className="text-slate-400 text-xs">🏪 {killPreview.labReport.retailers.length} retailer(s) holding units</p>
                            )}
                          </div>
                        )}

                        {/* Confirm Kill */}
                        <div className="rounded-xl border-2 border-red-500/40 p-5 space-y-4 bg-red-500/5">
                          <div className="flex items-center gap-2 text-red-400 text-sm font-bold">
                            <span>⚠️</span><span>Step 2 — Confirm Kill Switch</span>
                          </div>
                          <p className="text-slate-400 text-xs">You have reviewed the unit details above. Select a reason and execute.</p>
                          <div>
                            <label className="text-slate-400 text-xs font-semibold uppercase tracking-wider mb-1.5 block">Reason</label>
                            <select value={killReason} onChange={e => setKillReason(e.target.value)}
                              className="w-full px-4 py-2.5 rounded-xl border border-red-500/30 bg-slate-800 text-white focus:outline-none focus:border-red-400 text-sm">
                              {['STOLEN', 'COUNTERFEIT', 'EXPIRED', 'CONTAMINATED', 'RECALLED', 'REGULATORY_ACTION'].map(r => (
                                <option key={r} value={r} style={{ background: '#1e293b' }}>{r.replace(/_/g, ' ')}</option>
                              ))}
                            </select>
                          </div>
                          <button onClick={handleKill} disabled={killLoading}
                            className="w-full py-3 rounded-xl bg-red-600 hover:bg-red-500 disabled:opacity-50 text-white font-bold text-sm transition-colors">
                            {killLoading ? '⏳ Executing...' : '⚡ Execute Kill Switch'}
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                )}

                {killResult && (
                  <div className={`p-4 rounded-xl border ${killResult.success ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-red-500/30 bg-red-500/10'}`}>
                    {killResult.success ? (
                      <div>
                        <p className="text-emerald-400 font-bold text-sm">✅ Kill Switch Executed</p>
                        <p className="text-emerald-300/70 text-xs mt-1">{killResult.data?.message || 'Unit and hierarchy deactivated.'}</p>
                        {killResult.data?.killedCount && <p className="text-emerald-400 text-xs font-mono">{killResult.data.killedCount} units deactivated</p>}
                      </div>
                    ) : (
                      <p className="text-red-400 text-sm font-semibold">❌ {killResult.error}</p>
                    )}
                  </div>
                )}
              </div>
            )}

          </div>
        </div>
      </div>
    </div>
  )
}
