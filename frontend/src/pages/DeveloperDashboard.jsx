import { useEffect, useState, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import { apiFetch } from '../api/client.js'

/* ── Count-up animation hook ────────────────────────────────────────────────── */
function useCountUp(target, duration = 900) {
  const [value, setValue] = useState(0)
  const raf = useRef(null)

  useEffect(() => {
    if (typeof target !== 'number') return
    const start = Date.now()
    const tick = () => {
      const p = Math.min((Date.now() - start) / duration, 1)
      const eased = 1 - Math.pow(1 - p, 3) // ease-out cubic
      setValue(Math.round(eased * target))
      if (p < 1) raf.current = requestAnimationFrame(tick)
    }
    raf.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf.current)
  }, [target, duration])

  return value
}

/* ── Status colors ───────────────────────────────────────────────────────────── */
const STATUS_COLOR = {
  ANALYZED:         { bg: 'var(--green-dim)',  border: '#065f46', text: '#34d399' },
  ANALYSIS_PENDING: { bg: 'rgba(251,191,36,0.08)', border: '#78350f', text: '#fbbf24' },
  ANALYSIS_FAILED:  { bg: 'var(--red-dim)',    border: '#7f1d1d', text: '#f87171' },
}

export default function DeveloperDashboard() {
  const { auth, role } = useAuth()
  const [data, setData]     = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    apiFetch('/api/v1/dashboard/developer')
      .then(r => r.ok ? r.json() : null)
      .then(d => { if (d) setData(d) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const total     = useCountUp(data?.requirementsCreated ?? 0)
  const prs       = useCountUp(data?.generatedPrs ?? 0)
  const compliance = useCountUp(
    typeof data?.averageComplianceScore === 'number' ? data.averageComplianceScore : 0
  )

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', animation: 'fadeIn 0.4s ease' }}>

      {/* ── Header ── */}
      <div style={{ marginBottom: '2rem', animation: 'fadeInUp 0.5s ease' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.375rem' }}>
              <div style={{
                padding: '4px 12px', borderRadius: '999px', fontSize: '0.72rem', fontWeight: 700,
                background: 'var(--primary-dim)', border: '1px solid rgba(99,102,241,0.3)',
                color: 'var(--primary-light)', textTransform: 'uppercase', letterSpacing: '0.08em',
              }}>Developer</div>
            </div>
            <h1 style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text)' }}>
              Welcome back
              <span className="text-gradient"> {auth?.email?.split('@')[0]}</span>
            </h1>
            <p style={{ color: 'var(--text-3)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
              Your personal pipeline activity and metrics
            </p>
          </div>
          <Link to="/pipeline" className="btn btn-ghost" style={{ fontSize: '0.825rem' }}>
            View all pipelines →
          </Link>
        </div>
      </div>

      {/* ── Stat cards ── */}
      {loading ? (
        <SkeletonRow count={4} />
      ) : (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '1rem', marginBottom: '2rem',
        }}>
          <StatCard
            label="Requirements Created" value={total}
            icon="◈" gradient="linear-gradient(135deg,#6366f1,#8b5cf6)"
            glow="rgba(99,102,241,0.2)" delay="0.05s"
          />
          <StatCard
            label="Pull Requests Opened" value={prs}
            icon="⑂" gradient="linear-gradient(135deg,#06b6d4,#22d3ee)"
            glow="rgba(34,211,238,0.2)" delay="0.10s"
          />
          <StatCard
            label="Avg Compliance Score"
            value={typeof data?.averageComplianceScore === 'number' ? `${compliance}%` : '—'}
            icon="◉" gradient="linear-gradient(135deg,#10b981,#34d399)"
            glow="rgba(52,211,153,0.2)" delay="0.15s"
          />
          <StatCard
            label="Dominant Risk Level"
            value={data?.dominantRiskLevel ?? '—'}
            icon="⚑"
            gradient={riskGradient(data?.dominantRiskLevel)}
            glow={riskGlow(data?.dominantRiskLevel)}
            delay="0.20s"
          />
        </div>
      )}

      {/* ── Recent requirements ── */}
      <div style={{ animation: 'fadeInUp 0.5s 0.25s ease both' }}>
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          marginBottom: '1rem',
        }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text)' }}>
            Recent Requirements
          </h2>
          <Link to="/pipeline" style={{ fontSize: '0.8rem', color: 'var(--primary-light)' }}>
            See all →
          </Link>
        </div>

        {loading ? (
          <SkeletonList rows={5} />
        ) : !data?.recentRequirements?.length ? (
          <EmptyState />
        ) : (
          <div style={{
            background: 'var(--card)', border: '1px solid var(--border)',
            borderRadius: '14px', overflow: 'hidden',
          }}>
            {data.recentRequirements.map((r, i) => {
              const sc = STATUS_COLOR[r.status] || STATUS_COLOR.ANALYSIS_PENDING
              return (
                <Link key={r.id} to={`/requirements/${r.id}`} style={{
                  display: 'flex', alignItems: 'center', gap: '1rem',
                  padding: '1rem 1.25rem',
                  borderBottom: i < data.recentRequirements.length - 1 ? '1px solid var(--border)' : 'none',
                  transition: 'background 0.15s',
                  animation: `fadeInUp 0.4s ${i * 0.06}s ease both`,
                }}
                  onMouseEnter={e => e.currentTarget.style.background = 'var(--card-hover)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                >
                  {/* Ticket badge */}
                  <span style={{
                    fontFamily: 'monospace', fontSize: '0.78rem', fontWeight: 700,
                    color: 'var(--primary-light)', background: 'var(--primary-dim)',
                    padding: '3px 8px', borderRadius: '5px', flexShrink: 0,
                    border: '1px solid rgba(99,102,241,0.2)',
                  }}>{r.externalKey}</span>

                  {/* Title */}
                  <span style={{
                    flex: 1, color: 'var(--text)', fontWeight: 500,
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>{r.title}</span>

                  {/* Status badge */}
                  <span style={{
                    padding: '3px 10px', borderRadius: '999px', fontSize: '0.7rem', fontWeight: 600,
                    background: sc.bg, border: `1px solid ${sc.border}`, color: sc.text,
                    flexShrink: 0,
                  }}>{r.status.replace(/_/g, ' ')}</span>

                  {/* Date */}
                  <span style={{ color: 'var(--text-3)', fontSize: '0.75rem', flexShrink: 0 }}>
                    {relativeTime(r.createdAt)}
                  </span>
                </Link>
              )
            })}
          </div>
        )}
      </div>

      {/* ── Quick actions ── */}
      <div style={{ marginTop: '2rem', animation: 'fadeInUp 0.5s 0.35s ease both' }}>
        <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>Quick Actions</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem' }}>
          {[
            { label: 'View Pipeline',     to: '/pipeline', icon: '◈', color: '#6366f1' },
            { label: 'AI Code Review',    to: '/review',   icon: '✦', color: '#a78bfa' },
            { label: 'Search Codebase',   to: '/search',   icon: '⌕', color: '#22d3ee' },
          ].map(({ label, to, icon, color }) => (
            <Link key={to} to={to} style={{
              display: 'flex', alignItems: 'center', gap: '0.75rem',
              padding: '1rem 1.25rem',
              background: 'var(--card)', border: '1px solid var(--border)',
              borderRadius: '12px', transition: 'all 0.2s',
              color: 'var(--text-2)', fontWeight: 500, fontSize: '0.875rem',
            }}
              onMouseEnter={e => {
                e.currentTarget.style.borderColor = color + '60'
                e.currentTarget.style.background = 'var(--card-hover)'
                e.currentTarget.style.transform = 'translateY(-1px)'
                e.currentTarget.style.boxShadow = `0 8px 24px ${color}18`
              }}
              onMouseLeave={e => {
                e.currentTarget.style.borderColor = 'var(--border)'
                e.currentTarget.style.background = 'var(--card)'
                e.currentTarget.style.transform = 'none'
                e.currentTarget.style.boxShadow = 'none'
              }}
            >
              <span style={{
                width: 34, height: 34, borderRadius: '8px', flexShrink: 0,
                background: `${color}18`, display: 'flex', alignItems: 'center',
                justifyContent: 'center', fontSize: '16px', color,
              }}>{icon}</span>
              {label}
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}

/* ── StatCard ────────────────────────────────────────────────────────────────── */
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
        borderColor: hovered ? (glow.replace('0.2', '0.35')) : 'var(--border)',
        animation: `fadeInUp 0.5s ${delay} ease both`,
      }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.875rem' }}>
        <div style={{
          width: 38, height: 38, borderRadius: '10px',
          background: gradient, display: 'flex', alignItems: 'center',
          justifyContent: 'center', fontSize: '16px', color: 'white',
          boxShadow: `0 4px 14px ${glow}`,
        }}>{icon}</div>
        <span style={{
          fontSize: '0.65rem', fontWeight: 600, color: 'var(--text-3)',
          textTransform: 'uppercase', letterSpacing: '0.08em',
        }}>{label}</span>
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

function SkeletonList({ rows }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height: 52, borderRadius: '10px' }} />
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div style={{
      background: 'var(--card)', border: '1px solid var(--border)',
      borderRadius: '14px', padding: '3rem', textAlign: 'center',
      animation: 'scaleIn 0.4s ease',
    }}>
      <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>◈</div>
      <div style={{ fontWeight: 600, color: 'var(--text-2)', marginBottom: '0.5rem' }}>No requirements yet</div>
      <div style={{ color: 'var(--text-3)', fontSize: '0.85rem' }}>
        Import a Jira ticket or create a requirement to get started.
      </div>
    </div>
  )
}

function relativeTime(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1)  return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

function riskGradient(level) {
  switch (level) {
    case 'LOW':      return 'linear-gradient(135deg,#10b981,#34d399)'
    case 'MEDIUM':   return 'linear-gradient(135deg,#f59e0b,#fbbf24)'
    case 'HIGH':     return 'linear-gradient(135deg,#f97316,#fb923c)'
    case 'CRITICAL': return 'linear-gradient(135deg,#ef4444,#f87171)'
    default:         return 'linear-gradient(135deg,#475569,#64748b)'
  }
}
function riskGlow(level) {
  switch (level) {
    case 'LOW':      return 'rgba(52,211,153,0.2)'
    case 'MEDIUM':   return 'rgba(251,191,36,0.2)'
    case 'HIGH':     return 'rgba(249,115,22,0.2)'
    case 'CRITICAL': return 'rgba(239,68,68,0.2)'
    default:         return 'rgba(100,116,139,0.15)'
  }
}
