const cfg = {
  LOW:      { bg: 'var(--green-dim)',  border: '#065f46', text: '#34d399', dot: '#10b981' },
  MEDIUM:   { bg: 'var(--yellow-dim)', border: '#78350f', text: '#fcd34d', dot: '#f59e0b' },
  HIGH:     { bg: 'var(--orange-dim)', border: '#7c2d12', text: '#fb923c', dot: '#f97316' },
  CRITICAL: { bg: 'var(--red-dim)',    border: '#7f1d1d', text: '#f87171', dot: '#ef4444' },
}

export default function RiskBadge({ level, score }) {
  if (!level) return <span style={{ color: 'var(--text-3)', fontSize: '0.8rem' }}>—</span>
  const c = cfg[level] || cfg.MEDIUM
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '5px',
      background: c.bg, color: c.text,
      border: `1px solid ${c.border}`,
      padding: '3px 10px', borderRadius: '999px',
      fontSize: '0.72rem', fontWeight: 600,
      whiteSpace: 'nowrap',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: c.dot, flexShrink: 0 }} />
      {level}{score != null ? ` · ${score}` : ''}
    </span>
  )
}
