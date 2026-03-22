import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

const tabs = [
  {
    id: 'manufacturer',
    label: 'Manufacturer',
    sublabel: 'Batch Production',
    icon: '🏭',
    color: '#00D9FF',
    gradient: 'from-cyan-500/20 to-blue-500/10',
  },
  {
    id: 'distributor',
    label: 'Distributor',
    sublabel: 'Wholesale Supply',
    icon: '🚚',
    color: '#BF5FFF',
    gradient: 'from-purple-500/20 to-violet-500/10',
  },
  {
    id: 'retailer',
    label: 'Retailer',
    sublabel: 'Medical Shop',
    icon: '🏪',
    color: '#39FF14',
    gradient: 'from-green-500/20 to-emerald-500/10',
  },
  {
    id: 'patient',
    label: 'Patient',
    sublabel: 'Scan & Report',
    icon: '👤',
    color: '#FF6B35',
    gradient: 'from-orange-500/20 to-red-500/10',
  },
  {
    id: 'regulator',
    label: 'Regulator',
    sublabel: 'Oversight & Control',
    icon: '⚖️',
    color: '#FF2D78',
    gradient: 'from-pink-500/20 to-rose-500/10',
  },
]

function LiveClock() {
  const [time, setTime] = useState(new Date())
  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 1000)
    return () => clearInterval(t)
  }, [])
  return (
    <div className="text-center">
      <p className="text-xs text-gray-500 mb-0.5">SYSTEM TIME</p>
      <p className="font-mono text-sm text-electric-blue tracking-widest">
        {time.toLocaleTimeString('en-IN', { hour12: false })}
      </p>
      <p className="text-xs text-gray-600 mt-0.5">
        {time.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}
      </p>
    </div>
  )
}

export default function Sidebar({ activeTab, setActiveTab }) {
  const [hoveredTab, setHoveredTab] = useState(null)

  return (
    <aside className="w-72 flex flex-col relative overflow-hidden"
      style={{ background: 'linear-gradient(180deg, #0D1117 0%, #050816 100%)', borderRight: '1px solid rgba(0,217,255,0.08)' }}>

      {/* Ambient glow top */}
      <div className="absolute top-0 left-0 right-0 h-32 pointer-events-none"
        style={{ background: 'radial-gradient(ellipse at 50% 0%, rgba(0,217,255,0.08) 0%, transparent 70%)' }} />

      {/* Logo area */}
      <div className="px-6 pt-8 pb-6 relative">
        <div className="flex items-center gap-3 mb-1">
          <div className="relative">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center text-xl"
              style={{ background: 'linear-gradient(135deg, rgba(0,217,255,0.2), rgba(191,95,255,0.2))', border: '1px solid rgba(0,217,255,0.3)' }}>
              💊
            </div>
            <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-neon-green animate-pulse"
              style={{ boxShadow: '0 0 8px #39FF14' }} />
          </div>
          <div>
            <h1 className="text-xl font-black tracking-tight" style={{ background: 'linear-gradient(135deg, #00D9FF, #BF5FFF)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              PharmaTrust
            </h1>
            <p className="text-xs text-gray-500 tracking-widest uppercase">Supply Chain v2.0</p>
          </div>
        </div>

        {/* Divider */}
        <div className="mt-4 h-px" style={{ background: 'linear-gradient(90deg, transparent, rgba(0,217,255,0.3), transparent)' }} />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-4 space-y-1">
        <p className="text-xs text-gray-600 uppercase tracking-widest px-2 mb-3 font-semibold">Dashboards</p>
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id
          return (
            <motion.button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              onHoverStart={() => setHoveredTab(tab.id)}
              onHoverEnd={() => setHoveredTab(null)}
              whileTap={{ scale: 0.97 }}
              className="w-full text-left rounded-xl transition-all duration-300 relative overflow-hidden group"
              style={isActive ? {
                background: `linear-gradient(135deg, ${tab.color}18, ${tab.color}08)`,
                border: `1px solid ${tab.color}40`,
                boxShadow: `0 0 20px ${tab.color}15`,
              } : {
                background: 'transparent',
                border: '1px solid transparent',
              }}
            >
              {/* Active left bar */}
              {isActive && (
                <motion.div
                  layoutId="activeBar"
                  className="absolute left-0 top-2 bottom-2 w-0.5 rounded-full"
                  style={{ background: `linear-gradient(180deg, ${tab.color}, ${tab.color}44)`, boxShadow: `0 0 8px ${tab.color}` }}
                />
              )}

              {/* Hover shimmer */}
              {!isActive && (
                <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300 rounded-xl"
                  style={{ background: `linear-gradient(135deg, ${tab.color}08, transparent)` }} />
              )}

              <div className="flex items-center gap-3 px-3 py-3">
                {/* Icon */}
                <div className="w-9 h-9 rounded-lg flex items-center justify-center text-lg flex-shrink-0 transition-all duration-300"
                  style={isActive ? {
                    background: `linear-gradient(135deg, ${tab.color}30, ${tab.color}10)`,
                    boxShadow: `0 0 12px ${tab.color}30`,
                  } : {
                    background: 'rgba(255,255,255,0.04)',
                  }}>
                  {tab.icon}
                </div>

                {/* Text */}
                <div className="flex-1 min-w-0">
                  <p className={`text-sm font-semibold transition-colors duration-200 ${isActive ? 'text-white' : 'text-gray-400 group-hover:text-gray-200'}`}>
                    {tab.label}
                  </p>
                  <p className="text-xs truncate transition-colors duration-200"
                    style={{ color: isActive ? tab.color : 'rgba(156,163,175,0.6)' }}>
                    {tab.sublabel}
                  </p>
                </div>

                {/* Active dot */}
                {isActive && (
                  <div className="w-1.5 h-1.5 rounded-full flex-shrink-0 animate-pulse"
                    style={{ background: tab.color, boxShadow: `0 0 6px ${tab.color}` }} />
                )}
              </div>
            </motion.button>
          )
        })}
      </nav>

      {/* Bottom section */}
      <div className="px-4 pb-6 space-y-3">
        {/* Divider */}
        <div className="h-px" style={{ background: 'linear-gradient(90deg, transparent, rgba(0,217,255,0.15), transparent)' }} />

        {/* Live Clock */}
        <div className="glass-card p-3">
          <LiveClock />
        </div>

        {/* System Status */}
        <div className="glass-card p-3">
          <p className="text-xs text-gray-500 uppercase tracking-widest mb-2 font-semibold">System Status</p>
          <div className="space-y-1.5">
            {[
              { label: 'Backend API', status: 'Online', color: '#39FF14' },
              { label: 'Blockchain', status: 'Active', color: '#00D9FF' },
              { label: 'AI Engine', status: 'Ready', color: '#BF5FFF' },
            ].map(s => (
              <div key={s.label} className="flex items-center justify-between">
                <span className="text-xs text-gray-500">{s.label}</span>
                <div className="flex items-center gap-1.5">
                  <div className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: s.color, boxShadow: `0 0 4px ${s.color}` }} />
                  <span className="text-xs font-medium" style={{ color: s.color }}>{s.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Version */}
        <p className="text-center text-xs text-gray-700 font-mono">v2.0.0 · Secure · Verified</p>
      </div>
    </aside>
  )
}
