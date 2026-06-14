import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'

const ROLE_BADGE = {
  ADMIN:               { label: 'Admin',   color: '#a78bfa', bg: 'rgba(167,139,250,0.1)', border: 'rgba(167,139,250,0.3)' },
  ENGINEERING_MANAGER: { label: 'Manager', color: '#22d3ee', bg: 'rgba(34,211,238,0.08)', border: 'rgba(34,211,238,0.25)' },
  DEVELOPER:           { label: 'Dev',     color: '#818cf8', bg: 'rgba(99,102,241,0.1)',  border: 'rgba(99,102,241,0.25)' },
}

function navItems(role) {
  const items = [
    { to: '/pipeline',            icon: '◈', label: 'All Pipelines' },
    { to: '/review',              icon: '◎', label: 'AI Code Review' },
    { to: '/search',              icon: '⌕', label: 'Search' },
  ]

  const dashboards = []
  if (role === 'DEVELOPER' || role === 'ENGINEERING_MANAGER' || role === 'ADMIN') {
    dashboards.push({ to: '/dashboard/developer', icon: '⬡', label: 'My Dashboard' })
  }
  if (role === 'ENGINEERING_MANAGER' || role === 'ADMIN') {
    dashboards.push({ to: '/dashboard/manager', icon: '◉', label: 'Team Overview' })
  }
  if (role === 'ADMIN') {
    dashboards.push({ to: '/dashboard/admin', icon: '✦', label: 'Admin Panel' })
  }

  return { dashboards, items }
}

export default function Sidebar() {
  const { auth, role, logout } = useAuth()
  const navigate = useNavigate()
  const rb = ROLE_BADGE[role] ?? ROLE_BADGE.DEVELOPER
  const { dashboards, items } = navItems(role)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <nav style={{
      width: '220px',
      background: 'var(--surface)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      flexShrink: 0,
      position: 'sticky',
      top: 0,
      height: '100vh',
    }}>
      {/* Brand */}
      <div style={{ padding: '1.5rem 1.25rem 1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.25rem' }}>
          <div style={{
            width: 28, height: 28, borderRadius: '8px',
            background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '14px', flexShrink: 0,
            boxShadow: '0 4px 12px rgba(99,102,241,0.35)',
          }}>⚡</div>
          <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text)' }}>AI Platform</span>
        </div>
        <div style={{ fontSize: '0.7rem', color: 'var(--text-3)', paddingLeft: '36px' }}>
          Engineering Productivity
        </div>
      </div>

      <div style={{ height: '1px', background: 'var(--border)', margin: '0 1rem 0.75rem' }} />

      {/* Dashboards section */}
      {dashboards.length > 0 && (
        <>
          <NavGroup label="Dashboards" />
          <div style={{ padding: '0 0.75rem', display: 'flex', flexDirection: 'column', gap: '2px', marginBottom: '0.5rem' }}>
            {dashboards.map(({ to, icon, label }) => (
              <SideLink key={to} to={to} icon={icon} label={label} />
            ))}
          </div>
        </>
      )}

      {/* Navigation section */}
      <NavGroup label="Navigation" />
      <div style={{ padding: '0 0.75rem', display: 'flex', flexDirection: 'column', gap: '2px' }}>
        {items.map(({ to, icon, label }) => (
          <SideLink key={to} to={to} icon={icon} label={label} />
        ))}
      </div>

      {/* Bottom: user info + logout */}
      <div style={{ marginTop: 'auto', borderTop: '1px solid var(--border)' }}>
        {/* User card */}
        <div style={{ padding: '0.875rem 1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.5rem' }}>
            <div style={{
              width: 30, height: 30, borderRadius: '9px', flexShrink: 0,
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '0.85rem', fontWeight: 700, color: 'white',
            }}>
              {auth?.email?.[0]?.toUpperCase() ?? '?'}
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{
                fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>{auth?.email?.split('@')[0] ?? '—'}</div>
              <div style={{ fontSize: '0.65rem', color: 'var(--text-3)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {auth?.email ?? ''}
              </div>
            </div>
          </div>

          {/* Role badge */}
          <div style={{ marginBottom: '0.75rem' }}>
            <span style={{
              display: 'inline-block', padding: '2px 10px', borderRadius: '999px',
              fontSize: '0.68rem', fontWeight: 700, letterSpacing: '0.06em',
              background: rb.bg, border: `1px solid ${rb.border}`, color: rb.color,
            }}>{rb.label}</span>
          </div>

          {/* Logout */}
          <button onClick={handleLogout} style={{
            width: '100%', padding: '0.5rem', borderRadius: '8px',
            fontSize: '0.78rem', fontWeight: 600,
            background: 'rgba(239,68,68,0.07)', border: '1px solid rgba(239,68,68,0.2)',
            color: '#f87171', transition: 'all 0.2s', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem',
          }}
            onMouseEnter={e => {
              e.currentTarget.style.background = 'rgba(239,68,68,0.14)'
              e.currentTarget.style.borderColor = 'rgba(239,68,68,0.4)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.background = 'rgba(239,68,68,0.07)'
              e.currentTarget.style.borderColor = 'rgba(239,68,68,0.2)'
            }}>
            ⎋ Sign out
          </button>
        </div>
      </div>
    </nav>
  )
}

function NavGroup({ label }) {
  return (
    <div style={{
      padding: '0 1.25rem 0.4rem', fontSize: '0.65rem', fontWeight: 600,
      color: 'var(--text-3)', textTransform: 'uppercase', letterSpacing: '0.1em',
    }}>{label}</div>
  )
}

function SideLink({ to, icon, label }) {
  return (
    <NavLink to={to} end style={({ isActive }) => ({
      display: 'flex', alignItems: 'center', gap: '0.6rem',
      padding: '0.55rem 0.75rem', borderRadius: '8px',
      fontSize: '0.875rem', fontWeight: 500,
      background: isActive ? 'var(--primary-dim)' : 'transparent',
      color: isActive ? '#a5b4fc' : 'var(--text-2)',
      transition: 'all 0.15s',
      border: isActive ? '1px solid rgba(99,102,241,0.3)' : '1px solid transparent',
    })}>
      <span style={{ fontSize: '1rem', width: 18, textAlign: 'center' }}>{icon}</span>
      {label}
    </NavLink>
  )
}
