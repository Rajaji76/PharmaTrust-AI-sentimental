import { Canvas, useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import { OrbitControls, MeshTransmissionMaterial } from '@react-three/drei'
import * as THREE from 'three'

function RotatingMedicineBox() {
  const boxRef = useRef()
  const glowRef = useRef()

  useFrame((state) => {
    // Rotate the box
    boxRef.current.rotation.y += 0.01
    boxRef.current.rotation.x = Math.sin(state.clock.elapsedTime * 0.5) * 0.1
    
    // Floating animation
    boxRef.current.position.y = Math.sin(state.clock.elapsedTime) * 0.2
    
    // Pulsating glow
    if (glowRef.current) {
      glowRef.current.scale.setScalar(1 + Math.sin(state.clock.elapsedTime * 2) * 0.1)
    }
  })

  return (
    <group ref={boxRef}>
      {/* Main Medicine Box */}
      <mesh>
        <boxGeometry args={[2, 1.2, 0.8]} />
        <meshStandardMaterial 
          color="#00D9FF" 
          transparent 
          opacity={0.4}
          metalness={0.9}
          roughness={0.1}
          emissive="#00D9FF"
          emissiveIntensity={0.3}
        />
      </mesh>

      {/* Holographic Glow Layer */}
      <mesh ref={glowRef}>
        <boxGeometry args={[2.1, 1.3, 0.9]} />
        <meshBasicMaterial 
          color="#39FF14" 
          transparent 
          opacity={0.15}
          side={THREE.BackSide}
        />
      </mesh>

      {/* Cross Symbol (Medical) */}
      <mesh position={[0, 0, 0.41]}>
        <boxGeometry args={[0.6, 0.2, 0.05]} />
        <meshStandardMaterial 
          color="#39FF14" 
          emissive="#39FF14"
          emissiveIntensity={0.8}
        />
      </mesh>
      <mesh position={[0, 0, 0.41]}>
        <boxGeometry args={[0.2, 0.6, 0.05]} />
        <meshStandardMaterial 
          color="#39FF14" 
          emissive="#39FF14"
          emissiveIntensity={0.8}
        />
      </mesh>

      {/* QR Code Placeholder */}
      <mesh position={[0, -0.4, 0.41]}>
        <planeGeometry args={[0.5, 0.5]} />
        <meshStandardMaterial 
          color="#1A1F3A" 
          emissive="#00D9FF"
          emissiveIntensity={0.2}
        />
      </mesh>

      {/* Edge Highlights */}
      {[
        [-1, 0.6, 0.4], [1, 0.6, 0.4], 
        [-1, -0.6, 0.4], [1, -0.6, 0.4]
      ].map((pos, i) => (
        <mesh key={i} position={pos}>
          <sphereGeometry args={[0.05, 16, 16]} />
          <meshStandardMaterial 
            color="#39FF14" 
            emissive="#39FF14"
            emissiveIntensity={1}
          />
        </mesh>
      ))}

      {/* Holographic Particles */}
      {Array.from({ length: 20 }).map((_, i) => {
        const angle = (i / 20) * Math.PI * 2
        const radius = 2.5
        return (
          <mesh 
            key={i} 
            position={[
              Math.cos(angle) * radius, 
              Math.sin(angle * 2) * 0.5, 
              Math.sin(angle) * radius
            ]}
          >
            <sphereGeometry args={[0.03, 8, 8]} />
            <meshBasicMaterial 
              color={i % 2 === 0 ? "#00D9FF" : "#39FF14"} 
              transparent 
              opacity={0.6}
            />
          </mesh>
        )
      })}
    </group>
  )
}

export default function MedicineBottle3D() {
  return (
    <Canvas camera={{ position: [0, 0, 6], fov: 50 }}>
      <ambientLight intensity={0.3} />
      
      {/* Main Lights */}
      <pointLight position={[5, 5, 5]} intensity={1.5} color="#00D9FF" />
      <pointLight position={[-5, -5, -5]} intensity={1} color="#39FF14" />
      <pointLight position={[0, 5, 0]} intensity={0.8} color="#ffffff" />
      
      {/* Rim Lights for Holographic Effect */}
      <spotLight 
        position={[0, 0, 5]} 
        intensity={0.5} 
        angle={0.6} 
        penumbra={1} 
        color="#00D9FF"
      />
      
      <RotatingMedicineBox />
      <OrbitControls 
        enableZoom={false} 
        enablePan={false}
        autoRotate
        autoRotateSpeed={0.5}
      />
      
      {/* Fog for depth */}
      <fog attach="fog" args={['#0A0E27', 5, 15]} />
    </Canvas>
  )
}
