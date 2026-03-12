import { motion } from 'framer-motion'

export default function DistributorPanel() {
  return (
    <div className="max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-8 glow-border"
      >
        <h2 className="text-3xl font-bold text-electric-blue mb-6">
          Distributor Dashboard
        </h2>
        
        <div className="grid grid-cols-3 gap-6 mb-8">
          <div className="bg-dark-bg p-6 rounded-lg border border-electric-blue/30">
            <p className="text-gray-400 text-sm mb-2">Total Batches</p>
            <p className="text-3xl font-bold text-electric-blue">1,234</p>
          </div>
          <div className="bg-dark-bg p-6 rounded-lg border border-neon-green/30">
            <p className="text-gray-400 text-sm mb-2">In Transit</p>
            <p className="text-3xl font-bold text-neon-green">456</p>
          </div>
          <div className="bg-dark-bg p-6 rounded-lg border border-yellow-500/30">
            <p className="text-gray-400 text-sm mb-2">Delivered</p>
            <p className="text-3xl font-bold text-yellow-500">778</p>
          </div>
        </div>

        <div className="space-y-4">
          <h3 className="text-xl font-semibold text-white mb-4">Recent Shipments</h3>
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-dark-bg p-4 rounded-lg border border-electric-blue/20 flex justify-between items-center">
              <div>
                <p className="text-white font-semibold">Batch #{1000 + i}</p>
                <p className="text-gray-400 text-sm">Paracetamol 500mg</p>
              </div>
              <span className="px-4 py-2 bg-neon-green/20 text-neon-green rounded-full text-sm">
                In Transit
              </span>
            </div>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
