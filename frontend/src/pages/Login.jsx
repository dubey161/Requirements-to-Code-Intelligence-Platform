import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import { authPost } from '../api/client.js'

const FEATURES = [
  { icon: '◎', text: 'LLM Analysis via Gemini, Groq, or Ollama' },
  { icon: '◈', text: 'Auto Java Code Generation per entity' },
  { icon: '⑂', text: 'GitHub Branch, Commit & PR creation' },
  { icon: '✦', text: 'AI-powered PR code review' },
  { icon: '⚑', text: 'Compliance & Risk scoring' },
]

export default function Login() {
  const [tab, setTab]         = useState('login')     // 'login' | 'register'
  const [email, setEmail]     = useState('')
  const [password, setPass]   = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState(null)
  const [showPass, setShow]   = useState(false)

  const { login } = useAuth()
  const navigate  = useNavigate()
  const location  = useLocation()
  const from      = location.state?.from?.pathname ?? '/'

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const endpoint = tab === 'login' ? '/auth/login' : '/auth/register'
      const res = await authPost(endpoint, { email, password })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.message || `${tab === 'login' ? 'Login' : 'Registration'} failed`)
      }
      const data = await res.json()
      login(data)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      background: 'var(--bg)',
      animation: 'fadeIn 0.4s ease',
    }}>
      {/* ── Left panel — brand + animated background ── */}
      <div style={{
        flex: '0 0 52%',
        position: 'relative',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '4rem',
        background: 'linear-gradient(160deg, #0a0f28 0%, #0d1535 40%, #0a1228 100%)',
      }}>

        {/* Animated gradient orbs */}
        <Orb size={480} x="10%" y="5%"  color="rgba(99,102,241,0.18)" delay="0s"    dur="18s" />
        <Orb size={360} x="55%" y="60%" color="rgba(139,92,246,0.14)"  delay="3s"    dur="22s" />
        <Orb size={280} x="70%" y="-5%" color="rgba(34,211,238,0.08)"  delay="6s"    dur="16s" />
        <Orb size={200} x="-5%" y="70%" color="rgba(251,191,36,0.06)"  delay="9s"    dur="20s" />

        {/* Mesh grid overlay */}
        <div style={{
          position: 'absolute', inset: 0, pointerEvents: 'none',
          backgroundImage: `
            linear-gradient(rgba(99,102,241,0.04) 1px, transparent 1px),
            linear-gradient(90deg, rgba(99,102,241,0.04) 1px, transparent 1px)
          `,
          backgroundSize: '48px 48px',
        }} />

        {/* Content */}
        <div style={{ position: 'relative', zIndex: 1 }}>

          {/* Logo */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: '0.75rem',
            marginBottom: '3rem',
            animation: 'fadeInDown 0.6s ease both',
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: '12px',
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '22px',
              boxShadow: '0 8px 24px rgba(99,102,241,0.4)',
            }}>⚡</div>
            <div>
              <div style={{ fontWeight: 800, fontSize: '1.1rem', color: 'var(--text)' }}>AI Platform</div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-3)', marginTop: '1px' }}>Engineering Productivity</div>
            </div>
          </div>

          {/* Headline */}
          <div style={{ marginBottom: '2.5rem', animation: 'fadeInUp 0.7s 0.1s ease both' }}>
            <h1 style={{
              fontSize: '2.8rem', fontWeight: 900, lineHeight: 1.15,
              color: 'var(--text)', marginBottom: '1rem',
            }}>
              Requirement to
              <br />
              <span className="text-gradient">Pull Request</span>
              <br />
              in minutes.
            </h1>
            <p style={{ color: 'var(--text-2)', fontSize: '1rem', lineHeight: 1.7, maxWidth: '380px' }}>
              The AI-powered platform that turns your Jira tickets
              into production-ready Spring Boot code — automatically.
            </p>
          </div>

          {/* Feature list */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {FEATURES.map((f, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: '0.875rem',
                animation: `fadeInUp 0.5s ${0.2 + i * 0.07}s ease both`,
              }}>
                <div style={{
                  width: 32, height: 32, borderRadius: '8px', flexShrink: 0,
                  background: 'rgba(99,102,241,0.12)',
                  border: '1px solid rgba(99,102,241,0.2)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '14px', color: '#818cf8',
                }}>{f.icon}</div>
                <span style={{ color: 'var(--text-2)', fontSize: '0.875rem' }}>{f.text}</span>
              </div>
            ))}
          </div>

          {/* Role badges */}
          <div style={{
            display: 'flex', gap: '0.5rem', marginTop: '2.5rem', flexWrap: 'wrap',
            animation: 'fadeInUp 0.5s 0.6s ease both',
          }}>
            {[
              { role: 'Developer', color: '#6366f1' },
              { role: 'Manager',   color: '#22d3ee' },
              { role: 'Admin',     color: '#a78bfa' },
            ].map(({ role, color }) => (
              <span key={role} style={{
                padding: '4px 12px', borderRadius: '999px', fontSize: '0.72rem', fontWeight: 600,
                background: `${color}18`, border: `1px solid ${color}35`, color,
              }}>{role}</span>
            ))}
            <span style={{ fontSize: '0.72rem', color: 'var(--text-3)', alignSelf: 'center' }}>
              Role-based dashboards
            </span>
          </div>
        </div>
      </div>

      {/* ── Right panel — form ── */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '2rem',
        animation: 'slideInRight 0.5s ease both',
      }}>
        <div style={{
          width: '100%', maxWidth: '420px',
          background: 'rgba(13, 21, 48, 0.8)',
          backdropFilter: 'blur(20px)',
          border: '1px solid rgba(255,255,255,0.07)',
          borderRadius: '20px',
          padding: '2.5rem',
          boxShadow: '0 25px 80px rgba(0,0,0,0.5)',
        }}>

          {/* Tab switch */}
          <div style={{
            display: 'flex', background: 'var(--surface)',
            borderRadius: '10px', padding: '4px',
            marginBottom: '2rem',
          }}>
            {['login', 'register'].map(t => (
              <button key={t} onClick={() => { setTab(t); setError(null) }}
                style={{
                  flex: 1, padding: '0.55rem', borderRadius: '7px',
                  fontSize: '0.875rem', fontWeight: 600,
                  background: tab === t ? 'var(--card-elevated)' : 'transparent',
                  color: tab === t ? 'var(--text)' : 'var(--text-3)',
                  border: tab === t ? '1px solid var(--border-hover)' : '1px solid transparent',
                  transition: 'all 0.2s',
                }}>
                {t === 'login' ? 'Sign In' : 'Create Account'}
              </button>
            ))}
          </div>

          {/* Heading */}
          <div style={{ marginBottom: '1.75rem' }}>
            <h2 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: '0.375rem' }}>
              {tab === 'login' ? 'Welcome back' : 'Join the platform'}
            </h2>
            <p style={{ color: 'var(--text-3)', fontSize: '0.875rem' }}>
              {tab === 'login'
                ? 'Sign in to your account to continue'
                : 'Create a Developer account to get started'}
            </p>
          </div>

          {/* Form */}
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: '1.1rem' }}>

            <FormField label="Email address">
              <input
                className="input"
                type="email"
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoFocus
              />
            </FormField>

            <FormField label="Password">
              <div style={{ position: 'relative' }}>
                <input
                  className="input"
                  type={showPass ? 'text' : 'password'}
                  autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
                  placeholder={tab === 'register' ? 'Min 8 characters' : '••••••••'}
                  value={password}
                  onChange={e => setPass(e.target.value)}
                  required
                  minLength={8}
                  style={{ paddingRight: '2.75rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShow(s => !s)}
                  style={{
                    position: 'absolute', right: '0.875rem', top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none', color: 'var(--text-3)',
                    fontSize: '1rem', lineHeight: 1,
                  }}>
                  {showPass ? '○' : '●'}
                </button>
              </div>
            </FormField>

            {/* Error */}
            {error && (
              <div style={{
                background: 'rgba(239,68,68,0.08)',
                border: '1px solid rgba(239,68,68,0.25)',
                borderRadius: '8px',
                padding: '0.65rem 0.875rem',
                color: '#f87171', fontSize: '0.825rem',
                animation: 'fadeInDown 0.3s ease',
              }}>
                {error}
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading}
              style={{ width: '100%', padding: '0.75rem', marginTop: '0.25rem', fontSize: '0.9rem' }}>
              {loading ? (
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Spinner size={16} /> {tab === 'login' ? 'Signing in…' : 'Creating account…'}
                </span>
              ) : (
                tab === 'login' ? 'Sign In →' : 'Create Account →'
              )}
            </button>
          </form>

          {/* Register note */}
          {tab === 'register' && (
            <p style={{
              marginTop: '1.25rem', color: 'var(--text-3)',
              fontSize: '0.8rem', textAlign: 'center', lineHeight: 1.6,
            }}>
              New accounts are created as <span style={{ color: 'var(--primary-light)' }}>Developer</span>.
              <br />An Admin can upgrade your role after registration.
            </p>
          )}

          {/* Divider + help */}
          <div style={{
            marginTop: '1.75rem', paddingTop: '1.25rem',
            borderTop: '1px solid var(--border)',
            display: 'flex', flexDirection: 'column', gap: '0.5rem',
          }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-3)', textAlign: 'center' }}>
              Default admin credentials for first run:
            </div>
            <div style={{
              background: 'var(--surface)', border: '1px solid var(--border)',
              borderRadius: '8px', padding: '0.5rem 0.75rem',
              fontSize: '0.75rem', color: 'var(--text-2)',
              fontFamily: 'monospace', textAlign: 'center',
            }}>
              Use <code>POST /api/v1/admin/users</code> to create an Admin
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ── Sub-components ──────────────────────────────────────────────────────────── */

function FormField({ label, children }) {
  return (
    <div>
      <label style={{
        display: 'block', fontSize: '0.78rem', fontWeight: 600,
        color: 'var(--text-2)', letterSpacing: '0.04em',
        textTransform: 'uppercase', marginBottom: '0.45rem',
      }}>
        {label}
      </label>
      {children}
    </div>
  )
}

function Orb({ size, x, y, color, delay, dur }) {
  return (
    <div style={{
      position: 'absolute',
      left: x, top: y,
      width: size, height: size,
      borderRadius: '50%',
      background: `radial-gradient(circle, ${color} 0%, transparent 70%)`,
      filter: 'blur(2px)',
      animation: `orb ${dur} ${delay} ease-in-out infinite`,
      pointerEvents: 'none',
    }} />
  )
}

function Spinner({ size = 18 }) {
  return (
    <div style={{
      width: size, height: size,
      border: `2px solid rgba(255,255,255,0.25)`,
      borderTopColor: 'white',
      borderRadius: '50%',
      animation: 'spin 0.7s linear infinite',
      flexShrink: 0,
    }} />
  )
}
