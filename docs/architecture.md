# Architecture

## Architectural Position

Start as a modular monolith, not a fleet of empty microservices.

The platform has substantial domain uncertainty: requirement extraction, compliance evidence, review findings, and risk formulas will evolve together. A modular monolith provides fast iteration and simpler testing. Kafka remains the integration boundary for long-running work and can support later service extraction.

## Capability Boundaries

| Module | Responsibility | Primary store |
|---|---|---|
| Requirement Intelligence | Jira ingestion, normalized requirements, acceptance criteria, analysis | PostgreSQL |
| Code Generation | Plans and controlled code-generation jobs | PostgreSQL/Object storage |
| Repository Intelligence | GitHub repositories, commits, files, symbols, embeddings | Elasticsearch/PostgreSQL |
| PR Intelligence | Webhook intake, diffs, commits, analysis orchestration | PostgreSQL/Elasticsearch |
| Compliance | Requirement-to-code evidence and gap classification | PostgreSQL |
| Review | Quality, architecture, security, and performance findings | PostgreSQL |
| Test Intelligence | Changed behavior, test gaps, coverage evidence | PostgreSQL |
| Risk | Versioned, explainable risk calculation | PostgreSQL |
| Reviewer Recommendation | Ownership, expertise, load, recommendation explanations | PostgreSQL/Elasticsearch |
| Engineering Search | Hybrid keyword, vector, and relationship search | Elasticsearch |
| Delivery Dashboard | Read models and engineering metrics | PostgreSQL/Redis |

Future Java packages should use:

```text
<module>/
  api/             HTTP/event contracts
  application/     use cases and orchestration
  domain/          business rules and repository ports
  infrastructure/  Jira, GitHub, LLM, database, and search adapters
```

## Event Flow

```text
Jira webhook -> requirement ingestion -> requirement.analysis.requested
GitHub webhook -> PR ingestion -> pr.analysis.requested
pr.analysis.requested -> compliance/review/test analyzers
analysis results -> risk calculation -> release readiness projection
```

Events must include an event ID, aggregate ID, correlation ID, schema version, and occurred-at timestamp. Use an outbox table before publishing business events to prevent database/event inconsistencies.

## AI Design Rules

- LLM output is untrusted structured data and must pass schema validation.
- Store prompt version, model, parameters, source references, latency, and token usage.
- Every compliance conclusion must include evidence from both the requirement and code.
- Deterministic analyzers such as compiler output, static analysis, coverage, and secret scanners outrank LLM opinions.
- Code generation produces a patch or branch for human review; it never writes directly to the protected branch.
- Provider-specific APIs stay behind application ports.

## Security Baseline

- Verify Jira and GitHub webhook signatures before accepting events.
- Use OAuth/GitHub App installation tokens; never store personal access tokens in plaintext.
- Encrypt integration credentials and separate tenant data.
- Treat repository text and PR comments as prompt-injection-capable content.
- Restrict generated-code tools to an isolated workspace with explicit file and command policies.
- Preserve an immutable audit trail for AI recommendations and user actions.

