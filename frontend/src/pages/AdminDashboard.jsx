import { useEffect, useState, useRef } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import { apiFetch } from '../api/client.js'

/* ── Count-up hook ─────────────────────────────────────────────────────────── */
function useCountUp(target, duration = 900) {
  const [value, setValue] = useState(0)
  const raf = useRef(null)
  useEffect(() => {
    if (typeof target !== 'number') return
    const start = Date.now()
    const tick = () => {
      const p = Math.min((Date.now() - start) / duration, 1)
      setValue(Math.round((1 - Math.pow(1 - p, 3)) * target))
      if (p < 1) raf.current = requestAnimationFrame(tick)
    }
    raf.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf.current)
  }, [target, duration])
  return value
}

const ROLE_STYLE = {
  ADMIN:                { bg: 'rgba(167,139,250,0.1)', border: 'rgba(167,139,250,0.35)', text: '#a78bfa' },
  ENGINEERING_MANAGER:  { bg: 'rgba(34,211,238,0.08)', border: 'rgba(34,211,238,0.3)',   text: '#22d3ee' },
  DEVELOPER:            { bg: 'rgba(99,102,241,0.1)',  border: 'rgba(99,102,241,0.3)',   text: '#818cf8' },
}

export default function AdminDashboard() {
  const [data, setData]         = useState(null)
  const [loading, setLoading]   = useState(true)
  const [users, setUsers]       = useState([])
  const [usersLoading, setUL]   = useState(true)
  const [togglingId, setToggle] = useState(null)
  const [tab, setTab]           = useState('overview') // 'overview' | 'users'

  useEffect(() => {
    apiFetch('/api/v1/dashboard/admin')
      .then(r => r.ok ? r.json() : null)
      .then(d => { if (d) setData(d) })
      .catch(() => {})
      .finally(() => setLoading(false))

    apiFetch('/api/v1/admin/users')
      .then(r => r.ok ? r.json() : [])
      .then(setUsers)
      .catch(() => {})
      .finally(() => setUL(false))
  }, [])

  const totalUsers = useCountUp(data?.totalUsers ?? 0)
  const totalReqs  = useCountUp(data?.totalRequirements ?? 0)
  const totalPrs   = useCountUp(data?.totalPullRequests ?? 0)
  const avgScore   = useCountUp(data?.avgComplianceScore ?? 0)

  const toggleActive = async (user) => {
    setToggle(user.id)
    try {
      const action = user.active ? 'deactivate' : 'activate'
      const res = await apiFetch(`/api/v1/admin/users/${user.id}/${action}`, { method: 'PUT' })
      if (res.ok) {
        setUsers(prev => prev.map(u => u.id === user.id ? { ...u, active: !u.active } : u))
      }
    } catch {}
    setToggle(null)
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', animation: 'fadeIn 0.4s ease' }}>

      {/* ── Header ── */}
      <div style={{ marginBottom: '2rem', animation: 'fadeInUp 0.5s ease' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.375rem' }}>
              <div style={{
                padding: '4px 12px', borderRadius: '999px', fontSize: '0.72rem', fontWeight: 700,
                background: 'rgba(167,139,250,0.1)', border: '1px solid rgba(167,139,250,0.35)',
                color: '#a78bfa', textTransform: 'uppercase', letterSpacing: '0.08em',
              }}>Admin</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span className="status-dot live" />
                <span style={{ fontSize: '0.72rem', color: 'var(--text-3)' }}>System operational</span>
              </div>
            </div>
            <h1 style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text)' }}>
              Admin
              <span style={{
                background: 'linear-gradient(135deg,#a78bfa,#8b5cf6)',
                WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text',
              }}> Control Panel</span>
            </h1>
            <p style={{ color: 'var(--text-3)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
              User management, system health, and platform-wide metrics
            </p>
          </div>
        </div>
      </div>

      {/* ── Stat cards ── */}
      {loading ? <SkeletonRow count={4} /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px,1fr))', gap: '1rem', marginBottom: '2rem' }}>
          <StatCard label="Total Users" value={totalUsers}
            icon="◉" gradient="linear-gradient(135deg,#a78bfa,#8b5cf6)" glow="rgba(167,139,250,0.2)" delay="0.05s" />
          <StatCard label="Requirements" value={totalReqs}
            icon="◈" gradient="linear-gradient(135deg,#6366f1,#8b5cf6)" glow="rgba(99,102,241,0.2)" delay="0.10s" />
          <StatCard label="Pull Requests" value={totalPrs}
            icon="⑂" gradient="linear-gradient(135deg,#06b6d4,#22d3ee)" glow="rgba(34,211,238,0.2)" delay="0.15s" />
          <StatCard label="Avg Compliance"
            value={typeof data?.avgComplianceScore === 'number' ? `${avgScore}%` : '—'}
            icon="✦" gradient="linear-gradient(135deg,#10b981,#34d399)" glow="rgba(52,211,153,0.2)" delay="0.20s" />
        </div>
      )}

      {/* ── Tabs ── */}
      <div style={{
        display: 'flex', gap: '2px', background: 'var(--surface)',
        borderRadius: '10px', padding: '4px', marginBottom: '1.5rem',
        width: 'fit-content', animation: 'fadeInUp 0.5s 0.25s ease both',
      }}>
        {[['overview', 'Overview'], ['users', 'User Management']].map(([key, label]) => (
          <button key={key} onClick={() => setTab(key)} style={{
            padding: '0.5rem 1.25rem', borderRadius: '7px', fontSize: '0.875rem', fontWeight: 600,
            background: tab === key ? 'var(--card-elevated)' : 'transparent',
            color: tab === key ? 'var(--text)' : 'var(--text-3)',
            border: tab === key ? '1px solid var(--border-hover)' : '1px solid transparent',
            transition: 'all 0.2s',
          }}>{label}</button>
        ))}
      </div>

      {tab === 'overview' && (
        <div style={{ animation: 'fadeInUp 0.4s ease both' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>

            {/* Users by role */}
            <div>
              <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem', color: 'var(--text)' }}>
                Users by Role
              </h2>
              {loading ? <SkeletonBlock height={160} /> : (
                <div style={{
                  background: 'var(--card)', border: '1px solid var(--border)',
                  borderRadius: '14px', overflow: 'hidden',
                }}>
                  {Object.entries(ROLE_STYLE).map(([role, style], i) => {
                    const count = data?.usersByRole?.[role] ?? 0
                    return (
                      <div key={role} style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        padding: '0.875rem 1.25rem',
                        borderBottom: i < 2 ? '1px solid var(--border)' : 'none',
                      }}>
                        <span style={{
                          padding: '3px 10px', borderRadius: '999px',
                          fontSize: '0.72rem', fontWeight: 700,
                          background: style.bg, border: `1px solid ${style.border}`, color: style.text,
                        }}>{role.replace(/_/g, ' ')}</span>
                        <span style={{ fontWeight: 700, fontSize: '1.1rem', color: 'var(--text)' }}>{count}</span>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {/* Quality metrics */}
            <div>
              <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem', color: 'var(--text)' }}>
                Quality Metrics
              </h2>
              {loading ? <SkeletonBlock height={160} /> : (
                <div style={{
                  background: 'var(--card)', border: '1px solid var(--border)',
                  borderRadius: '14px', overflow: 'hidden',
                }}>
                  {[
                    { label: 'Avg Compliance Score', value: data?.avgComplianceScore != null ? `${Math.round(data.avgComplianceScore)}%` : '—', color: '#34d399' },
                    { label: 'PRs per Requirement',  value: data?.totalRequirements ? (data.totalPullRequests / data.totalRequirements).toFixed(2) : '—', color: '#22d3ee' },
                    { label: 'Dominant Risk Level',  value: data?.dominantRiskLevel ?? '—', color: '#fbbf24' },
                  ].map(({ label, value, color }, i) => (
                    <div key={label} style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      padding: '0.875rem 1.25rem',
                      borderBottom: i < 2 ? '1px solid var(--border)' : 'none',
                    }}>
                      <span style={{ color: 'var(--text-2)', fontSize: '0.875rem' }}>{label}</span>
                      <span style={{ fontWeight: 700, fontSize: '1rem', color }}>{value}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {tab === 'users' && (
        <div style={{ animation: 'fadeInUp 0.4s ease both' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <h2 style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text)' }}>
              All Users
              <span style={{ marginLeft: '0.5rem', fontSize: '0.8rem', color: 'var(--text-3)', fontWeight: 400 }}>
                ({users.length})
              </span>
            </h2>
          </div>
          {usersLoading ? <SkeletonList rows={6} /> : !users.length ? (
            <EmptyState label="No users found" />
          ) : (
            <div style={{
              background: 'var(--card)', border: '1px solid var(--border)',
              borderRadius: '14px', overflow: 'hidden',
            }}>
              {/* Table header */}
              <div style={{
                display: 'grid', gridTemplateColumns: '1fr 180px 100px 80px',
                padding: '0.75rem 1.25rem', borderBottom: '1px solid var(--border)',
                fontSize: '0.7rem', fontWeight: 600, color: 'var(--text-3)',
                textTransform: 'uppercase', letterSpacing: '0.08em',
              }}>
                <span>User</span>
                <span>Role</span>
                <span>Status</span>
                <span>Action</span>
              </div>

              {users.map((u, i) => {
                const rs = ROLE_STYLE[u.role] ?? ROLE_STYLE.DEVELOPER
                return (
                  <div key={u.id} style={{
                    display: 'grid', gridTemplateColumns: '1fr 180px 100px 80px',
                    alignItems: 'center', padding: '0.875rem 1.25rem',
                    borderBottom: i < users.length - 1 ? '1px solid var(--border)' : 'none',
                    animation: `fadeInUp 0.4s ${i * 0.04}s ease both`,
                  }}>
                    {/* Email + avatar */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', minWidth: 0 }}>
                      <div style={{
                        width: 32, height: 32, borderRadius: '9px', flexShrink: 0,
                        background: `linear-gradient(135deg, ${AVATAR_GRADIENTS[i % AVATAR_GRADIENTS.length]})`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '0.85rem', fontWeight: 700, color: 'white',
                      }}>
                        {u.email[0].toUpperCase()}
                      </div>
                      <span style={{
                        color: 'var(--text)', fontSize: '0.875rem', fontWeight: 500,
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                      }}>{u.email}</span>
                    </div>

                    {/* Role badge */}
                    <span style={{
                      padding: '3px 10px', borderRadius: '999px', width: 'fit-content',
                      fontSize: '0.7rem', fontWeight: 700,
                      background: rs.bg, border: `1px solid ${rs.border}`, color: rs.text,
                    }}>{u.role.replace(/_/g, ' ')}</span>

                    {/* Active status */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span style={{
                        width: 8, height: 8, borderRadius: '50%',
                        background: u.active ? '#34d399' : '#f87171',
                        boxShadow: u.active ? '0 0 0 3px rgba(52,211,153,0.2)' : 'none',
                        display: 'inline-block', flexShrink: 0,
                      }} />
                      <span style={{ fontSize: '0.75rem', color: u.active ? '#34d399' : '#f87171' }}>
                        {u.active ? 'Active' : 'Inactive'}
                      </span>
                    </div>

                    {/* Toggle button */}
                    <button
                      disabled={togglingId === u.id}
                      onClick={() => toggleActive(u)}
                      style={{
                        padding: '4px 10px', borderRadius: '6px', fontSize: '0.7rem', fontWeight: 600,
                        background: u.active ? 'rgba(239,68,68,0.1)' : 'rgba(52,211,153,0.1)',
                        border: `1px solid ${u.active ? 'rgba(239,68,68,0.3)' : 'rgba(52,211,153,0.3)'}`,
                        color: u.active ? '#f87171' : '#34d399',
                        cursor: togglingId === u.id ? 'wait' : 'pointer',
                        opacity: togglingId === u.id ? 0.5 : 1,
                        transition: 'all 0.2s',
                      }}>
                      {u.active ? 'Disable' : 'Enable'}
                    </button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/* ── Sub-components ──────────────────────────────────────────────────────────── */

const AVATAR_GRADIENTS = [
  '#6366f1,#8b5cf6', '#06b6d4,#22d3ee', '#10b981,#34d399',
  '#f59e0b,#fbbf24', '#ec4899,#f472b6', '#f97316,#fb923c',
]

function StatCard({ label, value, icon, gradient, glow, delay }) {
  const [hovered, setHovered] = useState(false)
  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: 'var(--card)', border: '1px solid var(--border)',
        borderRadius: '14px', padding: '1.4rem',
        transition: 'all 0.25s ease',
        transform: hovered ? 'translateY(-3px)' : 'none',
        boxShadow: hovered ? `0 12px 32px ${glow}` : 'none',
        borderColor: hovered ? glow.replace('0.2', '0.35') : 'var(--border)',
        animation: `fadeInUp 0.5s ${delay} ease both`,
      }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.875rem' }}>
        <div style={{
          width: 38, height: 38, borderRadius: '10px', background: gradient,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '16px', color: 'white', boxShadow: `0 4px 14px ${glow}`,
        }}>{icon}</div>
        <span style={{ fontSize: '0.65rem', fontWeight: 600, color: 'var(--text-3)',
          textTransform: 'uppercase', letterSpacing: '0.08em' }}>{label}</span>
      </div>
      <div style={{
        fontSize: '2rem', fontWeight: 800,
        background: gradient, WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent', backgroundClip: 'text',
      }}>{value}</div>
    </div>
  )
}

function SkeletonRow({ count }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: `repeat(${count}, 1fr)`, gap: '1rem', marginBottom: '2rem' }}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height: 110, borderRadius: '14px' }} />
      ))}
    </div>
  )
}

function SkeletonBlock({ height }) {
  return <div className="skeleton" style={{ height, borderRadius: '14px' }} />
}

function SkeletonList({ rows }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height: 52, borderRadius: '10px' }} />
      ))}
    </div>
  )
}

function EmptyState({ label }) {
  return (
    <div style={{
      background: 'var(--card)', border: '1px solid var(--border)',
      borderRadius: '14px', padding: '3rem', textAlign: 'center',
    }}>
      <div style={{ fontWeight: 600, color: 'var(--text-2)' }}>{label}</div>
    </div>
  )
}
