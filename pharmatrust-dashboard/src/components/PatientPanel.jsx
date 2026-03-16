import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { scanAPI, securityAPI } from '../services/api'

export default function PatientPanel() {
  const [serialNumber, setSerialNumber] = useState('')
  const [scanning, setScanning] = useState(false)
  const [result, setResult] = useState(null)
  
  // Offline TOTP Verification
  const [offlineMode, setOfflineMode] = useState(false)
  const [totpCode, setTotpCode] = useState('')
  const [totpVerifying, setTotpVerifying] = useState(false)

  const handleVerify = async (e) => {
    e.preventDefault()
    setScanning(true)
    setResult(null)

    // Simulate scanning animation
    await new Promise(resolve => setTimeout(resolve, 1500))

    try {
      const response = await scanAPI.scanUnit(serialNumber)
      
      // Check if counterfeit or scan limit exceeded
      const isCounterfeit = response.isCounterfeit || response.scanCount >= 5
      
      setResult({ 
        success: !isCounterfeit, 
        data: response,
        isCounterfeit: isCounterfeit
      })
    } catch (error) {
      setResult({ 
        success: false, 
        error: error.response?.data?.message || 'Invalid or counterfeit medicine detected',
        isCounterfeit: true
      })
    } finally {
      setScanning(false)
    }
  }
  
  const handleOfflineVerify = async (e) => {
    e.preventDefault()
    setTotpVerifying(true)
    setResult(null)
    
    try {
      // In real app, shared secret would be cached locally
      const sharedSecret = 'demo-secret-key'
      const response = await securityAPI.verifyTOTP(serialNumber, totpCode, sharedSecret)
      
      setResult({
        success: response.valid,
        data: {
          serialNumber: serialNumber,
          verificationMethod: 'OFFLINE_TOTP',
          message: response.message
        },
        isOffline: true
      })
    } catch (error) {
      setResult({
        success: false,
        error: 'TOTP verification failed. Invalid or expired code.',
        isCounterfeit: true
      })
    } finally {
      setTotpVerifying(false)
    }
  }

  return (
    <div className="max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-8 glow-border"
      >
        <h2 className="text-3xl font-bold text-neon-green mb-6">
          Verify Medicine
        </h2>
        
        {/* Offline Mode Toggle */}
        <div className="mb-6 flex items-center justify-between bg-dark-bg p-4 rounded-lg border border-neon-green/30">
          <div>
            <p className="text-white font-semibold">Offline Verification Mode</p>
            <p className="text-gray-400 text-sm">Use TOTP code when no internet connection</p>
          </div>
          <button
            onClick={() => setOfflineMode(!offlineMode)}
            className={`relative w-16 h-8 rounded-full transition-colors ${
              offlineMode ? 'bg-neon-green' : 'bg-gray-600'
            }`}
          >
            <div className={`absolute top-1 left-1 w-6 h-6 bg-white rounded-full transition-transform ${
              offlineMode ? 'transform translate-x-8' : ''
            }`}></div>
          </button>
        </div>

        <form onSubmit={offlineMode ? handleOfflineVerify : handleVerify} className="space-y-6">
          <div>
            <label className="block text-sm text-gray-300 mb-2">
              Enter Serial Number or Scan QR Code
            </label>
            <input
              type="text"
              value={serialNumber}
              onChange={(e) => setSerialNumber(e.target.value)}
              className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-4 text-white text-lg focus:border-neon-green focus:outline-none"
              placeholder="Enter serial number (e.g., BATCH-2024-001-00001)"
              required
              disabled={scanning || totpVerifying}
            />
          </div>
          
          {/* TOTP Code Input (Offline Mode Only) */}
          {offlineMode && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
            >
              <label className="block text-sm text-gray-300 mb-2">
                TOTP Code (8-digit from QR code)
              </label>
              <input
                type="text"
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 8))}
                className="w-full bg-dark-bg border border-neon-green/30 rounded-lg px-4 py-4 text-white text-lg font-mono focus:border-neon-green focus:outline-none"
                placeholder="12345678"
                maxLength="8"
                required={offlineMode}
                disabled={totpVerifying}
              />
              <p className="text-gray-400 text-xs mt-2">
                📱 Enter the 8-digit TOTP code from the QR code for offline verification
              </p>
            </motion.div>
          )}

          <motion.button
            type="submit"
            disabled={scanning || totpVerifying}
            className="w-full bg-neon-green text-dark-bg font-bold py-4 rounded-lg hover:bg-neon-green/80 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            whileHover={{ scale: (scanning || totpVerifying) ? 1 : 1.02 }}
            whileTap={{ scale: (scanning || totpVerifying) ? 1 : 0.98 }}
          >
            {scanning || totpVerifying ? (
              <span className="flex items-center justify-center gap-2">
                <div className="w-5 h-5 border-2 border-dark-bg border-t-transparent rounded-full animate-spin"></div>
                {offlineMode ? 'Verifying TOTP...' : 'Scanning...'}
              </span>
            ) : (
              offlineMode ? '🔐 Verify Offline (TOTP)' : '✓ Verify Medicine'
            )}
          </motion.button>
        </form>

        {/* Scanning Animation */}
        <AnimatePresence>
          {(scanning || totpVerifying) && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="mt-8 relative h-64 bg-dark-bg rounded-lg overflow-hidden border border-neon-green/30"
            >
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="text-center">
                  <div className="w-16 h-16 border-4 border-neon-green border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
                  <p className="text-neon-green animate-pulse">
                    {offlineMode ? 'Verifying TOTP code...' : 'Scanning medicine...'}
                  </p>
                  <p className="text-gray-400 text-sm mt-2">
                    {offlineMode ? 'Offline verification in progress' : 'Verifying authenticity'}
                  </p>
                </div>
              </div>
              
              {/* Scan Line Animation */}
              {!offlineMode && (
                <motion.div
                  className="absolute w-full h-1 bg-neon-green shadow-lg shadow-neon-green"
                  animate={{
                    y: [0, 256, 0]
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                    ease: "linear"
                  }}
                />
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {/* Result Display */}
        {result && !scanning && !totpVerifying && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className={`mt-8 p-6 rounded-lg ${
              result.success 
                ? 'bg-neon-green/20 border-2 border-neon-green' 
                : 'bg-red-500/20 border-2 border-red-500'
            }`}
          >
            {result.success ? (
              <div>
                <div className="flex items-center gap-3 mb-4">
                  <div className="w-12 h-12 bg-neon-green rounded-full flex items-center justify-center text-2xl">
                    ✓
                  </div>
                  <div>
                    <h3 className="text-xl font-bold text-neon-green">Authentic Medicine</h3>
                    <p className="text-gray-300">
                      {result.isOffline 
                        ? 'Verified offline using TOTP' 
                        : 'This medicine is verified and safe to use'}
                    </p>
                  </div>
                </div>
                
                {result.isOffline && (
                  <div className="bg-electric-blue/20 border border-electric-blue rounded-lg p-3 mb-4">
                    <p className="text-electric-blue text-sm">
                      🔐 Offline Verification: This unit was verified using Time-based OTP without internet connection
                    </p>
                  </div>
                )}
                
                <div className="grid grid-cols-2 gap-4 mt-6">
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Medicine Name</p>
                    <p className="text-white font-semibold">{result.data.medicineName || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Batch Number</p>
                    <p className="text-white font-semibold">{result.data.batchNumber || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Mfg Date</p>
                    <p className="text-white font-semibold">{result.data.manufacturingDate || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Exp Date</p>
                    <p className="text-white font-semibold">{result.data.expiryDate || 'N/A'}</p>
                  </div>
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Scan Count</p>
                    <p className={`font-semibold ${result.data.scanCount >= 4 ? 'text-yellow-500' : 'text-white'}`}>
                      {result.data.scanCount || 0} / 5
                    </p>
                  </div>
                  <div className="bg-dark-bg/50 p-4 rounded-lg">
                    <p className="text-sm text-gray-400">Status</p>
                    <p className="text-white font-semibold">{result.data.status || 'ACTIVE'}</p>
                  </div>
                </div>
                
                {result.data.scanCount >= 4 && (
                  <div className="mt-4 bg-yellow-500/20 border border-yellow-500 rounded-lg p-3">
                    <p className="text-yellow-500 text-sm">⚠️ Warning: This unit has been scanned {result.data.scanCount} times. Maximum limit is 5.</p>
                  </div>
                )}
              </div>
            ) : (
              <div>
                <div className="flex items-center gap-3 mb-4">
                  <div className="w-16 h-16 bg-red-500 rounded-full flex items-center justify-center text-3xl animate-pulse">
                    ⚠
                  </div>
                  <div>
                    <h3 className="text-2xl font-bold text-red-500 animate-pulse">🚨 RED ALERT 🚨</h3>
                    <p className="text-red-400 font-semibold">STOLEN OR COUNTERFEIT DETECTED!</p>
                  </div>
                </div>
                
                <div className="bg-red-500/30 border-2 border-red-500 rounded-lg p-4 mb-4">
                  <p className="text-white font-bold text-lg">⛔ DO NOT CONSUME THIS MEDICINE</p>
                  <p className="text-gray-200 mt-2">{result.error || 'This unit has exceeded the maximum scan limit or is flagged as counterfeit.'}</p>
                </div>
                
                {result.data && (
                  <div className="grid grid-cols-2 gap-4 mt-4">
                    {result.data.scanCount && (
                      <div className="bg-dark-bg/50 p-4 rounded-lg border border-red-500/50">
                        <p className="text-sm text-gray-400">Scan Count</p>
                        <p className="text-red-500 font-bold text-xl">{result.data.scanCount} / 5 ⚠️</p>
                      </div>
                    )}
                    {result.data.status && (
                      <div className="bg-dark-bg/50 p-4 rounded-lg border border-red-500/50">
                        <p className="text-sm text-gray-400">Status</p>
                        <p className="text-red-500 font-bold">{result.data.status}</p>
                      </div>
                    )}
                  </div>
                )}
                
                <div className="mt-4 bg-dark-bg/50 p-4 rounded-lg">
                  <p className="text-white font-semibold mb-2">⚡ Immediate Actions Required:</p>
                  <ul className="text-gray-300 text-sm space-y-1 list-disc list-inside">
                    <li>Report to local authorities immediately</li>
                    <li>Do not purchase or consume this medicine</li>
                    <li>Take a photo of the packaging and serial number</li>
                    <li>Contact the manufacturer's helpline</li>
                  </ul>
                </div>
              </div>
            )}
          </motion.div>
        )}
      </motion.div>
    </div>
  )
}
