import { useEffect, useState, useRef } from 'react'
import { Link } from 'react-router-dom'
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

/* ── Risk palette ───────────────────────────────────────────────────────────── */
const RISK_COLOR = {
  LOW:      { bg: 'rgba(16,185,129,0.1)',  border: '#065f46', text: '#34d399', bar: '#34d399' },
  MEDIUM:   { bg: 'rgba(251,191,36,0.08)', border: '#78350f', text: '#fbbf24', bar: '#fbbf24' },
  HIGH:     { bg: 'rgba(249,115,22,0.1)',  border: '#7c2d12', text: '#fb923c', bar: '#fb923c' },
  CRITICAL: { bg: 'rgba(239,68,68,0.1)',   border: '#7f1d1d', text: '#f87171', bar: '#f87171' },
}

export default function ManagerDashboard() {
  const { auth } = useAuth()
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    apiFetch('/api/v1/dashboard/manager')
      .then(r => r.ok ? r.json() : null)
      .then(d => { if (d) setData(d) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const totalReqs  = useCountUp(data?.totalRequirements  ?? 0)
  const totalPrs   = useCountUp(data?.totalPullRequests  ?? 0)
  const avgScore   = useCountUp(data?.avgComplianceScore ?? 0)
  const analyzed   = useCountUp(data?.analyzedCount      ?? 0)

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', animation: 'fadeIn 0.4s ease' }}>

      {/* ── Header ── */}
      <div style={{ marginBottom: '2rem', animation: 'fadeInUp 0.5s ease' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.375rem' }}>
              <div style={{
                padding: '4px 12px', borderRadius: '999px', fontSize: '0.72rem', fontWeight: 700,
                background: 'rgba(34,211,238,0.1)', border: '1px solid rgba(34,211,238,0.3)',
                color: '#22d3ee', textTransform: 'uppercase', letterSpacing: '0.08em',
              }}>Engineering Manager</div>
            </div>
            <h1 style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text)' }}>
              Team Overview
              <span style={{
                background: 'linear-gradient(135deg,#06b6d4,#22d3ee)',
                WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text',
              }}> Dashboard</span>
            </h1>
            <p style={{ color: 'var(--text-3)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
              Aggregate pipeline health, compliance trends, and contributor activity
            </p>
          </div>
          <Link to="/pipeline" className="btn btn-ghost" style={{ fontSize: '0.825rem' }}>
            View all pipelines →
          </Link>
        </div>
      </div>

      {/* ── Stat cards ── */}
      {loading ? <SkeletonRow count={4} /> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px,1fr))', gap: '1rem', marginBottom: '2rem' }}>
          <StatCard label="Total Requirements" value={totalReqs}
            icon="◈" gradient="linear-gradient(135deg,#6366f1,#8b5cf6)" glow="rgba(99,102,241,0.2)" delay="0.05s" />
          <StatCard label="Pull Requests Opened" value={totalPrs}
            icon="⑂" gradient="linear-gradient(135deg,#06b6d4,#22d3ee)" glow="rgba(34,211,238,0.2)" delay="0.10s" />
          <StatCard label="Avg Compliance Score"
            value={typeof data?.avgComplianceScore === 'number' ? `${avgScore}%` : '—'}
            icon="◉" gradient="linear-gradient(135deg,#10b981,#34d399)" glow="rgba(52,211,153,0.2)" delay="0.15s" />
          <StatCard label="Analyzed Requirements" value={analyzed}
            icon="✦" gradient="linear-gradient(135deg,#a78bfa,#8b5cf6)" glow="rgba(167,139,250,0.2)" delay="0.20s" />
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '2rem' }}>

        {/* ── Risk Distribution ── */}
        <div style={{ animation: 'fadeInUp 0.5s 0.25s ease both' }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem', color: 'var(--text)' }}>
            Risk Distribution
          </h2>
          {loading ? <SkeletonBlock height={200} /> : (
            <div style={{
              background: 'var(--card)', border: '1px solid var(--border)',
              borderRadius: '14px', padding: '1.5rem',
            }}>
              {data?.riskDistribution && Object.keys(RISK_COLOR).map(level => {
                const count = data.riskDistribution[level] ?? 0
                const total = Object.values(data.riskDistribution).reduce((a, b) => a + b, 0) || 1
                const pct   = Math.round((count / total) * 100)
                const c     = RISK_COLOR[level]
                return (
                  <div key={level} style={{ marginBottom: '1rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
                      <span style={{
                        fontSize: '0.75rem', fontWeight: 600,
                        padding: '2px 8px', borderRadius: '999px',
                        background: c.bg, border: `1px solid ${c.border}`, color: c.text,
                      }}>{level}</span>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-2)', fontWeight: 600 }}>
                        {count} <span style={{ color: 'var(--text-3)' }}>({pct}%)</span>
                      </span>
                    </div>
                    <div style={{ height: 6, background: 'var(--surface)', borderRadius: 3, overflow: 'hidden' }}>
                      <div style={{
                        height: '100%', width: `${pct}%`, borderRadius: 3,
                        background: c.bar,
                        animation: 'progressFill 0.8s ease both',
                        boxShadow: `0 0 8px ${c.bar}55`,
                      }} />
                    </div>
                  </div>
                )
              })}
              {(!data?.riskDistribution || Object.keys(data.riskDistribution).length === 0) && (
                <div style={{ textAlign: 'center', color: 'var(--text-3)', padding: '2rem 0', fontSize: '0.875rem' }}>
                  No risk data yet
                </div>
              )}
            </div>
          )}
        </div>

        {/* ── Status Breakdown ── */}
        <div style={{ animation: 'fadeInUp 0.5s 0.30s ease both' }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem', color: 'var(--text)' }}>
            Pipeline Status
          </h2>
          {loading ? <SkeletonBlock height={200} /> : (
            <div style={{
              background: 'var(--card)', border: '1px solid var(--border)',
              borderRadius: '14px', padding: '1.5rem',
            }}>
              {data?.statusBreakdown && Object.entries(data.statusBreakdown).map(([status, count]) => {
                const total = Object.values(data.statusBreakdown).reduce((a, b) => a + b, 0) || 1
                const pct = Math.round((count / total) * 100)
                const color = STATUS_COLOR_MAP[status] ?? '#64748b'
                return (
                  <div key={status} style={{ marginBottom: '1rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color }}>
                        {status.replace(/_/g, ' ')}
                      </span>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-2)', fontWeight: 600 }}>
                        {count} <span style={{ color: 'var(--text-3)' }}>({pct}%)</span>
                      </span>
                    </div>
                    <div style={{ height: 6, background: 'var(--surface)', borderRadius: 3, overflow: 'hidden' }}>
                      <div style={{
                        height: '100%', width: `${pct}%`, borderRadius: 3,
                        background: color,
                        animation: 'progressFill 0.8s ease both',
                        boxShadow: `0 0 8px ${color}44`,
                      }} />
                    </div>
                  </div>
                )
              })}
              {(!data?.statusBreakdown || Object.keys(data.statusBreakdown).length === 0) && (
                <div style={{ textAlign: 'center', color: 'var(--text-3)', padding: '2rem 0', fontSize: '0.875rem' }}>
                  No pipeline data yet
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── Top Contributors ── */}
      <div style={{ animation: 'fadeInUp 0.5s 0.35s ease both' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text)' }}>Top Contributors</h2>
        </div>
        {loading ? <SkeletonList rows={5} /> : !data?.topContributors?.length ? (
          <EmptyState label="No contributor data yet" />
        ) : (
          <div style={{
            background: 'var(--card)', border: '1px solid var(--border)',
            borderRadius: '14px', overflow: 'hidden',
          }}>
            {data.topContributors.map((c, i) => (
              <div key={c.userId ?? i} style={{
                display: 'flex', alignItems: 'center', gap: '1rem',
                padding: '0.875rem 1.25rem',
                borderBottom: i < data.topContributors.length - 1 ? '1px solid var(--border)' : 'none',
                animation: `fadeInUp 0.4s ${i * 0.06}s ease both`,
              }}>
                {/* Rank */}
                <div style={{
                  width: 28, height: 28, borderRadius: '50%', flexShrink: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: i < 3 ? MEDAL_BG[i] : 'var(--surface)',
                  fontSize: '0.75rem', fontWeight: 700,
                  color: i < 3 ? MEDAL_TEXT[i] : 'var(--text-3)',
                  border: i < 3 ? `1px solid ${MEDAL_BORDER[i]}` : '1px solid var(--border)',
                }}>
                  {i === 0 ? '1' : i === 1 ? '2' : i === 2 ? '3' : i + 1}
                </div>

                {/* Avatar */}
                <div style={{
                  width: 34, height: 34, borderRadius: '10px', flexShrink: 0,
                  background: `linear-gradient(135deg, ${AVATAR_COLORS[i % AVATAR_COLORS.length]})`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '0.9rem', fontWeight: 700, color: 'white',
                }}>
                  {(c.email ?? '?')[0].toUpperCase()}
                </div>

                {/* Email */}
                <span style={{ flex: 1, color: 'var(--text)', fontWeight: 500, fontSize: '0.875rem',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {c.email}
                </span>

                {/* Stats */}
                <div style={{ display: 'flex', gap: '1rem', flexShrink: 0 }}>
                  <Chip label={`${c.requirementCount} reqs`} color="#6366f1" />
                  {c.prCount > 0 && <Chip label={`${c.prCount} PRs`} color="#22d3ee" />}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/* ── Helpers ─────────────────────────────────────────────────────────────────── */

const STATUS_COLOR_MAP = {
  RECEIVED:         '#64748b',
  ANALYSIS_PENDING: '#fbbf24',
  ANALYZED:         '#34d399',
  ANALYSIS_FAILED:  '#f87171',
}

const MEDAL_BG     = ['rgba(251,191,36,0.15)', 'rgba(148,163,184,0.12)', 'rgba(249,115,22,0.12)']
const MEDAL_BORDER = ['rgba(251,191,36,0.5)',  'rgba(148,163,184,0.4)', 'rgba(249,115,22,0.4)']
const MEDAL_TEXT   = ['#fbbf24', '#94a3b8', '#fb923c']
const AVATAR_COLORS = [
  '#6366f1,#8b5cf6', '#06b6d4,#22d3ee', '#10b981,#34d399',
  '#f59e0b,#fbbf24', '#ec4899,#f472b6', '#f97316,#fb923c',
]

function Chip({ label, color }) {
  return (
    <span style={{
      padding: '2px 8px', borderRadius: '6px', fontSize: '0.72rem', fontWeight: 600,
      background: `${color}18`, border: `1px solid ${color}35`, color,
    }}>{label}</span>
  )
}

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
      <div style={{ fontWeight: 600, color: 'var(--text-2)', marginBottom: '0.5rem' }}>{label}</div>
    </div>
  )
}
