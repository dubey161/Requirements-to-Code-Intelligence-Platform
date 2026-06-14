import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

const SEV_COLOR = { CRITICAL: '#ef4444', HIGH: '#f97316', MEDIUM: '#eab308', LOW: '#22c55e', INFO: '#60a5fa' }
const CAT_ICON  = { SECURITY: '🔒', VALIDATION: '✓', CODE_SMELL: '⚡', ARCHITECTURE: '⬡', PERFORMANCE: '⚙', REQUIREMENT_GAP: '◎' }

export default function ExternalReview() {
  const navigate = useNavigate()

  const [jiraKey,  setJiraKey]  = useState('')
  const [prNumber, setPrNumber] = useState('')
  const [step,     setStep]     = useState('form')   // form | reviewing | results | importing | done
  const [result,   setResult]   = useState(null)
  const [error,    setError]    = useState(null)
  const [newReqId, setNewReqId] = useState(null)

  // Accepts full Jira URL or bare key — extracts just the issue key (e.g. KAN-4)
  const parseJiraKey = (input) => {
    const trimmed = input.trim()
    // If it looks like a URL, grab the last path segment
    if (trimmed.includes('/')) {
      const parts = trimmed.split('/').filter(Boolean)
      return parts[parts.length - 1].toUpperCase()
    }
    return trimmed.toUpperCase()
  }

  const handleReview = async () => {
    if (!jiraKey.trim() || !prNumber.trim()) { setError('Please enter both Jira key and PR number.'); return }
    const resolvedKey = parseJiraKey(jiraKey)
    setError(null)
    setStep('reviewing')
    try {
      const res = await fetch('/api/v1/external-review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ jiraKey: resolvedKey, prNumber: parseInt(prNumber, 10) }),
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }))
        throw new Error(err.message || res.statusText)
      }
      setResult(await res.json())
      setStep('results')
    } catch (e) {
      setError(String(e))
      setStep('form')
    }
  }

  const handleAgreeAndImport = async () => {
    setStep('importing')
    try {
      const res = await fetch(`/api/v1/requirements/import/jira/${result.jiraKey}`, { method: 'POST' })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }))
        throw new Error(err.message || res.statusText)
      }
      const req = await res.json()
      setNewReqId(req.id)
      setStep('done')
    } catch (e) {
      setError(String(e))
      setStep('results')
    }
  }

  const reset = () => { setStep('form'); setResult(null); setError(null); setNewReqId(null) }

  return (
    <div style={{ padding: '2rem', maxWidth: '900px' }}>
      <style>{STYLES}</style>

      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text)', marginBottom: '0.25rem' }}>
          External PR Review
        </h1>
        <p style={{ color: 'var(--text-3)', fontSize: '0.875rem' }}>
          Provide a Jira issue key and a GitHub PR number. AI will review the PR diff against the requirement,
          highlight issues, and optionally import the requirement to run the full pipeline.
        </p>
      </div>

      {/* ── Pipeline breadcrumb ── */}
      <div className="er-breadcrumb">
        {['Enter Details', 'AI Review', 'Agree & Import', 'Run Pipeline'].map((s, i) => {
          const idx = { form: 0, reviewing: 1, results: 1, importing: 2, done: 3 }[step]
          return (
            <div key={s} className={`er-bc-step ${i <= idx ? 'er-bc-active' : ''}`}>
              <div className="er-bc-dot">{i < idx ? '✓' : i + 1}</div>
              <div className="er-bc-label">{s}</div>
              {i < 3 && <div className="er-bc-line" />}
            </div>
          )
        })}
      </div>

      {error && (
        <div className="er-error">
          <strong>Error:</strong> {error}
          <button onClick={() => setError(null)} className="er-error-close">×</button>
        </div>
      )}

      {/* ── STEP 1: Form ── */}
      {(step === 'form') && (
        <div className="er-card">
          <div className="er-card-title">Provide Jira + PR Details</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
            <div>
              <label className="er-label">Jira Issue Key</label>
              <input
                className="er-input"
                placeholder="e.g. KAN-4"
                value={jiraKey}
                onChange={e => setJiraKey(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleReview()}
              />
              <div className="er-hint">From your Jira project (e.g. KAN-4, JIRA-123)</div>
            </div>
            <div>
              <label className="er-label">GitHub PR Number</label>
              <input
                className="er-input"
                placeholder="e.g. 1"
                type="number"
                min="1"
                value={prNumber}
                onChange={e => setPrNumber(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleReview()}
              />
              <div className="er-hint">The PR number from your configured GitHub repo</div>
            </div>
          </div>
          <button className="er-btn-primary" onClick={handleReview}>
            Run AI Review
          </button>
        </div>
      )}

      {/* ── STEP: Reviewing (spinner) ── */}
      {step === 'reviewing' && (
        <div className="er-card er-center">
          <div className="er-spinner" />
          <div style={{ marginTop: '1rem', fontWeight: 600, color: 'var(--text)' }}>
            Reviewing PR #{prNumber} against {jiraKey}...
          </div>
          <div style={{ marginTop: '0.5rem', color: 'var(--text-3)', fontSize: '0.875rem' }}>
            Fetching Jira requirement, GitHub diff, and running AI analysis
          </div>
        </div>
      )}

      {/* ── STEP 2: Results ── */}
      {step === 'results' && result && (
        <>
          {/* Verdict hero */}
          <div className={`er-verdict ${result.approved ? 'er-verdict-ok' : 'er-verdict-fail'}`}>
            <div className="er-verdict-icon">{result.approved ? '✓' : '✗'}</div>
            <div>
              <div className="er-verdict-title">
                {result.approved ? 'Approved' : 'Changes Requested'}
              </div>
              <div className="er-verdict-sub">
                {result.jiraKey} · PR #{result.prNumber} · {result.issueCount} issues
                {result.criticalCount > 0 && ` · ${result.criticalCount} critical`}
              </div>
            </div>
            <div style={{ marginLeft: 'auto', fontSize: '0.75rem', color: 'var(--text-3)' }}>
              Model: {result.modelId}
            </div>
          </div>

          {/* Jira context */}
          <div className="er-card" style={{ marginBottom: '1rem' }}>
            <div className="er-card-title">{result.jiraKey} — {result.jiraTitle}</div>
            {result.jiraDescription && (
              <p style={{ color: 'var(--text-2)', fontSize: '0.875rem', margin: 0 }}>{result.jiraDescription}</p>
            )}
          </div>

          {/* Summary */}
          <div className="er-card" style={{ marginBottom: '1rem' }}>
            <div className="er-card-title">AI Summary</div>
            <p style={{ color: 'var(--text-2)', fontSize: '0.9rem', lineHeight: '1.6', margin: 0 }}>
              {result.summary}
            </p>
          </div>

          {/* Issues */}
          {result.issues.length > 0 && (
            <div className="er-card" style={{ marginBottom: '1.5rem' }}>
              <div className="er-card-title">{result.issues.length} Issue{result.issues.length > 1 ? 's' : ''} Found</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {result.issues.map((iss, i) => (
                  <div key={i} className="er-issue" style={{ '--sev': SEV_COLOR[iss.severity] || '#60a5fa' }}>
                    <div className="er-issue-header">
                      <span className="er-sev-badge" style={{ background: SEV_COLOR[iss.severity] + '22', color: SEV_COLOR[iss.severity] }}>
                        {iss.severity}
                      </span>
                      <span className="er-cat-badge">
                        {CAT_ICON[iss.category] || '•'} {iss.category}
                      </span>
                      {iss.location && <span className="er-loc">{iss.location}</span>}
                    </div>
                    <div className="er-issue-desc">{iss.description}</div>
                    {iss.suggestion && (
                      <div className="er-issue-fix">
                        <span style={{ color: '#34d399', marginRight: '0.5rem' }}>Fix:</span>
                        {iss.suggestion}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {result.issues.length === 0 && (
            <div className="er-card er-center" style={{ marginBottom: '1.5rem', color: '#34d399' }}>
              No issues found. The PR looks good against the Jira requirement.
            </div>
          )}

          {/* Actions */}
          <div className="er-actions">
            <div>
              <div className="er-actions-title">Agree with the AI findings?</div>
              <div className="er-actions-sub">
                Clicking "Agree & Import" will import <strong>{result.jiraKey}</strong> from Jira, create a
                requirement in the platform, and trigger the full pipeline (analysis → code gen → PR → compliance → risk).
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', flexShrink: 0 }}>
              <button className="er-btn-secondary" onClick={reset}>Re-run</button>
              <button className="er-btn-primary" onClick={handleAgreeAndImport}>
                Agree &amp; Import {result.jiraKey}
              </button>
            </div>
          </div>
        </>
      )}

      {/* ── STEP: Importing ── */}
      {step === 'importing' && (
        <div className="er-card er-center">
          <div className="er-spinner" />
          <div style={{ marginTop: '1rem', fontWeight: 600, color: 'var(--text)' }}>
            Importing {result?.jiraKey} from Jira...
          </div>
          <div style={{ marginTop: '0.5rem', color: 'var(--text-3)', fontSize: '0.875rem' }}>
            Creating requirement and triggering analysis pipeline
          </div>
        </div>
      )}

      {/* ── STEP: Done ── */}
      {step === 'done' && newReqId && (
        <div className="er-card er-center">
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>✓</div>
          <div style={{ fontWeight: 700, fontSize: '1.25rem', color: 'var(--text)', marginBottom: '0.5rem' }}>
            {result?.jiraKey} imported successfully
          </div>
          <div style={{ color: 'var(--text-3)', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
            Requirement created. Analysis has been triggered automatically.
            Open the pipeline to run Code Gen → PR → Compliance → Risk → AI Review.
          </div>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button className="er-btn-secondary" onClick={reset}>Review Another PR</button>
            <button className="er-btn-primary" onClick={() => navigate(`/requirements/${newReqId}`)}>
              Open Pipeline
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

const STYLES = `
.er-breadcrumb {
  display: flex;
  align-items: center;
  margin-bottom: 2rem;
  gap: 0;
}
.er-bc-step {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}
.er-bc-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--border);
  color: var(--text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.3s;
}
.er-bc-label {
  font-size: 0.8rem;
  color: var(--text-3);
  white-space: nowrap;
  transition: color 0.3s;
}
.er-bc-active .er-bc-dot {
  background: #6366f1;
  color: white;
}
.er-bc-active .er-bc-label {
  color: var(--text);
  font-weight: 600;
}
.er-bc-line {
  width: 40px;
  height: 2px;
  background: var(--border);
  margin: 0 0.5rem;
  flex-shrink: 0;
}
.er-error {
  background: rgba(239,68,68,0.1);
  border: 1px solid rgba(239,68,68,0.3);
  border-radius: 10px;
  padding: 0.75rem 1rem;
  color: #f87171;
  font-size: 0.875rem;
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.er-error-close {
  margin-left: auto;
  background: none;
  border: none;
  color: #f87171;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
  padding: 0;
}
.er-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1.25rem;
}
.er-card-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 1rem;
}
.er-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 3rem;
}
.er-label {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 0.4rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.er-input {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.65rem 0.875rem;
  color: var(--text);
  font-size: 0.95rem;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s;
}
.er-input:focus { border-color: #6366f1; }
.er-hint {
  font-size: 0.75rem;
  color: var(--text-3);
  margin-top: 0.35rem;
}
.er-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 0.65rem 1.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}
.er-btn-primary:hover { opacity: 0.85; }
.er-btn-secondary {
  background: var(--surface);
  color: var(--text-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.65rem 1.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.er-btn-secondary:hover { background: var(--card); }
.er-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid var(--border);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: er-spin 0.8s linear infinite;
}
@keyframes er-spin { to { transform: rotate(360deg); } }
.er-verdict {
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.er-verdict-ok   { background: rgba(52,211,153,0.1); border: 1px solid rgba(52,211,153,0.3); }
.er-verdict-fail { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.25); }
.er-verdict-icon {
  width: 48px; height: 48px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 1.5rem; font-weight: 700; flex-shrink: 0;
}
.er-verdict-ok   .er-verdict-icon { background: rgba(52,211,153,0.2); color: #34d399; }
.er-verdict-fail .er-verdict-icon { background: rgba(239,68,68,0.15); color: #f87171; }
.er-verdict-title { font-weight: 700; font-size: 1.1rem; color: var(--text); }
.er-verdict-sub   { font-size: 0.8rem; color: var(--text-3); margin-top: 0.2rem; }
.er-issue {
  border: 1px solid var(--sev, var(--border));
  border-left-width: 3px;
  border-radius: 8px;
  padding: 0.875rem 1rem;
  background: var(--surface);
}
.er-issue-header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; flex-wrap: wrap; }
.er-sev-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.er-cat-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  background: var(--border);
  color: var(--text-2);
}
.er-loc {
  font-family: monospace;
  font-size: 0.75rem;
  color: var(--text-3);
  margin-left: auto;
}
.er-issue-desc { font-size: 0.875rem; color: var(--text-2); line-height: 1.5; }
.er-issue-fix  { font-size: 0.8rem; color: var(--text-3); margin-top: 0.4rem; line-height: 1.5; }
.er-actions {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.er-actions-title { font-weight: 600; color: var(--text); margin-bottom: 0.25rem; }
.er-actions-sub   { font-size: 0.8rem; color: var(--text-3); line-height: 1.5; max-width: 500px; }
`
