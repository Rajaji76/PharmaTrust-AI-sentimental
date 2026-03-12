import { useState } from 'react'
import { motion } from 'framer-motion'
import { manufacturerAPI } from '../services/api'

export default function ManufacturerPanel({ onBatchCreated }) {
  const [formData, setFormData] = useState({
    batchNumber: '',
    medicineName: '',
    quantity: '',
    mfgDate: '',
    expDate: ''
  })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    
    try {
      const response = await manufacturerAPI.createBatch({
        batchNumber: formData.batchNumber,
        medicineName: formData.medicineName,
        quantity: parseInt(formData.quantity),
        manufacturingDate: formData.mfgDate,
        expiryDate: formData.expDate
      })
      
      setResult({ 
        success: true, 
        data: response,
        message: `Batch ${formData.batchNumber} created successfully with ${formData.quantity} units!`
      })
      
      // Trigger supply chain animation
      if (onBatchCreated) {
        onBatchCreated()
      }
      
      // Reset form
      setFormData({
        batchNumber: '',
        medicineName: '',
        quantity: '',
        mfgDate: '',
        expDate: ''
      })
    } catch (error) {
      setResult({ 
        success: false, 
        error: error.response?.data?.message || error.message || 'Failed to create batch'
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-8 glow-border"
      >
        <h2 className="text-3xl font-bold text-electric-blue mb-6">
          Create New Batch
        </h2>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm text-gray-300 mb-2">
                Batch Number
              </label>
              <input
                type="text"
                value={formData.batchNumber}
                onChange={(e) => setFormData({...formData, batchNumber: e.target.value})}
                className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                placeholder="BATCH-2024-001"
                required
              />
            </div>

            <div>
              <label className="block text-sm text-gray-300 mb-2">
                Medicine Name
              </label>
              <input
                type="text"
                value={formData.medicineName}
                onChange={(e) => setFormData({...formData, medicineName: e.target.value})}
                className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                placeholder="Paracetamol 500mg"
                required
              />
            </div>

            <div>
              <label className="block text-sm text-gray-300 mb-2">
                Quantity
              </label>
              <input
                type="number"
                value={formData.quantity}
                onChange={(e) => setFormData({...formData, quantity: e.target.value})}
                className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                placeholder="10000"
                min="1"
                required
              />
            </div>

            <div>
              <label className="block text-sm text-gray-300 mb-2">
                Manufacturing Date
              </label>
              <input
                type="date"
                value={formData.mfgDate}
                onChange={(e) => setFormData({...formData, mfgDate: e.target.value})}
                className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                required
              />
            </div>

            <div className="col-span-2">
              <label className="block text-sm text-gray-300 mb-2">
                Expiry Date
              </label>
              <input
                type="date"
                value={formData.expDate}
                onChange={(e) => setFormData({...formData, expDate: e.target.value})}
                className="w-full bg-dark-bg border border-electric-blue/30 rounded-lg px-4 py-3 text-white focus:border-electric-blue focus:outline-none"
                required
              />
            </div>
          </div>

          <motion.button
            type="submit"
            disabled={loading}
            className="w-full bg-electric-blue text-dark-bg font-bold py-4 rounded-lg hover:bg-electric-blue/80 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            whileHover={{ scale: loading ? 1 : 1.02 }}
            whileTap={{ scale: loading ? 1 : 0.98 }}
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <div className="w-5 h-5 border-2 border-dark-bg border-t-transparent rounded-full animate-spin"></div>
                Creating Batch...
              </span>
            ) : (
              'Create Batch'
            )}
          </motion.button>
        </form>

        {result && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className={`mt-6 p-4 rounded-lg ${
              result.success ? 'bg-neon-green/20 border border-neon-green' : 'bg-red-500/20 border border-red-500'
            }`}
          >
            <p className={`font-semibold ${result.success ? 'text-neon-green' : 'text-red-500'}`}>
              {result.success ? '✓ ' + result.message : '✗ Error: ' + result.error}
            </p>
          </motion.div>
        )}
      </motion.div>
    </div>
  )
}
