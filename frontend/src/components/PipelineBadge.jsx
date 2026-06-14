const colors = {
  DONE:    { bg: '#14532d', text: '#4ade80' },
  PENDING: { bg: '#1e3a5f', text: '#60a5fa' },
  FAILED:  { bg: '#7f1d1d', text: '#f87171' },
}

export default function PipelineBadge({ status }) {
  const c = colors[status] || colors.PENDING
  return (
    <span style={{
      background: c.bg, color: c.text,
      padding: '2px 10px', borderRadius: '999px',
      fontSize: '0.75rem', fontWeight: 600,
    }}>
      {status}
    </span>
  )
}
