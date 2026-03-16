import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { scanAPI, verifyAPI } from '../services/api'
import QRScanner from './QRScanner'

const SCAN_HISTORY_KEY = 'pharmatrust_scan_history'

function loadHistory() {
  try { return JSON.parse(localStorage.getItem(SCAN_HISTORY_KEY) || '[]') } catch { return [] }
}

function saveHistory(history) {
  localStorage.setItem(SCAN_HISTORY_KEY, JSON.stringify(history.slice(0, 20)))
}

export default function PatientPanel({ autoScanSerial }) {
  const [serialNumber, setSerialNumber] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [offlineMode, setOfflineMode] = useState(false)
  const [totpCode, setTotpCode] = useState('')
  const [activeTab, setActiveTab] = useState('scan')
  const [scanHistory, setScanHistory] = useState(loadHistory)

  // Auto-scan if serial number passed via URL (?sn=...)
  useEffect(() => {
    if (autoScanSerial) {
      setSerialNumber(autoScanSerial)
      // Trigger scan automatically after a short delay
      setTimeout(() => {
        doScan(autoScanSerial)
      }, 500)
    }
  }, [autoScanSerial])

  const doScan = async (sn) => {
    setLoading(true); setResult(null)
    try {
      const response = await scanAPI.scanUnit(sn)
      setResult({ success: response.isValid && !response.isRecalled && !response.isBlocked, data: response, offline: false })
      const entry = {
        serialNumber: sn,
        medicineName: response.medicineName || 'Unknown',
        status: response.isRecalled ? 'RECALLED' : response.isBlocked ? 'BLOCKED' : response.isCounterfeit ? 'COUNTERFEIT' : response.isValid ? 'AUTHENTIC' : 'INVALID',
        scannedAt: new Date().toISOString(),
        offline: false,
      }
      const updated = [entry, ...scanHistory]
      setScanHistory(updated)
      saveHistory(updated)
    } catch (error) {
      setResult({ success: false, error: error.response?.data?.message || error.message || 'Scan failed' })
    } finally { setLoading(false) }
  }

  const handleScan = async (e) => {
    e.preventDefault()
    setLoading(true); setResult(null)
    try {
      let response
      if (offlineMode) {
        response = await scanAPI.verifyOffline(serialNumber, totpCode)
        setResult({ success: response.isValid, data: response, offline: true })
      } else {
        await doScan(serialNumber)
        return
      }
      // Save to history (offline path)
      const entry = {
        serialNumber,
        medicineName: response.medicineName || 'Unknown',
        status: response.isRecalled ? 'RECALLED' : response.isBlocked ? 'BLOCKED' : response.isCounterfeit ? 'COUNTERFEIT' : response.isValid ? 'AUTHENTIC' : 'INVALID',
        scannedAt: new Date().toISOString(),
        offline: offlineMode,
      }
      const updated = [entry, ...scanHistory]
      setScanHistory(updated)
      saveHistory(updated)
    } catch (error) {
      setResult({ success: false, error: error.response?.data?.message || error.message || 'Scan failed' })
    } finally { setLoading(false) }
  }

  const clearHistory = () => { setScanHistory([]); saveHistory([]) }

  const historyStatusColor = (status) => {
    const map = {
      AUTHENTIC: 'text-green-400 bg-green-400/10 border-green-400/30',
      RECALLED: 'text-red-400 bg-red-400/10 border-red-400/30',
      BLOCKED: 'text-red-400 bg-red-400/10 border-red-400/30',
      COUNTERFEIT: 'text-orange-400 bg-orange-400/10 border-orange-400/30',
      INVALID: 'text-gray-400 bg-gray-400/10 border-gray-400/30',
    }
    return map[status] || map.INVALID
  }

  return (
    <div className="w-full">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="glass-panel p-8 glow-border">
        <h2 className="text-3xl font-bold neon-text-blue mb-1">🔍 Verify Medicine Authenticity</h2>
        <p className="text-gray-500 text-sm mb-6 font-mono">Scan QR code or enter serial number to verify your medicine</p>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 p-1 bg-dark-bg/60 rounded-xl border border-white/5">
          {[
            { id: 'scan', label: '🔍 Scan' },
            { id: 'history', label: `📋 History (${scanHistory.length})` },
          ].map(tab => (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`flex-1 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-electric-blue/20 to-neon-purple/20 text-white border border-electric-blue/30'
                  : 'text-gray-500 hover:text-gray-300'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* SCAN TAB */}
        {activeTab === 'scan' && (
          <>
            <div className="mb-6 flex items-center justify-between bg-dark-bg/50 p-4 rounded-lg border border-electric-blue/20">
              <div>
                <p className="text-white font-semibold">Offline Verification Mode</p>
                <p className="text-xs text-gray-400">Verify without internet using TOTP code</p>
              </div>
              <button onClick={() => setOfflineMode(!offlineMode)}
                className={`relative inline-flex h-8 w-14 items-center rounded-full transition-colors ${offlineMode ? 'bg-electric-blue' : 'bg-gray-600'}`}>
                <span className={`inline-block h-6 w-6 transform rounded-full bg-white transition-transform ${offlineMode ? 'translate-x-7' : 'translate-x-1'}`} />
              </button>
            </div>

            <form onSubmit={handleScan} className="space-y-6">
              <div>
                {/* QR Image Upload */}
                <div className="mb-4">
                  <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 font-semibold">Scan QR Code Image</p>
                  <QRScanner
                    onScan={(sn) => setSerialNumber(sn)}
                    color="#00D9FF"
                    label="Upload QR Code Photo"
                  />
                </div>
                <label className="block text-sm text-gray-300 mb-2">Or enter Serial Number manually *</label>
                <input type="text" value={serialNumber} onChange={e => setSerialNumber(e.target.value)}
                  className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono"
                  placeholder="PAR-20240313-ABC123-D-000001-HASH" required />
              </div>

              <AnimatePresence>
                {offlineMode && (
                  <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
                    <label className="block text-sm text-gray-300 mb-2">TOTP Code (8 digits) *</label>
                    <input type="text" value={totpCode} onChange={e => setTotpCode(e.target.value)}
                      className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none font-mono text-center text-2xl tracking-widest"
                      placeholder="12345678" maxLength="8" pattern="[0-9]{8}" required={offlineMode} />
                  </motion.div>
                )}
              </AnimatePresence>

              <button type="submit" disabled={loading}
                className="cyber-btn w-full bg-gradient-to-r from-electric-blue to-neon-purple text-white py-4 rounded-xl font-bold hover:shadow-neon-blue transition-all disabled:opacity-40 disabled:cursor-not-allowed text-sm uppercase tracking-wider">
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Verifying...
                  </span>
                ) : `🔍 ${offlineMode ? 'Verify Offline' : 'Scan Medicine'}`}
              </button>
            </form>

            <AnimatePresence>
              {result && (
                <motion.div initial={{ opacity: 0, scale: 0.9, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.9 }} transition={{ type: 'spring', stiffness: 200, damping: 20 }}
                  className="mt-6">
                  {result.data?.isRecalled || result.data?.isBlocked ? (
                    <motion.div
                      animate={{ boxShadow: ['0 0 20px rgba(239,68,68,0.5)', '0 0 60px rgba(239,68,68,0.8)', '0 0 20px rgba(239,68,68,0.5)'] }}
                      transition={{ duration: 1, repeat: Infinity }}
                      className="bg-red-600/20 border-2 border-red-500 rounded-xl p-6">
                      <div className="flex items-center mb-4">
                        <motion.span
                          animate={{ scale: [1, 1.2, 1] }}
                          transition={{ duration: 0.5, repeat: Infinity }}
                          className="text-6xl mr-4">🚨</motion.span>
                        <div>
                          <h3 className="text-2xl font-bold text-red-400">DANGER — DO NOT CONSUME!</h3>
                          <p className="text-red-300 text-sm mt-1">This batch has been recalled</p>
                        </div>
                      </div>
                      <p className="text-white font-semibold">{result.data.message}</p>
                      {result.data.recallReason && <p className="text-red-200 mt-2 text-sm">Reason: {result.data.recallReason}</p>}
                    </motion.div>
                  ) : result.data?.isCounterfeit ? (
                    <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-6">
                      <h3 className="text-2xl font-bold text-red-400 mb-3">⚠️ COUNTERFEIT DETECTED!</h3>
                      <p className="text-red-300 text-sm">{result.data.message}</p>
                      <p className="text-white mt-2 text-sm">Scan Count: {result.data.scanCount} / {result.data.maxScanLimit}</p>
                    </div>
                  ) : result.success ? (
                    <motion.div
                      animate={{ boxShadow: ['0 0 20px rgba(57,255,20,0.2)', '0 0 40px rgba(57,255,20,0.4)', '0 0 20px rgba(57,255,20,0.2)'] }}
                      transition={{ duration: 2, repeat: Infinity }}
                      className="bg-neon-green/5 border border-neon-green/30 rounded-xl p-6">
                      <h3 className="text-xl font-bold text-neon-green mb-4 flex items-center gap-2">
                        <motion.span animate={{ scale: [1, 1.1, 1] }} transition={{ duration: 1, repeat: Infinity }}>✅</motion.span>
                        {result.data.unitType === 'BOX' || result.data.unitType === 'CARTON'
                          ? 'AUTHENTIC BOX / PACKAGE'
                          : 'AUTHENTIC MEDICINE'}
                      </h3>
                      <div className="grid grid-cols-2 gap-3 text-sm">
                        {[
                          ['💊 Medicine', result.data.medicineName],
                          ['🏭 Manufacturer', result.data.manufacturerName || 'PharmaCorp'],
                          ['📦 Batch Number', result.data.batchNumber],
                          ['📅 Mfg Date', result.data.manufacturingDate],
                          ['⏰ Expiry Date', result.data.expiryDate],
                          ['🔢 Scan Count', `${result.data.scanCount} / ${result.data.maxScanLimit}`],
                        ].map(([label, value]) => (
                          <div key={label} className="glass-card p-3 rounded-lg">
                            <p className="text-gray-500 text-xs">{label}</p>
                            <p className="text-white font-semibold mt-0.5">{value || '—'}</p>
                          </div>
                        ))}
                        <div className="glass-card p-3 rounded-lg col-span-2">
                          <p className="text-gray-500 text-xs">⛓️ Blockchain TX</p>
                          <p className="text-electric-blue font-mono text-xs break-all mt-0.5">{result.data.blockchainTxId || 'Pending'}</p>
                        </div>
                        {result.data.merkleRoot && (
                          <div className="glass-card p-3 rounded-lg col-span-2">
                            <p className="text-gray-500 text-xs">🌳 Merkle Root (Tamper Proof)</p>
                            <p className="text-neon-purple font-mono text-xs break-all mt-0.5">{result.data.merkleRoot}</p>
                          </div>
                        )}
                      </div>

                      {result.data.parentSerialNumber && (
                        <div className="mt-3 p-3 rounded-lg glass-card border border-electric-blue/20">
                          <p className="text-xs text-gray-500">📦 Part of Box</p>
                          <p className="text-electric-blue font-mono text-xs break-all mt-0.5">{result.data.parentSerialNumber}</p>
                        </div>
                      )}

                      {(result.data.unitType === 'BOX' || result.data.unitType === 'CARTON') && result.data.childUnits && (
                        <div className="mt-4">
                          <p className="text-gray-400 text-xs uppercase tracking-wider mb-2">
                            📋 Contents: {result.data.childCount} units inside this box
                          </p>
                          <div className="max-h-48 overflow-y-auto space-y-1 pr-1">
                            {result.data.childUnits.map((child, i) => (
                              <div key={i} className="flex justify-between items-center glass-card px-3 py-2 rounded-lg text-xs">
                                <span className="text-gray-300 font-mono truncate max-w-[70%]">{child.serialNumber}</span>
                                <span className={`px-2 py-0.5 rounded border font-semibold ${
                                  child.status === 'ACTIVE' ? 'text-neon-green bg-neon-green/10 border-neon-green/20'
                                  : child.status === 'RECALLED' ? 'text-red-400 bg-red-400/10 border-red-400/20'
                                  : 'text-gray-400 bg-gray-400/10 border-gray-400/20'
                                }`}>{child.status}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {result.data.expiryDate && (() => {
                        const daysLeft = Math.ceil((new Date(result.data.expiryDate) - new Date()) / (1000 * 60 * 60 * 24))
                        if (daysLeft < 90) return (
                          <div className={`mt-3 p-3 rounded-lg ${daysLeft < 30 ? 'bg-red-500/10 border border-red-500/20' : 'bg-yellow-500/10 border border-yellow-500/20'}`}>
                            <p className={`text-sm font-semibold ${daysLeft < 30 ? 'text-red-400' : 'text-yellow-400'}`}>
                              ⚠️ {daysLeft < 0 ? 'EXPIRED!' : `Expires in ${daysLeft} days`}
                            </p>
                          </div>
                        )
                        return null
                      })()}

                      <div className="mt-4 glass-card p-3 rounded-lg border border-electric-blue/20">
                        <p className="text-xs text-gray-400">
                          <span className="text-electric-blue font-semibold">✓ Verification: </span>
                          {result.offline ? 'Offline TOTP' : 'Online — Blockchain Secured'}
                        </p>
                      </div>
                    </motion.div>
                  ) : (
                    <div className="bg-red-500/10 border border-red-500/20 rounded-xl p-6">
                      <h3 className="text-lg font-bold text-red-400">Verification Failed</h3>
                      <p className="text-red-300 mt-2 text-sm">{result.error || 'Unknown error'}</p>
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </>
        )}

        {/* HISTORY TAB */}
        {activeTab === 'history' && (
          <div>
            <div className="flex justify-between items-center mb-4">
              <p className="text-gray-400 text-sm">Last 20 scans (stored locally)</p>
              {scanHistory.length > 0 && (
                <button onClick={clearHistory} className="px-3 py-1 text-xs bg-red-500/20 text-red-400 border border-red-500/30 rounded hover:bg-red-500/30 transition-all">
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
                      <p className="text-gray-600 text-xs">{new Date(entry.scannedAt).toLocaleString()} {entry.offline ? '· Offline' : ''}</p>
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
