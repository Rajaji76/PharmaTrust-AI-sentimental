import { useState, useEffect, useRef } from 'react'
import gsap from 'gsap'
import Sidebar from './components/Sidebar'
import MedicineBottle3D from './components/MedicineBottle3D'
import SupplyChainFlow3D from './components/SupplyChainFlow3D'
import ManufacturerPanel from './components/ManufacturerPanel'
import DistributorPanel from './components/DistributorPanel'
import RetailerPanel from './components/RetailerPanel'
import PatientPanel from './components/PatientPanel'
import RegulatorPanel from './components/RegulatorPanel'

function App() {
  const [activeTab, setActiveTab] = useState('manufacturer')
  const [pulseActive, setPulseActive] = useState(false)
  const [show3DFlow, setShow3DFlow] = useState(false)
  const panelRef = useRef(null)

  useEffect(() => {
    // GSAP animation for tab transitions
    if (panelRef.current) {
      gsap.fromTo(
        panelRef.current,
        { opacity: 0, x: 50, scale: 0.95 },
        { 
          opacity: 1, 
          x: 0, 
          scale: 1,
          duration: 0.5,
          ease: 'power3.out'
        }
      )
    }
  }, [activeTab])

  const handleTabChange = (tab) => {
    gsap.to(panelRef.current, {
      opacity: 0,
      x: -30,
      duration: 0.2,
      onComplete: () => {
        setActiveTab(tab)
      }
    })
  }

  const handleBatchCreated = () => {
    setPulseActive(true)
    setShow3DFlow(true)
    setTimeout(() => {
      setPulseActive(false)
    }, 5000)
  }

  const renderPanel = () => {
    switch(activeTab) {
      case 'manufacturer':
        return <ManufacturerPanel onBatchCreated={handleBatchCreated} />
      case 'distributor':
        return <DistributorPanel />
      case 'retailer':
        return <RetailerPanel />
      case 'patient':
        return <PatientPanel />
      case 'regulator':
        return <RegulatorPanel />
      default:
        return <ManufacturerPanel onBatchCreated={handleBatchCreated} />
    }
  }

  return (
    <div className="min-h-screen bg-dark-bg flex">
      <Sidebar activeTab={activeTab} setActiveTab={handleTabChange} />
      
      <main className="flex-1 p-8 relative overflow-hidden">
        {/* Background Grid */}
        <div className="absolute inset-0 bg-grid-pattern opacity-10"></div>
        
        {/* Toggle 3D View Button */}
        <button
          onClick={() => setShow3DFlow(!show3DFlow)}
          className="absolute top-4 right-4 z-20 px-4 py-2 bg-electric-blue/20 text-electric-blue border border-electric-blue/50 rounded-lg hover:bg-electric-blue/30 transition-all"
        >
          {show3DFlow ? '📦 Show Medicine Box' : '🔗 Show Supply Chain'}
        </button>

        {/* 3D Visualization Center */}
        <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-[600px] h-96 z-0">
          {show3DFlow ? (
            <SupplyChainFlow3D 
              activeNode={activeTab} 
              pulseActive={pulseActive}
            />
          ) : (
            <MedicineBottle3D />
          )}
        </div>

        {/* Content Panel */}
        <div ref={panelRef} className="relative z-10">
          {renderPanel()}
        </div>
      </main>
    </div>
  )
}

export default App
