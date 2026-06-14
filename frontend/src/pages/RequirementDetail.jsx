import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { apiFetch } from '../api/client.js'

// ── Stage config (correct pipeline order) ──────────────────────────────────────
const STAGES = [
  { key: 'intake',          label: 'Intake',       icon: '⊕', color: '#818cf8', endpoint: null },
  { key: 'analysis',        label: 'Analysis',     icon: '◎', color: '#34d399', endpoint: id => `/api/v1/requirements/${id}/analysis` },
  { key: 'codeGeneration',  label: 'Code Gen',     icon: '◈', color: '#60a5fa', endpoint: id => `/api/v1/requirements/${id}/code` },
  { key: 'buildValidation', label: 'Build Check',  icon: '⬡', color: '#2dd4bf', endpoint: id => `/api/v1/requirements/${id}/build-validation` },
  { key: 'githubPr',        label: 'GitHub PR',    icon: '⑂', color: '#a78bfa', endpoint: id => `/api/v1/requirements/${id}/pull-request` },
  { key: 'compliance',      label: 'Compliance',   icon: '◉', color: '#fbbf24', endpoint: id => `/api/v1/requirements/${id}/compliance` },
  { key: 'aiReview',        label: 'AI Review',    icon: '✦', color: '#c084fc', endpoint: id => `/api/v1/requirements/${id}/ai-review` },
  { key: 'riskScoring',     label: 'Risk Score',   icon: '⚑', color: '#f87171', endpoint: id => `/api/v1/requirements/${id}/risk` },
]

const TABS = ['Overview', 'Analysis', 'Code', 'Build', 'Pull Request', 'Compliance', 'AI Review', 'Risk']

// ── Main ───────────────────────────────────────────────────────────────────────
export default function RequirementDetail() {
  const { id } = useParams()
  const [pipeline, setPipeline]     = useState(null)
  const [analysis, setAnalysis]     = useState(null)
  const [compliance, setCompliance] = useState(null)
  const [code, setCode]             = useState(null)
  const [risk, setRisk]             = useState(null)
  const [aiReview, setAiReview]           = useState(null)
  const [buildValidation, setBuildValidation] = useState(null)
  const [activeTab, setActiveTab]   = useState('Overview')
  const [loading, setLoading]       = useState(true)
  const [triggering, setTriggering] = useState({})
  const [justCompleted, setJustCompleted] = useState({})
  const [pdfLoading, setPdfLoading] = useState(false)
  const prevStatusRef = useRef({})

  const fetchAll = useCallback(async () => {
    const [p, a, c, k, r, ar, bv] = await Promise.allSettled([
      apiFetch(`/api/v1/requirements/${id}/pipeline`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/analysis`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/compliance`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/code`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/risk`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/ai-review`).then(r => r.ok ? r.json() : null),
      apiFetch(`/api/v1/requirements/${id}/build-validation`).then(r => r.ok ? r.json() : null),
    ])
    if (p.status === 'fulfilled' && p.value) {
      const newPipeline = p.value
      const newly = {}
      STAGES.forEach(s => {
        const prev = prevStatusRef.current[s.key]
        const curr = newPipeline[s.key]?.status
        if (prev && prev !== 'DONE' && curr === 'DONE') newly[s.key] = true
      })
      if (Object.keys(newly).length) { setJustCompleted(newly); setTimeout(() => setJustCompleted({}), 1500) }
      STAGES.forEach(s => { prevStatusRef.current[s.key] = newPipeline[s.key]?.status })
      setPipeline(newPipeline)
    }
    if (a.status === 'fulfilled' && a.value) setAnalysis(a.value)
    if (c.status === 'fulfilled' && c.value) setCompliance(c.value)
    if (k.status === 'fulfilled' && k.value) setCode(k.value)
    if (r.status === 'fulfilled' && r.value) setRisk(r.value)
    if (ar.status === 'fulfilled' && ar.value) setAiReview(ar.value)
    if (bv.status === 'fulfilled' && bv.value) setBuildValidation(bv.value)
  }, [id])

  useEffect(() => { fetchAll().finally(() => setLoading(false)) }, [fetchAll])
  useEffect(() => { const t = setInterval(fetchAll, 4000); return () => clearInterval(t) }, [fetchAll])

  const trigger = async (stage) => {
    if (!stage.endpoint) return
    setTriggering(t => ({ ...t, [stage.key]: true }))
    try {
      const res = await apiFetch(stage.endpoint(id), { method: 'POST' })
      if (res.ok) await fetchAll()
    } catch (e) { console.error(e) }
    finally { setTriggering(t => ({ ...t, [stage.key]: false })) }
  }

  const downloadPdf = async () => {
    setPdfLoading(true)
    try {
      const res  = await apiFetch(`/api/v1/requirements/${id}/report`)
      const data = await res.json()
      openPrintWindow(data)
    } catch (e) { alert('Failed to generate report: ' + e) }
    finally { setPdfLoading(false) }
  }

  if (loading) return <LoadingScreen />
  if (!pipeline) return <NotFound />

  // Compute aiReview stage status for pipeline display
  const enrichedPipeline = {
    ...pipeline,
    aiReview: { status: aiReview ? 'DONE' : 'PENDING', detail: aiReview ? `${aiReview.approved ? 'Approved' : 'Changes requested'} · ${aiReview.issueCount} issues` : null }
  }

  return (
    <div className="rd-root">
      <style>{STYLES}</style>

      <Link to="/" className="rd-back">← Dashboard</Link>

      {/* ── HEADER ── */}
      <div className="rd-header">
        <div className="rd-header-left">
          <div className="rd-header-badges">
            <span className="rd-key-badge">{pipeline.externalKey}</span>
            <RiskPill level={pipeline.riskLevel} score={pipeline.riskScore} />
            <span className="rd-status-chip">{pipeline.requirementStatus}</span>
          </div>
          <h1 className="rd-title">{pipeline.title}</h1>
        </div>
        <div className="rd-header-actions">
          <button className="rd-pdf-btn" onClick={downloadPdf} disabled={pdfLoading}>
            {pdfLoading ? '⟳ Generating…' : '⬇ Download Report'}
          </button>
          {pipeline.prUrl && (
            <a href={pipeline.prUrl} target="_blank" rel="noreferrer" className="rd-pr-btn">
              ⑂ PR #{pipeline.prNumber} ↗
            </a>
          )}
        </div>
      </div>

      {/* ── PIPELINE FLOW ── */}
      <div className="rd-pipeline-wrap">
        <div className="rd-pipeline-header">
          <span className="rd-section-label">Pipeline Flow</span>
          <span className="rd-auto-refresh">● auto-refresh 4s</span>
        </div>
        <div className="rd-pipeline-track">
          {STAGES.map((stage, i) => {
            const stageData = enrichedPipeline[stage.key]
            const status    = stageData?.status || 'PENDING'
            const isDone    = status === 'DONE'
            const isFailed  = status === 'FAILED'
            const isBusy    = triggering[stage.key]
            const isNew     = justCompleted[stage.key]
            const prevDone  = i === 0 || enrichedPipeline[STAGES[i-1].key]?.status === 'DONE'
            const canRun    = stage.endpoint && !isBusy && prevDone
            const detail    = stageData?.detail || computeDetail(stage, pipeline, aiReview)
            return (
              <div key={stage.key} className="rd-stage-cell">
                {i > 0 && <PipelineConnector prevDone={enrichedPipeline[STAGES[i-1].key]?.status === 'DONE'} color={STAGES[i-1].color} />}
                <StageCard stage={stage} status={status} detail={detail}
                  isDone={isDone} isFailed={isFailed} isBusy={isBusy} isNew={isNew}
                  canRun={canRun} onTrigger={() => trigger(stage)}
                  onRerun={() => trigger(stage)} />
              </div>
            )
          })}
        </div>
        <PipelineProgress pipeline={enrichedPipeline} />
      </div>

      {/* ── ACTIONS BAR ── */}
      <ActionsBar pipeline={pipeline} aiReview={aiReview}
        onTriggerReview={() => trigger(STAGES.find(s => s.key === 'aiReview'))}
        reviewBusy={triggering['aiReview']} id={id} />

      {/* ── TABS ── */}
      <div className="rd-tabs-bar">
        {TABS.map(tab => (
          <button key={tab} className={`rd-tab ${activeTab === tab ? 'rd-tab-active' : ''}`}
                  onClick={() => setActiveTab(tab)}>
            {tab}
            {tab === 'AI Review' && aiReview && (
              <span className={`rd-tab-dot ${aiReview.approved ? 'rd-dot-green' : 'rd-dot-red'}`} />
            )}
          </button>
        ))}
      </div>

      <div className="rd-tab-content">
        {activeTab === 'Overview'    && <OverviewTab pipeline={pipeline} analysis={analysis} compliance={compliance} risk={risk} />}
        {activeTab === 'Analysis'    && <AnalysisTab analysis={analysis} />}
        {activeTab === 'Code'        && <CodeTab code={code} />}
        {activeTab === 'Build'       && <BuildTab buildValidation={buildValidation}
          onTrigger={() => trigger(STAGES.find(s => s.key === 'buildValidation'))}
          busy={triggering['buildValidation']} />}
        {activeTab === 'Pull Request'&& <PrTab pipeline={pipeline} />}
        {activeTab === 'Compliance'  && <ComplianceTab compliance={compliance} />}
        {activeTab === 'AI Review'   && <AiReviewTab aiReview={aiReview} pipeline={enrichedPipeline}
          onTrigger={() => trigger(STAGES.find(s => s.key === 'aiReview'))}
          busy={triggering['aiReview']}
          requirementId={id}
          onFixApplied={fetchAll} />}
        {activeTab === 'Risk'        && <RiskTab risk={risk} />}
      </div>
    </div>
  )
}

// ── Stage helpers ──────────────────────────────────────────────────────────────
function computeDetail(stage, pipeline, aiReview) {
  switch (stage.key) {
    case 'githubPr':      return pipeline.prNumber ? `PR #${pipeline.prNumber}` : null
    case 'compliance':    return pipeline.complianceScore != null ? `${pipeline.complianceScore}/100` : null
    case 'riskScoring':   return pipeline.riskLevel ? `${pipeline.riskLevel} · ${pipeline.riskScore}` : null
    case 'aiReview':      return aiReview ? `${aiReview.issueCount} issues` : null
    default:              return null
  }
}

// ── Pipeline components ────────────────────────────────────────────────────────
function StageCard({ stage, status, detail, isDone, isFailed, isBusy, isNew, canRun, onTrigger, onRerun }) {
  return (
    <div className={['rd-stage-card',
      isDone   ? 'rd-stage-done'   : '',
      isFailed ? 'rd-stage-failed' : '',
      isBusy   ? 'rd-stage-busy'   : '',
      isNew    ? 'rd-stage-pop'    : '',
      !canRun && !isDone && !isBusy ? 'rd-stage-locked' : '',
    ].join(' ')} style={{ '--sc': stage.color }}>
      {isDone && <div className="rd-glow-ring" />}
      <div className={`rd-stage-icon ${isBusy ? 'rd-spin' : isDone ? 'rd-pulse-icon' : ''}`}
           style={{ color: isDone ? stage.color : isFailed ? '#f87171' : '#334155' }}>
        {isBusy ? '⟳' : stage.icon}
      </div>
      <div className="rd-stage-name">{stage.label}</div>
      <StatusBadge status={status} color={stage.color} busy={isBusy} />
      {detail && <div className="rd-stage-detail" title={detail}>{detail}</div>}
      <div className="rd-stage-actions">
        {canRun && !isDone && <button className="rd-run-btn" style={{ '--sc': stage.color }} onClick={onTrigger}>▶ Run</button>}
        {isDone && stage.endpoint && <button className="rd-rerun-btn" onClick={onRerun}>↺ Re-run</button>}
      </div>
      {!canRun && !isDone && !isBusy && <div className="rd-lock-icon">🔒</div>}
    </div>
  )
}

function PipelineConnector({ prevDone, color }) {
  return (
    <div className="rd-connector">
      <div className={`rd-connector-line ${prevDone ? 'rd-connector-active' : ''}`} style={{ '--c': color }}>
        {prevDone && <div className="rd-connector-fill" style={{ background: color }} />}
      </div>
      <div className={`rd-connector-arrow ${prevDone ? 'rd-connector-arrow-lit' : ''}`}
           style={{ borderLeftColor: prevDone ? color : undefined }} />
    </div>
  )
}

function StatusBadge({ status, color, busy }) {
  if (busy)   return <div className="rd-stage-badge rd-badge-busy"><span className="rd-dot-spin"/>Running</div>
  if (status === 'DONE')    return <div className="rd-stage-badge rd-badge-done" style={{ '--sc': color }}><span className="rd-dot-done" style={{ background: color }}/>Done</div>
  if (status === 'FAILED')  return <div className="rd-stage-badge rd-badge-failed"><span className="rd-dot-fail"/>Failed</div>
  return <div className="rd-stage-badge rd-badge-pending"><span className="rd-dot-pending"/>Pending</div>
}

function PipelineProgress({ pipeline }) {
  const done = STAGES.filter(s => pipeline[s.key]?.status === 'DONE').length
  return (
    <div className="rd-progress-wrap">
      <div className="rd-progress-track">
        <div className="rd-progress-fill" style={{ width: `${(done / STAGES.length) * 100}%` }} />
      </div>
      <span className="rd-progress-label">{done}/{STAGES.length} stages</span>
    </div>
  )
}

// ── Actions bar ────────────────────────────────────────────────────────────────
function ActionsBar({ pipeline, aiReview, onTriggerReview, reviewBusy, id }) {
  return (
    <div className="rd-actions-bar">
      <div className="rd-actions-label">Quick Actions</div>
      <div className="rd-actions-row">
        {pipeline.prUrl && <>
          <a href={pipeline.prUrl} target="_blank" rel="noreferrer" className="rd-action-btn rd-action-pr">⑂ View PR on GitHub</a>
          <a href={`${pipeline.prUrl}/files`} target="_blank" rel="noreferrer" className="rd-action-btn rd-action-diff">◈ Review Diff</a>
          <button className="rd-action-btn rd-action-merge" onClick={() => window.open(pipeline.prUrl, '_blank')}>✓ Merge PR (GitHub)</button>
        </>}
        <button className={`rd-action-btn rd-action-ai ${reviewBusy ? 'rd-btn-busy' : ''}`}
                onClick={onTriggerReview} disabled={!pipeline.prUrl || reviewBusy}>
          {reviewBusy ? '⟳ Reviewing…' : '✦ Run AI Review'}
        </button>
        <button className="rd-action-btn rd-action-es" disabled title="Enable Elasticsearch to push index">
          ◎ Push to ES (disabled)
        </button>
      </div>
      {pipeline.prUrl && (
        <div className="rd-actions-note">
          ✓ 6 files committed to <code>feat/jira-123-create-customer-api</code> — open PR to merge into main.
        </div>
      )}
    </div>
  )
}

// ── Tab components ─────────────────────────────────────────────────────────────
function OverviewTab({ pipeline, analysis, compliance, risk }) {
  return (
    <div className="rd-overview-grid">
      <GlassCard title="Analysis" icon="◎" color="#34d399">
        {analysis ? (
          <div className="rd-stats-grid">
            {[['Entities',analysis.entities?.length??0,'#a5b4fc'],['Endpoints',analysis.apiEndpoints?.length??0,'#60a5fa'],
              ['Validations',analysis.validationRules?.length??0,'#34d399'],['Security',analysis.securityRequirements?.length??0,'#fbbf24'],
              ['Functional',analysis.functionalRequirements?.length??0,'#a78bfa'],['Edge Cases',analysis.edgeCases?.length??0,'#f87171'],
            ].map(([label,val,color]) => (
              <div key={label} className="rd-stat-chip">
                <div className="rd-stat-value" style={{ color }}>{val}</div>
                <div className="rd-stat-label">{label}</div>
              </div>
            ))}
          </div>
        ) : <EmptyState>Run Analysis first</EmptyState>}
      </GlassCard>

      <GlassCard title="Compliance" icon="◉" color="#fbbf24">
        {compliance ? (
          <div className="rd-compliance-summary">
            <ScoreRing score={compliance.complianceScore} />
            <div>
              <div style={{ fontSize:'1.5rem', fontWeight:700, color: compliance.complianceScore >= 80 ? '#34d399':'#f87171' }}>
                {compliance.complianceScore}/100
              </div>
              <div style={{ display:'flex', gap:'1rem', marginTop:'.5rem' }}>
                <Metric label="Gaps" value={compliance.gaps?.length??0} />
                <Metric label="Critical" value={compliance.criticalGaps??0} color="#f87171" />
              </div>
            </div>
          </div>
        ) : <EmptyState>Run Compliance first</EmptyState>}
      </GlassCard>

      <GlassCard title="Risk Score" icon="⚑" color="#f87171">
        {risk ? (
          <div>
            <RiskPill level={risk.riskLevel} score={risk.overallScore} large />
            <div style={{ display:'flex', gap:'1rem', marginTop:'1rem', flexWrap:'wrap' }}>
              <Metric label="Compliance" value={`${risk.complianceContribution??0}%`} color="#fbbf24" />
              <Metric label="Security" value={`${risk.securityContribution??0}%`} color="#f87171" />
              <Metric label="Completeness" value={`${risk.completenessContribution??0}%`} color="#60a5fa" />
            </div>
            {risk.recommendation && <div className="rd-recommendation">{risk.recommendation}</div>}
          </div>
        ) : <EmptyState>Run Risk Scoring first</EmptyState>}
      </GlassCard>

      {analysis?.entities?.length > 0 && (
        <GlassCard title={`Entities (${analysis.entities.length})`} icon="◈" color="#a78bfa" span2>
          <div className="rd-entity-list">
            {analysis.entities.map(e => (
              <div key={e.name} className="rd-entity-chip">
                <span className="rd-entity-name">{e.name}</span>
                {e.fields?.length > 0 && <span className="rd-entity-fields">{e.fields.slice(0,5).map(f=>f.name).join(' · ')}{e.fields.length>5?` +${e.fields.length-5}`:''}</span>}
              </div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  )
}

const TYPE_META = {
  ALGORITHM:       { color: '#f59e0b', desc: 'Standalone Java class — no Spring Boot, no DB' },
  SPRING_CRUD:     { color: '#818cf8', desc: 'Spring Boot CRUD: Entity + Repository + Service + Controller' },
  REST_API:        { color: '#818cf8', desc: 'REST API with specific endpoints' },
  MICROSERVICE:    { color: '#06b6d4', desc: 'Kafka / event-driven microservice' },
  BATCH_JOB:       { color: '#a78bfa', desc: 'Scheduled batch processing' },
  CLI_APPLICATION: { color: '#10b981', desc: 'Command-line tool with main()' },
  LIBRARY:         { color: '#ec4899', desc: 'Utility / library class' },
}

function AnalysisTab({ analysis }) {
  if (!analysis) return <EmptyState large>Click Run on the Analysis stage above.</EmptyState>
  const type = analysis.requirementType || 'SPRING_CRUD'
  const typeMeta = TYPE_META[type] || TYPE_META.SPRING_CRUD
  const plan = analysis.generationPlan
  return (
    <div className="rd-tab-sections">

      {/* Requirement Type Banner */}
      <div style={{
        background: 'var(--card)', border: `1px solid ${typeMeta.color}40`,
        borderLeft: `4px solid ${typeMeta.color}`, borderRadius: '10px',
        padding: '0.875rem 1.25rem', display: 'flex', alignItems: 'center', gap: '1rem'
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
            <span style={{ fontWeight: 700, color: typeMeta.color, fontSize: '0.9rem' }}>{type}</span>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-3)', background: 'var(--surface)',
              padding: '1px 7px', borderRadius: '4px', border: '1px solid var(--border)' }}>
              Requirement Type
            </span>
          </div>
          <div style={{ fontSize: '0.82rem', color: 'var(--text-3)' }}>{typeMeta.desc}</div>
        </div>
        {plan && (
          <div style={{ marginLeft: 'auto', fontSize: '0.78rem', color: 'var(--text-3)', textAlign: 'right' }}>
            <div style={{ marginBottom: '0.2rem' }}>Expected: <strong style={{ color: 'var(--text-2)' }}>
              {plan.expectedArtifacts?.join(', ')}
            </strong></div>
            {plan.mustNotHave?.length > 0 && (
              <div style={{ color: '#f87171' }}>
                Forbidden: {plan.mustNotHave.slice(0, 4).join(', ')}
              </div>
            )}
          </div>
        )}
      </div>

      {analysis.apiEndpoints?.length > 0 && (
        <GlassCard title={`API Endpoints (${analysis.apiEndpoints.length})`} icon="⊕" color="#60a5fa">
          <div className="rd-endpoint-list">
            {analysis.apiEndpoints.map((ep,i) => (
              <div key={i} className="rd-endpoint-row">
                <MethodBadge method={ep.httpMethod} /><code className="rd-endpoint-path">{ep.suggestedPath}</code>
                <span className="rd-endpoint-desc">{ep.description}</span>
              </div>
            ))}
          </div>
        </GlassCard>
      )}
      {analysis.validationRules?.length > 0 && (
        <GlassCard title={`Validation Rules (${analysis.validationRules.length})`} icon="◎" color="#34d399">
          <div className="rd-tag-list">
            {analysis.validationRules.map((r,i) => (
              <span key={i} className="rd-validation-tag"><span className="rd-vt-field">{r.targetField}</span><span className="rd-vt-rule">{r.ruleType}</span></span>
            ))}
          </div>
        </GlassCard>
      )}
      {analysis.securityRequirements?.length > 0 && (
        <GlassCard title={`Security (${analysis.securityRequirements.length})`} icon="⚑" color="#f87171">
          <div className="rd-security-list">
            {analysis.securityRequirements.map((s,i) => (
              <div key={i} className="rd-security-row"><span className="rd-sec-category">{s.category}</span><span className="rd-sec-desc">{s.description}</span></div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  )
}

function CodeTab({ code }) {
  if (!code) return <EmptyState large>Run Code Generation to generate Java source files.</EmptyState>
  return (
    <div>
      <div className="rd-code-header">
        <span className="rd-code-stat">{code.fileCount} files</span>
        <span className="rd-code-sep">·</span>
        <code className="rd-code-pkg">{code.targetPackage}</code>
      </div>
      <div className="rd-file-list">
        {code.files?.map((f,i) => (
          <details key={i} className="rd-file-item">
            <summary className="rd-file-summary">
              <span className="rd-file-type-badge">{f.fileType}</span>
              <span className="rd-file-name">{f.fileName}</span>
              <span className="rd-file-chevron">›</span>
            </summary>
            <pre className="rd-file-code">{f.content}</pre>
          </details>
        ))}
      </div>
    </div>
  )
}

function BuildTab({ buildValidation: bv, onTrigger, busy }) {
  const STATUS_COLOR = { PASSED: '#34d399', WARNING: '#fbbf24', FAILED: '#f87171' }
  const CHECK_COLOR  = { PASS: '#34d399', WARN: '#fbbf24', FAIL: '#f87171' }
  const CHECK_ICON   = { PASS: '✓', WARN: '⚠', FAIL: '✗' }
  if (!bv) return (
    <EmptyState large>
      <div style={{ marginBottom: '1rem' }}>Run Build Validation after Code Generation.</div>
      <button onClick={onTrigger} disabled={busy}
        style={{ background:'linear-gradient(135deg,#2dd4bf,#0d9488)',color:'white',border:'none',
          borderRadius:'8px',padding:'0.65rem 1.5rem',fontWeight:600,cursor:busy?'wait':'pointer' }}>
        {busy ? 'Running…' : 'Run Build Validation'}
      </button>
    </EmptyState>
  )
  const color = STATUS_COLOR[bv.status] || '#94a3b8'
  return (
    <div className="rd-tab-sections">
      <div className="rd-score-cards">
        <div className="rd-score-card">
          <div className="rd-score-label">Status</div>
          <div className="rd-score-value" style={{ color }}>{bv.status}</div>
        </div>
        <div className="rd-score-card">
          <div className="rd-score-label">Files Checked</div>
          <div className="rd-score-value">{bv.fileCount}</div>
        </div>
        <div className="rd-score-card">
          <div className="rd-score-label">Errors</div>
          <div className="rd-score-value" style={{ color: bv.errorCount > 0 ? '#f87171' : '#34d399' }}>{bv.errorCount}</div>
        </div>
        <div className="rd-score-card">
          <div className="rd-score-label">Warnings</div>
          <div className="rd-score-value" style={{ color: bv.warningCount > 0 ? '#fbbf24' : '#34d399' }}>{bv.warningCount}</div>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1.25rem' }}>
        {bv.checks?.map((chk, i) => (
          <div key={i} style={{ background: 'var(--surface)', border: `1px solid ${CHECK_COLOR[chk.status] || '#475569'}`,
            borderLeft: `4px solid ${CHECK_COLOR[chk.status] || '#475569'}`,
            borderRadius: '8px', padding: '0.875rem 1rem', display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
            <span style={{ fontSize: '1.1rem', color: CHECK_COLOR[chk.status], flexShrink: 0 }}>
              {CHECK_ICON[chk.status] || '•'}
            </span>
            <div>
              <div style={{ fontWeight: 600, color: 'var(--text)', fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                {chk.name}
              </div>
              <div style={{ color: 'var(--text-3)', fontSize: '0.8rem', lineHeight: 1.5 }}>{chk.detail}</div>
            </div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: '1.25rem' }}>
        <button onClick={onTrigger} disabled={busy}
          style={{ background:'var(--surface)',color:'var(--text-2)',border:'1px solid var(--border)',
            borderRadius:'8px',padding:'0.55rem 1.25rem',fontWeight:600,cursor:busy?'wait':'pointer',fontSize:'0.875rem' }}>
          {busy ? 'Re-running…' : 'Re-run Validation'}
        </button>
      </div>
    </div>
  )
}

function PrTab({ pipeline }) {
  if (!pipeline.prUrl) return <EmptyState large>Run the GitHub PR stage first.</EmptyState>
  return (
    <div className="rd-pr-tab">
      <div className="rd-pr-hero">
        <div className="rd-pr-hero-icon">⑂</div>
        <div>
          <div className="rd-pr-hero-title">Pull Request #{pipeline.prNumber}</div>
          <div className="rd-pr-hero-sub">dubey161/AI_Generator</div>
        </div>
        <a href={pipeline.prUrl} target="_blank" rel="noreferrer" className="rd-pr-open-btn">Open on GitHub ↗</a>
      </div>
      <div className="rd-pr-detail-grid">
        {[['Branch','feat/jira-123-create-customer-api'],['Base','main'],['Files committed','6 Java files']].map(([k,v]) => (
          <div key={k} className="rd-pr-detail-row"><span>{k}</span><code>{v}</code></div>
        ))}
        <div className="rd-pr-detail-row"><span>URL</span><a href={pipeline.prUrl} target="_blank" rel="noreferrer" style={{ color:'#60a5fa' }}>{pipeline.prUrl}</a></div>
      </div>
      <div className="rd-merge-guide">
        <div className="rd-merge-guide-title">How to merge into main</div>
        <div className="rd-merge-steps">
          {['Open the PR on GitHub','Review the 6 files in "Files changed" tab','Click "Merge pull request" → "Confirm merge"','Code is now live on main branch'].map((s,i) => (
            <div key={i} className="rd-merge-step"><span className="rd-merge-num">{i+1}</span><span>{s}</span></div>
          ))}
        </div>
        <a href={pipeline.prUrl} target="_blank" rel="noreferrer" className="rd-merge-cta">✓ Go to GitHub and Merge PR ↗</a>
      </div>
    </div>
  )
}

function ComplianceTab({ compliance }) {
  if (!compliance) return <EmptyState large>Run Compliance after creating a PR.</EmptyState>
  const color = compliance.complianceScore >= 80 ? '#34d399' : compliance.complianceScore >= 60 ? '#fbbf24' : '#f87171'
  return (
    <div className="rd-tab-sections">
      <div className="rd-score-cards">
        <ScoreCard label="Score" value={`${compliance.complianceScore}/100`} color={color} />
        <ScoreCard label="Total Gaps" value={compliance.gaps?.length??0} />
        <ScoreCard label="Critical" value={compliance.criticalGaps??0} color={(compliance.criticalGaps??0)>0?'#f87171':'#34d399'} />
      </div>
      {compliance.gaps?.length > 0 && (
        <GlassCard title="Compliance Gaps" icon="◉" color="#fbbf24">
          <div style={{ display:'flex', flexDirection:'column', gap:'6px' }}>
            {compliance.gaps.map((g,i) => (
              <div key={i} className="rd-gap-row">
                <SeverityBadge severity={g.severity} />
                <div><div className="rd-gap-desc">{g.description}</div>{g.expected && <div className="rd-gap-expected">Expected: {g.expected}</div>}</div>
              </div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  )
}

function RiskTab({ risk }) {
  if (!risk) return <EmptyState large>Run Risk Scoring after compliance.</EmptyState>
  return (
    <div className="rd-tab-sections">
      <div className="rd-score-cards">
        <ScoreCard label="Risk Level" value={risk.riskLevel} color={risk.riskLevel==='LOW'?'#34d399':risk.riskLevel==='MEDIUM'?'#fbbf24':'#f87171'} />
        <ScoreCard label="Score" value={`${risk.overallScore}/100`} />
        <ScoreCard label="Compliance" value={`${risk.complianceContribution??0}%`} color="#fbbf24" />
        <ScoreCard label="Security" value={`${risk.securityContribution??0}%`} color="#f87171" />
      </div>
      {risk.recommendation && (
        <GlassCard title="Recommendation" icon="⚑" color="#f87171">
          <p style={{ color:'var(--text-2)', lineHeight:1.7 }}>{risk.recommendation}</p>
        </GlassCard>
      )}
    </div>
  )
}

function AiReviewTab({ aiReview, pipeline, onTrigger, busy, requirementId, onFixApplied }) {
  const [fixInstructions, setFixInstructions] = useState('')
  const [fixing, setFixing]     = useState(false)
  const [fixResult, setFixResult] = useState(null)
  const [fixError, setFixError]   = useState(null)
  const [fixStep, setFixStep]     = useState(0)

  const FIX_STEPS = ['Improving knowledge model…', 'Re-generating code…', 'Running build validation…', 'Committing to GitHub…', 'Re-running compliance + risk…', 'Running fresh AI review…']

  const applyFixes = async () => {
    setFixing(true); setFixResult(null); setFixError(null); setFixStep(0)
    const stepTimer = setInterval(() => setFixStep(s => Math.min(s + 1, FIX_STEPS.length - 1)), 8000)
    try {
      const res = await fetch(`/api/v1/requirements/${requirementId}/apply-fixes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ customInstructions: fixInstructions }),
      })
      if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || res.statusText) }
      const result = await res.json()
      setFixResult(result)
      onFixApplied && onFixApplied()
    } catch (e) { setFixError(String(e)) }
    finally { clearInterval(stepTimer); setFixing(false) }
  }

  if (!pipeline.prUrl) return (
    <EmptyState large>AI Review requires a GitHub PR. Run the GitHub PR stage first.</EmptyState>
  )
  if (!aiReview && !busy) return (
    <div style={{ textAlign:'center', padding:'3rem 2rem' }}>
      <div style={{ fontSize:'2.5rem', marginBottom:'1rem', color:'#c084fc' }}>✦</div>
      <div style={{ fontSize:'1rem', fontWeight:600, color:'var(--text)', marginBottom:'.5rem' }}>AI Code Review</div>
      <div style={{ color:'var(--text-3)', fontSize:'.875rem', marginBottom:'1.5rem', maxWidth:'420px', margin:'0 auto 1.5rem' }}>
        Run an LLM-powered review of your PR diff against the requirements — checks security, validation, architecture, and requirement gaps.
      </div>
      <button className="rd-run-btn-large" onClick={onTrigger}>✦ Run AI Review</button>
    </div>
  )
  if (busy) return (
    <div style={{ display:'flex', flexDirection:'column', alignItems:'center', padding:'3rem', gap:'1rem', color:'var(--text-3)' }}>
      <div className="rd-big-spinner" />
      <div>LLM is reviewing your PR diff…</div>
    </div>
  )
  return (
    <div className="rd-tab-sections">
      {/* Verdict */}
      <div className={`rd-review-hero ${aiReview.approved ? 'rd-review-approved' : 'rd-review-changes'}`}>
        <div className="rd-review-verdict">
          <span className="rd-review-icon">{aiReview.approved ? '✓' : '✕'}</span>
          <div>
            <div className="rd-review-verdict-label">{aiReview.approved ? 'Approved' : 'Changes Requested'}</div>
            <div className="rd-review-meta">Model: {aiReview.modelId} · {aiReview.issueCount} issues · {aiReview.criticalCount} critical</div>
          </div>
        </div>
        <p className="rd-review-summary">{aiReview.summary}</p>
        <div style={{ display:'flex', gap:'.6rem', flexWrap:'wrap' }}>
          <button className="rd-rerun-btn" onClick={onTrigger}>↺ Re-run Review</button>
          {pipeline.prUrl && <a href={pipeline.prUrl} target="_blank" rel="noreferrer" className="rd-action-btn rd-action-pr" style={{ fontSize:'.78rem' }}>View PR ↗</a>}
        </div>
      </div>

      {/* Issues */}
      {aiReview.issues?.length > 0 && (
        <GlassCard title={`Issues Found (${aiReview.issues.length})`} icon="✦" color="#c084fc">
          <div style={{ display:'flex', flexDirection:'column', gap:'8px' }}>
            {aiReview.issues.map((issue, i) => (
              <div key={i} className="rd-issue-row">
                <div className="rd-issue-header">
                  <SeverityBadge severity={issue.severity} />
                  <span className="rd-issue-category">{issue.category}</span>
                  {issue.location && <code className="rd-issue-location">{issue.location}</code>}
                </div>
                <div className="rd-issue-desc">{issue.description}</div>
                {issue.suggestion && (
                  <div className="rd-issue-suggestion">
                    <span className="rd-suggestion-label">Fix:</span> {issue.suggestion}
                  </div>
                )}
              </div>
            ))}
          </div>
        </GlassCard>
      )}

      {/* ── AI Fix Loop Panel ── */}
      {fixResult ? (
        <div className="rd-fix-result">
          <div className="rd-fix-result-title">
            {fixResult.approved ? '✓ Fixes Applied & Approved' : '✗ Fixes Applied — Review Again'}
          </div>
          <div className="rd-fix-scores">
            <FixScoreChange label="Compliance" before={fixResult.complianceBefore} after={fixResult.complianceAfter} suffix="/100" higherIsBetter />
            <FixScoreChange label="Risk Score"  before={fixResult.riskBefore}       after={fixResult.riskAfter}       suffix="/100" higherIsBetter={false} />
            <FixScoreChange label="Issues"      before={fixResult.issuesBefore}     after={fixResult.issuesAfter}     suffix="" higherIsBetter={false} />
            <div className="rd-fix-score-card">
              <div className="rd-fix-score-label">Build</div>
              <div className="rd-fix-score-val" style={{ color: fixResult.buildStatus === 'PASSED' ? '#34d399' : '#fbbf24' }}>
                {fixResult.buildStatus}
              </div>
            </div>
          </div>
          <p style={{ color:'var(--text-2)', fontSize:'.85rem', margin:'0 0 1rem', lineHeight:1.6 }}>{fixResult.newReviewSummary}</p>
          <div style={{ display:'flex', gap:'.75rem' }}>
            {fixResult.prUrl && (
              <a href={fixResult.prUrl} target="_blank" rel="noreferrer"
                style={{ background:'linear-gradient(135deg,#6366f1,#8b5cf6)',color:'white',textDecoration:'none',
                  borderRadius:'8px',padding:'.65rem 1.25rem',fontWeight:600,fontSize:'.875rem' }}>
                View PR &amp; Merge ↗
              </a>
            )}
            <button onClick={() => setFixResult(null)}
              style={{ background:'var(--surface)',color:'var(--text-2)',border:'1px solid var(--border)',
                borderRadius:'8px',padding:'.65rem 1rem',fontWeight:600,fontSize:'.875rem',cursor:'pointer' }}>
              Apply Again
            </button>
          </div>
        </div>
      ) : (
        <div className="rd-fix-panel">
          <div className="rd-fix-panel-header">
            <span style={{ fontSize:'1.1rem' }}>⟳</span>
            <div>
              <div style={{ fontWeight:700, color:'var(--text)', fontSize:'.95rem' }}>Apply AI Fixes &amp; Re-push</div>
              <div style={{ color:'var(--text-3)', fontSize:'.8rem', marginTop:'.2rem' }}>
                AI will improve the knowledge model, re-generate code, validate, commit to the PR branch, and re-run compliance + risk.
              </div>
            </div>
          </div>
          <textarea
            className="rd-fix-textarea"
            placeholder="Optional: add custom instructions (e.g. 'add rate limiting', 'use soft delete', 'add pagination to list endpoint')…"
            value={fixInstructions}
            onChange={e => setFixInstructions(e.target.value)}
            disabled={fixing}
            rows={3}
          />
          {fixError && <div style={{ color:'#f87171', fontSize:'.8rem', marginBottom:'.75rem' }}>{fixError}</div>}
          {fixing ? (
            <div className="rd-fix-progress">
              <div className="rd-fix-spinner" />
              <div>
                <div style={{ fontWeight:600, color:'var(--text)', fontSize:'.875rem' }}>{FIX_STEPS[fixStep]}</div>
                <div style={{ display:'flex', gap:'4px', marginTop:'8px' }}>
                  {FIX_STEPS.map((_, i) => (
                    <div key={i} className={`rd-fix-dot ${i <= fixStep ? 'rd-fix-dot-active' : ''}`} />
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <button className="rd-fix-btn" onClick={applyFixes}>
              ⟳ Apply Fixes &amp; Re-push
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function FixScoreChange({ label, before, after, suffix, higherIsBetter }) {
  const improved = higherIsBetter ? after > before : after < before
  const same = after === before
  const color = same ? 'var(--text-3)' : improved ? '#34d399' : '#f87171'
  const arrow = same ? '→' : improved ? '↑' : '↓'
  return (
    <div className="rd-fix-score-card">
      <div className="rd-fix-score-label">{label}</div>
      <div className="rd-fix-score-val" style={{ color }}>
        {before}{suffix} {arrow} {after}{suffix}
      </div>
    </div>
  )
}

// ── PDF generation ─────────────────────────────────────────────────────────────
function openPrintWindow(data) {
  const req = data.requirement || {}
  const analysis = data.analysis || {}
  const pr = data.pullRequest || {}
  const compliance = data.compliance || {}
  const risk = data.risk || {}
  const review = data.aiReview || {}

  const html = `<!DOCTYPE html>
<html>
<head>
  <title>Pipeline Report — ${req.externalKey || 'Requirement'}</title>
  <style>
    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 0; color: #1e293b; }
    .cover { background: linear-gradient(135deg, #0f172a, #1e3a5f); color: white; padding: 48px 56px; }
    .cover-title { font-size: 28px; font-weight: 700; margin: 0 0 8px; }
    .cover-sub   { font-size: 14px; opacity: .7; margin: 0 0 24px; }
    .cover-key   { display: inline-block; background: rgba(99,102,241,.4); color: #a5b4fc; padding: 4px 12px; border-radius: 6px; font-family: monospace; font-size: 13px; }
    .body        { padding: 40px 56px; }
    h2           { font-size: 16px; font-weight: 700; color: #0f172a; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; margin: 32px 0 16px; }
    h3           { font-size: 13px; font-weight: 600; color: #475569; margin: 16px 0 8px; }
    .row         { display: flex; gap: 24px; flex-wrap: wrap; margin-bottom: 12px; }
    .kv          { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 16px; flex: 1; min-width: 140px; }
    .kv-label    { font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: #94a3b8; margin-bottom: 4px; }
    .kv-value    { font-size: 20px; font-weight: 700; color: #0f172a; }
    table        { width: 100%; border-collapse: collapse; font-size: 13px; margin: 8px 0; }
    th           { background: #f1f5f9; padding: 8px 12px; text-align: left; font-weight: 600; font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: #64748b; }
    td           { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; }
    .badge       { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; }
    .badge-green { background: #dcfce7; color: #166534; }
    .badge-red   { background: #fee2e2; color: #991b1b; }
    .badge-yellow{ background: #fef9c3; color: #854d0e; }
    .desc        { color: #475569; font-size: 13px; line-height: 1.6; margin: 0; }
    .footer      { text-align: center; padding: 24px; color: #94a3b8; font-size: 12px; border-top: 1px solid #e2e8f0; margin-top: 40px; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
  </style>
</head>
<body>
  <div class="cover">
    <div class="cover-key">${req.externalKey || ''}</div>
    <div class="cover-title" style="margin-top:12px">${req.title || 'Pipeline Report'}</div>
    <div class="cover-sub">AI Engineering Productivity Platform · Generated ${new Date().toLocaleString()}</div>
  </div>
  <div class="body">
    <h2>Requirement</h2>
    <p class="desc">${req.description || '—'}</p>
    ${(req.acceptanceCriteria||[]).length ? `<h3>Acceptance Criteria</h3><ul>${(req.acceptanceCriteria||[]).map(c=>`<li class="desc">${c}</li>`).join('')}</ul>` : ''}

    <h2>Pipeline Summary</h2>
    <div class="row">
      <div class="kv"><div class="kv-label">Entities</div><div class="kv-value">${(analysis.entities||[]).length}</div></div>
      <div class="kv"><div class="kv-label">API Endpoints</div><div class="kv-value">${(analysis.apiEndpoints||[]).length}</div></div>
      <div class="kv"><div class="kv-label">Files Generated</div><div class="kv-value">${(data.codeGeneration||{}).fileCount||0}</div></div>
      <div class="kv"><div class="kv-label">Compliance Score</div><div class="kv-value">${compliance.complianceScore != null ? compliance.complianceScore+'/100' : '—'}</div></div>
      <div class="kv"><div class="kv-label">Risk Level</div><div class="kv-value">${risk.riskLevel||'—'}</div></div>
    </div>

    ${(analysis.apiEndpoints||[]).length ? `
    <h2>API Endpoints</h2>
    <table><tr><th>Method</th><th>Path</th><th>Description</th></tr>
    ${(analysis.apiEndpoints||[]).map(ep=>`<tr><td><span class="badge badge-green">${ep.httpMethod||''}</span></td><td><code>${ep.suggestedPath||''}</code></td><td>${ep.description||''}</td></tr>`).join('')}
    </table>` : ''}

    ${pr.prNumber ? `
    <h2>Pull Request</h2>
    <table>
      <tr><td><b>PR Number</b></td><td>#${pr.prNumber}</td></tr>
      <tr><td><b>Branch</b></td><td><code>${pr.headBranch||''}</code></td></tr>
      <tr><td><b>URL</b></td><td>${pr.htmlUrl||''}</td></tr>
    </table>` : ''}

    ${compliance.complianceScore != null ? `
    <h2>Compliance Report</h2>
    <div class="row">
      <div class="kv"><div class="kv-label">Score</div><div class="kv-value" style="color:${compliance.complianceScore>=80?'#166534':'#991b1b'}">${compliance.complianceScore}/100</div></div>
      <div class="kv"><div class="kv-label">Total Gaps</div><div class="kv-value">${compliance.gapCount||0}</div></div>
      <div class="kv"><div class="kv-label">Critical Gaps</div><div class="kv-value" style="color:#991b1b">${compliance.criticalGaps||0}</div></div>
    </div>
    ${(compliance.gaps||[]).length ? `<table><tr><th>Severity</th><th>Description</th><th>Expected</th></tr>
    ${(compliance.gaps||[]).map(g=>`<tr><td><span class="badge ${g.severity==='CRITICAL'?'badge-red':g.severity==='HIGH'?'badge-yellow':'badge-green'}">${g.severity}</span></td><td>${g.description||''}</td><td>${g.expected||''}</td></tr>`).join('')}
    </table>` : ''}` : ''}

    ${risk.riskLevel ? `
    <h2>Risk Assessment</h2>
    <div class="row">
      <div class="kv"><div class="kv-label">Risk Level</div><div class="kv-value" style="color:${risk.riskLevel==='LOW'?'#166534':risk.riskLevel==='MEDIUM'?'#854d0e':risk.riskLevel==='HIGH'?'#b45309':'#991b1b'}">${risk.riskLevel}</div></div>
      <div class="kv"><div class="kv-label">Risk Score (0 = safe)</div><div class="kv-value">${risk.overallScore||0}/100</div></div>
      <div class="kv"><div class="kv-label">Security Risk %</div><div class="kv-value">${risk.securityContribution||0}%</div></div>
      <div class="kv"><div class="kv-label">Compliance Risk %</div><div class="kv-value">${risk.complianceContribution||0}%</div></div>
    </div>
    ${risk.recommendation ? `<p class="desc"><b>Recommendation:</b> ${risk.recommendation}</p>` : ''}` : ''}

    ${review.summary ? `
    <h2>AI Code Review</h2>
    <p class="desc"><b>Verdict:</b> <span class="badge ${review.approved?'badge-green':'badge-red'}">${review.approved?'APPROVED':'CHANGES REQUESTED'}</span></p>
    <p class="desc">${review.summary}</p>
    ${(review.issues||[]).length ? `<table><tr><th>Severity</th><th>Category</th><th>Issue</th><th>Suggestion</th></tr>
    ${(review.issues||[]).map(i=>`<tr><td><span class="badge ${i.severity==='CRITICAL'?'badge-red':i.severity==='HIGH'?'badge-yellow':'badge-green'}">${i.severity}</span></td><td>${i.category}</td><td>${i.description}</td><td>${i.suggestion||''}</td></tr>`).join('')}
    </table>` : ''}` : ''}

    <div class="footer">AI Engineering Productivity Platform · ${req.externalKey} · ${new Date().toLocaleDateString()}</div>
  </div>
  <script>window.onload = () => { window.print(); }</script>
</body></html>`

  const win = window.open('', '_blank')
  win.document.write(html)
  win.document.close()
}

// ── Atoms ──────────────────────────────────────────────────────────────────────
function GlassCard({ title, icon, color, children, span2 }) {
  return (
    <div className="rd-glass-card" style={{ '--gc': color, gridColumn: span2 ? 'span 2' : undefined }}>
      <div className="rd-glass-header"><span style={{ color }}>{icon}</span><span className="rd-glass-title">{title}</span></div>
      <div className="rd-glass-body">{children}</div>
    </div>
  )
}
function ScoreCard({ label, value, color='var(--text)' }) {
  return <div className="rd-score-card"><div className="rd-score-label">{label}</div><div className="rd-score-value" style={{ color }}>{value}</div></div>
}
function ScoreRing({ score }) {
  const color = score>=80?'#34d399':score>=60?'#fbbf24':'#f87171'
  const r=24,circ=2*Math.PI*r,dash=(score/100)*circ
  return (
    <div style={{ position:'relative',width:64,height:64,flexShrink:0 }}>
      <svg width="64" height="64" style={{ transform:'rotate(-90deg)' }}>
        <circle cx="32" cy="32" r={r} fill="none" stroke="var(--border)" strokeWidth="5"/>
        <circle cx="32" cy="32" r={r} fill="none" stroke={color} strokeWidth="5"
          strokeDasharray={`${dash} ${circ-dash}`} strokeLinecap="round" style={{ transition:'stroke-dasharray .8s ease' }}/>
      </svg>
      <div style={{ position:'absolute',inset:0,display:'flex',alignItems:'center',justifyContent:'center',fontSize:'.85rem',fontWeight:700,color }}>{score}</div>
    </div>
  )
}
function RiskPill({ level, score, large }) {
  if (!level) return null
  const cfg={LOW:{bg:'#052e16',text:'#34d399',border:'#065f46'},MEDIUM:{bg:'#1c1500',text:'#fbbf24',border:'#78350f'},HIGH:{bg:'#1c0f00',text:'#fb923c',border:'#7c2d12'},CRITICAL:{bg:'#1f0808',text:'#f87171',border:'#7f1d1d'}}
  const c=cfg[level]||cfg.LOW
  return <span style={{ background:c.bg,color:c.text,border:`1px solid ${c.border}`,padding:large?'.4rem 1rem':'2px 10px',borderRadius:'20px',fontSize:large?'.9rem':'.72rem',fontWeight:700,display:'inline-flex',alignItems:'center',gap:'5px' }}>
    <span style={{ width:6,height:6,borderRadius:'50%',background:c.text,display:'inline-block' }}/>{level}{score!=null?` · ${score}`:''}
  </span>
}
function Metric({ label, value, color='var(--text)' }) {
  return <div><div style={{ fontSize:'1.1rem',fontWeight:700,color }}>{value}</div><div style={{ fontSize:'.68rem',color:'var(--text-3)' }}>{label}</div></div>
}
function MethodBadge({ method }) {
  const cfg={GET:{bg:'#0c1f38',text:'#60a5fa',border:'#1e3a5f'},POST:{bg:'#052e16',text:'#4ade80',border:'#065f46'},PUT:{bg:'#1c1500',text:'#fbbf24',border:'#78350f'},PATCH:{bg:'#1a1040',text:'#a5b4fc',border:'#312e81'},DELETE:{bg:'#1f0808',text:'#f87171',border:'#7f1d1d'}}
  const c=cfg[method]||{bg:'var(--surface)',text:'var(--text)',border:'var(--border)'}
  return <span style={{ background:c.bg,color:c.text,border:`1px solid ${c.border}`,padding:'2px 8px',borderRadius:'5px',fontSize:'.7rem',fontWeight:700,minWidth:'50px',textAlign:'center',display:'inline-block',flexShrink:0 }}>{method}</span>
}
function SeverityBadge({ severity }) {
  const cfg={CRITICAL:{bg:'#1f0808',text:'#f87171',border:'#7f1d1d'},HIGH:{bg:'#1c0f00',text:'#fb923c',border:'#7c2d12'},MEDIUM:{bg:'#1c1500',text:'#fcd34d',border:'#78350f'},LOW:{bg:'#0c1f38',text:'#60a5fa',border:'#1e3a5f'}}
  const c=cfg[severity]||cfg.LOW
  return <span style={{ background:c.bg,color:c.text,border:`1px solid ${c.border}`,padding:'2px 8px',borderRadius:'5px',fontSize:'.7rem',fontWeight:600,whiteSpace:'nowrap',flexShrink:0 }}>{severity}</span>
}
function EmptyState({ children, large }) {
  return <div style={{ padding:large?'3rem 2rem':'1.5rem',textAlign:'center',color:'var(--text-3)',fontSize:'.875rem' }}>{children}</div>
}
function LoadingScreen() {
  return <div style={{ display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',minHeight:'60vh',gap:'1rem' }}><style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style><div style={{ width:36,height:36,border:'3px solid #1e3a5f',borderTopColor:'#6366f1',borderRadius:'50%',animation:'spin .8s linear infinite' }}/><span style={{ color:'var(--text-3)' }}>Loading pipeline…</span></div>
}
function NotFound() {
  return <div style={{ padding:'3rem',textAlign:'center',color:'#f87171' }}>Requirement not found. <Link to="/" style={{ color:'#818cf8' }}>← Back</Link></div>
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const STYLES = `
@keyframes spin       { to { transform: rotate(360deg) } }
@keyframes pulse-glow { 0%,100%{opacity:.6;transform:scale(1)} 50%{opacity:1;transform:scale(1.15)} }
@keyframes pop-in     { 0%{transform:scale(.85);opacity:.5} 60%{transform:scale(1.06)} 100%{transform:scale(1);opacity:1} }
@keyframes fade-up    { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
@keyframes ring-pulse { 0%,100%{box-shadow:0 0 0 0 var(--sc,#818cf8)} 50%{box-shadow:0 0 12px color-mix(in srgb,var(--sc) 40%,transparent)} }
@keyframes fill-in    { from{width:0} to{width:100%} }

.rd-root { padding:1.75rem 2rem; max-width:1400px; animation:fade-up .3s ease; }
.rd-back { color:var(--text-3);font-size:.8rem;display:inline-flex;align-items:center;gap:4px;margin-bottom:1.25rem;text-decoration:none;transition:color .15s; }
.rd-back:hover { color:var(--text-2); }

/* header */
.rd-header { display:flex;align-items:flex-start;justify-content:space-between;gap:1rem;flex-wrap:wrap;
  background:linear-gradient(135deg,var(--card) 0%,#0d1a36 100%);border:1px solid var(--border);
  border-radius:14px;padding:1.5rem 1.75rem;margin-bottom:1.25rem; }
.rd-header-left  { flex:1;min-width:0; }
.rd-header-actions { display:flex;align-items:center;gap:.6rem;flex-wrap:wrap; }
.rd-header-badges  { display:flex;align-items:center;gap:.6rem;margin-bottom:.4rem;flex-wrap:wrap; }
.rd-key-badge   { background:var(--primary-dim);color:#818cf8;padding:2px 10px;border-radius:6px;fontWeight:700;font-size:.78rem;font-family:monospace;border:1px solid rgba(99,102,241,.3); }
.rd-status-chip { font-size:.7rem;padding:2px 8px;border-radius:5px;background:var(--surface);border:1px solid var(--border);color:var(--text-3); }
.rd-title       { font-size:1.25rem;font-weight:700;color:var(--text);margin:0; }
.rd-pr-btn      { display:inline-flex;align-items:center;gap:6px;background:#0d2137;border:1px solid #1e3a5f;color:#60a5fa;padding:.45rem .9rem;border-radius:10px;font-size:.8rem;text-decoration:none;transition:all .15s; }
.rd-pr-btn:hover { background:#112840;transform:translateY(-1px); }
.rd-pdf-btn     { display:inline-flex;align-items:center;gap:6px;background:linear-gradient(135deg,#312e81,#1e3a5f);
  border:1px solid rgba(99,102,241,.4);color:#a5b4fc;padding:.45rem .9rem;border-radius:10px;
  font-size:.8rem;font-weight:600;cursor:pointer;transition:all .15s; }
.rd-pdf-btn:hover { filter:brightness(1.2);transform:translateY(-1px); }

/* pipeline */
.rd-pipeline-wrap   { background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:1.5rem;margin-bottom:1.25rem; }
.rd-pipeline-header { display:flex;align-items:center;justify-content:space-between;margin-bottom:1.25rem; }
.rd-section-label   { font-size:.7rem;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:var(--text-3); }
.rd-auto-refresh    { font-size:.68rem;color:#1e3a5f; }
.rd-pipeline-track  { display:flex;align-items:stretch;overflow-x:auto;padding-bottom:4px;gap:0; }

/* stage cell */
.rd-stage-cell   { display:flex;align-items:center;flex-shrink:0; }
.rd-connector    { display:flex;align-items:center;width:36px;flex-shrink:0; }
.rd-connector-line { flex:1;height:2px;background:var(--border);position:relative;overflow:hidden; }
.rd-connector-active::after { content:'';position:absolute;inset:0;background:var(--c,#818cf8);animation:fill-in .5s ease forwards; }
.rd-connector-arrow { width:0;height:0;border-top:5px solid transparent;border-bottom:5px solid transparent;border-left:7px solid var(--border);transition:border-color .3s; }
.rd-connector-arrow-lit { border-left-color:var(--c,#818cf8) !important; }

/* stage card */
.rd-stage-card  { width:148px;min-height:170px;border-radius:12px;padding:1rem .875rem;
  display:flex;flex-direction:column;gap:5px;cursor:pointer;position:relative;
  background:var(--card);border:1px solid var(--border);transition:all .2s;flex-shrink:0;overflow:hidden; }
.rd-stage-card:hover { transform:translateY(-2px);border-color:#334155; }
.rd-stage-done   { background:linear-gradient(135deg,var(--card),#0a1628);border-color:color-mix(in srgb,var(--sc) 30%,transparent) !important; }
.rd-stage-done:hover { box-shadow:0 6px 28px color-mix(in srgb,var(--sc) 20%,transparent); }
.rd-stage-failed { border-color:#7f1d1d !important;background:#0d0505; }
.rd-stage-busy   { border-color:#1e3a5f !important; }
.rd-stage-pop    { animation:pop-in .4s ease; }
.rd-stage-locked { opacity:.5;cursor:not-allowed; }
.rd-glow-ring    { position:absolute;inset:-1px;border-radius:12px;pointer-events:none;
  border:1px solid color-mix(in srgb,var(--sc) 40%,transparent);animation:ring-pulse 2.5s ease infinite; }
.rd-stage-icon   { font-size:1.6rem;line-height:1;transition:color .2s; }
.rd-pulse-icon   { animation:pulse-glow 2.5s ease infinite; }
.rd-spin         { animation:spin .7s linear infinite;display:inline-block; }
.rd-stage-name   { font-size:.67rem;font-weight:700;text-transform:uppercase;letter-spacing:.07em;color:var(--text-2); }
.rd-stage-badge  { display:inline-flex;align-items:center;gap:5px;font-size:.67rem;font-weight:600;padding:2px 8px;border-radius:20px;background:var(--surface);width:fit-content; }
.rd-badge-done   { color:var(--sc,#34d399);background:color-mix(in srgb,var(--sc,#34d399) 12%,transparent); }
.rd-badge-failed { color:#f87171;background:#1f080820; }
.rd-badge-busy   { color:#60a5fa;background:#0c1f3820; }
.rd-badge-pending{ color:var(--text-3); }
.rd-dot-done  { width:6px;height:6px;border-radius:50%;background:var(--sc,#34d399);display:inline-block; }
.rd-dot-fail  { width:6px;height:6px;border-radius:50%;background:#f87171;display:inline-block; }
.rd-dot-pending{ width:6px;height:6px;border-radius:50%;background:#334155;display:inline-block; }
.rd-dot-spin  { width:8px;height:8px;border:1.5px solid #1e3a5f;border-top-color:#60a5fa;border-radius:50%;display:inline-block;animation:spin .7s linear infinite; }
.rd-stage-detail { font-size:.63rem;color:var(--text-3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
.rd-stage-actions { margin-top:auto; }
.rd-run-btn  { width:100%;padding:.35rem;border-radius:8px;font-size:.7rem;font-weight:700;cursor:pointer;
  border:1px solid color-mix(in srgb,var(--sc) 50%,transparent);
  background:color-mix(in srgb,var(--sc) 12%,transparent);color:var(--sc);transition:all .15s; }
.rd-run-btn:hover { filter:brightness(1.3);transform:translateY(-1px); }
.rd-rerun-btn { width:100%;padding:.3rem;border-radius:8px;font-size:.65rem;font-weight:600;cursor:pointer;
  border:1px solid var(--border);background:transparent;color:var(--text-3);transition:all .15s; }
.rd-rerun-btn:hover { border-color:var(--text-3);color:var(--text-2); }
.rd-lock-icon { position:absolute;top:8px;right:8px;font-size:.75rem;opacity:.5; }

/* progress */
.rd-progress-wrap  { display:flex;align-items:center;gap:.75rem;margin-top:1rem; }
.rd-progress-track { flex:1;height:4px;background:var(--border);border-radius:2px;overflow:hidden; }
.rd-progress-fill  { height:100%;background:linear-gradient(90deg,#6366f1,#34d399);border-radius:2px;transition:width .8s ease; }
.rd-progress-label { font-size:.7rem;color:var(--text-3);white-space:nowrap; }

/* actions bar */
.rd-actions-bar   { background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:1.1rem 1.5rem;margin-bottom:1.25rem; }
.rd-actions-label { font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:var(--text-3);margin-bottom:.6rem; }
.rd-actions-row   { display:flex;gap:.5rem;flex-wrap:wrap;margin-bottom:.6rem; }
.rd-action-btn    { display:inline-flex;align-items:center;gap:6px;padding:.45rem .9rem;border-radius:8px;
  font-size:.78rem;font-weight:600;text-decoration:none;transition:all .15s;cursor:pointer;border:none;white-space:nowrap; }
.rd-action-btn:disabled { opacity:.4;cursor:not-allowed; }
.rd-action-btn:not(:disabled):hover { transform:translateY(-1px);filter:brightness(1.2); }
.rd-action-pr     { background:#0d2137;color:#60a5fa;border:1px solid #1e3a5f; }
.rd-action-diff   { background:#0a1628;color:#818cf8;border:1px solid rgba(99,102,241,.3); }
.rd-action-merge  { background:linear-gradient(135deg,#1e3a5f,#312e81);color:#a5b4fc;border:1px solid rgba(99,102,241,.4); }
.rd-action-ai     { background:linear-gradient(135deg,#2d1657,#1a0a3d);color:#c084fc;border:1px solid rgba(192,132,252,.3); }
.rd-action-es     { background:var(--card);color:var(--text-3);border:1px solid var(--border); }
.rd-btn-busy      { opacity:.7;cursor:wait !important; }
.rd-actions-note  { font-size:.72rem;color:var(--text-3);line-height:1.5; }
.rd-actions-note code { background:var(--card);padding:1px 6px;border-radius:4px;color:#a5b4fc; }

/* tabs */
.rd-tabs-bar  { display:flex;gap:0;border-bottom:1px solid var(--border);margin-bottom:1.5rem; }
.rd-tab       { padding:.55rem 1.1rem;font-size:.82rem;font-weight:500;background:transparent;
  border:none;border-bottom:2px solid transparent;color:var(--text-3);cursor:pointer;
  transition:all .15s;margin-bottom:-1px;display:inline-flex;align-items:center;gap:5px; }
.rd-tab:hover   { color:var(--text-2); }
.rd-tab-active  { color:var(--text);border-bottom-color:var(--primary); }
.rd-tab-dot     { width:6px;height:6px;border-radius:50%;display:inline-block; }
.rd-dot-green   { background:#34d399; }
.rd-dot-red     { background:#f87171; }
.rd-tab-content { animation:fade-up .25s ease; }

/* overview */
.rd-overview-grid { display:grid;grid-template-columns:1fr 1fr 1fr;gap:1rem; }
.rd-stats-grid    { display:grid;grid-template-columns:1fr 1fr;gap:.5rem; }
.rd-stat-chip     { background:var(--surface);border:1px solid var(--border);border-radius:8px;padding:.5rem .75rem; }
.rd-stat-value    { font-size:1.4rem;font-weight:700; }
.rd-stat-label    { font-size:.62rem;color:var(--text-3);text-transform:uppercase;letter-spacing:.05em; }
.rd-compliance-summary { display:flex;align-items:center;gap:1rem; }
.rd-entity-list   { display:flex;flex-direction:column;gap:6px; }
.rd-entity-chip   { background:var(--surface);border:1px solid rgba(99,102,241,.2);border-radius:8px;padding:.5rem .75rem; }
.rd-entity-name   { color:#a5b4fc;font-weight:700;font-size:.875rem; }
.rd-entity-fields { color:var(--text-3);font-size:.75rem;margin-left:8px; }

/* glass card */
.rd-glass-card   { background:var(--card);border:1px solid color-mix(in srgb,var(--gc) 20%,var(--border));border-radius:12px;overflow:hidden; }
.rd-glass-header { display:flex;align-items:center;gap:.5rem;padding:.75rem 1.25rem;background:color-mix(in srgb,var(--gc) 5%,var(--surface));border-bottom:1px solid var(--border); }
.rd-glass-title  { font-size:.73rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--text-2); }
.rd-glass-body   { padding:1rem 1.25rem; }

/* tab sections */
.rd-tab-sections { display:flex;flex-direction:column;gap:1rem; }
.rd-score-cards  { display:grid;grid-template-columns:repeat(4,1fr);gap:1rem; }
.rd-score-card   { background:var(--card);border:1px solid var(--border);border-radius:10px;padding:1rem 1.25rem; }
.rd-score-label  { font-size:.68rem;color:var(--text-3);text-transform:uppercase;letter-spacing:.05em;margin-bottom:6px; }
.rd-score-value  { font-size:1.6rem;font-weight:700; }

/* endpoints */
.rd-endpoint-list { display:flex;flex-direction:column;gap:4px; }
.rd-endpoint-row  { display:flex;align-items:center;gap:.75rem;padding:.45rem .75rem;background:var(--surface);border-radius:7px;border:1px solid var(--border); }
.rd-endpoint-path { color:var(--text);font-size:.83rem;flex:1; }
.rd-endpoint-desc { color:var(--text-3);font-size:.78rem; }
.rd-tag-list      { display:flex;flex-wrap:wrap;gap:.4rem; }
.rd-validation-tag{ background:var(--surface);border:1px solid var(--border);border-radius:6px;padding:3px 10px;font-size:.78rem;display:inline-flex;gap:6px; }
.rd-vt-field { color:#a5b4fc;font-weight:600; }
.rd-vt-rule  { color:var(--text-3); }
.rd-security-list { display:flex;flex-direction:column;gap:4px; }
.rd-security-row  { display:flex;align-items:flex-start;gap:.75rem;padding:.5rem .75rem;background:var(--surface);border-radius:7px;border:1px solid var(--border); }
.rd-sec-category  { background:#1f0a00;color:#fb923c;padding:2px 8px;border-radius:4px;font-size:.7rem;font-weight:600;white-space:nowrap;border:1px solid #7c2d12; }
.rd-sec-desc      { color:var(--text-2);font-size:.85rem; }

/* code */
.rd-code-header { display:flex;align-items:center;gap:.75rem;margin-bottom:1rem;background:var(--card);padding:.75rem 1rem;border-radius:8px;border:1px solid var(--border); }
.rd-code-stat   { color:#a5b4fc;font-weight:600;font-size:.875rem; }
.rd-code-sep    { color:var(--text-3); }
.rd-code-pkg    { color:var(--text-3);font-size:.8rem; }
.rd-file-list   { display:flex;flex-direction:column;gap:6px; }
.rd-file-item   { background:var(--card);border-radius:8px;border:1px solid var(--border);overflow:hidden; }
.rd-file-summary{ padding:.75rem 1rem;cursor:pointer;display:flex;align-items:center;gap:.75rem;user-select:none;list-style:none;transition:background .15s; }
.rd-file-summary:hover { background:var(--surface); }
.rd-file-type-badge { background:var(--surface);border:1px solid var(--border);color:var(--text-3);padding:2px 8px;border-radius:4px;font-size:.68rem;white-space:nowrap; }
.rd-file-name   { color:#a5b4fc;font-family:monospace;font-size:.875rem;flex:1; }
.rd-file-chevron{ color:var(--text-3);font-size:1rem;transition:transform .2s; }
details[open] .rd-file-chevron { transform:rotate(90deg); }
.rd-file-code   { padding:1rem 1.25rem;overflow-x:auto;font-size:.76rem;color:#cbd5e1;border-top:1px solid var(--border);background:#060c18;line-height:1.65;max-height:420px;margin:0; }

/* PR tab */
.rd-pr-tab        { display:flex;flex-direction:column;gap:1rem; }
.rd-pr-hero       { display:flex;align-items:center;gap:1rem;background:var(--card);border:1px solid var(--border);border-radius:12px;padding:1.25rem 1.5rem;flex-wrap:wrap; }
.rd-pr-hero-icon  { font-size:2rem;color:#a78bfa; }
.rd-pr-hero-title { font-size:1.1rem;font-weight:700;color:var(--text); }
.rd-pr-hero-sub   { font-size:.78rem;color:var(--text-3); }
.rd-pr-open-btn   { margin-left:auto;background:#0d2137;border:1px solid #1e3a5f;color:#60a5fa;padding:.5rem 1.1rem;border-radius:8px;font-size:.82rem;font-weight:600;text-decoration:none;transition:all .15s; }
.rd-pr-detail-grid{ background:var(--card);border:1px solid var(--border);border-radius:10px;overflow:hidden; }
.rd-pr-detail-row { display:flex;gap:1rem;padding:.75rem 1.25rem;border-bottom:1px solid var(--border);font-size:.85rem; }
.rd-pr-detail-row:last-child { border-bottom:none; }
.rd-pr-detail-row span:first-child { color:var(--text-3);min-width:120px; }
.rd-merge-guide   { background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:1.25rem 1.5rem; }
.rd-merge-guide-title { font-size:.8rem;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:var(--text-3);margin-bottom:.875rem; }
.rd-merge-steps   { display:flex;flex-direction:column;gap:.6rem;margin-bottom:1.25rem; }
.rd-merge-step    { display:flex;align-items:flex-start;gap:.875rem;font-size:.875rem;color:var(--text-2); }
.rd-merge-num     { width:22px;height:22px;border-radius:50%;background:var(--primary-dim);color:#818cf8;font-size:.72rem;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0;border:1px solid rgba(99,102,241,.3); }
.rd-merge-cta     { display:inline-flex;align-items:center;gap:6px;background:linear-gradient(135deg,#312e81,#1e3a5f);color:#a5b4fc;padding:.65rem 1.25rem;border-radius:8px;font-size:.875rem;font-weight:600;text-decoration:none;border:1px solid rgba(99,102,241,.4);transition:all .15s; }
.rd-merge-cta:hover { filter:brightness(1.2);transform:translateY(-1px); }

/* compliance / risk */
.rd-gap-row      { display:flex;align-items:flex-start;gap:.75rem;padding:.75rem 1rem;background:var(--surface);border-radius:8px;border:1px solid var(--border); }
.rd-gap-desc     { color:var(--text);font-size:.875rem;margin-bottom:2px; }
.rd-gap-expected { color:var(--text-3);font-size:.78rem; }
.rd-recommendation { margin-top:.875rem;background:rgba(248,113,113,.06);border:1px solid rgba(248,113,113,.2);border-radius:8px;padding:.75rem 1rem;color:var(--text-2);font-size:.85rem;line-height:1.6; }

/* AI review */
.rd-review-hero      { border-radius:12px;padding:1.25rem 1.5rem;border:1px solid; }
.rd-review-approved  { background:#052e16;border-color:#065f46; }
.rd-review-changes   { background:#1f0808;border-color:#7f1d1d; }
.rd-review-verdict   { display:flex;align-items:center;gap:.875rem;margin-bottom:.875rem; }
.rd-review-icon      { font-size:1.75rem; }
.rd-review-verdict-label { font-size:1rem;font-weight:700;color:var(--text); }
.rd-review-meta      { font-size:.75rem;color:var(--text-3);margin-top:2px; }
.rd-review-summary   { color:var(--text-2);font-size:.875rem;line-height:1.7;margin:0 0 .875rem; }
.rd-issue-row        { background:var(--surface);border:1px solid var(--border);border-radius:8px;padding:.875rem 1rem; }
.rd-issue-header     { display:flex;align-items:center;gap:.6rem;margin-bottom:.5rem;flex-wrap:wrap; }
.rd-issue-category   { font-size:.72rem;font-weight:700;color:var(--text-3);text-transform:uppercase;letter-spacing:.05em; }
.rd-issue-location   { font-size:.72rem;color:#818cf8;background:var(--primary-dim);padding:1px 6px;border-radius:4px; }
.rd-issue-desc       { color:var(--text);font-size:.85rem;margin-bottom:.4rem; }
.rd-issue-suggestion { color:var(--text-3);font-size:.8rem;display:flex;gap:5px; }
.rd-suggestion-label { color:#34d399;font-weight:600;flex-shrink:0; }
.rd-run-btn-large    { background:linear-gradient(135deg,#2d1657,#1a0a3d);color:#c084fc;border:1px solid rgba(192,132,252,.4);padding:.65rem 2rem;border-radius:10px;font-size:.9rem;font-weight:700;cursor:pointer;transition:all .15s; }
.rd-run-btn-large:hover { filter:brightness(1.2);transform:translateY(-2px); }
.rd-big-spinner      { width:40px;height:40px;border:3px solid rgba(192,132,252,.2);border-top-color:#c084fc;border-radius:50%;animation:spin .8s linear infinite; }

/* ── Fix Loop Panel ── */
.rd-fix-panel {
  background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(139,92,246,0.06));
  border: 1px solid rgba(99,102,241,0.25);
  border-radius: 14px;
  padding: 1.5rem;
}
.rd-fix-panel-header {
  display: flex; align-items: flex-start; gap: 0.875rem; margin-bottom: 1rem;
  font-size: 1.1rem; color: #a5b4fc;
}
.rd-fix-textarea {
  width: 100%; box-sizing: border-box;
  background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
  color: var(--text); font-size: 0.875rem; padding: 0.75rem;
  resize: vertical; outline: none; font-family: inherit;
  margin-bottom: 0.875rem; transition: border-color 0.2s;
}
.rd-fix-textarea:focus { border-color: #6366f1; }
.rd-fix-btn {
  background: linear-gradient(135deg,#6366f1,#8b5cf6); color: white; border: none;
  border-radius: 8px; padding: 0.7rem 1.5rem; font-size: 0.9rem; font-weight: 700;
  cursor: pointer; transition: opacity 0.2s; letter-spacing: 0.02em;
}
.rd-fix-btn:hover { opacity: 0.85; }
.rd-fix-progress { display: flex; align-items: center; gap: 1rem; }
.rd-fix-spinner { width:28px;height:28px;border:3px solid rgba(99,102,241,.2);border-top-color:#6366f1;border-radius:50%;animation:spin .7s linear infinite;flex-shrink:0; }
.rd-fix-dot { width:8px;height:8px;border-radius:50%;background:var(--border);transition:background .3s; }
.rd-fix-dot-active { background: #6366f1; }
.rd-fix-result {
  background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 1.5rem;
}
.rd-fix-result-title { font-size: 1rem; font-weight: 700; color: var(--text); margin-bottom: 1rem; }
.rd-fix-scores { display: flex; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 1rem; }
.rd-fix-score-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 0.75rem 1rem; flex: 1; min-width: 120px; }
.rd-fix-score-label { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-3); margin-bottom: 0.25rem; }
.rd-fix-score-val { font-size: 0.95rem; font-weight: 700; color: var(--text); }

summary::-webkit-details-marker { display:none; }
`
