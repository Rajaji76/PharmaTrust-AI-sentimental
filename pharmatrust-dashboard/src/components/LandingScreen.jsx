import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ─── Enhanced 3D Supply Chain Globe / Network Canvas ─────────────────────────
function SupplyChainCanvas() {
  const canvasRef = useRef(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let W = canvas.width = canvas.offsetWidth
    let H = canvas.height = canvas.offsetHeight
    let angle = 0
    let time = 0

    // Globe nodes (lat/lon style on a sphere)
    const sphereR = Math.min(W, H) * 0.28
    const nodeCount = 28
    const nodes = Array.from({ length: nodeCount }, (_, i) => {
      const phi = Math.acos(-1 + (2 * i) / nodeCount)
      const theta = Math.sqrt(nodeCount * Math.PI) * phi
      return {
        bx: sphereR * Math.sin(phi) * Math.cos(theta),
        by: sphereR * Math.sin(phi) * Math.sin(theta),
        bz: sphereR * Math.cos(phi),
        pulse: Math.random() * Math.PI * 2,
        type: i % 5, // 0=manufacturer,1=distributor,2=retailer,3=patient,4=regulator
      }
    })

    // Orbit rings
    const rings = [
      { r: sphereR * 1.15, tilt: 0.3, speed: 0.004, color: '#00D9FF' },
      { r: sphereR * 1.35, tilt: -0.5, speed: -0.003, color: '#BF5FFF' },
      { r: sphereR * 1.55, tilt: 0.8, speed: 0.002, color: '#39FF14' },
    ]

    // Floating data packets on orbits
    const packets = rings.map((ring, ri) =>
      Array.from({ length: 3 + ri }, (_, i) => ({
        angle: (i / (3 + ri)) * Math.PI * 2,
        ring: ri,
        speed: ring.speed * (1.5 + Math.random()),
      }))
    ).flat()

    // Background star field
    const stars = Array.from({ length: 200 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      r: Math.random() * 1.2,
      alpha: Math.random() * 0.5 + 0.1,
      twinkle: Math.random() * Math.PI * 2,
    }))

    const nodeColors = ['#00D9FF', '#BF5FFF', '#39FF14', '#FF6B35', '#FF2D78']

    const project = (x, y, z, fov = 600) => {
      const scale = fov / (fov + z)
      return { sx: x * scale + W / 2, sy: y * scale + H / 2, scale }
    }

    const rotateY = (x, y, z, a) => ({
      x: x * Math.cos(a) + z * Math.sin(a),
      y,
      z: -x * Math.sin(a) + z * Math.cos(a),
    })

    const rotateX = (x, y, z, a) => ({
      x,
      y: y * Math.cos(a) - z * Math.sin(a),
      z: y * Math.sin(a) + z * Math.cos(a),
    })

    let animId
    const draw = () => {
      ctx.clearRect(0, 0, W, H)
      angle += 0.005
      time += 0.02

      // Stars
      stars.forEach(s => {
        s.twinkle += 0.02
        const a = s.alpha * (0.6 + 0.4 * Math.sin(s.twinkle))
        ctx.beginPath()
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${a})`
        ctx.fill()
      })

      // Rotate all nodes
      const rotated = nodes.map(n => {
        let r = rotateY(n.bx, n.by, n.bz, angle)
        r = rotateX(r.x, r.y, r.z, 0.3)
        return { ...r, pulse: n.pulse, type: n.type }
      })

      // Sort by z for painter's algorithm
      const sorted = [...rotated].sort((a, b) => a.z - b.z)

      // Draw globe wireframe (latitude/longitude lines)
      const latLines = 6, lonLines = 8
      for (let lat = 0; lat < latLines; lat++) {
        const phi = (lat / latLines) * Math.PI
        const pts = []
        for (let i = 0; i <= 60; i++) {
          const theta = (i / 60) * Math.PI * 2
          let p = {
            x: sphereR * Math.sin(phi) * Math.cos(theta),
            y: sphereR * Math.cos(phi),
            z: sphereR * Math.sin(phi) * Math.sin(theta),
          }
          let r = rotateY(p.x, p.y, p.z, angle)
          r = rotateX(r.x, r.y, r.z, 0.3)
          pts.push(project(r.x, r.y, r.z))
        }
        ctx.beginPath()
        pts.forEach((p, i) => i === 0 ? ctx.moveTo(p.sx, p.sy) : ctx.lineTo(p.sx, p.sy))
        ctx.strokeStyle = 'rgba(0,217,255,0.06)'
        ctx.lineWidth = 0.5
        ctx.stroke()
      }
      for (let lon = 0; lon < lonLines; lon++) {
        const theta = (lon / lonLines) * Math.PI * 2
        const pts = []
        for (let i = 0; i <= 60; i++) {
          const phi = (i / 60) * Math.PI
          let p = {
            x: sphereR * Math.sin(phi) * Math.cos(theta),
            y: sphereR * Math.cos(phi),
            z: sphereR * Math.sin(phi) * Math.sin(theta),
          }
          let r = rotateY(p.x, p.y, p.z, angle)
          r = rotateX(r.x, r.y, r.z, 0.3)
          pts.push(project(r.x, r.y, r.z))
        }
        ctx.beginPath()
        pts.forEach((p, i) => i === 0 ? ctx.moveTo(p.sx, p.sy) : ctx.lineTo(p.sx, p.sy))
        ctx.strokeStyle = 'rgba(191,95,255,0.05)'
        ctx.lineWidth = 0.5
        ctx.stroke()
      }

      // Draw connections between nearby nodes
      for (let i = 0; i < sorted.length; i++) {
        for (let j = i + 1; j < sorted.length; j++) {
          const dx = sorted[i].x - sorted[j].x
          const dy = sorted[i].y - sorted[j].y
          const dz = sorted[i].z - sorted[j].z
          const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
          if (dist < sphereR * 0.85) {
            const a = project(sorted[i].x, sorted[i].y, sorted[i].z)
            const b = project(sorted[j].x, sorted[j].y, sorted[j].z)
            const alpha = (1 - dist / (sphereR * 0.85)) * 0.25 * Math.min(a.scale, b.scale)
            ctx.beginPath()
            ctx.moveTo(a.sx, a.sy)
            ctx.lineTo(b.sx, b.sy)
            ctx.strokeStyle = `rgba(0,217,255,${alpha})`
            ctx.lineWidth = 0.8
            ctx.stroke()
          }
        }
      }

      // Draw orbit rings
      rings.forEach((ring, ri) => {
        const pts = []
        for (let i = 0; i <= 120; i++) {
          const a = (i / 120) * Math.PI * 2
          let p = {
            x: ring.r * Math.cos(a),
            y: ring.r * Math.sin(a) * Math.sin(ring.tilt),
            z: ring.r * Math.sin(a) * Math.cos(ring.tilt),
          }
          let r = rotateY(p.x, p.y, p.z, angle * 0.3)
          pts.push(project(r.x, r.y, r.z))
        }
        ctx.beginPath()
        pts.forEach((p, i) => i === 0 ? ctx.moveTo(p.sx, p.sy) : ctx.lineTo(p.sx, p.sy))
        ctx.strokeStyle = ring.color + '22'
        ctx.lineWidth = 1
        ctx.stroke()
      })

      // Draw data packets on orbits
      packets.forEach(pkt => {
        pkt.angle += rings[pkt.ring].speed * 2
        const ring = rings[pkt.ring]
        let p = {
          x: ring.r * Math.cos(pkt.angle),
          y: ring.r * Math.sin(pkt.angle) * Math.sin(ring.tilt),
          z: ring.r * Math.sin(pkt.angle) * Math.cos(ring.tilt),
        }
        let r = rotateY(p.x, p.y, p.z, angle * 0.3)
        const proj = project(r.x, r.y, r.z)
        const size = proj.scale * 4
        const grd = ctx.createRadialGradient(proj.sx, proj.sy, 0, proj.sx, proj.sy, size * 2)
        grd.addColorStop(0, ring.color + 'ff')
        grd.addColorStop(1, ring.color + '00')
        ctx.beginPath()
        ctx.arc(proj.sx, proj.sy, size, 0, Math.PI * 2)
        ctx.fillStyle = grd
        ctx.fill()
      })

      // Draw nodes
      sorted.forEach(n => {
        const { sx, sy, scale } = project(n.x, n.y, n.z)
        const pulseFactor = 0.8 + 0.2 * Math.sin(time + n.pulse)
        const r = scale * 7 * pulseFactor
        const c = nodeColors[n.type]
        const grd = ctx.createRadialGradient(sx, sy, 0, sx, sy, r)
        grd.addColorStop(0, c + 'ff')
        grd.addColorStop(0.5, c + '88')
        grd.addColorStop(1, c + '00')
        ctx.beginPath()
        ctx.arc(sx, sy, r, 0, Math.PI * 2)
        ctx.fillStyle = grd
        ctx.fill()
        // Outer ring
        ctx.beginPath()
        ctx.arc(sx, sy, r * 2, 0, Math.PI * 2)
        ctx.strokeStyle = c + '20'
        ctx.lineWidth = 1
        ctx.stroke()
      })

      // Center glow
      const cg = ctx.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, sphereR * 0.6)
      cg.addColorStop(0, 'rgba(0,217,255,0.06)')
      cg.addColorStop(1, 'rgba(0,217,255,0)')
      ctx.beginPath()
      ctx.arc(W / 2, H / 2, sphereR * 0.6, 0, Math.PI * 2)
      ctx.fillStyle = cg
      ctx.fill()

      animId = requestAnimationFrame(draw)
    }
    draw()
    const onResize = () => {
      W = canvas.width = canvas.offsetWidth
      H = canvas.height = canvas.offsetHeight
    }
    window.addEventListener('resize', onResize)
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', onResize) }
  }, [])
  return <canvas ref={canvasRef} className="absolute inset-0 w-full h-full" />
}

// ─── Role Data ────────────────────────────────────────────────────────────────
const roles = [
  {
    id: 'manufacturer',
    label: 'Manufacturer',
    desc: 'Create & certify medicine batches with AI lab verification',
    icon: '🏭',
    color: '#00D9FF',
    shadow: 'rgba(0,217,255,0.4)',
    gradient: 'linear-gradient(135deg, rgba(0,217,255,0.2), rgba(0,217,255,0.05))',
  },
  {
    id: 'distributor',
    label: 'Distributor',
    desc: 'Manage wholesale supply chain & track shipments',
    icon: '🚚',
    color: '#BF5FFF',
    shadow: 'rgba(191,95,255,0.4)',
    gradient: 'linear-gradient(135deg, rgba(191,95,255,0.2), rgba(191,95,255,0.05))',
  },
  {
    id: 'retailer',
    label: 'Retailer',
    desc: 'Medical shop inventory & patient dispensing',
    icon: '🏪',
    color: '#39FF14',
    shadow: 'rgba(57,255,20,0.4)',
    gradient: 'linear-gradient(135deg, rgba(57,255,20,0.2), rgba(57,255,20,0.05))',
  },
  {
    id: 'patient',
    label: 'Patient',
    desc: 'Scan & verify medicine authenticity instantly',
    icon: '👤',
    color: '#FF6B35',
    shadow: 'rgba(255,107,53,0.4)',
    gradient: 'linear-gradient(135deg, rgba(255,107,53,0.2), rgba(255,107,53,0.05))',
  },
  {
    id: 'regulator',
    label: 'Regulator',
    desc: 'Oversight, compliance & supply chain control',
    icon: '⚖️',
    color: '#FF2D78',
    shadow: 'rgba(255,45,120,0.4)',
    gradient: 'linear-gradient(135deg, rgba(255,45,120,0.2), rgba(255,45,120,0.05))',
  },
]

// ─── Typewriter ───────────────────────────────────────────────────────────────
function TypewriterText({ text, delay = 0 }) {
  const [displayed, setDisplayed] = useState('')
  const [done, setDone] = useState(false)
  useEffect(() => {
    let i = 0
    const t = setTimeout(() => {
      const iv = setInterval(() => {
        i++
        setDisplayed(text.slice(0, i))
        if (i >= text.length) { clearInterval(iv); setDone(true) }
      }, 40)
      return () => clearInterval(iv)
    }, delay)
    return () => clearTimeout(t)
  }, [text, delay])
  return <span>{displayed}{!done && <span className="typing-cursor" />}</span>
}

// ─── Main LandingScreen ───────────────────────────────────────────────────────
export default function LandingScreen({ onSelectRole }) {
  const [phase, setPhase] = useState('welcome')
  const [hoveredRole, setHoveredRole] = useState(null)

  useEffect(() => {
    if (phase !== 'welcome') return
    const t = setTimeout(() => setPhase('roles'), 4200)
    return () => clearTimeout(t)
  }, [phase])

  return (
    <div className="fixed inset-0 z-50 overflow-hidden" style={{ background: '#020510' }}>
      {/* Grid overlay */}
      <div className="absolute inset-0 bg-grid opacity-20 pointer-events-none" />

      {/* 3D Canvas — full screen always */}
      <div className="absolute inset-0">
        <SupplyChainCanvas />
      </div>

      {/* Vignette */}
      <div className="absolute inset-0 pointer-events-none"
        style={{ background: 'radial-gradient(ellipse at 50% 50%, transparent 25%, rgba(2,5,16,0.75) 100%)' }} />

      <AnimatePresence mode="wait">

        {/* ── WELCOME PHASE ── */}
        {phase === 'welcome' && (
          <motion.div
            key="welcome"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.6 } }}
            transition={{ duration: 0.8 }}
            className="absolute inset-0 flex flex-col items-center justify-center text-center px-8 pointer-events-none"
          >
            {/* Logo badge */}
            <motion.div
              initial={{ y: -40, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.3, duration: 0.8, ease: 'easeOut' }}
              className="mb-6 pointer-events-auto"
            >
              <div className="inline-flex items-center gap-3 px-6 py-3 rounded-full"
                style={{
                  background: 'rgba(0,217,255,0.07)',
                  border: '1px solid rgba(0,217,255,0.3)',
                  boxShadow: '0 0 40px rgba(0,217,255,0.15), inset 0 1px 0 rgba(0,217,255,0.2)',
                }}>
                <span className="text-2xl">💊</span>
                <span className="text-sm font-mono font-bold tracking-widest uppercase" style={{ color: '#00D9FF' }}>
                  PharmaTrust v2.0
                </span>
                <div className="w-2 h-2 rounded-full animate-pulse" style={{ background: '#39FF14', boxShadow: '0 0 10px #39FF14' }} />
              </div>
            </motion.div>

            {/* Heading */}
            <motion.h1
              initial={{ y: 30, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.55, duration: 0.9 }}
              className="text-7xl md:text-8xl font-black mb-5 leading-none tracking-tight"
              style={{
                background: 'linear-gradient(135deg, #ffffff 0%, #00D9FF 35%, #BF5FFF 70%, #FF2D78 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              Welcome to<br />PharmaTrust
            </motion.h1>

            {/* Tagline */}
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 1.0, duration: 0.6 }}
              className="text-2xl md:text-3xl font-light mb-12"
              style={{ color: 'rgba(255,255,255,0.45)' }}
            >
              <TypewriterText text="Track and secure every single dose." delay={1100} />
            </motion.p>

            {/* Stats */}
            <motion.div
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 1.5, duration: 0.6 }}
              className="flex items-center gap-12 mb-12 pointer-events-auto"
            >
              {[
                { val: 'AI', label: 'Lab Verification', color: '#00D9FF' },
                { val: 'QR', label: 'Unit Tracking', color: '#BF5FFF' },
                { val: '5', label: 'Role Dashboards', color: '#39FF14' },
              ].map(s => (
                <div key={s.label} className="text-center">
                  <p className="text-4xl font-black mb-1" style={{ color: s.color, textShadow: `0 0 20px ${s.color}` }}>{s.val}</p>
                  <p className="text-xs text-gray-500 tracking-widest uppercase">{s.label}</p>
                </div>
              ))}
            </motion.div>

            {/* Enter button */}
            <motion.button
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 1.9, duration: 0.5 }}
              whileHover={{ scale: 1.06 }}
              whileTap={{ scale: 0.97 }}
              onClick={() => setPhase('roles')}
              className="cyber-btn px-12 py-4 rounded-2xl text-lg font-bold tracking-widest uppercase pointer-events-auto"
              style={{
                background: 'linear-gradient(135deg, rgba(0,217,255,0.15), rgba(191,95,255,0.15))',
                border: '1px solid rgba(0,217,255,0.5)',
                color: '#fff',
                boxShadow: '0 0 40px rgba(0,217,255,0.25), 0 0 80px rgba(191,95,255,0.1)',
                letterSpacing: '0.15em',
              }}
            >
              Enter Platform →
            </motion.button>

            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 2.5 }}
              className="mt-5 text-xs text-gray-700 font-mono pointer-events-auto"
            >
              Auto-continuing in a moment...
            </motion.p>
          </motion.div>
        )}

        {/* ── ROLE SELECTOR PHASE ── */}
        {phase === 'roles' && (
          <motion.div
            key="roles"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.6 }}
            className="absolute inset-0 flex flex-col items-center justify-center px-6 py-8"
          >
            <motion.div
              initial={{ y: -20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.1 }}
              className="text-center mb-10"
            >
              <p className="text-xs font-mono tracking-widest uppercase mb-3" style={{ color: '#00D9FF' }}>
                — Select Your Role —
              </p>
              <h2 className="text-5xl md:text-6xl font-black text-white mb-3 leading-tight">
                Who are you?
              </h2>
              <p className="text-gray-500">Choose your role to login or register</p>
            </motion.div>

            {/* Role cards */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-5 w-full max-w-5xl">
              {roles.map((role, i) => (
                <motion.button
                  key={role.id}
                  initial={{ opacity: 0, y: 40 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.15 + i * 0.09, duration: 0.5, ease: 'easeOut' }}
                  whileHover={{ scale: 1.07, y: -6 }}
                  whileTap={{ scale: 0.97 }}
                  onHoverStart={() => setHoveredRole(role.id)}
                  onHoverEnd={() => setHoveredRole(null)}
                  onClick={() => onSelectRole(role.id)}
                  className="relative flex flex-col items-center justify-center p-7 rounded-2xl text-center overflow-hidden"
                  style={{
                    background: hoveredRole === role.id ? role.gradient : 'rgba(13,17,23,0.85)',
                    border: `1px solid ${hoveredRole === role.id ? role.color + '70' : 'rgba(255,255,255,0.07)'}`,
                    boxShadow: hoveredRole === role.id
                      ? `0 0 50px ${role.shadow}, 0 12px 40px rgba(0,0,0,0.5)`
                      : '0 4px 20px rgba(0,0,0,0.4)',
                    backdropFilter: 'blur(16px)',
                    minHeight: '190px',
                    transition: 'all 0.3s ease',
                  }}
                >
                  {/* Top glow on hover */}
                  {hoveredRole === role.id && (
                    <div className="absolute top-0 left-0 right-0 h-px"
                      style={{ background: `linear-gradient(90deg, transparent, ${role.color}, transparent)` }} />
                  )}

                  {/* Icon */}
                  <div className="text-5xl mb-4 relative z-10 transition-all duration-300"
                    style={{
                      filter: hoveredRole === role.id ? `drop-shadow(0 0 16px ${role.color})` : 'none',
                      transform: hoveredRole === role.id ? 'scale(1.15)' : 'scale(1)',
                    }}>
                    {role.icon}
                  </div>

                  {/* Label */}
                  <p className="text-base font-bold mb-2 relative z-10 transition-colors duration-200"
                    style={{ color: hoveredRole === role.id ? role.color : '#fff' }}>
                    {role.label}
                  </p>

                  {/* Desc */}
                  <p className="text-xs leading-relaxed relative z-10" style={{ color: 'rgba(156,163,175,0.7)' }}>
                    {role.desc}
                  </p>

                  {/* Bottom line */}
                  <div className="absolute bottom-0 left-0 right-0 h-0.5 rounded-b-2xl"
                    style={{
                      background: `linear-gradient(90deg, transparent, ${role.color}, transparent)`,
                      opacity: hoveredRole === role.id ? 1 : 0,
                      transition: 'opacity 0.3s',
                    }} />
                </motion.button>
              ))}
            </div>

            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.9 }}
              className="mt-8 text-xs text-gray-700 font-mono"
            >
              Powered by AI · Blockchain Secured · Real-time Tracking
            </motion.p>
          </motion.div>
        )}

      </AnimatePresence>
    </div>
  )
}
