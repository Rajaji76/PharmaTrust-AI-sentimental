import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ─── MEGA 3D Pharma Supply Chain Visual ──────────────────────────────────────
function SupplyChainCanvas() {
  const canvasRef = useRef(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let W = canvas.width = canvas.offsetWidth
    let H = canvas.height = canvas.offsetHeight
    let t = 0

    // ── Perspective helpers ──
    const FOV = 900
    const proj = (x, y, z) => {
      const s = FOV / (FOV + z + 400)
      return { x: x * s + W / 2, y: y * s + H / 2, s }
    }
    const ry = (x, y, z, a) => ({ x: x * Math.cos(a) + z * Math.sin(a), y, z: -x * Math.sin(a) + z * Math.cos(a) })
    const rx = (x, y, z, a) => ({ x, y: y * Math.cos(a) - z * Math.sin(a), z: y * Math.sin(a) + z * Math.cos(a) })

    // ── Stars ──
    const stars = Array.from({ length: 300 }, () => ({
      x: (Math.random() - 0.5) * W * 3, y: (Math.random() - 0.5) * H * 3,
      z: Math.random() * 1200 - 600,
      r: Math.random() * 1.5 + 0.3,
      tw: Math.random() * Math.PI * 2,
    }))

    // ── DNA Helix strands (supply chain backbone) ──
    const HELIX_H = 420
    const helixPts = Array.from({ length: 80 }, (_, i) => {
      const frac = i / 79
      const y = (frac - 0.5) * HELIX_H
      const a1 = frac * Math.PI * 6
      const a2 = a1 + Math.PI
      const r = 90
      return {
        x1: r * Math.cos(a1), y1: y, z1: r * Math.sin(a1),
        x2: r * Math.cos(a2), y2: y, z2: r * Math.sin(a2),
        frac,
      }
    })

    // ── Supply chain nodes (5 roles) ──
    const nodeColors = ['#00D9FF', '#BF5FFF', '#39FF14', '#FF6B35', '#FF2D78']
    const nodeLabels = ['MFG', 'DIST', 'RETAIL', 'PATIENT', 'REG']
    const nodeIcons = ['🏭', '🚚', '🏪', '👤', '⚖️']
    const orbitR = [200, 270, 340, 200, 270]
    const orbitTilt = [0.4, -0.3, 0.6, -0.5, 0.2]
    const orbitSpeed = [0.007, -0.005, 0.004, -0.006, 0.008]
    const nodes = nodeColors.map((c, i) => ({
      angle: (i / 5) * Math.PI * 2,
      r: orbitR[i], tilt: orbitTilt[i], speed: orbitSpeed[i],
      color: c, label: nodeLabels[i], icon: nodeIcons[i],
      pulse: Math.random() * Math.PI * 2,
      trail: [],
    }))

    // ── Floating QR-like data cubes ──
    const cubes = Array.from({ length: 12 }, (_, i) => ({
      x: (Math.random() - 0.5) * 600,
      y: (Math.random() - 0.5) * 500,
      z: (Math.random() - 0.5) * 400,
      size: 8 + Math.random() * 14,
      rx: Math.random() * Math.PI * 2,
      ry: Math.random() * Math.PI * 2,
      rz: Math.random() * Math.PI * 2,
      drx: (Math.random() - 0.5) * 0.02,
      dry: (Math.random() - 0.5) * 0.02,
      color: nodeColors[i % 5],
      alpha: 0.3 + Math.random() * 0.4,
    }))

    // ── Particle stream (data flow) ──
    const particles = Array.from({ length: 120 }, () => ({
      angle: Math.random() * Math.PI * 2,
      r: 80 + Math.random() * 280,
      y: (Math.random() - 0.5) * 500,
      speed: (0.01 + Math.random() * 0.02) * (Math.random() > 0.5 ? 1 : -1),
      color: nodeColors[Math.floor(Math.random() * 5)],
      size: 1.5 + Math.random() * 2.5,
      alpha: 0.4 + Math.random() * 0.6,
    }))

    // ── Shockwave rings ──
    const waves = []
    let waveTimer = 0

    // ── Nebula blobs ──
    const nebulas = [
      { x: -180, y: -120, r: 220, c: '0,217,255' },
      { x: 200, y: 100, r: 180, c: '191,95,255' },
      { x: 0, y: 180, r: 160, c: '57,255,20' },
    ]

    let animId
    let globalAngle = 0

    const drawCube = (cx, cy, size, color, alpha, rotX, rotY) => {
      const verts = [
        [-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1],
        [-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1],
      ].map(([x, y, z]) => {
        // rotate
        let p = rx(x, y, z, rotX)
        p = ry(p.x, p.y, p.z, rotY)
        return proj(p.x * size + cx - W / 2, p.y * size + cy - H / 2, p.z * size)
      })
      const faces = [
        [0, 1, 2, 3], [4, 5, 6, 7], [0, 1, 5, 4],
        [2, 3, 7, 6], [0, 3, 7, 4], [1, 2, 6, 5],
      ]
      faces.forEach(f => {
        ctx.beginPath()
        ctx.moveTo(verts[f[0]].x, verts[f[0]].y)
        f.slice(1).forEach(i => ctx.lineTo(verts[i].x, verts[i].y))
        ctx.closePath()
        ctx.strokeStyle = color + Math.floor(alpha * 180).toString(16).padStart(2, '0')
        ctx.lineWidth = 0.8
        ctx.stroke()
      })
    }

    const draw = () => {
      ctx.clearRect(0, 0, W, H)
      t += 0.016
      globalAngle += 0.004
      waveTimer += 0.016

      // ── Background gradient ──
      const bg = ctx.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, Math.max(W, H) * 0.8)
      bg.addColorStop(0, 'rgba(5,8,22,1)')
      bg.addColorStop(0.5, 'rgba(3,5,15,1)')
      bg.addColorStop(1, 'rgba(0,0,8,1)')
      ctx.fillStyle = bg
      ctx.fillRect(0, 0, W, H)

      // ── Nebula blobs ──
      nebulas.forEach(n => {
        const grd = ctx.createRadialGradient(W / 2 + n.x, H / 2 + n.y, 0, W / 2 + n.x, H / 2 + n.y, n.r)
        grd.addColorStop(0, `rgba(${n.c},0.07)`)
        grd.addColorStop(1, `rgba(${n.c},0)`)
        ctx.fillStyle = grd
        ctx.fillRect(0, 0, W, H)
      })

      // ── Stars ──
      stars.forEach(s => {
        s.tw += 0.015
        const p = proj(s.x, s.y, s.z)
        const a = (0.3 + 0.7 * Math.abs(Math.sin(s.tw))) * Math.min(1, p.s * 2)
        ctx.beginPath()
        ctx.arc(p.x, p.y, s.r * p.s, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${a})`
        ctx.fill()
      })

      // ── Shockwave rings ──
      if (waveTimer > 2.5) {
        waves.push({ r: 0, alpha: 0.8, color: nodeColors[Math.floor(Math.random() * 5)] })
        waveTimer = 0
      }
      for (let i = waves.length - 1; i >= 0; i--) {
        waves[i].r += 4
        waves[i].alpha -= 0.012
        if (waves[i].alpha <= 0) { waves.splice(i, 1); continue }
        ctx.beginPath()
        ctx.arc(W / 2, H / 2, waves[i].r, 0, Math.PI * 2)
        ctx.strokeStyle = waves[i].color + Math.floor(waves[i].alpha * 255).toString(16).padStart(2, '0')
        ctx.lineWidth = 1.5
        ctx.stroke()
      }

      // ── DNA Helix ──
      helixPts.forEach((pt, i) => {
        if (i === 0) return
        const prev = helixPts[i - 1]
        // Strand 1
        let r1a = ry(pt.x1, pt.y1, pt.z1, globalAngle)
        r1a = rx(r1a.x, r1a.y, r1a.z, 0.25)
        let r1b = ry(prev.x1, prev.y1, prev.z1, globalAngle)
        r1b = rx(r1b.x, r1b.y, r1b.z, 0.25)
        const p1a = proj(r1a.x, r1a.y, r1a.z)
        const p1b = proj(r1b.x, r1b.y, r1b.z)
        const alpha1 = 0.15 + 0.5 * pt.frac * (1 - pt.frac) * 4
        ctx.beginPath()
        ctx.moveTo(p1b.x, p1b.y)
        ctx.lineTo(p1a.x, p1a.y)
        ctx.strokeStyle = `rgba(0,217,255,${alpha1})`
        ctx.lineWidth = 1.5 * p1a.s
        ctx.stroke()

        // Strand 2
        let r2a = ry(pt.x2, pt.y2, pt.z2, globalAngle)
        r2a = rx(r2a.x, r2a.y, r2a.z, 0.25)
        let r2b = ry(prev.x2, prev.y2, prev.z2, globalAngle)
        r2b = rx(r2b.x, r2b.y, r2b.z, 0.25)
        const p2a = proj(r2a.x, r2a.y, r2a.z)
        ctx.beginPath()
        ctx.moveTo(proj(r2b.x, r2b.y, r2b.z).x, proj(r2b.x, r2b.y, r2b.z).y)
        ctx.lineTo(p2a.x, p2a.y)
        ctx.strokeStyle = `rgba(191,95,255,${alpha1})`
        ctx.lineWidth = 1.5 * p2a.s
        ctx.stroke()

        // Cross rungs every 4
        if (i % 4 === 0) {
          ctx.beginPath()
          ctx.moveTo(p1a.x, p1a.y)
          ctx.lineTo(p2a.x, p2a.y)
          ctx.strokeStyle = `rgba(57,255,20,${alpha1 * 0.6})`
          ctx.lineWidth = 0.8
          ctx.stroke()
          // Rung node
          const mid = { x: (p1a.x + p2a.x) / 2, y: (p1a.y + p2a.y) / 2 }
          const grd = ctx.createRadialGradient(mid.x, mid.y, 0, mid.x, mid.y, 5)
          grd.addColorStop(0, 'rgba(57,255,20,0.9)')
          grd.addColorStop(1, 'rgba(57,255,20,0)')
          ctx.beginPath()
          ctx.arc(mid.x, mid.y, 3, 0, Math.PI * 2)
          ctx.fillStyle = grd
          ctx.fill()
        }
      })

      // ── Particle stream ──
      particles.forEach(p => {
        p.angle += p.speed
        const px = p.r * Math.cos(p.angle)
        const pz = p.r * Math.sin(p.angle)
        let rp = ry(px, p.y, pz, globalAngle * 0.2)
        rp = rx(rp.x, rp.y, rp.z, 0.25)
        const pp = proj(rp.x, rp.y, rp.z)
        const a = p.alpha * pp.s
        const grd = ctx.createRadialGradient(pp.x, pp.y, 0, pp.x, pp.y, p.size * pp.s * 2)
        grd.addColorStop(0, p.color + 'ff')
        grd.addColorStop(1, p.color + '00')
        ctx.beginPath()
        ctx.arc(pp.x, pp.y, p.size * pp.s, 0, Math.PI * 2)
        ctx.fillStyle = grd
        ctx.globalAlpha = a
        ctx.fill()
        ctx.globalAlpha = 1
      })

      // ── Floating data cubes ──
      cubes.forEach(c => {
        c.rx += c.drx
        c.ry += c.dry
        let rp = ry(c.x, c.y, c.z, globalAngle * 0.15)
        rp = rx(rp.x, rp.y, rp.z, 0.25)
        const pp = proj(rp.x, rp.y, rp.z)
        drawCube(pp.x, pp.y, c.size * pp.s, c.color, c.alpha * pp.s, c.rx, c.ry)
      })

      // ── Orbit rings + nodes ──
      nodes.forEach((n, ni) => {
        n.angle += n.speed
        n.pulse += 0.05

        // Draw orbit ellipse
        const orbitPts = []
        for (let i = 0; i <= 100; i++) {
          const a = (i / 100) * Math.PI * 2
          let op = {
            x: n.r * Math.cos(a),
            y: n.r * Math.sin(a) * Math.sin(n.tilt),
            z: n.r * Math.sin(a) * Math.cos(n.tilt),
          }
          let rp = ry(op.x, op.y, op.z, globalAngle * 0.25)
          rp = rx(rp.x, rp.y, rp.z, 0.25)
          orbitPts.push(proj(rp.x, rp.y, rp.z))
        }
        ctx.beginPath()
        orbitPts.forEach((p, i) => i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y))
        ctx.strokeStyle = n.color + '18'
        ctx.lineWidth = 1
        ctx.stroke()

        // Node position
        let np = {
          x: n.r * Math.cos(n.angle),
          y: n.r * Math.sin(n.angle) * Math.sin(n.tilt),
          z: n.r * Math.sin(n.angle) * Math.cos(n.tilt),
        }
        let rnp = ry(np.x, np.y, np.z, globalAngle * 0.25)
        rnp = rx(rnp.x, rnp.y, rnp.z, 0.25)
        const pp = proj(rnp.x, rnp.y, rnp.z)

        // Trail
        n.trail.push({ x: pp.x, y: pp.y, a: 0.6 })
        if (n.trail.length > 25) n.trail.shift()
        n.trail.forEach((tr, ti) => {
          tr.a -= 0.024
          if (tr.a <= 0) return
          ctx.beginPath()
          ctx.arc(tr.x, tr.y, (ti / n.trail.length) * 4 * pp.s, 0, Math.PI * 2)
          ctx.fillStyle = n.color + Math.floor(tr.a * 255).toString(16).padStart(2, '0')
          ctx.fill()
        })

        // Node glow layers
        const pf = 0.85 + 0.15 * Math.sin(n.pulse)
        const nr = 18 * pp.s * pf
        ;[nr * 3, nr * 2, nr].forEach((gr, gi) => {
          const grd = ctx.createRadialGradient(pp.x, pp.y, 0, pp.x, pp.y, gr)
          const alphas = ['08', '15', '60']
          grd.addColorStop(0, n.color + alphas[gi])
          grd.addColorStop(1, n.color + '00')
          ctx.beginPath()
          ctx.arc(pp.x, pp.y, gr, 0, Math.PI * 2)
          ctx.fillStyle = grd
          ctx.fill()
        })

        // Node core
        ctx.beginPath()
        ctx.arc(pp.x, pp.y, nr * 0.55, 0, Math.PI * 2)
        ctx.fillStyle = n.color
        ctx.fill()

        // Node ring
        ctx.beginPath()
        ctx.arc(pp.x, pp.y, nr * 0.8, 0, Math.PI * 2)
        ctx.strokeStyle = n.color + 'aa'
        ctx.lineWidth = 1.5
        ctx.stroke()

        // Label
        if (pp.s > 0.6) {
          ctx.font = `bold ${Math.floor(10 * pp.s)}px monospace`
          ctx.fillStyle = n.color
          ctx.textAlign = 'center'
          ctx.fillText(n.label, pp.x, pp.y + nr + 14 * pp.s)
        }

        // Connection lines between nodes
        nodes.forEach((n2, ni2) => {
          if (ni2 <= ni) return
          let np2 = {
            x: n2.r * Math.cos(n2.angle),
            y: n2.r * Math.sin(n2.angle) * Math.sin(n2.tilt),
            z: n2.r * Math.sin(n2.angle) * Math.cos(n2.tilt),
          }
          let rnp2 = ry(np2.x, np2.y, np2.z, globalAngle * 0.25)
          rnp2 = rx(rnp2.x, rnp2.y, rnp2.z, 0.25)
          const pp2 = proj(rnp2.x, rnp2.y, rnp2.z)
          const dist = Math.hypot(pp.x - pp2.x, pp.y - pp2.y)
          if (dist < 280) {
            const alpha = (1 - dist / 280) * 0.35
            const grad = ctx.createLinearGradient(pp.x, pp.y, pp2.x, pp2.y)
            grad.addColorStop(0, n.color + Math.floor(alpha * 255).toString(16).padStart(2, '0'))
            grad.addColorStop(1, n2.color + Math.floor(alpha * 255).toString(16).padStart(2, '0'))
            ctx.beginPath()
            ctx.moveTo(pp.x, pp.y)
            ctx.lineTo(pp2.x, pp2.y)
            ctx.strokeStyle = grad
            ctx.lineWidth = 1.2
            ctx.stroke()

            // Animated data packet on connection
            const frac = (Math.sin(t * 2 + ni * 1.3) + 1) / 2
            const mx = pp.x + (pp2.x - pp.x) * frac
            const my = pp.y + (pp2.y - pp.y) * frac
            const pgrd = ctx.createRadialGradient(mx, my, 0, mx, my, 5)
            pgrd.addColorStop(0, '#ffffff')
            pgrd.addColorStop(0.4, n.color + 'cc')
            pgrd.addColorStop(1, n.color + '00')
            ctx.beginPath()
            ctx.arc(mx, my, 4, 0, Math.PI * 2)
            ctx.fillStyle = pgrd
            ctx.fill()
          }
        })
      })

      // ── Central core ──
      const coreR = 38 + 6 * Math.sin(t * 1.5)
      ;[coreR * 4, coreR * 2.5, coreR * 1.5, coreR].forEach((r, i) => {
        const alphas = [0.04, 0.08, 0.18, 0.9]
        const colors = ['0,217,255', '191,95,255', '0,217,255', '255,255,255']
        const grd = ctx.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, r)
        grd.addColorStop(0, `rgba(${colors[i]},${alphas[i]})`)
        grd.addColorStop(1, `rgba(${colors[i]},0)`)
        ctx.beginPath()
        ctx.arc(W / 2, H / 2, r, 0, Math.PI * 2)
        ctx.fillStyle = grd
        ctx.fill()
      })

      // Core spinning ring
      ctx.save()
      ctx.translate(W / 2, H / 2)
      ctx.rotate(t * 0.8)
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2
        const x = coreR * 1.2 * Math.cos(a)
        const y = coreR * 1.2 * Math.sin(a)
        ctx.beginPath()
        ctx.arc(x, y, 3, 0, Math.PI * 2)
        ctx.fillStyle = i % 2 === 0 ? '#00D9FF' : '#BF5FFF'
        ctx.fill()
      }
      ctx.restore()

      // Core inner cross
      ctx.save()
      ctx.translate(W / 2, H / 2)
      ctx.rotate(-t * 1.2)
      ctx.strokeStyle = 'rgba(0,217,255,0.5)'
      ctx.lineWidth = 1.5
      for (let i = 0; i < 4; i++) {
        const a = (i / 4) * Math.PI * 2
        ctx.beginPath()
        ctx.moveTo(0, 0)
        ctx.lineTo(coreR * 0.9 * Math.cos(a), coreR * 0.9 * Math.sin(a))
        ctx.stroke()
      }
      ctx.restore()

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
    shadow: 'rgba(0,217,255,0.5)',
    gradient: 'linear-gradient(135deg, rgba(0,217,255,0.18), rgba(0,217,255,0.04))',
  },
  {
    id: 'distributor',
    label: 'Distributor',
    desc: 'Manage wholesale supply chain & track shipments',
    icon: '🚚',
    color: '#BF5FFF',
    shadow: 'rgba(191,95,255,0.5)',
    gradient: 'linear-gradient(135deg, rgba(191,95,255,0.18), rgba(191,95,255,0.04))',
  },
  {
    id: 'retailer',
    label: 'Retailer',
    desc: 'Medical shop inventory & patient dispensing',
    icon: '🏪',
    color: '#39FF14',
    shadow: 'rgba(57,255,20,0.5)',
    gradient: 'linear-gradient(135deg, rgba(57,255,20,0.18), rgba(57,255,20,0.04))',
  },
  {
    id: 'patient',
    label: 'Patient',
    desc: 'Scan & verify medicine authenticity instantly',
    icon: '👤',
    color: '#FF6B35',
    shadow: 'rgba(255,107,53,0.5)',
    gradient: 'linear-gradient(135deg, rgba(255,107,53,0.18), rgba(255,107,53,0.04))',
  },
  {
    id: 'regulator',
    label: 'Regulator',
    desc: 'Oversight, compliance & supply chain control',
    icon: '⚖️',
    color: '#FF2D78',
    shadow: 'rgba(255,45,120,0.5)',
    gradient: 'linear-gradient(135deg, rgba(255,45,120,0.18), rgba(255,45,120,0.04))',
  },
]

// ─── Typewriter ───────────────────────────────────────────────────────────────
function TypewriterText({ text, delay = 0 }) {
  const [displayed, setDisplayed] = useState('')
  const [started, setStarted] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setStarted(true), delay)
    return () => clearTimeout(t)
  }, [delay])
  useEffect(() => {
    if (!started) return
    let i = 0
    const iv = setInterval(() => {
      setDisplayed(text.slice(0, i + 1))
      i++
      if (i >= text.length) clearInterval(iv)
    }, 38)
    return () => clearInterval(iv)
  }, [started, text])
  return <span>{displayed}<span className="animate-pulse">|</span></span>
}

// ─── Stat Counter ─────────────────────────────────────────────────────────────
function StatCounter({ value, label, color }) {
  const [count, setCount] = useState(0)
  useEffect(() => {
    let start = 0
    const step = Math.ceil(value / 60)
    const iv = setInterval(() => {
      start += step
      if (start >= value) { setCount(value); clearInterval(iv) }
      else setCount(start)
    }, 20)
    return () => clearInterval(iv)
  }, [value])
  return (
    <div className="text-center">
      <p className="text-2xl font-black font-mono" style={{ color }}>{count.toLocaleString()}+</p>
      <p className="text-gray-500 text-xs mt-0.5">{label}</p>
    </div>
  )
}

// ─── Main Landing Screen ──────────────────────────────────────────────────────
export default function LandingScreen({ onSelectRole }) {
  const [hoveredRole, setHoveredRole] = useState(null)
  const [selectedRole, setSelectedRole] = useState(null)

  const handleSelect = (roleId) => {
    setSelectedRole(roleId)
    setTimeout(() => onSelectRole(roleId), 400)
  }

  return (
    <div className="relative min-h-screen overflow-hidden" style={{ background: '#020510' }}>
      {/* Full-screen 3D canvas */}
      <div className="absolute inset-0">
        <SupplyChainCanvas />
      </div>

      {/* Vignette overlay */}
      <div className="absolute inset-0 pointer-events-none"
        style={{ background: 'radial-gradient(ellipse at center, transparent 30%, rgba(2,5,16,0.7) 100%)' }} />

      {/* Top scanline effect */}
      <div className="absolute inset-0 pointer-events-none opacity-[0.03]"
        style={{ backgroundImage: 'repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,217,255,1) 2px, rgba(0,217,255,1) 3px)', backgroundSize: '100% 4px' }} />

      {/* Content */}
      <div className="relative z-10 flex flex-col items-center justify-between min-h-screen px-6 py-8">

        {/* Header */}
        <motion.div initial={{ opacity: 0, y: -30 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8 }}
          className="text-center w-full">
          <div className="inline-flex items-center gap-3 mb-3">
            <div className="w-px h-8 bg-gradient-to-b from-transparent via-cyan-400 to-transparent" />
            <span className="text-xs font-mono text-cyan-400 tracking-[0.3em] uppercase">Blockchain · AI · Pharma</span>
            <div className="w-px h-8 bg-gradient-to-b from-transparent via-cyan-400 to-transparent" />
          </div>

          <h1 className="text-6xl md:text-8xl font-black tracking-tight mb-2"
            style={{
              background: 'linear-gradient(135deg, #00D9FF 0%, #BF5FFF 50%, #39FF14 100%)',
              WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
              filter: 'drop-shadow(0 0 40px rgba(0,217,255,0.4))',
            }}>
            PharmaTrust
          </h1>

          <p className="text-gray-400 text-lg md:text-xl font-light tracking-wide mb-1">
            <TypewriterText text="Next-Gen Pharmaceutical Supply Chain Security" delay={600} />
          </p>

          <div className="flex items-center justify-center gap-2 mt-3">
            <div className="w-2 h-2 rounded-full bg-neon-green animate-pulse" />
            <span className="text-neon-green text-xs font-mono tracking-widest">SYSTEM ONLINE · ALL NODES ACTIVE</span>
            <div className="w-2 h-2 rounded-full bg-neon-green animate-pulse" />
          </div>
        </motion.div>

        {/* Stats bar */}
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.2, duration: 0.8 }}
          className="flex gap-8 md:gap-16 px-8 py-4 rounded-2xl border border-white/5"
          style={{ background: 'rgba(0,217,255,0.04)', backdropFilter: 'blur(20px)' }}>
          <StatCounter value={2400000} label="Units Tracked" color="#00D9FF" />
          <StatCounter value={18500} label="Batches Verified" color="#BF5FFF" />
          <StatCounter value={99} label="Fraud Blocked %" color="#39FF14" />
          <StatCounter value={340} label="Partners" color="#FF6B35" />
        </motion.div>

        {/* Role selection */}
        <div className="w-full max-w-5xl">
          <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.8 }}
            className="text-center text-gray-500 text-sm font-mono tracking-widest uppercase mb-6">
            — Select Your Role to Enter —
          </motion.p>

          <div className="grid grid-cols-5 gap-3">
            {roles.map((role, i) => (
              <motion.button
                key={role.id}
                initial={{ opacity: 0, y: 40, scale: 0.8 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ delay: 0.3 + i * 0.1, type: 'spring', stiffness: 200 }}
                whileHover={{ scale: 1.06, y: -6 }}
                whileTap={{ scale: 0.96 }}
                onClick={() => handleSelect(role.id)}
                onMouseEnter={() => setHoveredRole(role.id)}
                onMouseLeave={() => setHoveredRole(null)}
                className="relative flex flex-col items-center gap-3 p-5 rounded-2xl border transition-all duration-300 cursor-pointer overflow-hidden"
                style={{
                  background: hoveredRole === role.id || selectedRole === role.id
                    ? role.gradient : 'rgba(255,255,255,0.02)',
                  borderColor: hoveredRole === role.id || selectedRole === role.id
                    ? role.color + '60' : 'rgba(255,255,255,0.06)',
                  boxShadow: hoveredRole === role.id || selectedRole === role.id
                    ? `0 0 30px ${role.shadow}, 0 0 60px ${role.shadow}40, inset 0 1px 0 ${role.color}20`
                    : 'none',
                }}>

                {/* Animated corner accent */}
                {(hoveredRole === role.id || selectedRole === role.id) && (
                  <>
                    <div className="absolute top-0 left-0 w-6 h-6 border-t-2 border-l-2 rounded-tl-xl"
                      style={{ borderColor: role.color }} />
                    <div className="absolute bottom-0 right-0 w-6 h-6 border-b-2 border-r-2 rounded-br-xl"
                      style={{ borderColor: role.color }} />
                  </>
                )}

                {/* Scan line on hover */}
                {hoveredRole === role.id && (
                  <motion.div
                    initial={{ top: '-100%' }} animate={{ top: '200%' }}
                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                    className="absolute left-0 right-0 h-px opacity-40"
                    style={{ background: `linear-gradient(90deg, transparent, ${role.color}, transparent)` }} />
                )}

                {/* Icon */}
                <div className="relative">
                  <div className="text-4xl" style={{
                    filter: hoveredRole === role.id ? `drop-shadow(0 0 12px ${role.color})` : 'none',
                    transition: 'filter 0.3s',
                  }}>
                    {role.icon}
                  </div>
                  {(hoveredRole === role.id || selectedRole === role.id) && (
                    <motion.div
                      initial={{ scale: 0 }} animate={{ scale: 1 }}
                      className="absolute -inset-3 rounded-full opacity-20"
                      style={{ background: role.color }} />
                  )}
                </div>

                {/* Label */}
                <div className="text-center">
                  <p className="font-bold text-sm tracking-wide"
                    style={{ color: hoveredRole === role.id || selectedRole === role.id ? role.color : '#e5e7eb' }}>
                    {role.label}
                  </p>
                  <p className="text-gray-600 text-xs mt-1 leading-tight">{role.desc}</p>
                </div>

                {/* Enter indicator */}
                <AnimatePresence>
                  {(hoveredRole === role.id || selectedRole === role.id) && (
                    <motion.div
                      initial={{ opacity: 0, scale: 0.8 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.8 }}
                      className="text-xs font-mono px-3 py-1 rounded-full border"
                      style={{ color: role.color, borderColor: role.color + '50', background: role.color + '10' }}>
                      ENTER →
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.button>
            ))}
          </div>
        </div>

        {/* Footer */}
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.5 }}
          className="flex items-center gap-6 text-gray-700 text-xs font-mono">
          <span>v2.0.0</span>
          <span>·</span>
          <span className="text-cyan-900">AES-256 Encrypted</span>
          <span>·</span>
          <span className="text-cyan-900">Blockchain Verified</span>
          <span>·</span>
          <span className="text-cyan-900">AI Powered</span>
        </motion.div>
      </div>
    </div>
  )
}
