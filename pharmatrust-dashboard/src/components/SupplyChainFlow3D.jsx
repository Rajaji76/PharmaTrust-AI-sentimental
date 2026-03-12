
import { Canvas, useFrame } from '@react-three/fiber'
import { useRef, useState, useEffect } from 'react'
import { Line, Sphere, Text } from '@react-three/drei'
import * as THREE from 'three'

const nodes = [
  { id: 'manufacturer', label: 'Manufacturer', position: [-4, 2, 0], color: '#00D9FF' },
  { id: 'distributor', label: 'Distributor', position: [-2, 0, 0], color: '#39FF14' },
  { id: 'retailer', label: 'Retailer', position: [0, -2, 0], color: '#FFD700' },
  { id: 'pharmacy', label: 'Pharmacy', position: [2, 0, 0], color: '#FF6B6B' },
  { id: 'patient', label: 'Patient', position: [4, 2, 0], color: '#9D4EDD' },
]

function SupplyChainNode({ node, isActive }) {
  const meshRef = useRef()
  
  useFrame((state) => {
    if (meshRef.current && isActive) {
      meshRef.current.scale.setScalar(1 + Math.sin(state.clock.elapsedTime * 3) * 0.1)
    }
  })

  return (
    <group position={node.position}>
      <Sphere ref={meshRef} args={[0.3, 32, 32]}>
        <meshStandardMaterial 
          color={node.color} 
          emissive={node.color}
          emissiveIntensity={isActive ? 0.8 : 0.3}
          metalness={0.8}
          roughness={0.2}
        />
      </Sphere>
      
      <Text
        position={[0, -0.6, 0]}
        fontSize={0.2}
        color="white"
        anchorX="center"
        anchorY="middle"
      >
        {node.label}
      </Text>
      
      {isActive && (
        <Sphere args={[0.4, 32, 32]}>
          <meshBasicMaterial 
            color={node.color} 
            transparent 
            opacity={0.2}
            side={THREE.BackSide}
          />
        </Sphere>
      )}
    </group>
  )
}

function PulseParticle({ fromNode, toNode, active }) {
  const particleRef = useRef()
  const [progress, setProgress] = useState(0)

  useFrame((state, delta) => {
    if (active && progress < 1) {
      setProgress(prev => Math.min(prev + delta * 0.5, 1))
    } else if (!active) {
      setProgress(0)
    }

    if (particleRef.current) {
      const from = new THREE.Vector3(...fromNode.position)
      const to = new THREE.Vector3(...toNode.position)
      particleRef.current.position.lerpVectors(from, to, progress)
    }
  })

  if (!active) return null

  return (
    <Sphere ref={particleRef} args={[0.15, 16, 16]}>
      <meshBasicMaterial 
        color="#39FF14" 
        emissive="#39FF14"
        emissiveIntensity={2}
      />
    </Sphere>
  )
}

function ConnectionLines() {
  const lines = []
  for (let i = 0; i < nodes.length - 1; i++) {
    lines.push({
      start: nodes[i].position,
      end: nodes[i + 1].position,
    })
  }

  return (
    <>
      {lines.map((line, index) => (
        <Line
          key={index}
          points={[line.start, line.end]}
          color="#00D9FF"
          lineWidth={2}
          transparent
          opacity={0.3}
        />
      ))}
    </>
  )
}

export default function SupplyChainFlow3D({ activeNode, pulseActive }) {
  return (
    <Canvas camera={{ position: [0, 0, 8], fov: 50 }}>
      <ambientLight intensity={0.4} />
      <pointLight position={[10, 10, 10]} intensity={1} color="#00D9FF" />
      <pointLight position={[-10, -10, -10]} intensity={0.5} color="#39FF14" />
      
      <ConnectionLines />
      
      {nodes.map((node) => (
        <SupplyChainNode 
          key={node.id} 
          node={node} 
          isActive={activeNode === node.id}
        />
      ))}
      
      {pulseActive && (
        <>
          <PulseParticle 
            fromNode={nodes[0]} 
            toNode={nodes[1]} 
            active={pulseActive}
          />
          <PulseParticle 
            fromNode={nodes[1]} 
            toNode={nodes[2]} 
            active={pulseActive}
          />
        </>
      )}
    </Canvas>
  )
}
