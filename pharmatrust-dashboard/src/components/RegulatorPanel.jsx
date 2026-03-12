import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { regulatorAPI } from '../services/api'

export default function RegulatorPanel() {
  const [units, setUnits] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [stats, setStats] = useState({
    total: 0,
    verified: 0,
    pending: 0,
    flagged: 0
  })

  useEffect(() => {
    fetchAllUnits()
  }, [])

  const fetchAllUnits = async () => {
    setLoading(true)
    setError(null)
    
    try {
      const data = await regulatorAPI.getAllUnits()
      setUnits(data)
      
      // Calculate stats
      setStats({
        total: data.length,
        verified: data.filter(u => u.status === 'VERIFIED').length,
        pending: data.filter(u => u.status === 'PENDING').length,
        flagged: data.filter(u => u.status === 'FLAGGED').length
      })
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to fetch units')
      // Use mock data if API fails
      const mockData = [
        { id: 1, serialNumber: 'BATCH-2024-001-00001', medicineName: 'Paracetamol 500mg', batchNumber: 'BATCH-2024-001', status: 'VERIFIED' },
        { id: 2, serialNumber: 'BATCH-2024-001-00002', medicineName: 'Paracetamol 500mg', batchNumber: 'BATCH-2024-001', status: 'VERIFIED' },
        { id: 3, serialNumber: 'BATCH-2024-002-00001', medicineName: 'Ibuprofen 400mg', batchNumber: 'BATCH-2024-002', status: 'PENDING' },
      ]
      setUnits(mockData)
      setStats({ total: 3, verified: 2, pending: 1, flagged: 0 })
    } finally {
      setLoading(false)
    }
  }

  const getStatusColor = (status) => {
    switch(status) {
      case 'VERIFIED': return 'text-neon-green bg-neon-green/20'
      case 'PENDING': return 'text-yellow-500 bg-yellow-500/20'
      case 'FLAGGED': return 'text-red-500 bg-red-500/20'
      default: return 'text-gray-400 bg-gray-400/20'
    }
  }

  return (
    <div className="max-w-6xl">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-8 glow-border"
      >
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-3xl font-bold text-electric-blue">
            Regulator Dashboard
          </h2>
          <motion.button
            onClick={fetchAllUnits}
            className="px-4 py-2 bg-electric-blue/20 text-electric-blue border border-electric-blue/50 rounded-lg hover:bg-electric-blue/30 transition-all"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            🔄 Refresh
          </motion.button>
        </div>
        
        {/* Stats Cards */}
        <div className="grid grid-cols-4 gap-4 mb-8">
          <div className="bg-dark-bg p-4 rounded-lg border border-electric-blue/30">
            <p className="text-gray-400 text-xs mb-1">Total Units</p>
            <p className="text-2xl font-bold text-electric-blue">{stats.total.toLocaleString()}</p>
          </div>
          <div className="bg-dark-bg p-4 rounded-lg border border-neon-green/30">
            <p className="text-gray-400 text-xs mb-1">Verified</p>
            <p className="text-2xl font-bold text-neon-green">{stats.verified.toLocaleString()}</p>
          </div>
          <div className="bg-dark-bg p-4 rounded-lg border border-yellow-500/30">
            <p className="text-gray-400 text-xs mb-1">Pending</p>
            <p className="text-2xl font-bold text-yellow-500">{stats.pending.toLocaleString()}</p>
          </div>
          <div className="bg-dark-bg p-4 rounded-lg border border-red-500/30">
            <p className="text-gray-400 text-xs mb-1">Flagged</p>
            <p className="text-2xl font-bold text-red-500">{stats.flagged.toLocaleString()}</p>
          </div>
        </div>

        {/* Units Table */}
        <div className="space-y-4">
          <h3 className="text-xl font-semibold text-white mb-4">All Units in System</h3>
          
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="w-12 h-12 border-4 border-electric-blue border-t-transparent rounded-full animate-spin"></div>
            </div>
          ) : error ? (
            <div className="bg-red-500/20 border border-red-500 rounded-lg p-4 text-red-500">
              {error} (Showing mock data)
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-electric-blue/30">
                    <th className="text-left py-3 px-4 text-gray-400 font-semibold">Serial Number</th>
                    <th className="text-left py-3 px-4 text-gray-400 font-semibold">Medicine Name</th>
                    <th className="text-left py-3 px-4 text-gray-400 font-semibold">Batch Number</th>
                    <th className="text-left py-3 px-4 text-gray-400 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {units.map((unit, index) => (
                    <motion.tr
                      key={unit.id || index}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.05 }}
                      className="border-b border-electric-blue/10 hover:bg-electric-blue/5 transition-colors"
                    >
                      <td className="py-3 px-4 text-white font-mono text-sm">{unit.serialNumber}</td>
                      <td className="py-3 px-4 text-white">{unit.medicineName}</td>
                      <td className="py-3 px-4 text-gray-300">{unit.batchNumber}</td>
                      <td className="py-3 px-4">
                        <span className={`px-3 py-1 rounded-full text-xs font-semibold ${getStatusColor(unit.status)}`}>
                          {unit.status}
                        </span>
                      </td>
                    </motion.tr>
                  ))}
                </tbody>
              </table>
              
              {units.length === 0 && (
                <div className="text-center py-12 text-gray-400">
                  No units found in the system
                </div>
              )}
            </div>
          )}
        </div>

        {/* Recent Alerts Section */}
        <div className="mt-8 space-y-4">
          <h3 className="text-xl font-semibold text-white mb-4">Recent Alerts</h3>
          {[
            { type: 'warning', message: 'Suspicious scan pattern detected - Batch #1234' },
            { type: 'info', message: 'New batch registered - Batch #5678' },
            { type: 'danger', message: 'Counterfeit attempt blocked - Serial #9999' }
          ].map((alert, i) => (
            <div key={i} className={`bg-dark-bg p-4 rounded-lg border ${
              alert.type === 'danger' ? 'border-red-500/50' : 
              alert.type === 'warning' ? 'border-yellow-500/50' : 
              'border-electric-blue/50'
            }`}>
              <div className="flex items-center gap-3">
                <div className={`w-3 h-3 rounded-full ${
                  alert.type === 'danger' ? 'bg-red-500' : 
                  alert.type === 'warning' ? 'bg-yellow-500' : 
                  'bg-electric-blue'
                } animate-pulse`}></div>
                <p className="text-white">{alert.message}</p>
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
