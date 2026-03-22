import { useRef, useEffect } from 'react'

export default function DashboardVisual({ role }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let W = canvas.width = window.innerWidth
    let H = canvas.height = window.innerHeight
    let t = 0, angle = 0, animId

    const ROLE_COLORS = {
      manufacturer: '#00D9FF', distributor: '#BF5FFF',
      retailer: '#39FF14', patient: '#FF6B35', regulator: '#FF2D78',
    }
    const C = ROLE_COLORS[role] || '#00D9FF'

    // ── Stars (300) ──────────────────────────────────────────────────────
    const stars = Array.from({ length: 300 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      r: Math.random() * 1.6 + 0.3,
      alpha: Math.random() * 0.6 + 0.1,
      tw: Math.random() * Math.PI * 2,
      spd: 0.008 + Math.random() * 0.025,
    }))

    // ── Nebula blobs (6 large) ───────────────────────────────────────────
    const nebulas = [
      { x: W * 0.15, y: H * 0.15, r: 320, color: C },
      { x: W * 0.85, y: H * 0.12, r: 280, color: '#BF5FFF' },
      { x: W * 0.08, y: H * 0.85, r: 300, color: '#39FF14' },
      { x: W * 0.92, y: H * 0.88, r: 310, color: '#FF2D78' },
      { x: W * 0.5,  y: H * 0.05, r: 260, color: '#FF6B35' },
      { x: W * 0.5,  y: H * 0.95, r: 260, color: C },
    ]

    // ── Hex grid ────────────────────────────────────────────────────────
    const HS = 36
    const hexes = []
    for (let r = -1; r < Math.ceil(H / (HS * 1.5)) + 2; r++) {
      for (let c = -1; c < Math.ceil(W / (HS * 1.75)) + 2; c++) {
        hexes.push({
          cx: c * HS * 1.75, cy: r * HS * 1.5 + (c % 2 === 0 ? 0 : HS * 0.75),
          ph: Math.random() * Math.PI * 2, spd: 0.003 + Math.random() * 0.007,
        })
      }
    }

    // ── Floating blockchain blocks (30) ──────────────────────────────────
    const blocks = Array.from({ length: 30 }, (_, i) => ({
      x: Math.random() * W, y: Math.random() * H,
      vx: (Math.random() - 0.5) * 0.4, vy: (Math.random() - 0.5) * 0.3,
      w: 40 + Math.random() * 40, h: 20 + Math.random() * 20,
      alpha: 0.12 + Math.random() * 0.18,
      rot: Math.random() * Math.PI * 2, rs: (Math.random() - 0.5) * 0.008,
      color: [C, '#BF5FFF', '#39FF14', '#FF2D78', '#FF6B35'][i % 5],
    }))

    // ── 3D supply chain nodes ────────────────────────────────────────────
    const NODES = [
      { id: 'manufacturer', label: '🏭', name: 'MFG',  bx: -220, by: -80,  bz: 60,   color: '#00D9FF' },
      { id: 'distributor',  label: '🚚', name: 'DIST', bx: -70,  by: 110,  bz: -80,  color: '#BF5FFF' },
      { id: 'retailer',     label: '🏪', name: 'RET',  bx: 130,  by: -110, bz: -50,  color: '#39FF14' },
      { id: 'patient',      label: '👤', name: 'PAT',  bx: 240,  by: 80,   bz: 80,   color: '#FF6B35' },
      { id: 'regulator',    label: '⚖️', name: 'REG', bx: 20,   by: -210, bz: -120, color: '#FF2D78' },
    ]
    const EDGES = [[0,1],[1,2],[2,3],[0,4],[1,4],[2,4],[3,4],[0,2],[1,3]]

    const packets = EDGES.map((e, i) => ({
      edge: i, progress: Math.random(), speed: 0.004 + Math.random() * 0.004,
      color: NODES[e[0]].color, size: 4 + Math.random() * 3,
    }))

    // ── Shockwave rings ──────────────────────────────────────────────────
    const shockwaves = Array.from({ length: 4 }, (_, i) => ({
      x: W / 2, y: H / 2, r: i * 120, maxR: 500,
      speed: 1.2 + i * 0.4, alpha: 0.0,
      color: [C, '#BF5FFF', '#39FF14', '#FF2D78'][i],
      phase: i * 0.8,
    }))

    // ── Particle stream (150) ────────────────────────────────────────────
    const stream = Array.from({ length: 150 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      vx: (Math.random() - 0.5) * 0.6, vy: -0.3 - Math.random() * 0.5,
      r: Math.random() * 2 + 0.5,
      alpha: Math.random() * 0.5 + 0.1,
      color: [C, '#BF5FFF', '#39FF14', '#FF2D78'][Math.floor(Math.random() * 4)],
    }))

    // ── Wireframe cubes (8) ──────────────────────────────────────────────
    const cubes = Array.from({ length: 8 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      size: 18 + Math.random() * 28,
      rx: Math.random() * Math.PI * 2, ry: Math.random() * Math.PI * 2,
      drx: (Math.random() - 0.5) * 0.012, dry: (Math.random() - 0.5) * 0.015,
      vx: (Math.random() - 0.5) * 0.25, vy: (Math.random() - 0.5) * 0.2,
      color: [C, '#BF5FFF', '#39FF14', '#FF2D78'][Math.floor(Math.random() * 4)],
      alpha: 0.15 + Math.random() * 0.2,
    }))

    const rotY = (x, y, z, a) => ({ x: x * Math.cos(a) + z * Math.sin(a), y, z: -x * Math.sin(a) + z * Math.cos(a) })
    const rotX = (x, y, z, a) => ({ x, y: y * Math.cos(a) - z * Math.sin(a), z: y * Math.sin(a) + z * Math.cos(a) })
    const proj = (x, y, z, ox, oy, fov = 600) => {
      const s = fov / (fov + z + 280)
      return { sx: x * s + ox, sy: y * s + oy, scale: s }
    }

    const drawCube = (cx, cy, size, rx, ry, color, alpha) => {
      const verts = [[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]]
      const edges = [[0,1],[1,2],[2,3],[3,0],[4,5],[5,6],[6,7],[7,4],[0,4],[1,5],[2,6],[3,7]]
      const projected = verts.map(([x, y, z]) => {
        let p = { x: x * size, y: y * size, z: z * size }
        let r = rotY(p.x, p.y, p.z, ry)
        r = rotX(r.x, r.y, r.z, rx)
        return { sx: r.x + cx, sy: r.y + cy }
      })
      ctx.save(); ctx.globalAlpha = alpha; ctx.strokeStyle = color; ctx.lineWidth = 0.8
      edges.forEach(([a, b]) => {
        ctx.beginPath(); ctx.moveTo(projected[a].sx, projected[a].sy)
        ctx.lineTo(projected[b].sx, projected[b].sy); ctx.stroke()
      })
      ctx.restore()
    }

    const draw = () => {
      ctx.clearRect(0, 0, W, H)
      t += 0.016; angle += 0.005

      // ── Nebula blobs ────────────────────────────────────────────────────
      nebulas.forEach(n => {
        const g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r)
        g.addColorStop(0, n.color + '44')
        g.addColorStop(0.4, n.color + '18')
        g.addColorStop(1, n.color + '00')
        ctx.beginPath(); ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2)
        ctx.fillStyle = g; ctx.fill()
      })

      // ── Stars ────────────────────────────────────────────────────────────
      stars.forEach(s => {
        s.tw += s.spd
        const a = s.alpha * (0.3 + 0.7 * Math.abs(Math.sin(s.tw)))
        ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${a})`; ctx.fill()
      })

      // ── Hex grid ─────────────────────────────────────────────────────────
      hexes.forEach(h => {
        h.ph += h.spd
        const pulse = 0.5 + 0.5 * Math.sin(h.ph)
        ctx.save(); ctx.globalAlpha = 0.03 + pulse * 0.055
        ctx.strokeStyle = C; ctx.lineWidth = 0.6
        ctx.beginPath()
        for (let i = 0; i < 6; i++) {
          const a = (Math.PI / 3) * i - Math.PI / 6
          i === 0
            ? ctx.moveTo(h.cx + HS * Math.cos(a), h.cy + HS * Math.sin(a))
            : ctx.lineTo(h.cx + HS * Math.cos(a), h.cy + HS * Math.sin(a))
        }
        ctx.closePath(); ctx.stroke(); ctx.restore()
      })

      // ── Particle stream ──────────────────────────────────────────────────
      stream.forEach(p => {
        p.x += p.vx; p.y += p.vy
        if (p.y < -5) { p.y = H + 5; p.x = Math.random() * W }
        if (p.x < 0) p.x = W; if (p.x > W) p.x = 0
        ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fillStyle = p.color + Math.floor(p.alpha * 255).toString(16).padStart(2,'0')
        ctx.fill()
      })

      // ── Wireframe cubes ──────────────────────────────────────────────────
      cubes.forEach(c => {
        c.rx += c.drx; c.ry += c.dry
        c.x += c.vx; c.y += c.vy
        if (c.x < -80) c.x = W + 80; if (c.x > W + 80) c.x = -80
        if (c.y < -80) c.y = H + 80; if (c.y > H + 80) c.y = -80
        drawCube(c.x, c.y, c.size, c.rx, c.ry, c.color, c.alpha)
      })

      // ── Floating blockchain blocks ────────────────────────────────────────
      blocks.forEach(b => {
        b.x += b.vx; b.y += b.vy; b.rot += b.rs
        if (b.x < -80) b.x = W + 80; if (b.x > W + 80) b.x = -80
        if (b.y < -50) b.y = H + 50; if (b.y > H + 50) b.y = -50
        ctx.save(); ctx.translate(b.x, b.y); ctx.rotate(b.rot)
        ctx.globalAlpha = b.alpha; ctx.strokeStyle = b.color; ctx.lineWidth = 1
        ctx.strokeRect(-b.w / 2, -b.h / 2, b.w, b.h)
        ctx.lineWidth = 0.4
        ctx.beginPath()
        ctx.moveTo(-b.w / 2 + 5, 0); ctx.lineTo(b.w / 2 - 5, 0)
        ctx.moveTo(0, -b.h / 2 + 4); ctx.lineTo(0, b.h / 2 - 4)
        ctx.stroke(); ctx.restore()
      })

      // ── Shockwave rings ──────────────────────────────────────────────────
      shockwaves.forEach(sw => {
        sw.r += sw.speed
        if (sw.r > sw.maxR) { sw.r = 0; sw.alpha = 0.5 }
        sw.alpha = Math.max(0, sw.alpha - 0.004)
        if (sw.alpha > 0) {
          ctx.beginPath(); ctx.arc(W / 2, H / 2, sw.r, 0, Math.PI * 2)
          ctx.strokeStyle = sw.color + Math.floor(sw.alpha * 255).toString(16).padStart(2,'0')
          ctx.lineWidth = 1.5; ctx.stroke()
        }
      })
      // Trigger new shockwaves periodically
      if (Math.floor(t * 60) % 180 === 0) {
        const sw = shockwaves[Math.floor(Math.random() * shockwaves.length)]
        sw.r = 0; sw.alpha = 0.6
      }

      // ── DNA Helix — right edge ────────────────────────────────────────────
      {
        const DX = W - 70, DY0 = 40, DH = H * 0.6, DS = 28, AMP = 28
        const ph = t * 1.0
        const freq = Math.PI * 2 / DS * 3.5
        for (let i = 0; i < DS - 1; i++) {
          const y1 = DY0 + (i / (DS - 1)) * DH
          const y2 = DY0 + ((i + 1) / (DS - 1)) * DH
          const x1a = DX + Math.sin(freq * i + ph) * AMP
          const x1b = DX + Math.sin(freq * (i + 1) + ph) * AMP
          const x2a = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const x2b = DX + Math.sin(freq * (i + 1) + ph + Math.PI) * AMP
          ctx.beginPath(); ctx.moveTo(x1a, y1); ctx.lineTo(x1b, y2)
          ctx.strokeStyle = C + '88'; ctx.lineWidth = 1.2; ctx.stroke()
          ctx.beginPath(); ctx.moveTo(x2a, y1); ctx.lineTo(x2b, y2)
          ctx.strokeStyle = '#BF5FFF88'; ctx.lineWidth = 1.2; ctx.stroke()
        }
        for (let i = 0; i < DS; i++) {
          const y = DY0 + (i / (DS - 1)) * DH
          const x1 = DX + Math.sin(freq * i + ph) * AMP
          const x2 = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const a1 = 0.5 + 0.5 * Math.abs(Math.sin(freq * i + ph))
          ctx.beginPath(); ctx.arc(x1, y, 3.5, 0, Math.PI * 2)
          ctx.fillStyle = C + Math.floor(a1 * 220 + 35).toString(16).padStart(2,'0'); ctx.fill()
          const a2 = 0.5 + 0.5 * Math.abs(Math.sin(freq * i + ph + Math.PI))
          ctx.beginPath(); ctx.arc(x2, y, 3.5, 0, Math.PI * 2)
          ctx.fillStyle = '#BF5FFF' + Math.floor(a2 * 220 + 35).toString(16).padStart(2,'0'); ctx.fill()
          if (i % 2 === 0) {
            ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y)
            ctx.strokeStyle = 'rgba(255,255,255,0.12)'; ctx.lineWidth = 0.8; ctx.stroke()
          }
        }
      }

      // ── DNA Helix — left edge ─────────────────────────────────────────────
      {
        const DX = 70, DY0 = H * 0.3, DH = H * 0.6, DS = 28, AMP = 28
        const ph = t * 0.8 + 1.5
        const freq = Math.PI * 2 / DS * 3.5
        for (let i = 0; i < DS - 1; i++) {
          const y1 = DY0 + (i / (DS - 1)) * DH
          const y2 = DY0 + ((i + 1) / (DS - 1)) * DH
          const x1a = DX + Math.sin(freq * i + ph) * AMP
          const x1b = DX + Math.sin(freq * (i + 1) + ph) * AMP
          const x2a = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const x2b = DX + Math.sin(freq * (i + 1) + ph + Math.PI) * AMP
          ctx.beginPath(); ctx.moveTo(x1a, y1); ctx.lineTo(x1b, y2)
          ctx.strokeStyle = '#FF2D7888'; ctx.lineWidth = 1.2; ctx.stroke()
          ctx.beginPath(); ctx.moveTo(x2a, y1); ctx.lineTo(x2b, y2)
          ctx.strokeStyle = '#39FF1488'; ctx.lineWidth = 1.2; ctx.stroke()
        }
        for (let i = 0; i < DS; i++) {
          const y = DY0 + (i / (DS - 1)) * DH
          const x1 = DX + Math.sin(freq * i + ph) * AMP
          const x2 = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const a1 = 0.5 + 0.5 * Math.abs(Math.sin(freq * i + ph))
          ctx.beginPath(); ctx.arc(x1, y, 3.5, 0, Math.PI * 2)
          ctx.fillStyle = '#FF2D78' + Math.floor(a1 * 200 + 55).toString(16).padStart(2,'0'); ctx.fill()
          const a2 = 0.5 + 0.5 * Math.abs(Math.sin(freq * i + ph + Math.PI))
          ctx.beginPath(); ctx.arc(x2, y, 3.5, 0, Math.PI * 2)
          ctx.fillStyle = '#39FF14' + Math.floor(a2 * 200 + 55).toString(16).padStart(2,'0'); ctx.fill()
          if (i % 2 === 0) {
            ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y)
            ctx.strokeStyle = 'rgba(255,255,255,0.1)'; ctx.lineWidth = 0.8; ctx.stroke()
          }
        }
      }

      // ── Spinning core reactor — center ────────────────────────────────────
      {
        const cx = W / 2, cy = H / 2
        // Outer glow
        const outerGlow = ctx.createRadialGradient(cx, cy, 0, cx, cy, 180)
        outerGlow.addColorStop(0, C + '22')
        outerGlow.addColorStop(0.5, C + '0a')
        outerGlow.addColorStop(1, C + '00')
        ctx.beginPath(); ctx.arc(cx, cy, 180, 0, Math.PI * 2)
        ctx.fillStyle = outerGlow; ctx.fill()

        // Spinning rings
        for (let ri = 0; ri < 5; ri++) {
          const r = 55 + ri * 28
          const startA = t * (0.5 + ri * 0.18) * (ri % 2 === 0 ? 1 : -1)
          const arcLen = Math.PI * (0.6 + ri * 0.15)
          ctx.beginPath()
          ctx.arc(cx, cy, r, startA, startA + arcLen)
          const colors = [C, '#BF5FFF', '#39FF14', '#FF2D78', '#FF6B35']
          ctx.strokeStyle = colors[ri % 5] + 'cc'
          ctx.lineWidth = 1.5 + (ri === 0 ? 1 : 0); ctx.stroke()
          // Dot at arc end
          const dotX = cx + r * Math.cos(startA + arcLen)
          const dotY = cy + r * Math.sin(startA + arcLen)
          ctx.beginPath(); ctx.arc(dotX, dotY, 3.5, 0, Math.PI * 2)
          ctx.fillStyle = colors[ri % 5]; ctx.fill()
          // Glow on dot
          const dg = ctx.createRadialGradient(dotX, dotY, 0, dotX, dotY, 10)
          dg.addColorStop(0, colors[ri % 5] + '88')
          dg.addColorStop(1, colors[ri % 5] + '00')
          ctx.beginPath(); ctx.arc(dotX, dotY, 10, 0, Math.PI * 2)
          ctx.fillStyle = dg; ctx.fill()
        }

        // Core pulse
        const pulse = 0.85 + 0.15 * Math.sin(t * 3.5)
        const cg = ctx.createRadialGradient(cx, cy, 0, cx, cy, 28 * pulse)
        cg.addColorStop(0, C + 'ff')
        cg.addColorStop(0.4, C + '88')
        cg.addColorStop(1, C + '00')
        ctx.beginPath(); ctx.arc(cx, cy, 28 * pulse, 0, Math.PI * 2)
        ctx.fillStyle = cg; ctx.fill()
        ctx.beginPath(); ctx.arc(cx, cy, 10 * pulse, 0, Math.PI * 2)
        ctx.fillStyle = '#ffffff'; ctx.fill()
      }

      // ── 3D Supply Chain Network ───────────────────────────────────────────
      const cx = W / 2, cy = H / 2
      const rotated = NODES.map(n => {
        let r = rotY(n.bx, n.by, n.bz, angle)
        r = rotX(r.x, r.y, r.z, 0.25)
        return { ...r, color: n.color, label: n.label, name: n.name, id: n.id }
      })

      // Edges with thick glowing lines
      EDGES.forEach((e, ei) => {
        const a = proj(rotated[e[0]].x, rotated[e[0]].y, rotated[e[0]].z, cx, cy)
        const b = proj(rotated[e[1]].x, rotated[e[1]].y, rotated[e[1]].z, cx, cy)
        const g = ctx.createLinearGradient(a.sx, a.sy, b.sx, b.sy)
        g.addColorStop(0, rotated[e[0]].color + '88')
        g.addColorStop(1, rotated[e[1]].color + '88')
        ctx.beginPath(); ctx.moveTo(a.sx, a.sy); ctx.lineTo(b.sx, b.sy)
        ctx.strokeStyle = g; ctx.lineWidth = 1.5; ctx.stroke()
        // Moving dots on edges
        for (let d = 0; d < 5; d++) {
          const p = ((t * 0.3 + d / 5 + ei * 0.13) % 1)
          const px = a.sx + (b.sx - a.sx) * p
          const py = a.sy + (b.sy - a.sy) * p
          ctx.beginPath(); ctx.arc(px, py, 2, 0, Math.PI * 2)
          ctx.fillStyle = rotated[e[0]].color + 'dd'; ctx.fill()
        }
      })

      // Data packets
      packets.forEach(pkt => {
        pkt.progress += pkt.speed
        if (pkt.progress > 1) pkt.progress = 0
        const e = EDGES[pkt.edge]
        const a = proj(rotated[e[0]].x, rotated[e[0]].y, rotated[e[0]].z, cx, cy)
        const b = proj(rotated[e[1]].x, rotated[e[1]].y, rotated[e[1]].z, cx, cy)
        const px = a.sx + (b.sx - a.sx) * pkt.progress
        const py = a.sy + (b.sy - a.sy) * pkt.progress
        const grd = ctx.createRadialGradient(px, py, 0, px, py, pkt.size * 4)
        grd.addColorStop(0, pkt.color + 'ff'); grd.addColorStop(1, pkt.color + '00')
        ctx.beginPath(); ctx.arc(px, py, pkt.size * 4, 0, Math.PI * 2)
        ctx.fillStyle = grd; ctx.fill()
        ctx.beginPath(); ctx.arc(px, py, pkt.size, 0, Math.PI * 2)
        ctx.fillStyle = pkt.color; ctx.fill()
      })

      // Nodes — large, glowing, dramatic
      const sorted = [...rotated].sort((a, b) => a.z - b.z)
      sorted.forEach(n => {
        const { sx, sy, scale } = proj(n.x, n.y, n.z, cx, cy)
        const isActive = n.id === role
        const pulse = 0.88 + 0.12 * Math.sin(t * 2.5)
        const r = scale * (isActive ? 42 : 30) * pulse

        // Big outer glow
        const outerG = ctx.createRadialGradient(sx, sy, 0, sx, sy, r * 5)
        outerG.addColorStop(0, n.color + (isActive ? '55' : '33'))
        outerG.addColorStop(1, n.color + '00')
        ctx.beginPath(); ctx.arc(sx, sy, r * 5, 0, Math.PI * 2)
        ctx.fillStyle = outerG; ctx.fill()

        // Mid glow
        const midG = ctx.createRadialGradient(sx, sy, 0, sx, sy, r * 2.5)
        midG.addColorStop(0, n.color + (isActive ? 'aa' : '66'))
        midG.addColorStop(1, n.color + '00')
        ctx.beginPath(); ctx.arc(sx, sy, r * 2.5, 0, Math.PI * 2)
        ctx.fillStyle = midG; ctx.fill()

        // Node body
        const ng = ctx.createRadialGradient(sx, sy, 0, sx, sy, r)
        ng.addColorStop(0, n.color + (isActive ? 'ff' : 'cc'))
        ng.addColorStop(0.6, n.color + (isActive ? 'aa' : '66'))
        ng.addColorStop(1, n.color + '22')
        ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2)
        ctx.fillStyle = ng; ctx.fill()
        ctx.strokeStyle = n.color + (isActive ? 'ff' : 'aa')
        ctx.lineWidth = isActive ? 3 : 1.8; ctx.stroke()

        // Emoji
        ctx.font = `${Math.floor(r * 0.9)}px serif`
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
        ctx.globalAlpha = 0.95; ctx.fillText(n.label, sx, sy); ctx.globalAlpha = 1

        // Name label
        ctx.font = `bold ${Math.floor(scale * 13)}px Inter, sans-serif`
        ctx.fillStyle = n.color
        ctx.shadowColor = n.color; ctx.shadowBlur = 8
        ctx.fillText(n.name, sx, sy + r + 16)
        ctx.shadowBlur = 0

        // Active role: extra orbit rings
        if (isActive) {
          for (let ri = 1; ri <= 3; ri++) {
            ctx.beginPath()
            ctx.arc(sx, sy, r * (1.6 + ri * 0.5) + 5 * Math.sin(t * 3 + ri), 0, Math.PI * 2)
            ctx.strokeStyle = n.color + ['88', '55', '33'][ri - 1]
            ctx.lineWidth = [2, 1.2, 0.8][ri - 1]; ctx.stroke()
          }
        }
      })

      // ── Orbit rings — top-left corner ─────────────────────────────────────
      {
        const ox = 90, oy = 90
        for (let ri = 0; ri < 4; ri++) {
          const r = 40 + ri * 26
          const startA = t * (0.45 + ri * 0.15)
          ctx.beginPath(); ctx.arc(ox, oy, r, startA, startA + Math.PI * 1.5)
          ctx.strokeStyle = [C, '#BF5FFF', '#39FF14', '#FF2D78'][ri] + '77'
          ctx.lineWidth = 1.2; ctx.stroke()
          const dotX = ox + r * Math.cos(startA + Math.PI * 1.5)
          const dotY = oy + r * Math.sin(startA + Math.PI * 1.5)
          ctx.beginPath(); ctx.arc(dotX, dotY, 4, 0, Math.PI * 2)
          ctx.fillStyle = [C, '#BF5FFF', '#39FF14', '#FF2D78'][ri]; ctx.fill()
        }
        ctx.font = 'bold 11px JetBrains Mono, monospace'
        ctx.fillStyle = C + 'cc'; ctx.textAlign = 'left'
        ctx.shadowColor = C; ctx.shadowBlur = 6
        ctx.fillText('◈ PHARMATRUST', ox - 35, oy + 115)
        ctx.shadowBlur = 0
      }

      // ── Orbit rings — bottom-right corner ─────────────────────────────────
      {
        const ox = W - 90, oy = H - 90
        for (let ri = 0; ri < 4; ri++) {
          const r = 40 + ri * 26
          const startA = -t * (0.4 + ri * 0.13) + Math.PI
          ctx.beginPath(); ctx.arc(ox, oy, r, startA, startA + Math.PI * 1.6)
          ctx.strokeStyle = ['#FF2D78', '#FF6B35', C, '#BF5FFF'][ri] + '77'
          ctx.lineWidth = 1.2; ctx.stroke()
          const dotX = ox + r * Math.cos(startA + Math.PI * 1.6)
          const dotY = oy + r * Math.sin(startA + Math.PI * 1.6)
          ctx.beginPath(); ctx.arc(dotX, dotY, 4, 0, Math.PI * 2)
          ctx.fillStyle = ['#FF2D78', '#FF6B35', C, '#BF5FFF'][ri]; ctx.fill()
        }
        ctx.font = 'bold 11px JetBrains Mono, monospace'
        ctx.fillStyle = '#FF2D78cc'; ctx.textAlign = 'right'
        ctx.shadowColor = '#FF2D78'; ctx.shadowBlur = 6
        ctx.fillText('AI SECURED ✓', ox + 35, oy - 115)
        ctx.shadowBlur = 0
      }

      // ── Scanline sweep ────────────────────────────────────────────────────
      const scanY = (t * 60) % H
      const sg = ctx.createLinearGradient(0, scanY - 40, 0, scanY + 40)
      sg.addColorStop(0, 'rgba(0,217,255,0)')
      sg.addColorStop(0.5, 'rgba(0,217,255,0.03)')
      sg.addColorStop(1, 'rgba(0,217,255,0)')
      ctx.fillStyle = sg; ctx.fillRect(0, scanY - 40, W, 80)

      // ── HUD panel — bottom-left ───────────────────────────────────────────
      const hx = 16, hy = H - 140
      ctx.save(); ctx.globalAlpha = 0.9
      ctx.fillStyle = 'rgba(5,8,22,0.92)'
      ctx.strokeStyle = C + '77'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.roundRect(hx, hy, 185, 118, 10)
      ctx.fill(); ctx.stroke(); ctx.globalAlpha = 1
      ctx.font = 'bold 10px JetBrains Mono, monospace'
      ctx.fillStyle = C; ctx.textAlign = 'left'
      ctx.shadowColor = C; ctx.shadowBlur = 5
      ctx.fillText('◈ PHARMATRUST LIVE', hx + 12, hy + 20)
      ctx.shadowBlur = 0
      ctx.font = '9px JetBrains Mono, monospace'
      ctx.fillStyle = 'rgba(255,255,255,0.55)'
      ;['NODES  : 5 ACTIVE','PACKETS: 9 FLOWING','BLOCKS : 30 VERIFIED','CHAIN  : SECURED ✓','AI     : MONITORING','UPTIME : 99.99%']
        .forEach((s, i) => ctx.fillText(s, hx + 12, hy + 38 + i * 14))
      ctx.restore()

      animId = requestAnimationFrame(draw)
    }

    draw()

    const onResize = () => {
      W = canvas.width = window.innerWidth
      H = canvas.height = window.innerHeight
      nebulas[0].x = W * 0.15; nebulas[0].y = H * 0.15
      nebulas[1].x = W * 0.85; nebulas[1].y = H * 0.12
      nebulas[2].x = W * 0.08; nebulas[2].y = H * 0.85
      nebulas[3].x = W * 0.92; nebulas[3].y = H * 0.88
      nebulas[4].x = W * 0.5;  nebulas[4].y = H * 0.05
      nebulas[5].x = W * 0.5;  nebulas[5].y = H * 0.95
    }
    window.addEventListener('resize', onResize)
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', onResize) }
  }, [role])

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'fixed', top: 0, left: 0,
        width: '100vw', height: '100vh',
        pointerEvents: 'none', zIndex: 0,
        opacity: 1,
      }}
    />
  )
}
