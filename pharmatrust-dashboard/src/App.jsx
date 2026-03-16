import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import LandingScreen from './components/LandingScreen'
import AuthModal from './components/AuthModal'
import DashboardVisual from './components/DashboardVisual'
import ManufacturerPanel from './components/ManufacturerPanelComplete'
import DistributorPanel from './components/DistributorPanel'
import RetailerPanel from './components/RetailerPanel'
import PatientPanel from './components/PatientPanelUpdated'
import RegulatorPanel from './components/RegulatorPanel'

// Map role id (from landing) → backend role string
const ROLE_MAP = {
  manufacturer: 'MANUFACTURER',
  distributor: 'DISTRIBUTOR',
  retailer: 'RETAILER',
  patient: 'PATIENT',
  regulator: 'REGULATOR',
}

// Role display info
const ROLE_INFO = {
  manufacturer: { label: 'Manufacturer Dashboard', icon: '🏭', color: '#00D9FF' },
  distributor:  { label: 'Distributor Dashboard',  icon: '🚚', color: '#BF5FFF' },
  retailer:     { label: 'Retailer Dashboard',     icon: '🏪', color: '#39FF14' },
  patient:      { label: 'Patient Verification',   icon: '👤', color: '#FF6B35' },
  regulator:    { label: 'Regulator Control',      icon: '⚖️', color: '#FF2D78' },
}

// Particle background
function ParticleField() {
  const canvasRef = useRef(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
    const particles = Array.from({ length: 50 }, () => ({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25,
      r: Math.random() * 1.2 + 0.4,
      alpha: Math.random() * 0.3 + 0.08,
      color: Math.random() > 0.6 ? '#00D9FF' : Math.random() > 0.5 ? '#BF5FFF' : '#39FF14',
    }))
    let animId
    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      particles.forEach(p => {
        p.x += p.vx; p.y += p.vy
        if (p.x < 0) p.x = canvas.width
        if (p.x > canvas.width) p.x = 0
        if (p.y < 0) p.y = canvas.height
        if (p.y > canvas.height) p.y = 0
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fillStyle = p.color + Math.floor(p.alpha * 255).toString(16).padStart(2, '0')
        ctx.fill()
      })
      particles.forEach((p, i) => {
        particles.slice(i + 1).forEach(q => {
          const d = Math.hypot(p.x - q.x, p.y - q.y)
          if (d < 90) {
            ctx.beginPath()
            ctx.moveTo(p.x, p.y)
            ctx.lineTo(q.x, q.y)
            ctx.strokeStyle = `rgba(0,217,255,${0.05 * (1 - d / 90)})`
            ctx.lineWidth = 0.4
            ctx.stroke()
          }
        })
      })
      animId = requestAnimationFrame(draw)
    }
    draw()
    const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight }
    window.addEventListener('resize', resize)
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', resize) }
  }, [])
  return <canvas ref={canvasRef} className="fixed inset-0 pointer-events-none z-0" />
}

// Top bar — shows role name + logout
function TopBar({ role, onLogout, onBack }) {
  const info = ROLE_INFO[role] || ROLE_INFO.manufacturer
  return (
    <div className="topbar-gradient px-6 py-2.5 flex items-center justify-between flex-shrink-0 relative z-20">
      {/* Left: back button + breadcrumb */}
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs transition-all hover:scale-105"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', color: '#9CA3AF' }}
        >
          ← Home
        </button>
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <span>PharmaTrust</span>
          <span className="text-gray-700">/</span>
          <span className="font-semibold" style={{ color: info.color }}>
            {info.icon} {info.label}
          </span>
        </div>
      </div>

      {/* Center: live indicators */}
      <div className="flex items-center gap-4">
        {[
          { label: 'BLOCKCHAIN', color: '#00D9FF' },
          { label: 'AI ENGINE', color: '#BF5FFF' },
          { label: 'SECURE', color: '#39FF14' },
        ].map(item => (
          <div key={item.label} className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full animate-pulse"
              style={{ background: item.color, boxShadow: `0 0 6px ${item.color}` }} />
            <span className="text-xs font-mono font-semibold tracking-widest" style={{ color: item.color }}>
              {item.label}
            </span>
          </div>
        ))}
      </div>

      {/* Right: logout */}
      <div className="flex items-center gap-2">
        <div className="px-3 py-1 rounded-full text-xs font-mono"
          style={{ background: 'rgba(57,255,20,0.1)', border: '1px solid rgba(57,255,20,0.3)', color: '#39FF14' }}>
          ● LIVE
        </div>
        <button onClick={onLogout}
          className="px-3 py-1 rounded-lg text-xs transition-all hover:scale-105"
          style={{ background: 'rgba(255,45,120,0.1)', border: '1px solid rgba(255,45,120,0.2)', color: '#FF2D78' }}>
          🚪 Logout
        </button>
      </div>
    </div>
  )
}

const panelVariants = {
  initial: { opacity: 0, y: 16, scale: 0.98 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.35, ease: [0.25, 0.46, 0.45, 0.94] } },
  exit: { opacity: 0, y: -8, scale: 0.99, transition: { duration: 0.2 } },
}

function App() {
  // 'landing' | 'auth' | 'dashboard'
  const [screen, setScreen] = useState('landing')
  const [selectedRole, setSelectedRole] = useState(null)   // e.g. 'manufacturer'
  const [autoScanSerial, setAutoScanSerial] = useState(null)

  // On mount: only skip landing for QR scan deep links
  useEffect(() => {
    // Validate token format, clear if bad
    const token = localStorage.getItem('authToken')
    if (token && token.split('.').length !== 3) {
      localStorage.clear()
    }

    // QR scan deep link — skip landing, go straight to patient verify
    const params = new URLSearchParams(window.location.search)
    const sn = params.get('sn')
    if (sn) {
      setAutoScanSerial(sn)
      setSelectedRole('patient')
      setScreen('dashboard')
      window.history.replaceState({}, '', window.location.pathname)
    }
    // Otherwise always show landing — even if token exists
    // User must click their role again, then auth screen will auto-login if token valid
  }, [])

  const handleRoleSelect = (roleId) => {
    setSelectedRole(roleId)
    // If already have a valid token for this role, skip auth screen
    const token = localStorage.getItem('authToken')
    const savedRole = localStorage.getItem('userRole')
    if (token && token.split('.').length === 3 && savedRole === ROLE_MAP[roleId]) {
      setScreen('dashboard')
    } else {
      setScreen('auth')
    }
  }

  const handleAuthSuccess = () => {
    setScreen('dashboard')
  }

  const handleLogout = () => {
    localStorage.clear()
    setSelectedRole(null)
    setScreen('landing')
  }

  const handleGoHome = () => {
    // Go back to landing without clearing session
    setScreen('landing')
  }

  const renderPanel = () => {
    switch (selectedRole) {
      case 'manufacturer': return <ManufacturerPanel onBatchCreated={() => {}} />
      case 'distributor':  return <DistributorPanel />
      case 'retailer':     return <RetailerPanel />
      case 'patient':      return <PatientPanel autoScanSerial={autoScanSerial} />
      case 'regulator':    return <RegulatorPanel />
      default:             return null
    }
  }

  return (
    <>
      {/* ── LANDING SCREEN ── */}
      <AnimatePresence>
        {screen === 'landing' && (
          <LandingScreen onSelectRole={handleRoleSelect} />
        )}
      </AnimatePresence>

      {/* ── AUTH MODAL (shown over dark bg, no dashboard behind) ── */}
      <AnimatePresence>
        {screen === 'auth' && selectedRole && (
          <motion.div
            key="auth-screen"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-40"
            style={{ background: '#020510' }}
          >
            {/* Subtle particle bg */}
            <ParticleField />
            <div className="fixed inset-0 bg-grid opacity-20 pointer-events-none" />

            {/* Back button */}
            <button
              onClick={() => setScreen('landing')}
              className="fixed top-6 left-6 z-50 flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all hover:scale-105"
              style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', color: '#9CA3AF' }}
            >
              ← Back
            </button>

            <AuthModal
              onAuthSuccess={handleAuthSuccess}
              allowedRoles={[ROLE_MAP[selectedRole]]}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── DASHBOARD (role-locked, full-width, 3D visual as transparent bg) ── */}
      <AnimatePresence>
        {screen === 'dashboard' && selectedRole && (
          <motion.div
            key="dashboard"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="min-h-screen flex flex-col"
            style={{ background: '#050816' }}
          >
            {/* 3D visual — 4 corner panels, always visible */}
            <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 0 }}>
              <DashboardVisual role={selectedRole} />
            </div>

            {/* Subtle dark overlay so content stays readable */}
            <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 1,
              background: 'radial-gradient(ellipse at 50% 50%, rgba(5,8,22,0.55) 0%, rgba(5,8,22,0.82) 100%)' }} />

            <div className="fixed inset-0 bg-grid pointer-events-none opacity-20" style={{ zIndex: 1 }} />

            {/* Top bar */}
            <div className="relative" style={{ zIndex: 10 }}>
              <TopBar role={selectedRole} onLogout={handleLogout} onBack={handleGoHome} />
            </div>

            {/* Full-width scrollable panel */}
            <main className="flex-1 overflow-y-auto relative" style={{ zIndex: 10 }}>
              <div className="w-full px-6 py-6" style={{ maxWidth: '1280px', margin: '0 auto' }}>
                <AnimatePresence mode="wait">
                  <motion.div
                    key={selectedRole}
                    variants={panelVariants}
                    initial="initial"
                    animate="animate"
                    exit="exit"
                  >
                    {renderPanel()}
                  </motion.div>
                </AnimatePresence>
              </div>
            </main>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

export default App
