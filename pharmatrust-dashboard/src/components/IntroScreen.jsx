import { useRef, useEffect, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ── Full-screen cinematic canvas background ──────────────────────────────────
function IntroBG() {
  const canvasRef = useRef(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let W = canvas.width = window.innerWidth
    let H = canvas.height = window.innerHeight
    let t = 0, animId

    // Stars
    const stars = Array.from({ length: 350 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      r: Math.random() * 1.8 + 0.2,
      alpha: Math.random() * 0.7 + 0.1,
      tw: Math.random() * Math.PI * 2,
      spd: 0.006 + Math.random() * 0.02,
    }))

    // Nebula blobs
    const nebulas = [
      { x: W*0.1,  y: H*0.1,  r: 380, color: '#00D9FF' },
      { x: W*0.9,  y: H*0.15, r: 320, color: '#BF5FFF' },
      { x: W*0.05, y: H*0.9,  r: 350, color: '#FF2D78' },
      { x: W*0.95, y: H*0.85, r: 340, color: '#39FF14' },
      { x: W*0.5,  y: H*0.5,  r: 420, color: '#00D9FF' },
    ]

    // Hex grid
    const HS = 40
    const hexes = []
    for (let r = -1; r < Math.ceil(H/(HS*1.5))+2; r++)
      for (let c = -1; c < Math.ceil(W/(HS*1.75))+2; c++)
        hexes.push({ cx: c*HS*1.75, cy: r*HS*1.5+(c%2===0?0:HS*0.75), ph: Math.random()*Math.PI*2, spd: 0.003+Math.random()*0.006 })

    // Floating blocks
    const blocks = Array.from({ length: 35 }, (_, i) => ({
      x: Math.random()*W, y: Math.random()*H,
      vx: (Math.random()-0.5)*0.5, vy: (Math.random()-0.5)*0.4,
      w: 45+Math.random()*50, h: 22+Math.random()*22,
      alpha: 0.1+Math.random()*0.2,
      rot: Math.random()*Math.PI*2, rs: (Math.random()-0.5)*0.007,
      color: ['#00D9FF','#BF5FFF','#39FF14','#FF2D78','#FF6B35'][i%5],
    }))

    // Particles
    const particles = Array.from({ length: 200 }, () => ({
      x: Math.random()*W, y: Math.random()*H,
      vx: (Math.random()-0.5)*0.5, vy: -0.2-Math.random()*0.6,
      r: Math.random()*2.2+0.4,
      alpha: Math.random()*0.5+0.1,
      color: ['#00D9FF','#BF5FFF','#39FF14','#FF2D78'][Math.floor(Math.random()*4)],
    }))

    // Shockwaves
    const shockwaves = Array.from({ length: 5 }, (_, i) => ({
      r: i*100, maxR: 600, speed: 1.0+i*0.35, alpha: 0,
      color: ['#00D9FF','#BF5FFF','#39FF14','#FF2D78','#FF6B35'][i],
    }))

    // Wireframe cubes
    const rotY = (x,y,z,a) => ({ x:x*Math.cos(a)+z*Math.sin(a), y, z:-x*Math.sin(a)+z*Math.cos(a) })
    const rotX = (x,y,z,a) => ({ x, y:y*Math.cos(a)-z*Math.sin(a), z:y*Math.sin(a)+z*Math.cos(a) })
    const cubes = Array.from({ length: 10 }, () => ({
      x: Math.random()*W, y: Math.random()*H,
      size: 20+Math.random()*35,
      rx: Math.random()*Math.PI*2, ry: Math.random()*Math.PI*2,
      drx: (Math.random()-0.5)*0.013, dry: (Math.random()-0.5)*0.016,
      vx: (Math.random()-0.5)*0.3, vy: (Math.random()-0.5)*0.25,
      color: ['#00D9FF','#BF5FFF','#39FF14','#FF2D78'][Math.floor(Math.random()*4)],
      alpha: 0.18+Math.random()*0.22,
    }))

    const drawCube = (cx,cy,size,rx,ry,color,alpha) => {
      const verts=[[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]]
      const edges=[[0,1],[1,2],[2,3],[3,0],[4,5],[5,6],[6,7],[7,4],[0,4],[1,5],[2,6],[3,7]]
      const p = verts.map(([x,y,z]) => { let r=rotY(x*size,y*size,z*size,ry); r=rotX(r.x,r.y,r.z,rx); return {sx:r.x+cx,sy:r.y+cy} })
      ctx.save(); ctx.globalAlpha=alpha; ctx.strokeStyle=color; ctx.lineWidth=0.9
      edges.forEach(([a,b]) => { ctx.beginPath(); ctx.moveTo(p[a].sx,p[a].sy); ctx.lineTo(p[b].sx,p[b].sy); ctx.stroke() })
      ctx.restore()
    }

    // DNA helix helper
    const drawDNA = (DX, DY0, DH, DS, AMP, ph, c1, c2) => {
      const freq = Math.PI*2/DS*3.5
      for (let i=0;i<DS-1;i++) {
        const y1=DY0+(i/(DS-1))*DH, y2=DY0+((i+1)/(DS-1))*DH
        const x1a=DX+Math.sin(freq*i+ph)*AMP, x1b=DX+Math.sin(freq*(i+1)+ph)*AMP
        const x2a=DX+Math.sin(freq*i+ph+Math.PI)*AMP, x2b=DX+Math.sin(freq*(i+1)+ph+Math.PI)*AMP
        ctx.beginPath(); ctx.moveTo(x1a,y1); ctx.lineTo(x1b,y2); ctx.strokeStyle=c1+'99'; ctx.lineWidth=1.4; ctx.stroke()
        ctx.beginPath(); ctx.moveTo(x2a,y1); ctx.lineTo(x2b,y2); ctx.strokeStyle=c2+'99'; ctx.lineWidth=1.4; ctx.stroke()
      }
      for (let i=0;i<DS;i++) {
        const y=DY0+(i/(DS-1))*DH
        const x1=DX+Math.sin(freq*i+ph)*AMP, x2=DX+Math.sin(freq*i+ph+Math.PI)*AMP
        const a1=0.5+0.5*Math.abs(Math.sin(freq*i+ph))
        ctx.beginPath(); ctx.arc(x1,y,4,0,Math.PI*2); ctx.fillStyle=c1+Math.floor(a1*220+35).toString(16).padStart(2,'0'); ctx.fill()
        const a2=0.5+0.5*Math.abs(Math.sin(freq*i+ph+Math.PI))
        ctx.beginPath(); ctx.arc(x2,y,4,0,Math.PI*2); ctx.fillStyle=c2+Math.floor(a2*220+35).toString(16).padStart(2,'0'); ctx.fill()
        if (i%2===0) { ctx.beginPath(); ctx.moveTo(x1,y); ctx.lineTo(x2,y); ctx.strokeStyle='rgba(255,255,255,0.13)'; ctx.lineWidth=0.9; ctx.stroke() }
      }
    }

    const draw = () => {
      ctx.clearRect(0,0,W,H)
      t += 0.016

      // Nebulas
      nebulas.forEach(n => {
        const g=ctx.createRadialGradient(n.x,n.y,0,n.x,n.y,n.r)
        g.addColorStop(0,n.color+'55'); g.addColorStop(0.4,n.color+'1a'); g.addColorStop(1,n.color+'00')
        ctx.beginPath(); ctx.arc(n.x,n.y,n.r,0,Math.PI*2); ctx.fillStyle=g; ctx.fill()
      })

      // Stars
      stars.forEach(s => {
        s.tw+=s.spd
        const a=s.alpha*(0.3+0.7*Math.abs(Math.sin(s.tw)))
        ctx.beginPath(); ctx.arc(s.x,s.y,s.r,0,Math.PI*2); ctx.fillStyle=`rgba(255,255,255,${a})`; ctx.fill()
      })

      // Hex grid
      hexes.forEach(h => {
        h.ph+=h.spd
        const pulse=0.5+0.5*Math.sin(h.ph)
        ctx.save(); ctx.globalAlpha=0.025+pulse*0.06; ctx.strokeStyle='#00D9FF'; ctx.lineWidth=0.7
        ctx.beginPath()
        for (let i=0;i<6;i++) { const a=(Math.PI/3)*i-Math.PI/6; i===0?ctx.moveTo(h.cx+HS*Math.cos(a),h.cy+HS*Math.sin(a)):ctx.lineTo(h.cx+HS*Math.cos(a),h.cy+HS*Math.sin(a)) }
        ctx.closePath(); ctx.stroke(); ctx.restore()
      })

      // Particles
      particles.forEach(p => {
        p.x+=p.vx; p.y+=p.vy
        if (p.y<-5) { p.y=H+5; p.x=Math.random()*W }
        if (p.x<0) p.x=W; if (p.x>W) p.x=0
        ctx.beginPath(); ctx.arc(p.x,p.y,p.r,0,Math.PI*2)
        ctx.fillStyle=p.color+Math.floor(p.alpha*255).toString(16).padStart(2,'0'); ctx.fill()
      })

      // Cubes
      cubes.forEach(c => {
        c.rx+=c.drx; c.ry+=c.dry; c.x+=c.vx; c.y+=c.vy
        if (c.x<-80) c.x=W+80; if (c.x>W+80) c.x=-80
        if (c.y<-80) c.y=H+80; if (c.y>H+80) c.y=-80
        drawCube(c.x,c.y,c.size,c.rx,c.ry,c.color,c.alpha)
      })

      // Blocks
      blocks.forEach(b => {
        b.x+=b.vx; b.y+=b.vy; b.rot+=b.rs
        if (b.x<-80) b.x=W+80; if (b.x>W+80) b.x=-80
        if (b.y<-50) b.y=H+50; if (b.y>H+50) b.y=-50
        ctx.save(); ctx.translate(b.x,b.y); ctx.rotate(b.rot)
        ctx.globalAlpha=b.alpha; ctx.strokeStyle=b.color; ctx.lineWidth=1.1
        ctx.strokeRect(-b.w/2,-b.h/2,b.w,b.h)
        ctx.lineWidth=0.4; ctx.beginPath()
        ctx.moveTo(-b.w/2+5,0); ctx.lineTo(b.w/2-5,0)
        ctx.moveTo(0,-b.h/2+4); ctx.lineTo(0,b.h/2-4)
        ctx.stroke(); ctx.restore()
      })

      // Shockwaves
      shockwaves.forEach(sw => {
        sw.r+=sw.speed; if (sw.r>sw.maxR) { sw.r=0; sw.alpha=0.55 }
        sw.alpha=Math.max(0,sw.alpha-0.0035)
        if (sw.alpha>0) {
          ctx.beginPath(); ctx.arc(W/2,H/2,sw.r,0,Math.PI*2)
          ctx.strokeStyle=sw.color+Math.floor(sw.alpha*255).toString(16).padStart(2,'0')
          ctx.lineWidth=1.8; ctx.stroke()
        }
      })
      if (Math.floor(t*60)%150===0) { const sw=shockwaves[Math.floor(Math.random()*shockwaves.length)]; sw.r=0; sw.alpha=0.6 }

      // DNA helices
      drawDNA(W-80, 30, H*0.65, 30, 32, t*1.1, '#00D9FF', '#BF5FFF')
      drawDNA(80, H*0.25, H*0.65, 30, 32, t*0.85+1.5, '#FF2D78', '#39FF14')

      // Spinning reactor center
      const cx=W/2, cy=H/2
      for (let ri=0;ri<6;ri++) {
        const r=60+ri*32, startA=t*(0.45+ri*0.2)*(ri%2===0?1:-1), arcLen=Math.PI*(0.55+ri*0.12)
        ctx.beginPath(); ctx.arc(cx,cy,r,startA,startA+arcLen)
        ctx.strokeStyle=['#00D9FF','#BF5FFF','#39FF14','#FF2D78','#FF6B35','#00D9FF'][ri]+'bb'
        ctx.lineWidth=1.8+(ri===0?1:0); ctx.stroke()
        const dx=cx+r*Math.cos(startA+arcLen), dy=cy+r*Math.sin(startA+arcLen)
        ctx.beginPath(); ctx.arc(dx,dy,4,0,Math.PI*2)
        ctx.fillStyle=['#00D9FF','#BF5FFF','#39FF14','#FF2D78','#FF6B35','#00D9FF'][ri]; ctx.fill()
      }
      const pulse=0.85+0.15*Math.sin(t*3.5)
      const cg=ctx.createRadialGradient(cx,cy,0,cx,cy,32*pulse)
      cg.addColorStop(0,'#00D9FFff'); cg.addColorStop(0.4,'#00D9FF88'); cg.addColorStop(1,'#00D9FF00')
      ctx.beginPath(); ctx.arc(cx,cy,32*pulse,0,Math.PI*2); ctx.fillStyle=cg; ctx.fill()
      ctx.beginPath(); ctx.arc(cx,cy,12*pulse,0,Math.PI*2); ctx.fillStyle='#ffffff'; ctx.fill()

      // Scanline
      const scanY=(t*55)%H
      const sg=ctx.createLinearGradient(0,scanY-40,0,scanY+40)
      sg.addColorStop(0,'rgba(0,217,255,0)'); sg.addColorStop(0.5,'rgba(0,217,255,0.035)'); sg.addColorStop(1,'rgba(0,217,255,0)')
      ctx.fillStyle=sg; ctx.fillRect(0,scanY-40,W,80)

      animId=requestAnimationFrame(draw)
    }
    draw()
    const onResize=()=>{ W=canvas.width=window.innerWidth; H=canvas.height=window.innerHeight }
    window.addEventListener('resize',onResize)
    return ()=>{ cancelAnimationFrame(animId); window.removeEventListener('resize',onResize) }
  }, [])

  return <canvas ref={canvasRef} style={{ position:'fixed', top:0, left:0, width:'100vw', height:'100vh', pointerEvents:'none', zIndex:0 }} />
}

// ── Typewriter hook ──────────────────────────────────────────────────────────
function useTypewriter(text, speed = 38, startDelay = 0) {
  const [displayed, setDisplayed] = useState('')
  const [done, setDone] = useState(false)
  useEffect(() => {
    setDisplayed(''); setDone(false)
    let i = 0
    const timeout = setTimeout(() => {
      const interval = setInterval(() => {
        i++
        setDisplayed(text.slice(0, i))
        if (i >= text.length) { clearInterval(interval); setDone(true) }
      }, speed)
      return () => clearInterval(interval)
    }, startDelay)
    return () => clearTimeout(timeout)
  }, [text, speed, startDelay])
  return { displayed, done }
}

// ── Counter animation ────────────────────────────────────────────────────────
function AnimCounter({ target, suffix = '', duration = 2000 }) {
  const [val, setVal] = useState(0)
  useEffect(() => {
    let start = null
    const step = (ts) => {
      if (!start) start = ts
      const progress = Math.min((ts - start) / duration, 1)
      setVal(Math.floor(progress * target))
      if (progress < 1) requestAnimationFrame(step)
    }
    requestAnimationFrame(step)
  }, [target, duration])
  return <span>{val.toLocaleString()}{suffix}</span>
}

// ── Main IntroScreen ─────────────────────────────────────────────────────────
export default function IntroScreen({ onDone }) {
  const [phase, setPhase] = useState(0) // 0=logo, 1=headline, 2=mission, 3=quote, 4=tech, 5=cta, 6=exit

  // Auto-advance phases
  useEffect(() => {
    const timings = [900, 2200, 3800, 5800, 7400, 9000]
    const timers = timings.map((ms, i) => setTimeout(() => setPhase(i + 1), ms))
    return () => timers.forEach(clearTimeout)
  }, [])

  const headline1 = useTypewriter('Welcome to PharmaTrust', 45, 1000)
  const headline2 = useTypewriter('Restoring Faith in Every Breath, One Dose at a Time.', 28, 2400)
  const mission1  = useTypewriter('Track and Secure Every Single Dose.', 35, 3900)
  const mission2  = useTypewriter('Global Integrity. Local Trust. Unbreakable Security.', 28, 5000)

  const handleEnter = () => {
    setPhase(6)
    setTimeout(onDone, 700)
  }

  return (
    <motion.div
      key="intro"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 1.04, transition: { duration: 0.7 } }}
      className="fixed inset-0 overflow-y-auto"
      style={{ background: '#020510', zIndex: 50 }}
    >
      <IntroBG />

      {/* Dark center vignette so text pops */}
      <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 1,
        background: 'radial-gradient(ellipse 70% 70% at 50% 50%, rgba(2,5,16,0.55) 0%, rgba(2,5,16,0.0) 100%)' }} />

      <div className="relative flex flex-col items-center text-center px-6 max-w-5xl w-full mx-auto py-8 pb-20" style={{ zIndex: 10 }}>

        {/* ── Logo badge ── */}
        <AnimatePresence>
          {phase >= 0 && (
            <motion.div initial={{ opacity:0, scale:0.5 }} animate={{ opacity:1, scale:1 }}
              transition={{ duration:0.8, ease:[0.34,1.56,0.64,1] }}
              className="mb-4 flex items-center gap-3"
            >
              <div className="relative">
                <div className="w-16 h-16 rounded-2xl flex items-center justify-center text-3xl"
                  style={{ background:'linear-gradient(135deg,#00D9FF22,#BF5FFF22)', border:'1.5px solid #00D9FF55',
                    boxShadow:'0 0 40px #00D9FF44, 0 0 80px #00D9FF22' }}>
                  💊
                </div>
                <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full animate-ping"
                  style={{ background:'#39FF14', opacity:0.7 }} />
                <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full"
                  style={{ background:'#39FF14', boxShadow:'0 0 8px #39FF14' }} />
              </div>
              <div className="text-left">
                <div className="text-xs font-mono tracking-[0.3em] mb-0.5" style={{ color:'#00D9FF99' }}>BLOCKCHAIN SECURED</div>
                <div className="text-sm font-bold tracking-widest" style={{ color:'#00D9FFcc' }}>PHARMATRUST v2.0</div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* ── Main Headline ── */}
        <div className="mb-2 min-h-[70px] flex flex-col items-center">
          <h1 className="font-black leading-tight mb-2"
            style={{ fontSize:'clamp(2rem,5vw,4rem)',
              background:'linear-gradient(135deg,#00D9FF,#BF5FFF,#39FF14)',
              WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent',
              backgroundClip:'text',
              textShadow:'none',
              filter:'drop-shadow(0 0 30px #00D9FF88)' }}>
            {headline1.displayed}<span className="animate-pulse" style={{ color:'#00D9FF' }}>
              {!headline1.done ? '|' : ''}
            </span>
          </h1>
          {phase >= 1 && (
            <motion.p initial={{ opacity:0 }} animate={{ opacity:1 }} transition={{ duration:0.5 }}
              className="text-lg font-light tracking-wide" style={{ color:'#94a3b8', maxWidth:'600px' }}>
              {headline2.displayed}<span className="animate-pulse" style={{ color:'#BF5FFF' }}>
                {!headline2.done ? '|' : ''}
              </span>
            </motion.p>
          )}
        </div>

        {/* ── Divider ── */}
        {phase >= 2 && (
          <motion.div initial={{ scaleX:0 }} animate={{ scaleX:1 }} transition={{ duration:0.8 }}
            className="w-64 h-px my-3"
            style={{ background:'linear-gradient(90deg,transparent,#00D9FF,#BF5FFF,transparent)' }} />
        )}

        {/* ── Mission Statement ── */}
        {phase >= 2 && (
          <motion.div initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:0.6 }}
            className="mb-3 min-h-[60px]">
            <p className="text-2xl font-bold mb-1"
              style={{ color:'#00D9FF', textShadow:'0 0 20px #00D9FF88', letterSpacing:'0.02em' }}>
              {mission1.displayed}<span className="animate-pulse">{!mission1.done?'|':''}</span>
            </p>
            {phase >= 3 && (
              <motion.p initial={{ opacity:0 }} animate={{ opacity:1 }} transition={{ duration:0.5 }}
                className="text-base font-medium tracking-widest uppercase"
                style={{ color:'#BF5FFF99', letterSpacing:'0.15em' }}>
                {mission2.displayed}<span className="animate-pulse">{!mission2.done?'|':''}</span>
              </motion.p>
            )}
          </motion.div>
        )}

        {/* ── Quote Block ── */}
        {phase >= 3 && (
          <motion.div initial={{ opacity:0, y:24 }} animate={{ opacity:1, y:0 }} transition={{ duration:0.7, delay:0.2 }}
            className="relative my-3 px-6 py-4 max-w-3xl"
            style={{ background:'rgba(0,217,255,0.04)', border:'1px solid rgba(0,217,255,0.18)',
              borderRadius:'16px', backdropFilter:'blur(8px)',
              boxShadow:'0 0 40px rgba(0,217,255,0.08), inset 0 0 40px rgba(0,217,255,0.03)' }}>
            {/* Quote marks */}
            <div className="absolute -top-4 left-6 text-5xl font-serif leading-none"
              style={{ color:'#00D9FF44' }}>"</div>
            <div className="absolute -bottom-6 right-6 text-5xl font-serif leading-none"
              style={{ color:'#BF5FFF44' }}>"</div>
            <p className="text-base leading-relaxed font-light italic mb-3" style={{ color:'#cbd5e1' }}>
              Technology is at its best when it protects the most vulnerable. In the world of medicine,{' '}
              <span className="font-semibold not-italic" style={{ color:'#00D9FF' }}>Trust</span>{' '}
              is the only ingredient that cannot be synthesized—it must be built.
            </p>
            <p className="text-xs font-mono tracking-widest" style={{ color:'#00D9FF88' }}>
              — PharmaTrust: Ensuring Authenticity from Factory to Pharmacy
            </p>
          </motion.div>
        )}

        {/* ── Tech pillars ── */}
        {phase >= 4 && (
          <motion.div initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:0.6 }}
            className="grid grid-cols-3 gap-3 my-3 w-full max-w-3xl">
            {[
              { icon:'🔗', title:'Immutable Ledger', desc:'Every unit is cryptographically signed.', color:'#00D9FF' },
              { icon:'🤖', title:'Real-Time Vigilance', desc:'AI-powered anomaly detection for supply chain sanctity.', color:'#BF5FFF' },
              { icon:'🛡️', title:'Zero Compromise', desc:"A counterfeit medicine isn't just fake—it's a threat to life.", color:'#FF2D78' },
            ].map((item, i) => (
              <motion.div key={i} initial={{ opacity:0, y:16 }} animate={{ opacity:1, y:0 }}
                transition={{ duration:0.5, delay:i*0.15 }}
                className="p-4 rounded-xl text-left"
                style={{ background:`rgba(${i===0?'0,217,255':i===1?'191,95,255':'255,45,120'},0.06)`,
                  border:`1px solid ${item.color}33`,
                  boxShadow:`0 0 20px ${item.color}11` }}>
                <div className="text-2xl mb-2">{item.icon}</div>
                <div className="text-xs font-bold mb-1 tracking-wide" style={{ color:item.color }}>{item.title}</div>
                <div className="text-xs leading-relaxed" style={{ color:'#64748b' }}>{item.desc}</div>
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* ── Live stats ── */}
        {phase >= 4 && (
          <motion.div initial={{ opacity:0 }} animate={{ opacity:1 }} transition={{ duration:0.6, delay:0.4 }}
            className="flex items-center gap-8 my-2">
            {[
              { label:'Doses Tracked', val:2847391, suffix:'+', color:'#00D9FF' },
              { label:'Batches Secured', val:18420, suffix:'', color:'#39FF14' },
              { label:'Threats Blocked', val:3847, suffix:'', color:'#FF2D78' },
            ].map((s, i) => (
              <div key={i} className="text-center">
                <div className="text-2xl font-black font-mono" style={{ color:s.color, textShadow:`0 0 15px ${s.color}88` }}>
                  <AnimCounter target={s.val} suffix={s.suffix} duration={2500} />
                </div>
                <div className="text-xs mt-1 tracking-widest uppercase" style={{ color:'#475569' }}>{s.label}</div>
              </div>
            ))}
          </motion.div>
        )}

        {/* ── CTA Button ── */}
        {phase >= 5 && (
          <motion.div initial={{ opacity:0, scale:0.8 }} animate={{ opacity:1, scale:1 }}
            transition={{ duration:0.6, ease:[0.34,1.56,0.64,1] }}
            className="mt-5">
            <button onClick={handleEnter}
              className="relative group px-12 py-4 rounded-2xl font-bold text-lg tracking-widest uppercase overflow-hidden transition-all duration-300 hover:scale-105"
              style={{ background:'linear-gradient(135deg,#00D9FF22,#BF5FFF22)',
                border:'1.5px solid #00D9FF88',
                color:'#00D9FF',
                boxShadow:'0 0 30px #00D9FF44, 0 0 60px #00D9FF22' }}>
              <span className="relative z-10">Enter PharmaTrust →</span>
              <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                style={{ background:'linear-gradient(135deg,#00D9FF33,#BF5FFF33)' }} />
            </button>
            <p className="mt-3 text-xs font-mono tracking-widest" style={{ color:'#334155' }}>
              BLOCKCHAIN SECURED · AI MONITORED · ZERO COMPROMISE
            </p>
          </motion.div>
        )}
      </div>

      {/* ── Bottom status bar ── */}
      <div className="fixed bottom-0 left-0 right-0 px-6 py-3 flex items-center justify-between" style={{ zIndex:10 }}>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full animate-pulse" style={{ background:'#39FF14', boxShadow:'0 0 8px #39FF14' }} />
          <span className="text-xs font-mono" style={{ color:'#39FF1488' }}>SYSTEM ONLINE</span>
        </div>
        <div className="text-xs font-mono" style={{ color:'#1e293b' }}>
          PHARMATRUST © 2026 · BLOCKCHAIN INTEGRITY PLATFORM
        </div>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full animate-pulse" style={{ background:'#00D9FF', boxShadow:'0 0 8px #00D9FF' }} />
          <span className="text-xs font-mono" style={{ color:'#00D9FF88' }}>AI ACTIVE</span>
        </div>
      </div>
    </motion.div>
  )
}
