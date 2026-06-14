import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api/client.js'

const EXAMPLES = [
  { label: 'retailer name validation', desc: 'Find requirements about name field rules' },
  { label: 'no special characters', desc: 'Find pattern/format validation constraints' },
  { label: 'GET API endpoint', desc: 'Find requirements with REST GET operations' },
  { label: 'authentication security', desc: 'Find requirements with auth/security rules' },
  { label: '@Pattern regexp', desc: 'Search generated code for @Pattern annotations' },
  { label: 'RetailerService', desc: 'Find generated service class for Retailer entity' },
  { label: 'alphanumeric field constraint', desc: 'Semantic search — finds related docs even without exact keyword match' },
]

const FILE_TYPE_COLOR = {
  ENTITY: '#6366f1',
  REPOSITORY: '#06b6d4',
  SERVICE: '#f59e0b',
  CONTROLLER: '#10b981',
  REQUEST: '#8b5cf6',
  RESPONSE: '#ec4899',
}

export default function Search() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const doSearch = async (q) => {
    const text = (q || query).trim()
    if (!text) return
    setLoading(true)
    setError(null)
    setResults(null)
    try {
      const res = await apiFetch(`/api/v1/search?q=${encodeURIComponent(text)}`)
      if (res.status === 404) {
        setError('Search is disabled. Set ELASTICSEARCH_ENABLED=true in .env and restart.')
        return
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setResults(await res.json())
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  const total = results ? (results.total ?? 0) : 0
  const reqs = results?.requirements ?? []
  const code = results?.generatedCode ?? []

  return (
    <div style={{ padding: '2rem', maxWidth: '960px' }}>
      {/* Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text)', marginBottom: '0.25rem' }}>
          ⌕ Semantic Search
        </h1>
        <p style={{ color: 'var(--text-3)', fontSize: '0.875rem' }}>
          Search across <strong style={{ color: 'var(--text-2)' }}>requirements</strong> (title, description, entity names, endpoints, security)
          and <strong style={{ color: 'var(--text-2)' }}>generated code</strong> (Java class content).
          Uses vector/semantic search when Gemini is configured, otherwise keyword search.
        </p>
      </div>

      {/* Search bar */}
      <form onSubmit={e => { e.preventDefault(); doSearch() }}
        style={{ marginBottom: '1rem', display: 'flex', gap: '0.75rem' }}>
        <input
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="e.g. retailer name, no special characters, @Pattern, GET API..."
          style={{
            flex: 1, padding: '0.65rem 1rem', borderRadius: '8px',
            background: 'var(--card)', border: '1px solid var(--border)',
            color: 'var(--text)', fontSize: '0.875rem', outline: 'none',
          }}
        />
        <button type="submit" disabled={loading || !query.trim()} style={{
          padding: '0.65rem 1.5rem', borderRadius: '8px', fontWeight: 600,
          fontSize: '0.875rem', background: 'var(--primary)', color: '#fff',
          border: 'none', cursor: loading || !query.trim() ? 'not-allowed' : 'pointer',
          opacity: loading || !query.trim() ? 0.5 : 1,
        }}>
          {loading ? 'Searching…' : 'Search'}
        </button>
      </form>

      {/* Example chips */}
      {!results && !loading && (
        <div style={{ marginBottom: '2rem' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-3)', marginBottom: '0.5rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Try these examples
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
            {EXAMPLES.map(ex => (
              <button key={ex.label} title={ex.desc}
                onClick={() => { setQuery(ex.label); doSearch(ex.label) }}
                style={{
                  padding: '0.3rem 0.75rem', borderRadius: '20px', fontSize: '0.8rem',
                  background: 'var(--card)', border: '1px solid var(--border)',
                  color: 'var(--text-2)', cursor: 'pointer', fontFamily: 'inherit',
                }}>
                {ex.label}
              </button>
            ))}
          </div>

          {/* What can be searched info box */}
          <div style={{ marginTop: '1.5rem', background: 'var(--card)', border: '1px solid var(--border)',
            borderRadius: '10px', padding: '1.25rem' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text)', marginBottom: '0.75rem' }}>
              What's indexed in Elasticsearch
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', fontSize: '0.8rem' }}>
              <div>
                <div style={{ color: '#818cf8', fontWeight: 600, marginBottom: '0.4rem' }}>📋 Requirements index</div>
                <ul style={{ color: 'var(--text-3)', margin: 0, paddingLeft: '1.2rem', lineHeight: 1.8 }}>
                  <li>Jira key (e.g. KAN-4)</li>
                  <li>Title &amp; description text</li>
                  <li>Acceptance criteria</li>
                  <li>Entity names (e.g. Retailer)</li>
                  <li>API endpoint paths</li>
                  <li>Validation field names</li>
                  <li>Security categories</li>
                  <li>Compliance &amp; risk scores</li>
                </ul>
              </div>
              <div>
                <div style={{ color: '#06b6d4', fontWeight: 600, marginBottom: '0.4rem' }}>💻 Generated Code index</div>
                <ul style={{ color: 'var(--text-3)', margin: 0, paddingLeft: '1.2rem', lineHeight: 1.8 }}>
                  <li>Full Java source code</li>
                  <li>Class names &amp; annotations</li>
                  <li>@Pattern, @NotBlank, @Size…</li>
                  <li>Method signatures</li>
                  <li>File type (Entity/Service/…)</li>
                  <li>Linked Jira key</li>
                </ul>
              </div>
            </div>
            <div style={{ marginTop: '1rem', padding: '0.6rem 0.875rem', background: 'rgba(245,158,11,0.08)',
              borderRadius: '6px', fontSize: '0.78rem', color: '#f59e0b' }}>
              💡 <strong>Semantic search</strong> (Gemini configured) finds conceptually similar content — e.g. "no spaces allowed" also matches "@Pattern alphanumeric" in generated code.
              Without Gemini, it falls back to keyword matching.
            </div>
          </div>
        </div>
      )}

      {error && (
        <div style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.3)',
          borderRadius: '10px', padding: '1rem 1.25rem', color: '#f87171', fontSize: '0.875rem', marginBottom: '1rem' }}>
          {error}
        </div>
      )}

      {/* Results */}
      {results && (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-3)' }}>
              <strong style={{ color: 'var(--text)' }}>{total}</strong> result{total !== 1 ? 's' : ''} for
              <strong style={{ color: '#818cf8' }}> "{query}"</strong>
              {reqs.length > 0 && <span> · {reqs.length} requirement{reqs.length !== 1 ? 's' : ''}</span>}
              {code.length > 0 && <span> · {code.length} code file{code.length !== 1 ? 's' : ''}</span>}
            </div>
            <button onClick={() => { setResults(null); setQuery('') }}
              style={{ fontSize: '0.75rem', color: 'var(--text-3)', background: 'none', border: 'none', cursor: 'pointer' }}>
              Clear ✕
            </button>
          </div>

          {total === 0 && (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-3)',
              background: 'var(--card)', borderRadius: '10px', border: '1px solid var(--border)' }}>
              No results found. Try a different query or check that the pipeline has run.
            </div>
          )}

          {/* Requirements section */}
          {reqs.length > 0 && (
            <div style={{ marginBottom: '1.5rem' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#818cf8', textTransform: 'uppercase',
                letterSpacing: '0.06em', marginBottom: '0.75rem' }}>
                📋 Requirements ({reqs.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
                {reqs.map((r, i) => (
                  <div key={i} style={{ background: 'var(--card)', border: '1px solid var(--border)',
                    borderRadius: '10px', padding: '1rem 1.25rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
                      <span style={{ background: 'var(--primary-dim)', color: '#818cf8',
                        padding: '2px 8px', borderRadius: '5px', fontSize: '0.75rem', fontFamily: 'monospace', fontWeight: 700 }}>
                        {r.externalKey}
                      </span>
                      <span style={{ fontSize: '0.72rem', padding: '1px 7px', borderRadius: '4px',
                        background: 'rgba(16,185,129,0.1)', color: '#10b981', fontWeight: 600 }}>
                        {r.status}
                      </span>
                      {r.complianceScore != null && (
                        <span style={{ fontSize: '0.72rem', color: 'var(--text-3)' }}>
                          compliance {r.complianceScore}/100
                        </span>
                      )}
                      {r.riskLevel && (
                        <span style={{ fontSize: '0.72rem', color: r.riskLevel === 'HIGH' || r.riskLevel === 'CRITICAL' ? '#ef4444' : '#f59e0b' }}>
                          {r.riskLevel} risk
                        </span>
                      )}
                    </div>
                    <div style={{ fontWeight: 600, color: 'var(--text)', marginBottom: '0.25rem', fontSize: '0.95rem' }}>
                      {r.title}
                    </div>
                    {r.description && (
                      <div style={{ color: 'var(--text-3)', fontSize: '0.82rem', marginBottom: '0.5rem',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {r.description}
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginBottom: '0.6rem' }}>
                      {(r.entityNames || []).map(e => (
                        <span key={e} style={{ fontSize: '0.72rem', padding: '1px 7px', borderRadius: '4px',
                          background: 'rgba(99,102,241,0.12)', color: '#818cf8' }}>entity: {e}</span>
                      ))}
                      {(r.endpointPaths || []).slice(0, 3).map(p => (
                        <span key={p} style={{ fontSize: '0.72rem', padding: '1px 7px', borderRadius: '4px',
                          background: 'rgba(6,182,212,0.1)', color: '#06b6d4', fontFamily: 'monospace' }}>{p}</span>
                      ))}
                      {(r.securityCategories || []).map(s => (
                        <span key={s} style={{ fontSize: '0.72rem', padding: '1px 7px', borderRadius: '4px',
                          background: 'rgba(239,68,68,0.08)', color: '#f87171' }}>{s}</span>
                      ))}
                    </div>
                    <Link to={`/requirements/${r.id}`}
                      style={{ color: '#818cf8', fontSize: '0.8rem', textDecoration: 'none', fontWeight: 600 }}>
                      View pipeline →
                    </Link>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Generated code section */}
          {code.length > 0 && (
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#06b6d4', textTransform: 'uppercase',
                letterSpacing: '0.06em', marginBottom: '0.75rem' }}>
                💻 Generated Code ({code.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
                {code.map((c, i) => (
                  <div key={i} style={{ background: 'var(--card)', border: '1px solid var(--border)',
                    borderRadius: '10px', padding: '1rem 1.25rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.5rem' }}>
                      <span style={{
                        fontSize: '0.72rem', padding: '2px 8px', borderRadius: '5px', fontWeight: 700,
                        background: `${FILE_TYPE_COLOR[c.fileType] ?? '#6366f1'}20`,
                        color: FILE_TYPE_COLOR[c.fileType] ?? '#818cf8',
                      }}>
                        {c.fileType}
                      </span>
                      <span style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-2)', fontWeight: 600 }}>
                        {c.fileName}
                      </span>
                      <span style={{ fontSize: '0.72rem', color: 'var(--text-3)', marginLeft: 'auto' }}>
                        {c.externalKey}
                      </span>
                    </div>
                    {c.content && (
                      <pre style={{
                        margin: 0, fontSize: '0.75rem', color: 'var(--text-3)',
                        overflow: 'hidden', maxHeight: '80px',
                        background: 'rgba(0,0,0,0.15)', borderRadius: '6px',
                        padding: '0.5rem 0.75rem', lineHeight: 1.5,
                        textOverflow: 'ellipsis', whiteSpace: 'pre-wrap',
                        wordBreak: 'break-all',
                      }}>
                        {c.content.slice(0, 300)}{c.content.length > 300 ? '…' : ''}
                      </pre>
                    )}
                    {c.requirementId && (
                      <Link to={`/requirements/${c.requirementId}`}
                        style={{ color: '#06b6d4', fontSize: '0.8rem', textDecoration: 'none',
                          fontWeight: 600, display: 'inline-block', marginTop: '0.5rem' }}>
                        View requirement →
                      </Link>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
