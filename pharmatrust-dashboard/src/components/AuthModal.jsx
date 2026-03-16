import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { authAPI } from '../services/api'

const roleConfig = {
  MANUFACTURER: { icon: '🏭', color: '#00D9FF', label: 'Manufacturer' },
  DISTRIBUTOR: { icon: '🚚', color: '#BF5FFF', label: 'Distributor' },
  RETAILER: { icon: '🏪', color: '#39FF14', label: 'Retailer' },
  PHARMACIST: { icon: '💊', color: '#39FF14', label: 'Pharmacist' },
  REGULATOR: { icon: '⚖️', color: '#FF2D78', label: 'Regulator' },
  PATIENT: { icon: '👤', color: '#FF6B35', label: 'Patient' },
}

function InputField({ label, type = 'text', value, onChange, placeholder, required, hint }) {
  return (
    <div>
      <label className="block text-xs font-semibold text-gray-400 mb-1.5 uppercase tracking-wider">{label}</label>
      <input
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        required={required}
        className="w-full rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-600 transition-all focus:outline-none"
        style={{
          background: 'rgba(255,255,255,0.04)',
          border: '1px solid rgba(255,255,255,0.08)',
        }}
        onFocus={e => { e.target.style.border = '1px solid rgba(0,217,255,0.5)'; e.target.style.boxShadow = '0 0 0 3px rgba(0,217,255,0.08)' }}
        onBlur={e => { e.target.style.border = '1px solid rgba(255,255,255,0.08)'; e.target.style.boxShadow = 'none' }}
      />
      {hint && <p className="text-xs text-gray-600 mt-1">{hint}</p>}
    </div>
  )
}

export default function AuthModal({ onAuthSuccess, allowedRoles = ['MANUFACTURER', 'DISTRIBUTOR', 'RETAILER', 'REGULATOR', 'PATIENT'] }) {
  const [authMode, setAuthMode] = useState('login')
  const [authForm, setAuthForm] = useState({
    email: '', password: '', fullName: '', organization: '',
    role: allowedRoles[0], shopName: '', shopAddress: '',
    licenseNumber: '', phoneNumber: '', gstNumber: '', cityState: '',
  })
  const [authLoading, setAuthLoading] = useState(false)
  const [authError, setAuthError] = useState(null)

  const set = (key) => (e) => setAuthForm(f => ({ ...f, [key]: e.target.value }))

  const handleAuth = async (e) => {
    e.preventDefault()
    setAuthLoading(true)
    setAuthError(null)
    try {
      let response
      if (authMode === 'login') {
        response = await authAPI.login({ email: authForm.email, password: authForm.password })
      } else {
        response = await authAPI.register({
          email: authForm.email, password: authForm.password,
          fullName: authForm.fullName, organization: authForm.organization,
          role: authForm.role,
          shopName: authForm.shopName || null, shopAddress: authForm.shopAddress || null,
          licenseNumber: authForm.licenseNumber || null, phoneNumber: authForm.phoneNumber || null,
          gstNumber: authForm.gstNumber || null, cityState: authForm.cityState || null,
        })
      }
      if (!response.token) throw new Error('No token received from server')
      localStorage.setItem('authToken', response.token)
      localStorage.setItem('userRole', response.role || authForm.role)
      localStorage.setItem('userEmail', authForm.email)
      if (onAuthSuccess) onAuthSuccess(response)
    } catch (error) {
      if (error.response?.data?.errors) {
        setAuthError(Object.entries(error.response.data.errors).map(([f, m]) => `${f}: ${m}`).join(', '))
      } else {
        setAuthError(error.response?.data?.message || error.message || 'Authentication failed')
      }
    } finally {
      setAuthLoading(false)
    }
  }

  const currentRole = roleConfig[authForm.role] || roleConfig.MANUFACTURER
  const isShopRole = ['DISTRIBUTOR', 'RETAILER', 'PHARMACIST'].includes(authForm.role)

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center p-4 overflow-y-auto"
      style={{ background: 'rgba(5,8,22,0.92)', backdropFilter: 'blur(12px)' }}>

      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.25, 0.46, 0.45, 0.94] }}
        className="w-full max-w-md my-8 relative"
        style={{
          background: 'linear-gradient(180deg, #0D1117 0%, #0A0E1A 100%)',
          border: '1px solid rgba(0,217,255,0.15)',
          borderRadius: '20px',
          boxShadow: '0 0 60px rgba(0,217,255,0.1), 0 40px 80px rgba(0,0,0,0.6)',
        }}
      >
        {/* Top glow */}
        <div className="absolute top-0 left-0 right-0 h-px rounded-t-xl"
          style={{ background: 'linear-gradient(90deg, transparent, rgba(0,217,255,0.6), rgba(191,95,255,0.4), transparent)' }} />

        <div className="p-8">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl mb-4 text-3xl"
              style={{ background: `linear-gradient(135deg, ${currentRole.color}20, ${currentRole.color}08)`, border: `1px solid ${currentRole.color}30` }}>
              {currentRole.icon}
            </div>
            <h2 className="text-2xl font-bold text-white mb-1">
              {authMode === 'login' ? 'Welcome Back' : 'Create Account'}
            </h2>
            <p className="text-sm text-gray-500">
              {authMode === 'login' ? 'Sign in to your PharmaTrust account' : 'Join the secure supply chain network'}
            </p>
          </div>

          {/* Mode toggle */}
          <div className="flex rounded-xl p-1 mb-6" style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)' }}>
            {['login', 'register'].map(mode => (
              <button key={mode} onClick={() => { setAuthMode(mode); setAuthError(null) }}
                className="flex-1 py-2 rounded-lg text-sm font-semibold transition-all duration-200"
                style={authMode === mode ? {
                  background: 'linear-gradient(135deg, rgba(0,217,255,0.2), rgba(191,95,255,0.1))',
                  color: '#00D9FF', border: '1px solid rgba(0,217,255,0.3)',
                } : { color: '#6B7280' }}>
                {mode === 'login' ? '🔐 Login' : '📝 Register'}
              </button>
            ))}
          </div>

          {/* Error */}
          <AnimatePresence>
            {authError && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}
                className="mb-4 p-3 rounded-lg text-sm text-red-400"
                style={{ background: 'rgba(255,45,120,0.08)', border: '1px solid rgba(255,45,120,0.2)' }}>
                ⚠️ {authError}
              </motion.div>
            )}
          </AnimatePresence>

          <form onSubmit={handleAuth} className="space-y-4">
            <InputField label="Email Address" type="email" value={authForm.email} onChange={set('email')} placeholder="you@example.com" required />
            <InputField label="Password" type="password" value={authForm.password} onChange={set('password')} placeholder="••••••••" required
              hint={authMode === 'register' ? '✅ Min 8 chars, uppercase, lowercase, digit — e.g. Password123' : null} />

            {authMode === 'register' && (
              <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-4">
                <InputField label="Full Name" value={authForm.fullName} onChange={set('fullName')} placeholder="Dr. Rajesh Kumar" required />
                <InputField label="Organization" value={authForm.organization} onChange={set('organization')} placeholder="Pharma Corp Ltd." required />

                {allowedRoles.length > 1 && (
                  <div>
                    <label className="block text-xs font-semibold text-gray-400 mb-1.5 uppercase tracking-wider">Role</label>
                    <div className="grid grid-cols-2 gap-2">
                      {allowedRoles.filter(r => r !== 'PATIENT').map(role => {
                        const rc = roleConfig[role] || {}
                        const isSelected = authForm.role === role
                        return (
                          <button key={role} type="button" onClick={() => setAuthForm(f => ({ ...f, role }))}
                            className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium transition-all"
                            style={isSelected ? {
                              background: `linear-gradient(135deg, ${rc.color}20, ${rc.color}08)`,
                              border: `1px solid ${rc.color}50`, color: rc.color,
                            } : { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)', color: '#6B7280' }}>
                            <span>{rc.icon}</span>
                            <span>{rc.label}</span>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )}

                {/* Shop identity fields */}
                {isShopRole && (
                  <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                    className="space-y-3 p-4 rounded-xl"
                    style={{ background: 'rgba(255,230,0,0.04)', border: '1px solid rgba(255,230,0,0.15)' }}>
                    <p className="text-xs font-semibold text-yellow-400 uppercase tracking-wider">
                      {authForm.role === 'DISTRIBUTOR' ? '🏭 Distributor Identity' : '🏪 Medical Shop Identity'}
                    </p>
                    <InputField label={authForm.role === 'DISTRIBUTOR' ? 'Company Name' : 'Shop Name'}
                      value={authForm.shopName} onChange={set('shopName')}
                      placeholder={authForm.role === 'DISTRIBUTOR' ? 'Sharma Pharma Distributors Pvt. Ltd.' : 'Sharma Medical Store'} required />
                    {authForm.role === 'DISTRIBUTOR' && (
                      <InputField label="GST Number" value={authForm.gstNumber} onChange={set('gstNumber')} placeholder="27AABCS1429B1ZB" required />
                    )}
                    <InputField label="Drug License Number" value={authForm.licenseNumber} onChange={set('licenseNumber')}
                      placeholder={authForm.role === 'DISTRIBUTOR' ? 'WB/DIST/2024/001234' : 'DL-MH-RET-123456'} required />
                    <InputField label={authForm.role === 'DISTRIBUTOR' ? 'Head Office Address' : 'Shop Address'}
                      value={authForm.shopAddress} onChange={set('shopAddress')}
                      placeholder={authForm.role === 'DISTRIBUTOR' ? 'Plot 45, Industrial Area, Phase 2' : '12, MG Road, Near Bus Stand'} required />
                    <div className="grid grid-cols-2 gap-3">
                      <InputField label="City / State" value={authForm.cityState} onChange={set('cityState')} placeholder="Mumbai, Maharashtra" required />
                      <InputField label="Phone" type="tel" value={authForm.phoneNumber} onChange={set('phoneNumber')} placeholder="+91 98765 43210" required />
                    </div>
                  </motion.div>
                )}
              </motion.div>
            )}

            <button type="submit" disabled={authLoading}
              className="w-full py-3 rounded-xl font-bold text-sm transition-all duration-200 relative overflow-hidden mt-2"
              style={{
                background: authLoading ? 'rgba(0,217,255,0.1)' : 'linear-gradient(135deg, #00D9FF, #7B5FFF)',
                color: 'white',
                boxShadow: authLoading ? 'none' : '0 0 30px rgba(0,217,255,0.3)',
              }}>
              {authLoading ? (
                <span className="flex items-center justify-center gap-2">
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Processing...
                </span>
              ) : (
                authMode === 'login' ? '🔐 Sign In' : '🚀 Create Account'
              )}
            </button>
          </form>
        </div>
      </motion.div>
    </div>
  )
}
