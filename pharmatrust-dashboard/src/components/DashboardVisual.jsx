import { useRef, useEffect } from 'react'

// Strategy: canvas is fixed full-screen, uses window dimensions directly.
// Draws animated elements in all 4 corners + full background so it's always visible.

export default function DashboardVisual({ role }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')

    // Always use window dimensions — not offsetWidth which can be 0
    let W = canvas.width = window.innerWidth
    let H = canvas.height = window.innerHeight
    let t = 0
    let animId

    const ROLE_COLORS = {
      manufacturer: '#00D9FF',
      distributor:  '#BF5FFF',
      retailer:     '#39FF14',
      patient:      '#FF6B35',
      regulator:    '#FF2D78',
    }
    const C = ROLE_COLORS[role] || '#00D9FF'

    // ── Stars ──────────────────────────────────────────────────────────────
    const stars = Array.from({ length: 180 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      r: Math.random() * 1.1 + 0.2,
      alpha: Math.random() * 0.45 + 0.08,
      tw: Math.random() * Math.PI * 2,
      spd: 0.01 + Math.random() * 0.02,
    }))

    // ── Nebula blobs ───────────────────────────────────────────────────────
    const nebulas = [
      { x: W * 0.08,  y: H * 0.12,  r: 200, color: C },
      { x: W * 0.92,  y: H * 0.1,   r: 180, color: '#BF5FFF' },
      { x: W * 0.05,  y: H * 0.88,  r: 190, color: '#39FF14' },
      { x: W * 0.94,  y: H * 0.9,   r: 200, color: '#FF2D78' },
    ]

    // ── Hex grid (full screen, very subtle) ───────────────────────────────
    const HS = 30
    const hexes = []
    for (let r = -1; r < Math.ceil(H / (HS * 1.5)) + 2; r++) {
      for (let c = -1; c < Math.ceil(W / (HS * 1.75)) + 2; c++) {
        hexes.push({
          cx: c * HS * 1.75,
          cy: r * HS * 1.5 + (c % 2 === 0 ? 0 : HS * 0.75),
          ph: Math.random() * Math.PI * 2,
          spd: 0.004 + Math.random() * 0.008,
        })
      }
    }

    // ── Floating blockchain blocks ─────────────────────────────────────────
    const blocks = Array.from({ length: 20 }, (_, i) => ({
      x: Math.random() * W, y: Math.random() * H,
      vx: (Math.random() - 0.5) * 0.3, vy: (Math.random() - 0.5) * 0.2,
      w: 32 + Math.random() * 30, h: 16 + Math.random() * 14,
      alpha: 0.07 + Math.random() * 0.13,
      rot: Math.random() * Math.PI * 2, rs: (Math.random() - 0.5) * 0.007,
      color: [C, '#BF5FFF', '#39FF14', '#FF2D78'][i % 4],
    }))

    // ── 3D supply chain nodes ──────────────────────────────────────────────
    const NODES = [
      { id: 'manufacturer', label: '🏭', name: 'MFG',  bx: -160, by: -55,  bz: 40,   color: '#00D9FF' },
      { id: 'distributor',  label: '🚚', name: 'DIST', bx: -50,  by: 75,   bz: -55,  color: '#BF5FFF' },
      { id: 'retailer',     label: '🏪', name: 'RET',  bx: 90,   by: -75,  bz: -35,  color: '#39FF14' },
      { id: 'patient',      label: '👤', name: 'PAT',  bx: 175,  by: 55,   bz: 55,   color: '#FF6B35' },
      { id: 'regulator',    label: '⚖️', name: 'REG', bx: 15,   by: -150, bz: -90,  color: '#FF2D78' },
    ]
    const EDGES = [[0,1],[1,2],[2,3],[0,4],[1,4],[2,4],[3,4]]

    const packets = EDGES.map((e, i) => ({
      edge: i, progress: Math.random(), speed: 0.003 + Math.random() * 0.003,
      color: NODES[e[0]].color, size: 3 + Math.random() * 2,
    }))

    // ── DNA helix — top-right corner ───────────────────────────────────────
    // ── Corner orbit rings ─────────────────────────────────────────────────

    const rotY = (x, y, z, a) => ({ x: x * Math.cos(a) + z * Math.sin(a), y, z: -x * Math.sin(a) + z * Math.cos(a) })
    const rotX = (x, y, z, a) => ({ x, y: y * Math.cos(a) - z * Math.sin(a), z: y * Math.sin(a) + z * Math.cos(a) })
    const proj = (x, y, z, ox, oy, fov = 480) => {
      const s = fov / (fov + z + 220)
      return { sx: x * s + ox, sy: y * s + oy, scale: s }
    }

    let angle = 0

    const draw = () => {
      ctx.clearRect(0, 0, W, H)
      t += 0.016
      angle += 0.004

      // ── Background nebula blobs ──────────────────────────────────────────
      nebulas.forEach(n => {
        const g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r)
        g.addColorStop(0, n.color + '22')
        g.addColorStop(0.5, n.color + '0a')
        g.addColorStop(1, n.color + '00')
        ctx.beginPath(); ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2)
        ctx.fillStyle = g; ctx.fill()
      })

      // ── Stars ────────────────────────────────────────────────────────────
      stars.forEach(s => {
        s.tw += s.spd
        const a = s.alpha * (0.4 + 0.6 * Math.abs(Math.sin(s.tw)))
        ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${a})`; ctx.fill()
      })

      // ── Hex grid ─────────────────────────────────────────────────────────
      hexes.forEach(h => {
        h.ph += h.spd
        const pulse = 0.5 + 0.5 * Math.sin(h.ph)
        ctx.save()
        ctx.globalAlpha = 0.018 + pulse * 0.032
        ctx.strokeStyle = C; ctx.lineWidth = 0.5
        ctx.beginPath()
        for (let i = 0; i < 6; i++) {
          const a = (Math.PI / 3) * i - Math.PI / 6
          i === 0
            ? ctx.moveTo(h.cx + HS * Math.cos(a), h.cy + HS * Math.sin(a))
            : ctx.lineTo(h.cx + HS * Math.cos(a), h.cy + HS * Math.sin(a))
        }
        ctx.closePath(); ctx.stroke(); ctx.restore()
      })

      // ── Floating blockchain blocks ────────────────────────────────────────
      blocks.forEach(b => {
        b.x += b.vx; b.y += b.vy; b.rot += b.rs
        if (b.x < -60) b.x = W + 60
        if (b.x > W + 60) b.x = -60
        if (b.y < -40) b.y = H + 40
        if (b.y > H + 40) b.y = -40
        ctx.save()
        ctx.translate(b.x, b.y); ctx.rotate(b.rot)
        ctx.globalAlpha = b.alpha
        ctx.strokeStyle = b.color; ctx.lineWidth = 0.8
        ctx.strokeRect(-b.w / 2, -b.h / 2, b.w, b.h)
        ctx.lineWidth = 0.35
        ctx.beginPath()
        ctx.moveTo(-b.w / 2 + 4, 0); ctx.lineTo(b.w / 2 - 4, 0)
        ctx.moveTo(0, -b.h / 2 + 3); ctx.lineTo(0, b.h / 2 - 3)
        ctx.stroke(); ctx.restore()
      })

      // ── DNA Helix — top-right corner ──────────────────────────────────────
      {
        const DX = W - 55, DY0 = 60, DH = H * 0.55, DS = 20, AMP = 20
        const ph = t * 0.9
        const freq = Math.PI * 2 / DS * 3
        for (let i = 0; i < DS; i++) {
          const y = DY0 + (i / (DS - 1)) * DH
          const x1 = DX + Math.sin(freq * i + ph) * AMP
          const x2 = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const a1 = 0.4 + 0.6 * Math.abs(Math.sin(freq * i + ph))
          ctx.beginPath(); ctx.arc(x1, y, 2.5, 0, Math.PI * 2)
          ctx.fillStyle = C + Math.floor(a1 * 220 + 35).toString(16).padStart(2,'0'); ctx.fill()
          const a2 = 0.4 + 0.6 * Math.abs(Math.sin(freq * i + ph + Math.PI))
          ctx.beginPath(); ctx.arc(x2, y, 2.5, 0, Math.PI * 2)
          ctx.fillStyle = '#BF5FFF' + Math.floor(a2 * 220 + 35).toString(16).padStart(2,'0'); ctx.fill()
          if (i % 2 === 0) {
            ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y)
            ctx.strokeStyle = 'rgba(255,255,255,0.07)'; ctx.lineWidth = 0.7; ctx.stroke()
          }
        }
        for (let i = 0; i < DS - 1; i++) {
          const y1 = DY0 + (i / (DS - 1)) * DH
          const y2 = DY0 + ((i + 1) / (DS - 1)) * DH
          const x1a = DX + Math.sin(freq * i + ph) * AMP
          const x1b = DX + Math.sin(freq * (i + 1) + ph) * AMP
          const x2a = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const x2b = DX + Math.sin(freq * (i + 1) + ph + Math.PI) * AMP
          ctx.beginPath(); ctx.moveTo(x1a, y1); ctx.lineTo(x1b, y2)
          ctx.strokeStyle = C + '44'; ctx.lineWidth = 0.9; ctx.stroke()
          ctx.beginPath(); ctx.moveTo(x2a, y1); ctx.lineTo(x2b, y2)
          ctx.strokeStyle = '#BF5FFF44'; ctx.lineWidth = 0.9; ctx.stroke()
        }
      }

      // ── DNA Helix — bottom-left corner ────────────────────────────────────
      {
        const DX = 55, DY0 = H * 0.35, DH = H * 0.55, DS = 20, AMP = 20
        const ph = t * 0.7 + 1.5
        const freq = Math.PI * 2 / DS * 3
        for (let i = 0; i < DS; i++) {
          const y = DY0 + (i / (DS - 1)) * DH
          const x1 = DX + Math.sin(freq * i + ph) * AMP
          const x2 = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const a1 = 0.4 + 0.6 * Math.abs(Math.sin(freq * i + ph))
          ctx.beginPath(); ctx.arc(x1, y, 2.5, 0, Math.PI * 2)
          ctx.fillStyle = '#FF2D78' + Math.floor(a1 * 200 + 55).toString(16).padStart(2,'0'); ctx.fill()
          const a2 = 0.4 + 0.6 * Math.abs(Math.sin(freq * i + ph + Math.PI))
          ctx.beginPath(); ctx.arc(x2, y, 2.5, 0, Math.PI * 2)
          ctx.fillStyle = '#39FF14' + Math.floor(a2 * 200 + 55).toString(16).padStart(2,'0'); ctx.fill()
          if (i % 2 === 0) {
            ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y)
            ctx.strokeStyle = 'rgba(255,255,255,0.06)'; ctx.lineWidth = 0.7; ctx.stroke()
          }
        }
        for (let i = 0; i < DS - 1; i++) {
          const y1 = DY0 + (i / (DS - 1)) * DH
          const y2 = DY0 + ((i + 1) / (DS - 1)) * DH
          const x1a = DX + Math.sin(freq * i + ph) * AMP
          const x1b = DX + Math.sin(freq * (i + 1) + ph) * AMP
          const x2a = DX + Math.sin(freq * i + ph + Math.PI) * AMP
          const x2b = DX + Math.sin(freq * (i + 1) + ph + Math.PI) * AMP
          ctx.beginPath(); ctx.moveTo(x1a, y1); ctx.lineTo(x1b, y2)
          ctx.strokeStyle = '#FF2D7844'; ctx.lineWidth = 0.9; ctx.stroke()
          ctx.beginPath(); ctx.moveTo(x2a, y1); ctx.lineTo(x2b, y2)
          ctx.strokeStyle = '#39FF1444'; ctx.lineWidth = 0.9; ctx.stroke()
        }
      }

      // ── Orbit rings — top-left corner ─────────────────────────────────────
      {
        const ox = 80, oy = 80
        for (let ri = 0; ri < 3; ri++) {
          const r = 35 + ri * 22
          const startA = t * (0.4 + ri * 0.15)
          ctx.beginPath()
          ctx.arc(ox, oy, r, startA, startA + Math.PI * 1.4)
          ctx.strokeStyle = [C, '#BF5FFF', '#39FF14'][ri] + '55'
          ctx.lineWidth = 1; ctx.stroke()
          // dot on orbit
          const dotX = ox + r * Math.cos(startA + Math.PI * 1.4)
          const dotY = oy + r * Math.sin(startA + Math.PI * 1.4)
          ctx.beginPath(); ctx.arc(dotX, dotY, 3, 0, Math.PI * 2)
          ctx.fillStyle = [C, '#BF5FFF', '#39FF14'][ri]; ctx.fill()
        }
        ctx.font = 'bold 10px JetBrains Mono, monospace'
        ctx.fillStyle = C + 'aa'; ctx.textAlign = 'left'
        ctx.fillText('◈ PHARMATRUST', ox - 30, oy + 90)
      }

      // ── Orbit rings — bottom-right corner ─────────────────────────────────
      {
        const ox = W - 80, oy = H - 80
        for (let ri = 0; ri < 3; ri++) {
          const r = 35 + ri * 22
          const startA = -t * (0.35 + ri * 0.12) + Math.PI
          ctx.beginPath()
          ctx.arc(ox, oy, r, startA, startA + Math.PI * 1.5)
          ctx.strokeStyle = ['#FF2D78', '#FF6B35', C][ri] + '55'
          ctx.lineWidth = 1; ctx.stroke()
          const dotX = ox + r * Math.cos(startA + Math.PI * 1.5)
          const dotY = oy + r * Math.sin(startA + Math.PI * 1.5)
          ctx.beginPath(); ctx.arc(dotX, dotY, 3, 0, Math.PI * 2)
          ctx.fillStyle = ['#FF2D78', '#FF6B35', C][ri]; ctx.fill()
        }
        ctx.font = 'bold 10px JetBrains Mono, monospace'
        ctx.fillStyle = '#FF2D78aa'; ctx.textAlign = 'right'
        ctx.fillText('AI SECURED ✓', ox + 30, oy - 90)
      }

      // ── 3D Supply Chain Network — center ──────────────────────────────────
      const cx = W / 2, cy = H / 2
      const rotated = NODES.map(n => {
        let r = rotY(n.bx, n.by, n.bz, angle)
        r = rotX(r.x, r.y, r.z, 0.2)
        return { ...r, color: n.color, label: n.label, name: n.name, id: n.id }
      })

      EDGES.forEach((e, ei) => {
        const a = proj(rotated[e[0]].x, rotated[e[0]].y, rotated[e[0]].z, cx, cy)
        const b = proj(rotated[e[1]].x, rotated[e[1]].y, rotated[e[1]].z, cx, cy)
        const g = ctx.createLinearGradient(a.sx, a.sy, b.sx, b.sy)
        g.addColorStop(0, rotated[e[0]].color + '55')
        g.addColorStop(1, rotated[e[1]].color + '55')
        ctx.beginPath(); ctx.moveTo(a.sx, a.sy); ctx.lineTo(b.sx, b.sy)
        ctx.strokeStyle = g; ctx.lineWidth = 1; ctx.stroke()
        for (let d = 0; d < 4; d++) {
          const p = ((t * 0.28 + d / 4 + ei * 0.15) % 1)
          ctx.beginPath()
          ctx.arc(a.sx + (b.sx - a.sx) * p, a.sy + (b.sy - a.sy) * p, 1.5, 0, Math.PI * 2)
          ctx.fillStyle = rotated[e[0]].color + 'cc'; ctx.fill()
        }
      })

      packets.forEach(pkt => {
        pkt.progress += pkt.speed
        if (pkt.progress > 1) pkt.progress = 0
        const e = EDGES[pkt.edge]
        const a = proj(rotated[e[0]].x, rotated[e[0]].y, rotated[e[0]].z, cx, cy)
        const b = proj(rotated[e[1]].x, rotated[e[1]].y, rotated[e[1]].z, cx, cy)
        const px = a.sx + (b.sx - a.sx) * pkt.progress
        const py = a.sy + (b.sy - a.sy) * pkt.progress
        const grd = ctx.createRadialGradient(px, py, 0, px, py, pkt.size * 3.5)
        grd.addColorStop(0, pkt.color + 'ff'); grd.addColorStop(1, pkt.color + '00')
        ctx.beginPath(); ctx.arc(px, py, pkt.size * 3.5, 0, Math.PI * 2)
        ctx.fillStyle = grd; ctx.fill()
        ctx.beginPath(); ctx.arc(px, py, pkt.size, 0, Math.PI * 2)
        ctx.fillStyle = pkt.color; ctx.fill()
      })

      const sorted = [...rotated].sort((a, b) => a.z - b.z)
      sorted.forEach(n => {
        const { sx, sy, scale } = proj(n.x, n.y, n.z, cx, cy)
        const isActive = n.id === role
        const pulse = 0.88 + 0.12 * Math.sin(t * 2.2)
        const r = scale * (isActive ? 28 : 20) * pulse
        const glow = ctx.createRadialGradient(sx, sy, 0, sx, sy, r * 3)
        glow.addColorStop(0, n.color + (isActive ? '55' : '28'))
        glow.addColorStop(1, n.color + '00')
        ctx.beginPath(); ctx.arc(sx, sy, r * 3, 0, Math.PI * 2)
        ctx.fillStyle = glow; ctx.fill()
        const ng = ctx.createRadialGradient(sx, sy, 0, sx, sy, r)
        ng.addColorStop(0, n.color + (isActive ? 'ee' : '88'))
        ng.addColorStop(1, n.color + '22')
        ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2)
        ctx.fillStyle = ng; ctx.fill()
        ctx.strokeStyle = n.color + (isActive ? 'ff' : '66')
        ctx.lineWidth = isActive ? 2.5 : 1.2; ctx.stroke()
        ctx.font = `${Math.floor(r * 0.85)}px serif`
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
        ctx.globalAlpha = 0.9; ctx.fillText(n.label, sx, sy); ctx.globalAlpha = 1
        ctx.font = `bold ${Math.floor(scale * 10)}px Inter, sans-serif`
        ctx.fillStyle = n.color; ctx.fillText(n.name, sx, sy + r + 12)
        if (isActive) {
          for (let ri = 1; ri <= 2; ri++) {
            ctx.beginPath()
            ctx.arc(sx, sy, r * (1.5 + ri * 0.5) + 4 * Math.sin(t * 3 + ri), 0, Math.PI * 2)
            ctx.strokeStyle = n.color + (ri === 1 ? '66' : '33')
            ctx.lineWidth = ri === 1 ? 1.5 : 0.8; ctx.stroke()
          }
        }
      })

      // ── Scanline sweep ────────────────────────────────────────────────────
      const scanY = (t * 55) % H
      const sg = ctx.createLinearGradient(0, scanY - 35, 0, scanY + 35)
      sg.addColorStop(0, 'rgba(0,217,255,0)')
      sg.addColorStop(0.5, 'rgba(0,217,255,0.022)')
      sg.addColorStop(1, 'rgba(0,217,255,0)')
      ctx.fillStyle = sg; ctx.fillRect(0, scanY - 35, W, 70)

      // ── Bottom-left HUD ───────────────────────────────────────────────────
      const hx = 14, hy = H - 125
      ctx.save()
      ctx.globalAlpha = 0.8
      ctx.fillStyle = 'rgba(5,8,22,0.88)'
      ctx.strokeStyle = C + '55'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.roundRect(hx, hy, 168, 108, 8)
      ctx.fill(); ctx.stroke()
      ctx.globalAlpha = 1
      ctx.font = 'bold 9px JetBrains Mono, monospace'
      ctx.fillStyle = C; ctx.textAlign = 'left'
      ctx.fillText('◈ PHARMATRUST LIVE', hx + 10, hy + 17)
      ctx.font = '8px JetBrains Mono, monospace'
      ctx.fillStyle = 'rgba(255,255,255,0.45)'
      ;['NODES  : 5 ACTIVE','PACKETS: 7 FLOWING','BLOCKS : 20 VERIFIED','CHAIN  : SECURED ✓','AI     : MONITORING']
        .forEach((s, i) => ctx.fillText(s, hx + 10, hy + 34 + i * 15))
      ctx.restore()

      animId = requestAnimationFrame(draw)
    }

    draw()

    const onResize = () => {
      W = canvas.width = window.innerWidth
      H = canvas.height = window.innerHeight
      // Reposition nebulas on resize
      nebulas[0].x = W * 0.08;  nebulas[0].y = H * 0.12
      nebulas[1].x = W * 0.92;  nebulas[1].y = H * 0.1
      nebulas[2].x = W * 0.05;  nebulas[2].y = H * 0.88
      nebulas[3].x = W * 0.94;  nebulas[3].y = H * 0.9
    }
    window.addEventListener('resize', onResize)
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', onResize) }
  }, [role])

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'fixed',
        top: 0, left: 0,
        width: '100vw',
        height: '100vh',
        pointerEvents: 'none',
        zIndex: 0,
        opacity: 0.55,
      }}
    />
  )
}
