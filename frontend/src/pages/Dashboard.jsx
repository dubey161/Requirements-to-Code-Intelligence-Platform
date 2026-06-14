import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import RiskBadge from '../components/RiskBadge.jsx'
import { apiFetch } from '../api/client.js'

const TYPE_COLORS = {
  ALGORITHM:       { bg: 'rgba(245,158,11,0.15)', text: '#f59e0b' },
  MICROSERVICE:    { bg: 'rgba(6,182,212,0.12)',  text: '#06b6d4' },
  BATCH_JOB:       { bg: 'rgba(139,92,246,0.12)', text: '#a78bfa' },
  CLI_APPLICATION: { bg: 'rgba(16,185,129,0.12)', text: '#10b981' },
  LIBRARY:         { bg: 'rgba(236,72,153,0.12)', text: '#ec4899' },
  REST_API:        { bg: 'rgba(99,102,241,0.12)', text: '#818cf8' },
}

const STAGES = [
  { key: 'intake',         label: 'Intake' },
  { key: 'analysis',       label: 'Analysis' },
  { key: 'codeGeneration', label: 'Code Gen' },
  { key: 'githubPr',       label: 'GitHub PR' },
  { key: 'compliance',     label: 'Compliance' },
  { key: 'riskScoring',    label: 'Risk' },
]

export default function Dashboard() {
  const navigate = useNavigate()
  const [pipelines, setPipelines] = useState([])
  const [loading, setLoading] = useState(true)
  const [offline, setOffline] = useState(false)
  const [error, setError] = useState(null)
  const [importModal, setImportModal] = useState(false)
  const [importKey, setImportKey] = useState('')
  const [importing, setImporting] = useState(false)
  const [importError, setImportError] = useState(null)

  const handleImport = async () => {
    const raw = importKey.trim()
    if (!raw) return
    // Accept full Jira URL or bare key
    const key = raw.includes('/') ? raw.split('/').filter(Boolean).pop().toUpperCase() : raw.toUpperCase()
    setImporting(true)
    setImportError(null)
    try {
      const res = await apiFetch(`/api/v1/requirements/import/jira/${key}`, { method: 'POST' })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }))
        throw new Error(err.message || res.statusText)
      }
      const req = await res.json()
      setImportModal(false)
      setImportKey('')
      navigate(`/requirements/${req.id}`)
    } catch (e) {
      setImportError(String(e))
    } finally {
      setImporting(false)
    }
  }

  const [esStats, setEsStats] = useState(null)

  useEffect(() => {
    apiFetch('/api/v1/requirements/pipeline')
      .then(r => {
        if (r.status === 502 || r.status === 503 || r.status === 0) { setOffline(true); return null }
        if (!r.ok) throw new Error(r.status)
        return r.json()
      })
      .then(data => { if (data) setPipelines(data) })
      .catch(e => {
        if (e?.message?.includes('fetch') || String(e).includes('ECONNREFUSED')) setOffline(true)
        else setError(String(e))
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    apiFetch('/api/v1/search/stats')
      .then(r => r.ok ? r.json() : null)
      .then(data => { if (data) setEsStats(data) })
      .catch(() => {})
  }, [])

  // Stats
  const total = pipelines.length
  const analyzed = pipelines.filter(p => p.analysis?.status === 'DONE').length
  const withPr = pipelines.filter(p => p.githubPr?.status === 'DONE').length
  const critical = pipelines.filter(p => p.riskLevel === 'CRITICAL').length

  return (
    <div style={{ padding: '2rem', maxWidth: '1400px' }}>

      {/* Header */}
      <div style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
          <div>
            <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text)', marginBottom: '0.25rem' }}>
              Pipeline Dashboard
            </h1>
            <p style={{ color: 'var(--text-3)', fontSize: '0.875rem' }}>
              Requirement → Code → PR → Compliance → Risk
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              onClick={() => { setImportModal(true); setImportKey(''); setImportError(null) }}
              style={{ padding: '0.5rem 1rem', borderRadius: '8px', fontSize: '0.8rem',
                background: 'linear-gradient(135deg,#6366f1,#8b5cf6)', border: 'none',
                color: 'white', cursor: 'pointer', fontWeight: 600 }}>
              + Import from Jira
            </button>
            <a href="http://localhost:8081/actuator/health" target="_blank" rel="noreferrer"
              style={{ padding: '0.5rem 1rem', borderRadius: '8px', fontSize: '0.8rem',
                background: 'var(--card)', border: '1px solid var(--border)', color: 'var(--text-2)' }}>
              Health ↗
            </a>
          </div>

          {/* ── Jira Import Modal ── */}
          {importModal && (
            <div style={{ position:'fixed',inset:0,background:'rgba(0,0,0,0.6)',zIndex:1000,
              display:'flex',alignItems:'center',justifyContent:'center' }}
              onClick={e => e.target === e.currentTarget && setImportModal(false)}>
              <div style={{ background:'var(--card)',border:'1px solid var(--border)',borderRadius:'16px',
                padding:'2rem',width:'420px',boxShadow:'0 25px 60px rgba(0,0,0,0.5)' }}>
                <h2 style={{ margin:'0 0 0.5rem',fontSize:'1.1rem',fontWeight:700,color:'var(--text)' }}>
                  Import from Jira
                </h2>
                <p style={{ margin:'0 0 1.25rem',color:'var(--text-3)',fontSize:'0.85rem' }}>
                  Enter a Jira issue key. The requirement will be imported, saved, and analysis triggered automatically.
                </p>
                <label style={{ display:'block',fontSize:'0.75rem',fontWeight:600,color:'var(--text-2)',
                  textTransform:'uppercase',letterSpacing:'0.05em',marginBottom:'0.4rem' }}>
                  Jira Issue Key
                </label>
                <input
                  autoFocus
                  placeholder="e.g. KAN-4"
                  value={importKey}
                  onChange={e => setImportKey(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleImport()}
                  style={{ width:'100%',boxSizing:'border-box',background:'var(--surface)',
                    border:'1px solid var(--border)',borderRadius:'8px',padding:'0.65rem 0.875rem',
                    color:'var(--text)',fontSize:'1rem',outline:'none',marginBottom:'0.5rem' }}
                />
                {importError && (
                  <div style={{ color:'#f87171',fontSize:'0.8rem',marginBottom:'0.75rem' }}>{importError}</div>
                )}
                <div style={{ display:'flex',gap:'0.75rem',marginTop:'1rem' }}>
                  <button onClick={() => setImportModal(false)}
                    style={{ flex:1,padding:'0.65rem',borderRadius:'8px',border:'1px solid var(--border)',
                      background:'var(--surface)',color:'var(--text-2)',cursor:'pointer',fontWeight:600 }}>
                    Cancel
                  </button>
                  <button onClick={handleImport} disabled={importing || !importKey.trim()}
                    style={{ flex:1,padding:'0.65rem',borderRadius:'8px',border:'none',
                      background:'linear-gradient(135deg,#6366f1,#8b5cf6)',color:'white',
                      cursor: importing ? 'wait' : 'pointer',fontWeight:600,opacity: importing ? 0.7 : 1 }}>
                    {importing ? 'Importing...' : 'Import'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Offline banner */}
      {offline && (
        <div style={{
          background: '#1c1108', border: '1px solid #78350f', borderRadius: '10px',
          padding: '1.25rem 1.5rem', marginBottom: '2rem',
          display: 'flex', alignItems: 'flex-start', gap: '1rem',
        }}>
          <span style={{ fontSize: '1.25rem', marginTop: '2px' }}>⚠️</span>
          <div>
            <div style={{ fontWeight: 600, color: '#fcd34d', marginBottom: '0.4rem' }}>
              Backend is offline
            </div>
            <div style={{ color: '#d97706', fontSize: '0.85rem', lineHeight: 1.6 }}>
              The API server is not reachable at <code style={{ background: '#2c1a00', padding: '1px 6px', borderRadius: '4px' }}>localhost:8081</code>.
              Start it with:
              <pre style={{ background: '#2c1a00', padding: '0.5rem 0.75rem', borderRadius: '6px',
                marginTop: '0.5rem', fontSize: '0.8rem', color: '#fbbf24' }}>
                mvn spring-boot:run
              </pre>
            </div>
          </div>
        </div>
      )}

      {/* Error banner */}
      {error && (
        <div style={{ background: 'var(--red-dim)', border: '1px solid #7f1d1d', borderRadius: '10px',
          padding: '1rem 1.25rem', marginBottom: '2rem', color: '#f87171', fontSize: '0.875rem' }}>
          Error: {error}
        </div>
      )}

      {/* Stats row */}
      {!offline && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '1rem', marginBottom: '1rem' }}>
          <StatCard label="Total Requirements" value={loading ? '—' : total} icon="◈" color="#6366f1" />
          <StatCard label="Analyzed" value={loading ? '—' : analyzed} icon="◎" color="#10b981" />
          <StatCard label="Pull Requests" value={loading ? '—' : withPr} icon="⑂" color="#60a5fa" />
          <StatCard label="Critical Risk" value={loading ? '—' : critical} icon="⚑" color={critical > 0 ? '#ef4444' : '#10b981'} />
          <Link to="/search" style={{ textDecoration: 'none' }}>
            <StatCard
              label="ES Indexed"
              value={esStats ? `${esStats.requirementDocs}r · ${esStats.codeDocs}c` : '—'}
              icon="⌕"
              color="#f59e0b"
              sub={esStats ? 'click to search' : 'connecting…'}
            />
          </Link>
        </div>
      )}
      {/* ES status bar */}
      {esStats && (
        <div style={{ display:'flex', alignItems:'center', gap:'0.75rem', marginBottom:'1.5rem',
          background:'rgba(245,158,11,0.06)', border:'1px solid rgba(245,158,11,0.2)',
          borderRadius:'10px', padding:'0.6rem 1rem', fontSize:'0.8rem' }}>
          <span style={{ color:'#f59e0b', fontWeight:700 }}>⌕ Elasticsearch</span>
          <span style={{ color:'var(--text-3)' }}>live ·</span>
          <span style={{ color:'var(--text-2)' }}>{esStats.requirementDocs} requirements indexed</span>
          <span style={{ color:'var(--border)' }}>·</span>
          <span style={{ color:'var(--text-2)' }}>{esStats.codeDocs} code files indexed</span>
          <span style={{ color:'var(--border)' }}>·</span>
          <span style={{ color:'var(--text-3)' }}>semantic search enabled</span>
          <Link to="/search" style={{ marginLeft:'auto', color:'#f59e0b', fontWeight:600, textDecoration:'none', fontSize:'0.75rem' }}>
            Search →
          </Link>
        </div>
      )}

      {/* Loading */}
      {loading && !offline && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', color: 'var(--text-3)',
          padding: '3rem', justifyContent: 'center' }}>
          <Spinner /> Loading pipelines...
        </div>
      )}

      {/* Empty state */}
      {!loading && !offline && !error && pipelines.length === 0 && (
        <div style={{ textAlign: 'center', padding: '4rem 2rem',
          background: 'var(--card)', borderRadius: '12px', border: '1px solid var(--border)' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '1rem' }}>◈</div>
          <div style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: '0.5rem' }}>No requirements yet</div>
          <div style={{ color: 'var(--text-3)', marginBottom: '1.5rem', fontSize: '0.875rem' }}>
            Import a Jira ticket or create one via the API to start the pipeline.
          </div>
          <code style={{ background: 'var(--surface)', border: '1px solid var(--border)',
            padding: '0.75rem 1rem', borderRadius: '8px', fontSize: '0.8rem', color: '#a5b4fc',
            display: 'block', maxWidth: '480px', margin: '0 auto', textAlign: 'left' }}>
            POST /api/v1/requirements/import/jira/PROJ-1
          </code>
        </div>
      )}

      {/* Pipeline table */}
      {!loading && pipelines.length > 0 && (
        <div style={{ background: 'var(--card)', borderRadius: '12px',
          border: '1px solid var(--border)', overflow: 'hidden' }}>

          {/* Table header */}
          <div style={{ padding: '1rem 1.5rem', borderBottom: '1px solid var(--border)',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-2)' }}>
              {total} requirement{total !== 1 ? 's' : ''} tracked
            </span>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border)' }}>
                  <TH>Ticket</TH>
                  <TH>Title</TH>
                  <TH>Pipeline</TH>
                  <TH>Risk</TH>
                  <TH>Score</TH>
                  <TH>PR</TH>
                </tr>
              </thead>
              <tbody>
                {pipelines.map(p => (
                  <tr key={p.requirementId}
                    style={{ borderBottom: '1px solid var(--border)', transition: 'background 0.1s' }}
                    onMouseEnter={e => e.currentTarget.style.background = 'var(--surface)'}
                    onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                    <TD>
                      <Link to={`/requirements/${p.requirementId}`}
                        style={{ color: '#818cf8', fontWeight: 600, fontSize: '0.8rem',
                          fontFamily: 'monospace', background: 'var(--primary-dim)',
                          padding: '2px 8px', borderRadius: '5px' }}>
                        {p.externalKey}
                      </Link>
                    </TD>
                    <TD style={{ maxWidth: '240px' }}>
                      <Link to={`/requirements/${p.requirementId}`}
                        style={{ color: 'var(--text)', fontWeight: 500 }}>
                        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {p.title}
                        </div>
                      </Link>
                      {p.requirementType && p.requirementType !== 'SPRING_CRUD' && (
                        <span style={{
                          fontSize: '0.65rem', padding: '1px 6px', borderRadius: '4px', marginTop: '2px',
                          display: 'inline-block', fontWeight: 700,
                          background: TYPE_COLORS[p.requirementType]?.bg ?? 'rgba(99,102,241,0.12)',
                          color: TYPE_COLORS[p.requirementType]?.text ?? '#818cf8',
                        }}>
                          {p.requirementType}
                        </span>
                      )}
                    </TD>
                    <TD>
                      <PipelineFlow pipeline={p} />
                    </TD>
                    <TD><RiskBadge level={p.riskLevel} score={p.riskScore} /></TD>
                    <TD>
                      {p.complianceScore != null
                        ? <ScoreBar score={p.complianceScore} />
                        : <span style={{ color: 'var(--text-3)' }}>—</span>}
                    </TD>
                    <TD>
                      {p.prUrl
                        ? <a href={p.prUrl} target="_blank" rel="noreferrer"
                            style={{ color: '#60a5fa', fontSize: '0.8rem',
                              display: 'flex', alignItems: 'center', gap: '4px' }}>
                            #{p.prNumber} ↗
                          </a>
                        : <span style={{ color: 'var(--text-3)' }}>—</span>}
                    </TD>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

function PipelineFlow({ pipeline }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
      {STAGES.map((s, i) => {
        const stage = pipeline[s.key]
        const status = stage?.status
        const color = status === 'DONE' ? '#10b981' : status === 'FAILED' ? '#ef4444' : '#1e3352'
        const textColor = status === 'DONE' ? '#10b981' : status === 'FAILED' ? '#ef4444' : '#334155'
        return (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
            <div title={`${s.label}: ${status || 'PENDING'}`} style={{
              width: 9, height: 9, borderRadius: '50%',
              background: color, flexShrink: 0,
              boxShadow: status === 'DONE' ? '0 0 6px rgba(16,185,129,0.4)' : 'none',
            }} />
            {i < STAGES.length - 1 && (
              <div style={{ width: 10, height: 1, background: textColor, flexShrink: 0 }} />
            )}
          </div>
        )
      })}
    </div>
  )
}

function ScoreBar({ score }) {
  const color = score >= 80 ? '#10b981' : score >= 60 ? '#f59e0b' : '#ef4444'
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <div style={{ width: 52, height: 4, background: 'var(--border)', borderRadius: '2px', overflow: 'hidden' }}>
        <div style={{ width: `${score}%`, height: '100%', background: color, transition: 'width 0.3s' }} />
      </div>
      <span style={{ fontSize: '0.75rem', color, fontWeight: 600 }}>{score}</span>
    </div>
  )
}

function StatCard({ label, value, icon, color, sub }) {
  return (
    <div style={{
      background: 'var(--card)', border: '1px solid var(--border)', borderRadius: '10px',
      padding: '1.1rem 1.25rem', height: '100%', boxSizing: 'border-box',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-3)', fontWeight: 500, textTransform: 'uppercase',
          letterSpacing: '0.05em' }}>{label}</span>
        <span style={{ fontSize: '1rem', color }}>{icon}</span>
      </div>
      <div style={{ fontSize: '1.5rem', fontWeight: 700, color }}>{value}</div>
      {sub && <div style={{ fontSize: '0.7rem', color: 'var(--text-3)', marginTop: '0.25rem' }}>{sub}</div>}
    </div>
  )
}

function TH({ children }) {
  return <th style={{ padding: '0.75rem 1.25rem', textAlign: 'left', color: 'var(--text-3)',
    fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.08em',
    whiteSpace: 'nowrap', background: 'var(--surface)' }}>{children}</th>
}

function TD({ children, style }) {
  return <td style={{ padding: '0.875rem 1.25rem', color: 'var(--text-2)', ...style }}>{children}</td>
}

function Spinner() {
  return (
    <div style={{ width: 18, height: 18, border: '2px solid var(--border)',
      borderTopColor: 'var(--primary)', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
    </div>
  )
}
