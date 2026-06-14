# Delivery Roadmap

## Phase 1: Requirement-to-PR MVP

1. Jira webhook and manual requirement intake
2. Structured requirement analysis with human correction
3. GitHub App installation and signed webhook intake
4. PR diff ingestion and durable analysis jobs
5. Requirement compliance findings with cited evidence
6. Basic AI review for quality, security, and missing tests
7. Explainable risk score and release-readiness API
8. Minimal React dashboard

This phase proves the differentiator: requirement-to-implementation validation.

## Phase 2: Developer Assistance

1. Architecture and API-contract suggestions
2. Patch-based Spring Boot code generation
3. Unit and integration test generation
4. PR comments and rerun/feedback workflow
5. Elasticsearch hybrid repository search

## Phase 3: Engineering Intelligence

1. Reviewer recommendation using ownership, expertise, availability, and load
2. Cross-system engineering assistant using cited RAG answers
3. Requirement-code-PR-deployment relationship graph
4. Historical quality and delivery metrics
5. Policy configuration by repository and module

## Phase 4: Scale and Operations

1. OpenTelemetry traces, Prometheus metrics, and Grafana dashboards
2. Dead-letter handling, retries, replay, and idempotency controls
3. Tenant isolation, quotas, retention, and cost controls
4. Evaluation datasets for compliance and review accuracy
5. Extract services only where workload or ownership requires independent scaling

## Deliberately Deferred

- Kubernetes before a deployable MVP exists
- AI-generated-code detection, which is unreliable and less valuable than validating all code equally
- A graph database before PostgreSQL relationships and Elasticsearch search prove insufficient
- Productivity rankings based on raw commit or PR counts

