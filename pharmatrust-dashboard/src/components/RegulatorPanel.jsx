import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { supplyChainAPI, securityAPI, regulatorAPI, complaintAPI } from '../services/api'
import AuthModal from './AuthModal'

export default function RegulatorPanel() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [activeTab, setActiveTab] = useState('overview')

  const [stats, setStats] = useState({ totalUnits: 0, activeUnits: 0, recalledUnits: 0, flaggedUnits: 0, suspiciousScans: 0, unitsByOwner: {} })
  const [alerts, setAlerts] = useState([])
  const [recalls, setRecalls] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [complaints, setComplaints] = useState([])
  const [loading, setLoading] = useState(false)

  const [killSwitchOpen, setKillSwitchOpen] = useState(false)
  const [killSerialNumber, setKillSerialNumber] = useState('')
  const [killReason, setKillReason] = useState('STOLEN')
  const [killLoading, setKillLoading] = useState(false)
  const [killResult, setKillResult] = useState(null)

  // Batch Tracker state
  const [trackerBatchNumber, setTrackerBatchNumber] = useState('')
  const [trackerLoading, setTrackerLoading] = useState(false)
  const [trackerResult, setTrackerResult] = useState(null)
  const [trackerError, setTrackerError] = useState('')

  useEffect(() => {
    const token = localStorage.getItem('authToken')
    if (token && token.split('.').length === 3) { setIsAuthenticated(true); loadAll() }
    else {
      localStorage.removeItem('authToken')
      setShowAuthModal(true)
    }
  }, [])

  useEffect(() => {
    if (!isAuthenticated) return
    const interval = setInterval(loadAll, 15000)
    return () => clearInterval(interval)
  }, [isAuthenticated])

  const loadAll = async () => {
    setLoading(true)
    try {
      const [statsData, alertsData, recallsData, logsData, complaintsData] = await Promise.allSettled([
        supplyChainAPI.getStats(),
        regulatorAPI.getAlerts(),
        regulatorAPI.getRecallEvents(),
        regulatorAPI.getAuditLogs(),
        complaintAPI.getAllComplaints(),
      ])
      if (statsData.status === 'fulfilled') setStats(statsData.value)
      if (alertsData.status === 'fulfilled') setAlerts(Array.isArray(alertsData.value) ? alertsData.value : [])
      if (recallsData.status === 'fulfilled') setRecalls(Array.isArray(recallsData.value) ? recallsData.value : [])
      if (logsData.status === 'fulfilled') setAuditLogs(Array.isArray(logsData.value) ? logsData.value : [])
      if (complaintsData.status === 'fulfilled') setComplaints(Array.isArray(complaintsData.value) ? complaintsData.value : [])
    } finally { setLoading(false) }
  }

  const handleAuthSuccess = () => { setIsAuthenticated(true); setShowAuthModal(false); loadAll() }
  const handleLogout = () => {
    localStorage.removeItem('authToken'); localStorage.removeItem('userRole'); localStorage.removeItem('username')
    setIsAuthenticated(false); setShowAuthModal(true)
  }

  const handleTrackBatch = async () => {
    if (!trackerBatchNumber.trim()) { setTrackerError('Enter a batch number'); return }
    setTrackerLoading(true); setTrackerResult(null); setTrackerError('')
    try {
      const data = await regulatorAPI.getBatchLocation(trackerBatchNumber.trim())
      setTrackerResult(data)
    } catch (err) {
      setTrackerError(err.response?.status === 404 ? 'Batch not found' : 'Failed to fetch batch location')
    } finally { setTrackerLoading(false) }
  }

  const handleKillHierarchy = async () => {
    if (!killSerialNumber.trim()) { setKillResult({ success: false, error: 'Enter a serial number' }); return }
    setKillLoading(true); setKillResult(null)
    try {
      const result = await securityAPI.killHierarchy(killSerialNumber, killReason)
      setKillResult({ success: true, data: result })
      setKillSerialNumber('')
      setTimeout(loadAll, 1000)
    } catch (err) {
      setKillResult({ success: false, error: err.response?.data?.error || 'Kill switch failed' })
    } finally { setKillLoading(false) }
  }

  const alertColor = (type) => {
    if (!type) return 'border-electric-blue/50 bg-electric-blue/5'
    const t = type.toUpperCase()
    if (t.includes('CRITICAL') || t.includes('DANGER') || t.includes('COUNTERFEIT')) return 'border-red-500/50 bg-red-500/5'
    if (t.includes('WARN') || t.includes('SUSPICIOUS')) return 'border-yellow-500/50 bg-yellow-500/5'
    return 'border-electric-blue/50 bg-electric-blue/5'
  }

  const alertDot = (type) => {
    if (!type) return 'bg-electric-blue'
    const t = type.toUpperCase()
    if (t.includes('CRITICAL') || t.includes('DANGER') || t.includes('COUNTERFEIT')) return 'bg-red-500'
    if (t.includes('WARN') || t.includes('SUSPICIOUS')) return 'bg-yellow-500'
    return 'bg-electric-blue'
  }

  return (
    <div className="w-full">
      {showAuthModal && <AuthModal onAuthSuccess={handleAuthSuccess} allowedRoles={['REGULATOR']} />}

      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-3xl font-bold neon-text-blue">👮 Regulator Dashboard</h2>
          <div className="flex gap-3">
            <button onClick={loadAll} disabled={loading}
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

        {/* Stats */}
        <div className="grid grid-cols-4 gap-4 mb-6">
          {[
            { label: 'Total Units', value: stats.totalUnits, color: 'electric-blue', icon: '📦' },
            { label: 'Active Units', value: stats.activeUnits, color: 'neon-green', icon: '✅' },
            { label: 'Suspicious Scans', value: stats.suspiciousScans, color: 'yellow-500', icon: '⚠️' },
            { label: 'Flagged Units', value: stats.flaggedUnits, color: 'red-500', icon: '🚨' },
          ].map((s, i) => (
            <motion.div key={s.label} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
              className={`glass-card p-4 rounded-xl border border-${s.color}/20 metric-card`}>
              <div className="flex items-center gap-2 mb-1">
                <span>{s.icon}</span>
                <p className="text-gray-500 text-xs">{s.label}</p>
              </div>
              <p className={`text-2xl font-bold text-${s.color}`}>{(s.value || 0).toLocaleString()}</p>
            </motion.div>
          ))}
        </div>

        {/* Tabs */}
        <div className="flex gap-1.5 mb-6 p-1 bg-dark-bg/60 rounded-xl border border-white/5 flex-wrap">
          {[
            { id: 'overview', label: '🗺️ Overview' },
            { id: 'alerts', label: `🚨 Alerts ${alerts.length > 0 ? `(${alerts.length})` : ''}` },
            { id: 'complaints', label: `📝 Complaints ${complaints.length > 0 ? `(${complaints.length})` : ''}` },
            { id: 'recalls', label: '📋 Recalls' },
            { id: 'audit', label: '📜 Audit' },
            { id: 'tracker', label: '📍 Tracker' },
            { id: 'killswitch', label: '⚡ Kill Switch' },
          ].map(tab => (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`px-3 py-2 rounded-lg text-xs font-semibold transition-all ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-electric-blue/20 to-neon-purple/20 text-white border border-electric-blue/30'
                  : 'text-gray-500 hover:text-gray-300'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* OVERVIEW TAB */}
        {activeTab === 'overview' && (
          <div className="glass-card p-6 rounded-xl border border-white/5">
            <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-4">Live Supply Chain Distribution</h3>
            {Object.keys(stats.unitsByOwner || {}).length === 0 ? (
              <p className="text-gray-600 text-center py-8 font-mono text-sm">No supply chain data available</p>
            ) : (
              <div className="grid grid-cols-5 gap-4">
                {Object.entries(stats.unitsByOwner || {}).map(([role, count], i) => (
                  <motion.div key={role} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                    className="gradient-border p-4 text-center">
                    <p className="text-gray-500 text-xs mb-2 uppercase tracking-wider">{role}</p>
                    <p className="text-3xl font-bold text-electric-blue">{(count || 0).toLocaleString()}</p>
                    <div className="mt-2 h-1 bg-dark-bg rounded-full overflow-hidden">
                      <div className="h-full bg-gradient-to-r from-electric-blue to-neon-purple transition-all duration-500"
                        style={{ width: `${stats.totalUnits > 0 ? (count / stats.totalUnits * 100) : 0}%` }} />
                    </div>
                  </motion.div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ALERTS TAB */}
        {activeTab === 'alerts' && (
          <div className="space-y-3">
            {alerts.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No active alerts</div>
            ) : alerts.map((alert, i) => (
              <div key={alert.id || i} className={`p-4 rounded-lg border ${alertColor(alert.alertType || alert.type)}`}>
                <div className="flex items-start gap-3">
                  <div className={`w-3 h-3 rounded-full mt-1 flex-shrink-0 animate-pulse ${alertDot(alert.alertType || alert.type)}`} />
                  <div className="flex-1">
                    <div className="flex justify-between items-start">
                      <p className="text-white font-semibold text-sm">{alert.message || alert.description}</p>
                      <span className="text-xs text-gray-500 ml-2 flex-shrink-0">
                        {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : ''}
                      </span>
                    </div>
                    {alert.serialNumber && <p className="text-gray-400 text-xs mt-1 font-mono">Serial: {alert.serialNumber}</p>}
                    {alert.alertType && <span className="text-xs px-2 py-0.5 rounded bg-dark-bg/50 text-gray-300 mt-1 inline-block">{alert.alertType}</span>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* COMPLAINTS TAB */}
        {activeTab === 'complaints' && (
          <div className="space-y-3">
            {complaints.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No complaints filed yet</div>
            ) : complaints.map((c, i) => {
              const severityColor = {
                CRITICAL: 'border-red-500/50 bg-red-500/5',
                HIGH: 'border-orange-500/50 bg-orange-500/5',
                MEDIUM: 'border-yellow-500/50 bg-yellow-500/5',
                LOW: 'border-electric-blue/50 bg-electric-blue/5',
              }[c.aiSeverity] || 'border-electric-blue/50 bg-electric-blue/5'
              const severityDot = {
                CRITICAL: 'bg-red-500',
                HIGH: 'bg-orange-500',
                MEDIUM: 'bg-yellow-500',
                LOW: 'bg-electric-blue',
              }[c.aiSeverity] || 'bg-electric-blue'
              const statusColor = {
                ESCALATED: 'bg-red-500/20 text-red-400 border-red-500/30',
                OPEN: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
                UNDER_REVIEW: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
                RESOLVED: 'bg-neon-green/20 text-neon-green border-neon-green/30',
              }[c.status] || 'bg-gray-500/20 text-gray-400 border-gray-500/30'
              return (
                <div key={c.id || i} className={`p-4 rounded-lg border ${severityColor}`}>
                  <div className="flex items-start gap-3">
                    <div className={`w-3 h-3 rounded-full mt-1 flex-shrink-0 animate-pulse ${severityDot}`} />
                    <div className="flex-1">
                      <div className="flex justify-between items-start flex-wrap gap-2">
                        <div>
                          <p className="text-white font-semibold text-sm">
                            {c.issueType?.replace(/_/g, ' ')} — {c.medicineName || 'Unknown Medicine'}
                          </p>
                          <p className="text-gray-400 text-xs font-mono mt-0.5">Serial: {c.serialNumber}</p>
                          {c.batchNumber && <p className="text-gray-500 text-xs font-mono">Batch: {c.batchNumber}</p>}
                        </div>
                        <div className="flex gap-2 items-center flex-shrink-0">
                          <span className={`text-xs px-2 py-0.5 rounded border font-bold ${statusColor}`}>{c.status}</span>
                          <span className={`text-xs px-2 py-0.5 rounded border font-bold ${severityDot.replace('bg-', 'border-').replace('500', '500/50')} ${severityDot.replace('bg-', 'text-')}`}>
                            {c.aiSeverity}
                          </span>
                        </div>
                      </div>
                      <p className="text-gray-300 text-sm mt-2 italic">"{c.description}"</p>
                      {c.aiAnalysis && (
                        <p className="text-gray-400 text-xs mt-1">🤖 AI: {c.aiAnalysis}</p>
                      )}
                      <div className="flex justify-between items-center mt-2">
                        <p className="text-gray-500 text-xs">
                          Reporter: {c.reporter?.shopName || c.reporter?.fullName || c.reporter?.email || 'Unknown'}
                          {c.reporter?.cityState ? ` · ${c.reporter.cityState}` : ''}
                        </p>
                        <p className="text-gray-600 text-xs">
                          {c.createdAt ? new Date(c.createdAt).toLocaleString() : ''}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* RECALLS TAB */}
        {activeTab === 'recalls' && (
          <div className="space-y-3">
            {recalls.length === 0 ? (
              <div className="text-center py-12 text-gray-500">No recall events</div>
            ) : recalls.map((recall, i) => (
              <div key={recall.id || i} className="bg-dark-bg p-4 rounded-lg border border-red-500/30">
                <div className="flex justify-between items-start">
                  <div>
                    <p className="text-white font-semibold">{recall.medicineName || recall.batchNumber}</p>
                    <p className="text-red-400 text-sm mt-1">Reason: {recall.reason || recall.recallReason}</p>
                    {recall.batchNumber && <p className="text-gray-400 text-xs font-mono mt-1">Batch: {recall.batchNumber}</p>}
                    {recall.affectedUnits && <p className="text-gray-400 text-xs mt-1">Affected Units: {recall.affectedUnits}</p>}
                  </div>
                  <div className="text-right">
                    <span className="px-2 py-1 bg-red-500/20 text-red-400 border border-red-500/30 rounded text-xs font-semibold">RECALLED</span>
                    {recall.createdAt && <p className="text-gray-500 text-xs mt-2">{new Date(recall.createdAt).toLocaleDateString()}</p>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* AUDIT LOGS TAB */}
        {activeTab === 'audit' && (
          <div>
            <p className="text-xs text-gray-500 mb-3">Privacy-protected: PII masked per GDPR</p>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {auditLogs.length === 0 ? (
                <div className="text-center py-12 text-gray-500">No audit logs</div>
              ) : auditLogs.map((log, i) => (
                <div key={log.id || i} className="bg-dark-bg p-3 rounded-lg border border-electric-blue/10 flex justify-between items-center">
                  <div>
                    <p className="text-white text-sm">{log.action || log.eventType}</p>
                    <p className="text-gray-400 text-xs font-mono">{log.entityId || log.serialNumber || '—'}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-gray-500 text-xs">{log.createdAt ? new Date(log.createdAt).toLocaleString() : ''}</p>
                    <p className="text-gray-600 text-xs">{log.performedBy || log.username || '***'}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* BATCH TRACKER TAB */}
        {activeTab === 'tracker' && (
          <div className="space-y-4">
            <div className="bg-dark-bg p-5 rounded-lg border border-electric-blue/30">
              <h3 className="text-lg font-semibold text-white mb-1">📍 Batch Location Tracker</h3>
              <p className="text-gray-400 text-sm mb-4">Track where every unit of a batch is in the supply chain right now</p>
              <div className="flex gap-3">
                <input
                  type="text"
                  value={trackerBatchNumber}
                  onChange={e => setTrackerBatchNumber(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleTrackBatch()}
                  placeholder="Enter batch number (e.g. BATCH-2024-001)"
                  className="flex-1 bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                  disabled={trackerLoading}
                />
                <button
                  onClick={handleTrackBatch}
                  disabled={trackerLoading}
                  className="px-6 py-3 bg-electric-blue text-white font-semibold rounded-lg hover:bg-electric-blue/80 transition-all disabled:opacity-50"
                >
                  {trackerLoading ? '⏳ Tracking...' : '🔍 Track'}
                </button>
              </div>
              {trackerError && <p className="text-red-400 text-sm mt-2">{trackerError}</p>}
            </div>

            {trackerResult && (
              <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
                {/* Batch Info */}
                <div className="bg-dark-bg p-4 rounded-lg border border-electric-blue/30 mb-4">
                  <div className="flex justify-between items-start flex-wrap gap-2">
                    <div>
                      <p className="text-white font-bold text-lg">{trackerResult.medicineName}</p>
                      <p className="text-gray-400 text-sm font-mono">{trackerResult.batchNumber}</p>
                    </div>
                    <div className="text-right">
                      <span className={`px-3 py-1 rounded text-xs font-bold ${
                        trackerResult.batchStatus === 'RECALLED' || trackerResult.batchStatus === 'RECALLED_AUTO'
                          ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                          : trackerResult.batchStatus === 'ACTIVE'
                          ? 'bg-neon-green/20 text-neon-green border border-neon-green/30'
                          : 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30'
                      }`}>{trackerResult.batchStatus}</span>
                      <p className="text-gray-500 text-xs mt-1">Expires: {trackerResult.expiryDate}</p>
                    </div>
                  </div>
                </div>

                {/* Summary by role */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
                  {Object.entries(trackerResult.summary || {}).map(([role, count]) => (
                    <div key={role} className="bg-dark-bg p-3 rounded-lg border border-electric-blue/20 text-center">
                      <p className="text-gray-400 text-xs mb-1">{role}</p>
                      <p className="text-2xl font-bold text-electric-blue">{count}</p>
                      <p className="text-gray-500 text-xs">units</p>
                    </div>
                  ))}
                </div>

                {/* Distributor breakdown */}
                {trackerResult.distributors?.length > 0 && (
                  <div className="bg-dark-bg rounded-lg border border-yellow-500/20 overflow-hidden mb-4">
                    <div className="p-3 border-b border-yellow-500/20 bg-yellow-500/5">
                      <p className="text-yellow-400 font-semibold text-sm">🚚 Distributors Holding This Batch</p>
                    </div>
                    <div className="divide-y divide-yellow-500/10">
                      {trackerResult.distributors.map((d, i) => (
                        <div key={i} className="p-4 flex flex-wrap gap-4 items-start">
                          <div className="flex-1 min-w-48">
                            <p className="text-white font-semibold">{d.shopName}</p>
                            <p className="text-gray-400 text-xs mt-0.5">{d.name} · {d.email}</p>
                            <p className="text-gray-500 text-xs mt-0.5">📍 {d.shopAddress}</p>
                          </div>
                          <div className="flex gap-4 text-xs">
                            <div>
                              <p className="text-gray-500">License</p>
                              <p className="text-yellow-400 font-mono">{d.licenseNumber}</p>
                            </div>
                            <div>
                              <p className="text-gray-500">Phone</p>
                              <p className="text-white">{d.phoneNumber}</p>
                            </div>
                            <div className="text-center">
                              <p className="text-gray-500">Units</p>
                              <p className="text-2xl font-bold text-yellow-400">{d.unitCount}</p>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Retailer/Pharmacist breakdown */}
                {trackerResult.retailers?.length > 0 && (
                  <div className="bg-dark-bg rounded-lg border border-neon-green/20 overflow-hidden mb-4">
                    <div className="p-3 border-b border-neon-green/20 bg-neon-green/5">
                      <p className="text-neon-green font-semibold text-sm">🏪 Retailers / Pharmacies Holding This Batch</p>
                    </div>
                    <div className="divide-y divide-neon-green/10">
                      {trackerResult.retailers.map((r, i) => (
                        <div key={i} className="p-4 flex flex-wrap gap-4 items-start">
                          <div className="flex-1 min-w-48">
                            <p className="text-white font-semibold">{r.shopName}</p>
                            <p className="text-gray-400 text-xs mt-0.5">{r.name} · {r.email}</p>
                            <p className="text-gray-500 text-xs mt-0.5">📍 {r.shopAddress}</p>
                            <span className="text-xs px-1.5 py-0.5 bg-neon-green/10 text-neon-green rounded mt-1 inline-block">{r.role}</span>
                          </div>
                          <div className="flex gap-4 text-xs">
                            <div>
                              <p className="text-gray-500">License</p>
                              <p className="text-neon-green font-mono">{r.licenseNumber}</p>
                            </div>
                            <div>
                              <p className="text-gray-500">Phone</p>
                              <p className="text-white">{r.phoneNumber}</p>
                            </div>
                            <div className="text-center">
                              <p className="text-gray-500">Units</p>
                              <p className="text-2xl font-bold text-neon-green">{r.unitCount}</p>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Unit table */}
                <div className="bg-dark-bg rounded-lg border border-electric-blue/20 overflow-hidden">
                  <div className="p-3 border-b border-electric-blue/20 flex justify-between items-center">
                    <p className="text-white font-semibold text-sm">All Units ({trackerResult.totalUnits})</p>
                  </div>
                  <div className="max-h-96 overflow-y-auto">
                    <table className="w-full text-sm">
                      <thead className="sticky top-0 bg-dark-bg border-b border-electric-blue/20">
                        <tr>
                          <th className="text-left text-gray-400 px-3 py-2 font-medium">Serial #</th>
                          <th className="text-left text-gray-400 px-3 py-2 font-medium">Type</th>
                          <th className="text-left text-gray-400 px-3 py-2 font-medium">Status</th>
                          <th className="text-left text-gray-400 px-3 py-2 font-medium">Current Location</th>
                          <th className="text-left text-gray-400 px-3 py-2 font-medium">Last Scan</th>
                        </tr>
                      </thead>
                      <tbody>
                        {trackerResult.units.map((unit, i) => (
                          <tr key={unit.serialNumber} className={`border-b border-electric-blue/10 ${i % 2 === 0 ? 'bg-electric-blue/5' : ''}`}>
                            <td className="px-3 py-2 font-mono text-xs text-white">{unit.serialNumber}</td>
                            <td className="px-3 py-2 text-gray-300 text-xs">{unit.unitType}</td>
                            <td className="px-3 py-2">
                              <span className={`text-xs px-2 py-0.5 rounded ${
                                unit.status === 'ACTIVE' ? 'bg-neon-green/20 text-neon-green'
                                : unit.status === 'RECALLED' || unit.status === 'RECALLED_AUTO' ? 'bg-red-500/20 text-red-400'
                                : 'bg-yellow-500/20 text-yellow-400'
                              }`}>{unit.status}</span>
                            </td>
                            <td className="px-3 py-2">
                              <p className="text-white text-xs font-semibold">{unit.currentOwnerRole}</p>
                              <p className="text-gray-500 text-xs">{unit.currentOwnerEmail}</p>
                            </td>
                            <td className="px-3 py-2">
                              <p className="text-gray-300 text-xs">{unit.lastScanResult}</p>
                              <p className="text-gray-500 text-xs">{unit.lastScanLocation}</p>
                              {unit.lastScannedAt && (
                                <p className="text-gray-600 text-xs">{new Date(unit.lastScannedAt).toLocaleDateString()}</p>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </motion.div>
            )}
          </div>
        )}

        {/* KILL SWITCH TAB */}
        {activeTab === 'killswitch' && (
          <div className="bg-red-500/10 p-6 rounded-lg border border-red-500/30">
            <h3 className="text-xl font-semibold text-red-500 mb-1">🚨 Hierarchy Kill Switch</h3>
            <p className="text-gray-400 text-sm mb-6">Block parent unit and all children recursively (e.g., stolen carton)</p>
            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm text-gray-300 mb-2">Parent Serial Number</label>
                <input type="text" value={killSerialNumber} onChange={e => setKillSerialNumber(e.target.value)}
                  className="w-full bg-dark-bg border border-red-500/30 rounded-lg px-4 py-3 text-white focus:border-red-500 focus:outline-none"
                  placeholder="CARTON-2024-001" disabled={killLoading} />
              </div>
              <div>
                <label className="block text-sm text-gray-300 mb-2">Reason</label>
                <select value={killReason} onChange={e => setKillReason(e.target.value)}
                  className="w-full bg-dark-bg border border-red-500/30 rounded-lg px-4 py-3 text-white focus:border-red-500 focus:outline-none" disabled={killLoading}>
                  <option value="STOLEN">STOLEN</option>
                  <option value="COUNTERFEIT">COUNTERFEIT</option>
                  <option value="RECALLED">RECALLED</option>
                  <option value="DAMAGED">DAMAGED</option>
                </select>
              </div>
            </div>
            <button onClick={handleKillHierarchy} disabled={killLoading}
              className="w-full bg-red-500 text-white font-bold py-4 rounded-lg hover:bg-red-600 transition-all disabled:opacity-50 disabled:cursor-not-allowed">
              {killLoading ? (
                <span className="flex items-center justify-center gap-2">
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Executing...
                </span>
              ) : '⚡ EXECUTE KILL SWITCH'}
            </button>
            {killResult && (
              <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
                className={`mt-4 p-4 rounded-lg ${killResult.success ? 'bg-neon-green/20 border border-neon-green' : 'bg-red-500/20 border border-red-500'}`}>
                {killResult.success ? (
                  <div>
                    <p className="text-neon-green font-semibold mb-2">✓ Kill Switch Executed</p>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="bg-dark-bg/50 p-2 rounded">
                        <p className="text-gray-400">Blocked Units</p>
                        <p className="text-white font-bold text-lg">{killResult.data.blockedCount}</p>
                      </div>
                      <div className="bg-dark-bg/50 p-2 rounded">
                        <p className="text-gray-400">Parent Serial</p>
                        <p className="text-white font-mono text-xs">{killResult.data.parentSerialNumber}</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p className="text-red-500 font-semibold">✗ {killResult.error}</p>
                )}
              </motion.div>
            )}
            <div className="mt-4 bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3">
              <p className="text-yellow-500 text-sm font-semibold mb-1">⚠️ Warning</p>
              <p className="text-gray-300 text-xs">This recursively blocks ALL child units. Blocking a carton invalidates all boxes, strips, and tablets inside it.</p>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  )
}
