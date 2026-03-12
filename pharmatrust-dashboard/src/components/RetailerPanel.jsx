import { motion } from 'framer-motion'

export default function RetailerPanel() {
  return (
    <div className="max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-8 glow-border"
      >
        <h2 className="text-3xl font-bold text-electric-blue mb-6">
          Retailer Inventory
        </h2>
        
        <div className="grid grid-cols-2 gap-6 mb-8">
          <div className="bg-dark-bg p-6 rounded-lg border border-electric-blue/30">
            <p className="text-gray-400 text-sm mb-2">Stock Available</p>
            <p className="text-3xl font-bold text-electric-blue">5,678</p>
          </div>
          <div className="bg-dark-bg p-6 rounded-lg border border-red-500/30">
            <p className="text-gray-400 text-sm mb-2">Low Stock Alert</p>
            <p className="text-3xl font-bold text-red-500">12</p>
          </div>
        </div>

        <div className="space-y-4">
          <h3 className="text-xl font-semibold text-white mb-4">Inventory Status</h3>
          {['Paracetamol 500mg', 'Ibuprofen 400mg', 'Amoxicillin 250mg'].map((medicine, i) => (
            <div key={i} className="bg-dark-bg p-4 rounded-lg border border-electric-blue/20">
              <div className="flex justify-between items-center mb-2">
                <p className="text-white font-semibold">{medicine}</p>
                <span className="text-neon-green">{Math.floor(Math.random() * 1000)} units</span>
              </div>
              <div className="w-full bg-gray-700 rounded-full h-2">
                <div 
                  className="bg-neon-green h-2 rounded-full" 
                  style={{ width: `${Math.floor(Math.random() * 100)}%` }}
                ></div>
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
