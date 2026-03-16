import { useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import jsQR from 'jsqr'

/**
 * QRScanner — upload a QR code image and extract the serial number from it.
 * onScan(serialNumber) is called when a QR is successfully decoded.
 */
export default function QRScanner({ onScan, color = '#00D9FF', label = 'Upload QR Code Image' }) {
  const inputRef = useRef(null)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState(null)
  const [preview, setPreview] = useState(null)
  const [decoded, setDecoded] = useState(null)

  const handleFile = (file) => {
    if (!file) return
    setError(null); setDecoded(null)
    setScanning(true)

    const reader = new FileReader()
    reader.onload = (e) => {
      setPreview(e.target.result)
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = img.width
        canvas.height = img.height
        const ctx = canvas.getContext('2d')
        ctx.drawImage(img, 0, 0)
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
        const code = jsQR(imageData.data, imageData.width, imageData.height)
        setScanning(false)
        if (code) {
          const raw = code.data
          // Extract serial number from URL if it's a full URL (e.g. http://...?sn=UNIT-...)
          let serial = raw
          try {
            const url = new URL(raw)
            const sn = url.searchParams.get('sn')
            if (sn) serial = sn
          } catch {
            // Not a URL — use raw value directly
          }
          setDecoded(serial)
          onScan(serial)
        } else {
          setError('No QR code found in this image. Try a clearer photo.')
        }
      }
      img.onerror = () => { setScanning(false); setError('Could not load image.') }
      img.src = e.target.result
    }
    reader.readAsDataURL(file)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    const file = e.dataTransfer.files[0]
    if (file && file.type.startsWith('image/')) handleFile(file)
  }

  const handleChange = (e) => handleFile(e.target.files[0])

  return (
    <div className="space-y-3">
      {/* Drop zone */}
      <div
        onDrop={handleDrop}
        onDragOver={e => e.preventDefault()}
        onClick={() => inputRef.current?.click()}
        className="relative flex flex-col items-center justify-center gap-2 p-5 rounded-xl cursor-pointer transition-all duration-200 group"
        style={{
          border: `1.5px dashed ${color}55`,
          background: `${color}08`,
          minHeight: '100px',
        }}
        onMouseEnter={e => e.currentTarget.style.background = `${color}12`}
        onMouseLeave={e => e.currentTarget.style.background = `${color}08`}
      >
        <input ref={inputRef} type="file" accept="image/*" className="hidden" onChange={handleChange} />

        {scanning ? (
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 border-2 border-t-transparent rounded-full animate-spin" style={{ borderColor: color }} />
            <span className="text-sm font-medium" style={{ color }}>Scanning QR...</span>
          </div>
        ) : preview ? (
          <div className="flex items-center gap-4 w-full">
            <img src={preview} alt="QR" className="w-16 h-16 object-contain rounded-lg border"
              style={{ borderColor: color + '44' }} />
            <div className="flex-1 min-w-0">
              {decoded ? (
                <div>
                  <p className="text-xs font-semibold mb-1" style={{ color }}>✓ QR Decoded</p>
                  <p className="text-xs font-mono text-gray-300 truncate">{decoded}</p>
                </div>
              ) : (
                <p className="text-xs text-red-400">No QR found</p>
              )}
              <button
                onClick={e => { e.stopPropagation(); setPreview(null); setDecoded(null); setError(null) }}
                className="text-xs text-gray-500 hover:text-gray-300 mt-1 underline"
              >
                Clear & try again
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="text-3xl">📷</div>
            <p className="text-sm font-medium" style={{ color }}>{label}</p>
            <p className="text-xs text-gray-600">Drag & drop or click to browse</p>
            <p className="text-xs text-gray-700">Supports JPG, PNG, WebP</p>
          </>
        )}
      </div>

      {/* Error */}
      <AnimatePresence>
        {error && (
          <motion.p
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="text-xs text-red-400 px-1"
          >
            ⚠️ {error}
          </motion.p>
        )}
      </AnimatePresence>
    </div>
  )
}
