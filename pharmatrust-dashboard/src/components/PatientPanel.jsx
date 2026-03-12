import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { verificationAPI } from '../services/api'

export default function PatientPanel() {
  const [serialNumber, setSerialNumber] = useState('')
  const [scanning, setScanning] = useState(false)
  const [result, setResult] = useState(null)

  const handleVerify = async (e) => {
    e.preventDefault()
    setScanning(true)
    setResult(null)

    // Simulate scanning animation
    await new Promise(resolve => setTimeout(resolve, 2000))

    try {
      const response = await verificationAPI.checkMedicine(serialNumber)
      setResult({ success: true, data: response })
    } catch (error) {
      setResult({ 
        success: false, 
        error: error.response?.data?.message || 'Invalid or counterfeit medicine detected'
      })
    } finally {
      setScanning(false)
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

        <form onSubmit={handleVerify} className="space-y-6">
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
              disabled={scanning}
            />
          </div>

          <motion.button
            type="submit"
            disabled={scanning}
            className="w-full bg-neon-green text-dark-bg font-bold py-4 rounded-lg hover:bg-neon-green/80 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            whileHover={{ scale: scanning ? 1 : 1.02 }}
            whileTap={{ scale: scanning ? 1 : 0.98 }}
          >
            {scanning ? (
              <span className="flex items-center justify-center gap-2">
                <div className="w-5 h-5 border-2 border-dark-bg border-t-transparent rounded-full animate-spin"></div>
                Scanning...
              </span>
            ) : (
              'Verify Medicine'
            )}
          </motion.button>
        </form>

        {/* Scanning Animation */}
        <AnimatePresence>
          {scanning && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="mt-8 relative h-64 bg-dark-bg rounded-lg overflow-hidden border border-neon-green/30"
            >
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="text-center">
                  <div className="w-16 h-16 border-4 border-neon-green border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
                  <p className="text-neon-green animate-pulse">Scanning medicine...</p>
                  <p className="text-gray-400 text-sm mt-2">Verifying authenticity</p>
                </div>
              </div>
              
              {/* Scan Line Animation */}
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
            </motion.div>
          )}
        </AnimatePresence>

        {/* Result Display */}
        {result && !scanning && (
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
                    <p className="text-gray-300">This medicine is verified and safe to use</p>
                  </div>
                </div>
                
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
                  {result.data.status && (
                    <div className="bg-dark-bg/50 p-4 rounded-lg col-span-2">
                      <p className="text-sm text-gray-400">Status</p>
                      <p className="text-white font-semibold">{result.data.status}</p>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 bg-red-500 rounded-full flex items-center justify-center text-2xl">
                  ✗
                </div>
                <div>
                  <h3 className="text-xl font-bold text-red-500">Warning!</h3>
                  <p className="text-gray-300">{result.error}</p>
                  <p className="text-sm text-gray-400 mt-2">Do not consume this medicine. Report to authorities.</p>
                </div>
              </div>
            )}
          </motion.div>
        )}
      </motion.div>
    </div>
  )
}
