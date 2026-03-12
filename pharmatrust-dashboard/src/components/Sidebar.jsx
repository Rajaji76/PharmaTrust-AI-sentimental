import { useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import gsap from 'gsap'

const tabs = [
  { id: 'manufacturer', label: 'Manufacturer', icon: '🏭' },
  { id: 'distributor', label: 'Distributor', icon: '🚚' },
  { id: 'retailer', label: 'Retailer', icon: '🏪' },
  { id: 'patient', label: 'Patient', icon: '👤' },
  { id: 'regulator', label: 'Regulator', icon: '⚖️' },
]

export default function Sidebar({ activeTab, setActiveTab }) {
  const buttonRefs = useRef([])

  useEffect(() => {
    buttonRefs.current.forEach((btn, index) => {
      if (btn) {
        btn.addEventListener('mouseenter', () => {
          gsap.to(btn, {
            x: 10,
            duration: 0.3,
            ease: 'power2.out'
          })
        })
        
        btn.addEventListener('mouseleave', () => {
          gsap.to(btn, {
            x: 0,
            duration: 0.3,
            ease: 'power2.out'
          })
        })
      }
    })
  }, [])

  return (
    <aside className="w-64 bg-dark-panel border-r border-electric-blue/20 p-6">
      <div className="mb-12">
        <h1 className="text-2xl font-bold text-electric-blue mb-2">
          PharmaTrust
        </h1>
        <p className="text-sm text-gray-400">Secure Supply Chain</p>
      </div>

      <nav className="space-y-2">
        {tabs.map((tab, index) => (
          <motion.button
            key={tab.id}
            ref={el => buttonRefs.current[index] = el}
            onClick={() => setActiveTab(tab.id)}
            className={`w-full text-left px-4 py-3 rounded-lg transition-all ${
              activeTab === tab.id
                ? 'bg-electric-blue/20 text-electric-blue border border-electric-blue/50'
                : 'text-gray-400 hover:bg-dark-bg hover:text-white'
            }`}
            whileTap={{ scale: 0.95 }}
          >
            <span className="mr-3 text-xl">{tab.icon}</span>
            {tab.label}
          </motion.button>
        ))}
      </nav>

      <div className="mt-auto pt-12">
        <div className="glass-panel p-4">
          <p className="text-xs text-gray-400 mb-2">System Status</p>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 bg-neon-green rounded-full animate-pulse"></div>
            <span className="text-sm text-neon-green">Online</span>
          </div>
        </div>
      </div>
    </aside>
  )
}
