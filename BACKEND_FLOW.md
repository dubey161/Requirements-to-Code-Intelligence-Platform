# AI Engineering Productivity Platform — Complete Backend Flow

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack & Configuration](#2-tech-stack--configuration)
3. [Database Schema — All Flyway Migrations](#3-database-schema--all-flyway-migrations)
4. [Async Architecture](#4-async-architecture)
5. [Phase 1 — Requirement Intake](#5-phase-1--requirement-intake)
6. [Phase 2 — Requirement Analyzer (Rule-Based)](#6-phase-2--requirement-analyzer-rule-based)
7. [Phase 3 — LLM Analyzers (Gemini, Groq, Ollama)](#7-phase-3--llm-analyzers-gemini-groq-ollama)
8. [Phase 4 — Code Generator](#8-phase-4--code-generator)
9. [Phase 5 — GitHub Integration & PR Creation](#9-phase-5--github-integration--pr-creation)
10. [Phase 6 — Jira Integration](#10-phase-6--jira-integration)
11. [Phase 7 — Compliance Engine](#11-phase-7--compliance-engine)
12. [Phase 8 — Risk Scoring Engine](#12-phase-8--risk-scoring-engine)
13. [Phase 9 — AI PR Review](#13-phase-9--ai-pr-review)
14. [Phase 10 — Build Validation](#14-phase-10--build-validation)
15. [Elasticsearch Indexing](#15-elasticsearch-indexing)
16. [Pipeline Report API](#16-pipeline-report-api)
17. [Complete API Reference](#17-complete-api-reference)
18. [End-to-End Flow Walkthrough](#18-end-to-end-flow-walkthrough)

---

## 1. Project Overview

This platform automates the full software delivery pipeline — from raw requirement text to a GitHub Pull Request with compliance, risk, and AI review scores. Every phase is independently triggered via REST API, and all results are persisted in PostgreSQL.

```
Requirement Text
      |
      v
  [Analysis]  <-- rule-based regex OR LLM (Gemini / Groq / Ollama)
      |
      v
  [Code Generation]  <-- Java Entity/Repo/Service/Controller templates
      |
      v
  [Build Validation]  <-- JavaParser syntax + structural integrity checks
      |
      v
  [GitHub PR]  <-- branch, commit files, open PR
      |
      v
  [Compliance Check]  <-- diff vs knowledge model
      |
      v
  [Risk Score]  <-- weighted formula, posted as PR comment
      |
      v
  [AI PR Review]  <-- LLM reviews diff against requirements
```

---

## 2. Tech Stack & Configuration

### Core Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Database | PostgreSQL 15+ |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| HTTP Client | Spring `RestClient` (sync) |
| Async | Spring `@Async` with custom thread pools |
| Search | Elasticsearch (optional, off by default) |
| Cache | Redis (optional, off by default) |
| Build | Maven |

### application.yml — Key Configuration

```yaml
server:
  port: 8081

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/codex}
    username: ${DATABASE_USERNAME:ved}
    password: ${DATABASE_PASSWORD:admin123}
  jpa:
    hibernate:
      ddl-auto: validate       # Flyway owns schema, Hibernate only validates
    open-in-view: false

  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://localhost:9200}
  data:
    elasticsearch:
      repositories:
        enabled: ${ELASTICSEARCH_ENABLED:false}   # off by default
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      repositories:
        enabled: false

analyzer:
  provider: ${ANALYZER_PROVIDER:rule-based}  # rule-based | gemini | groq | ollama
  fallback-to-rule-based: ${ANALYZER_FALLBACK:true}
  gemini:
    api-key: ${GEMINI_API_KEY:}
    model: ${GEMINI_MODEL:gemini-2.0-flash}
    timeout-seconds: 30
  groq:
    api-key: ${GROQ_API_KEY:}
    model: ${GROQ_MODEL:llama-3.3-70b-versatile}
    timeout-seconds: 30
  ollama:
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:llama3.2}
    timeout-seconds: 120

github:
  token: ${GITHUB_TOKEN:}
  owner: ${GITHUB_OWNER:}
  repo: ${GITHUB_REPO:}
  base-branch: ${GITHUB_BASE_BRANCH:main}
  webhook:
    secret: ${GITHUB_WEBHOOK_SECRET:}
    enabled: ${GITHUB_WEBHOOK_ENABLED:true}

jira:
  base-url: ${JIRA_BASE_URL:}
  email: ${JIRA_EMAIL:}
  api-token: ${JIRA_API_TOKEN:}

codegen:
  base-package: ${CODEGEN_BASE_PACKAGE:com.example.generated}
```

### Required Environment Variables

```bash
# Minimal (rule-based works without LLM keys)
DATABASE_URL=jdbc:postgresql://localhost:5432/codex
DATABASE_USERNAME=ved
DATABASE_PASSWORD=admin123

# Pick one LLM provider (optional but recommended)
ANALYZER_PROVIDER=gemini
GEMINI_API_KEY=your_key_here          # free at aistudio.google.com

# OR
ANALYZER_PROVIDER=groq
GROQ_API_KEY=your_key_here            # free at console.groq.com

# OR (fully local, no key needed)
ANALYZER_PROVIDER=ollama              # requires ollama running: ollama pull llama3.2

# GitHub (for PR creation)
GITHUB_TOKEN=ghp_...
GITHUB_OWNER=your_username
GITHUB_REPO=your_repo

# Jira (for issue import)
JIRA_BASE_URL=https://yourcompany.atlassian.net
JIRA_EMAIL=you@company.com
JIRA_API_TOKEN=your_token
```

---

## 3. Database Schema — All Flyway Migrations

Flyway runs on startup and applies migrations in order. Schema is never modified by Hibernate (`ddl-auto: validate`).

### V1 — Requirement Tables

```sql
-- Core requirement story
CREATE TABLE requirement_story (
    id           UUID         PRIMARY KEY,
    external_key VARCHAR(100) NOT NULL UNIQUE,   -- e.g. "PROJ-123" or custom key
    title        VARCHAR(300) NOT NULL,
    description  TEXT         NOT NULL,
    status       VARCHAR(30)  NOT NULL,           -- RECEIVED | ANALYSIS_PENDING | ANALYZED | ANALYSIS_FAILED
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0  -- optimistic locking
);

-- Acceptance criteria stored as ordered list (not a JSON blob)
CREATE TABLE requirement_acceptance_criterion (
    requirement_id  UUID    NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    criterion_order INTEGER NOT NULL,
    criterion       TEXT    NOT NULL,
    CONSTRAINT uq_requirement_acceptance_criterion_order UNIQUE (requirement_id, criterion_order)
);

CREATE INDEX idx_requirement_acceptance_criterion_requirement
    ON requirement_acceptance_criterion (requirement_id);
```

**Design notes:**
- `external_key` enforces no duplicate intake of the same Jira ticket or custom story
- Acceptance criteria are stored in a separate table with ordering to preserve the original sequence
- `version` column enables JPA optimistic locking to prevent concurrent update races
- `status` transitions: `RECEIVED` → `ANALYSIS_PENDING` → `ANALYZED` (or `ANALYSIS_FAILED`)

---

### V2 — Requirement Analysis Table

```sql
CREATE TABLE requirement_analysis (
    id               UUID        PRIMARY KEY,
    requirement_id   UUID        NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    knowledge_model  TEXT        NOT NULL,          -- full RequirementKnowledgeModel serialized as JSON
    analyzer_version VARCHAR(50) NOT NULL,          -- e.g. "rule-based-v1" or "llm-gemini-2.0-flash-v1"
    analyzed_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_requirement_analysis_requirement UNIQUE (requirement_id)  -- one analysis per requirement
);

CREATE INDEX idx_requirement_analysis_requirement ON requirement_analysis (requirement_id);
```

**Design notes:**
- `knowledge_model` is a `TEXT` column holding a complete JSON document (not JSONB) for portability
- A `KnowledgeModelConverter` (`@Converter`) handles Java record <-> JSON string automatically
- The unique constraint means re-analysis updates the existing row (upsert pattern)
- `analyzer_version` provides full auditability of which analyzer produced the result

**The `RequirementKnowledgeModel` JSON shape stored in `knowledge_model`:**

```json
{
  "entities": [
    {
      "name": "Order",
      "description": "Domain entity identified from requirement",
      "fields": [
        { "name": "id", "inferredType": "UUID", "required": false, "constraints": [] },
        { "name": "customerId", "inferredType": "UUID", "required": true, "constraints": [] }
      ],
      "relationships": ["Order belongs to Customer"]
    }
  ],
  "apiEndpoints": [
    {
      "httpMethod": "POST",
      "suggestedPath": "/api/v1/orders",
      "description": "Create a new order",
      "requestEntity": "CreateOrderRequest",
      "responseEntity": "OrderResponse"
    }
  ],
  "validationRules": [
    {
      "targetField": "email",
      "ruleType": "FORMAT",
      "description": "email must be a valid email address",
      "extractedFrom": "the source text that triggered this rule"
    }
  ],
  "functionalRequirements": [
    { "id": "FR-001", "description": "...", "priority": "MUST", "sourceContext": "acceptance-criteria" }
  ],
  "securityRequirements": [
    { "category": "AUTHENTICATION", "description": "..." }
  ],
  "edgeCases": [
    { "condition": "Order already exists", "expectedBehavior": "Return 409 Conflict", "sourceContext": "..." }
  ]
}
```

---

### V3 — Generated Code Bundle

```sql
CREATE TABLE generated_code_bundle (
    id              UUID         PRIMARY KEY,
    requirement_id  UUID         NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    generated_files TEXT         NOT NULL,           -- JSON array of GeneratedFile objects
    target_package  VARCHAR(200) NOT NULL,           -- e.g. "com.example.generated"
    generated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_generated_code_requirement UNIQUE (requirement_id)
);

CREATE INDEX idx_generated_code_requirement ON generated_code_bundle (requirement_id);
```

**Design notes:**
- `generated_files` is a JSON array of `{ fileName, fileType, packagePath, content }` objects
- `GeneratedFilesConverter` handles Java list <-> JSON string
- `fileType` enum values: `ENTITY`, `REPOSITORY`, `SERVICE`, `CONTROLLER`, `REQUEST`, `RESPONSE`
- Re-generation refreshes the existing row (upsert)

---

### V4 — Pull Request

```sql
CREATE TABLE pull_request (
    id             UUID         PRIMARY KEY,
    requirement_id UUID         NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    pr_number      INTEGER      NOT NULL,
    html_url       VARCHAR(500) NOT NULL,
    head_branch    VARCHAR(200) NOT NULL,
    head_sha       VARCHAR(100) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_pull_request_requirement UNIQUE (requirement_id)
);

CREATE INDEX idx_pull_request_requirement ON pull_request (requirement_id);
```

**Design notes:**
- `pr_number` is the GitHub PR number, used by compliance, risk, and AI review to fetch PR data
- `head_sha` is the commit SHA of the PR head, useful for diff operations

---

### V5 — Compliance Report

```sql
CREATE TABLE compliance_report (
    id               UUID    PRIMARY KEY,
    requirement_id   UUID    NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    pr_number        INTEGER NOT NULL,
    gaps             TEXT    NOT NULL,                -- JSON array of ComplianceGap objects
    compliance_score INTEGER NOT NULL CHECK (compliance_score BETWEEN 0 AND 100),
    checked_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_compliance_report_requirement UNIQUE (requirement_id)
);

CREATE INDEX idx_compliance_report_requirement ON compliance_report (requirement_id);
```

**Design notes:**
- `gaps` is a JSON array of `{ category, severity, description }` where `category` is `MISSING_ENTITY`, `MISSING_ENDPOINT`, `MISSING_VALIDATION`, `MISSING_SECURITY`, and `severity` is `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- `compliance_score` ranges 0 (completely non-compliant) to 100 (fully compliant)
- The risk engine reads this table — so compliance must be run before risk scoring

---

### V6 — Risk Score

```sql
CREATE TABLE risk_score (
    id                        UUID        PRIMARY KEY,
    requirement_id            UUID        NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    pr_number                 INTEGER     NOT NULL,
    overall_score             INTEGER     NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    compliance_contribution   INTEGER     NOT NULL,    -- weighted input from compliance (40%)
    security_contribution     INTEGER     NOT NULL,    -- weighted input from security gaps (35%)
    completeness_contribution INTEGER     NOT NULL,    -- weighted input from missing entities (25%)
    risk_level                VARCHAR(20) NOT NULL,    -- LOW | MEDIUM | HIGH | CRITICAL
    recommendation            TEXT        NOT NULL,
    scored_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_risk_score_requirement UNIQUE (requirement_id)
);

CREATE INDEX idx_risk_score_requirement ON risk_score (requirement_id);
```

---

### V7 — AI PR Review

```sql
CREATE TABLE ai_pr_review (
    id             UUID         PRIMARY KEY,
    requirement_id UUID         NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    pr_number      INTEGER      NOT NULL,
    model_id       VARCHAR(100) NOT NULL,     -- e.g. "gemini-2.0-flash"
    prompt_version VARCHAR(20)  NOT NULL,     -- e.g. "v1" — for prompt evolution tracking
    summary        TEXT         NOT NULL,
    approved       BOOLEAN      NOT NULL DEFAULT FALSE,
    issues         TEXT         NOT NULL,     -- JSON array of AiReviewIssue objects
    raw_response   TEXT,                      -- full LLM output for debugging
    reviewed_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Multiple reviews per requirement (history as models are upgraded)
CREATE INDEX idx_ai_pr_review_requirement ON ai_pr_review (requirement_id, reviewed_at DESC);
```

**Design notes:**
- Unlike other tables, this has NO unique constraint on `requirement_id` — every review run creates a new row
- `reviewed_at DESC` index lets the API efficiently return the latest review
- `raw_response` preserves the full LLM output for debugging prompt issues
- `model_id` + `prompt_version` enables reproducibility audits

---

### V8 — Widen Analyzer Version Column

```sql
ALTER TABLE requirement_analysis ALTER COLUMN analyzer_version TYPE VARCHAR(200);
```

Simple column widening migration to accommodate longer LLM model version strings.

---

### V9 — Build Validation Report

```sql
CREATE TABLE build_validation_report (
    id             UUID    PRIMARY KEY,
    requirement_id UUID    NOT NULL REFERENCES requirement_story(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL,    -- PASSED | WARNING | FAILED
    file_count     INTEGER NOT NULL DEFAULT 0,
    error_count    INTEGER NOT NULL DEFAULT 0,
    warning_count  INTEGER NOT NULL DEFAULT 0,
    checks         TEXT    NOT NULL,        -- JSON array of ValidationCheck objects
    validated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_build_validation_requirement UNIQUE (requirement_id)
);

CREATE INDEX idx_build_validation_requirement ON build_validation_report (requirement_id);
```

**Design notes:**
- `checks` JSON contains individual check results: syntax check, structural integrity, annotation presence, package consistency
- `status` is `FAILED` if any errors > 0, `WARNING` if only warnings, `PASSED` otherwise

---

## 4. Async Architecture

The platform uses two dedicated thread pools to avoid blocking HTTP threads during heavy operations.

```
AnalysisExecutor (analysis-)
  corePool=4, maxPool=8, queue=50
  Used for: LLM calls, Elasticsearch indexing after analysis

PipelineExecutor (pipeline-)
  corePool=2, maxPool=4, queue=100
  Used for: compliance check, risk scoring, code indexing updates
```

**How async analysis works after requirement creation:**

```
POST /api/v1/requirements
        |
        v
  RequirementService.create()
        |
        v  [within @Transactional]
  repository.save(story)           -- persists to DB with status=ANALYSIS_PENDING
        |
        v
  eventPublisher.publishEvent(     -- Spring ApplicationEvent
      new RequirementCreatedEvent(id, externalKey)
  )
        |  [transaction commits]
        |
        v  [Spring @TransactionalEventListener — fires AFTER commit]
  RequirementAnalyzerService.analyzeAsync(id)   -- runs on analysisExecutor thread pool
        |
        v
  story.status = ANALYZED (or ANALYSIS_FAILED)
```

**Why publish event after commit?** If the event fires inside the transaction, the async thread might read the story before it is visible in the database. Publishing after commit guarantees the story row is already committed when the analyzer reads it.

---

## 5. Phase 1 — Requirement Intake

### API

**`POST /api/v1/requirements`**

Request body:
```json
{
  "externalKey": "PROJ-42",
  "title": "User Registration with Email Verification",
  "description": "The system must allow new users to register...",
  "acceptanceCriteria": [
    "Email must be unique across all accounts",
    "Password must be at least 8 characters",
    "A verification email must be sent on registration"
  ]
}
```

Response (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "externalKey": "PROJ-42",
  "title": "User Registration with Email Verification",
  "status": "ANALYSIS_PENDING",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

**`GET /api/v1/requirements/{id}`** — fetch a requirement by UUID

### Validation

`CreateRequirementRequest` validates with Bean Validation:
- `externalKey` — not blank, max 100 chars
- `title` — not blank, max 300 chars
- `description` — not blank
- `acceptanceCriteria` — list, may be empty

If `externalKey` already exists, a `409 Conflict` is returned (checked via `repository.existsByExternalKey()`).

### What Happens Internally

1. `RequirementController.create()` receives and validates the request
2. Calls `RequirementService.create()` within a `@Transactional` context
3. `RequirementStory.receive()` factory method creates the domain object with `status = RECEIVED`, then immediately transitions to `ANALYSIS_PENDING`
4. The story is persisted to `requirement_story` and `requirement_acceptance_criterion` tables
5. A `RequirementCreatedEvent` is published via Spring's `ApplicationEventPublisher`
6. After the transaction commits, the event listener triggers async analysis
7. The HTTP response returns immediately with `ANALYSIS_PENDING` — the client does not wait for analysis

---

## 6. Phase 2 — Requirement Analyzer (Rule-Based)

### Overview

The `RuleBasedRequirementAnalyzer` is the default analyzer (no LLM required). It uses pure regex and heuristic patterns to extract a `RequirementKnowledgeModel` from the requirement text.

**Version string:** `rule-based-v1`

### What It Extracts

#### Entities

Two-pass extraction:
1. **CRUD verb pattern** — matches phrases like "create Order", "register User", "manage Product" (weight: 2)
2. **Capitalized noun frequency** — counts any `Title-Case` word that is not a stop word (weight: 1)

Only candidates with total weight >= 2 are retained. For each entity it infers:
- **Fields** — looks for possessive patterns like "order's status", "user's email" to infer field names
- **Field types** — name-based heuristic: `id/uuid` → UUID, `price/cost` → BigDecimal, `date/at` → Instant, `enabled/active` → Boolean, otherwise String
- **Relationships** — matches "Order has many Items", "Order belongs to Customer"

#### API Endpoints

Maps CRUD verbs to HTTP methods:
- create/add/register/submit → `POST /api/v1/{resource}s`
- list/search/find all → `GET /api/v1/{resource}s`
- get/retrieve/fetch/view → `GET /api/v1/{resource}s/{id}`
- update/edit/modify → `PUT /api/v1/{resource}s/{id}`
- delete/remove/cancel → `DELETE /api/v1/{resource}s/{id}`

#### Validation Rules

| Pattern detected | Rule type |
|---|---|
| "field is required" | `NOT_NULL` |
| "must not exceed 255 characters" | `MAX_LENGTH` |
| "at least 8 characters" | `MIN_LENGTH` |
| "must be a valid email address" | `FORMAT` |
| "must be between 1 and 100" | `RANGE` |
| "must be unique" | `UNIQUE` |
| "must not contain spaces/special characters" | `PATTERN` with `^[a-zA-Z0-9]+$` |

#### Functional Requirements

- Each acceptance criterion becomes an `FR-001`, `FR-002`, ... entry
- `must/shall` → priority `MUST`, `should` → `SHOULD`, `may/can` → `MAY`
- Additional "must/should/shall" statements in the description text also become FRs (deduplication against AC)

#### Security Requirements

Regex keyword scanning against 5 categories:
- `AUTHENTICATION` — authenticated, logged in, JWT, OAuth, session
- `AUTHORIZATION` — authorized, permission, role, admin, restricted
- `DATA_PROTECTION` — encrypt, hash, bcrypt, PII, sensitive, password
- `RATE_LIMITING` — rate limit, throttle, brute force
- `INPUT_SANITIZATION` — inject, XSS, sanitize, SQL injection

#### Edge Cases

- Conditional clauses: "if/when X, then Y" patterns
- `409 Conflict` — triggered by "already exists/duplicate/conflict"
- `404 Not Found` — triggered by "not found/does not exist"
- `400 Bad Request` — triggered by "invalid/incorrect/malformed"

### API

**`POST /api/v1/requirements/{id}/analysis`** — trigger or re-trigger analysis

**`GET /api/v1/requirements/{id}/analysis`** — fetch the stored knowledge model

---

## 7. Phase 3 — LLM Analyzers (Gemini, Groq, Ollama)

### Strategy Pattern

The analyzer is a strategy interface:

```
RequirementAnalyzer (interface)
    |-- RuleBasedRequirementAnalyzer   (no LLM, always available)
    |-- LlmRequirementAnalyzer         (wraps any LlmGateway)
    |-- FallbackRequirementAnalyzer    (tries LLM, falls back to rule-based on failure)

LlmGateway (interface)
    |-- GeminiLlmGateway
    |-- GroqLlmGateway
    |-- OllamaLlmGateway
```

`AnalyzerConfiguration` reads `ANALYZER_PROVIDER` and wires the correct beans at startup:

```
ANALYZER_PROVIDER=rule-based  →  RuleBasedRequirementAnalyzer (no LLM bean)
ANALYZER_PROVIDER=gemini      →  FallbackRequirementAnalyzer(LlmRequirementAnalyzer(GeminiLlmGateway), RuleBasedRequirementAnalyzer)
ANALYZER_PROVIDER=groq        →  FallbackRequirementAnalyzer(LlmRequirementAnalyzer(GroqLlmGateway), RuleBasedRequirementAnalyzer)
ANALYZER_PROVIDER=ollama      →  FallbackRequirementAnalyzer(LlmRequirementAnalyzer(OllamaLlmGateway), RuleBasedRequirementAnalyzer)
```

If `ANALYZER_FALLBACK=false`, no fallback wrapper is applied.

---

### Prompt Engineering

`PromptBuilder` constructs a two-part prompt:

**System prompt** tells the LLM:
- It is an expert requirements analyst and backend architect
- It must return ONLY valid JSON, no markdown, no explanation
- Extraction rules for each section (entities, endpoints, validations, etc.)
- Field type constraints: `UUID | String | Integer | Long | BigDecimal | Boolean | Instant | Enum`
- HTTP method constraints: `GET | POST | PUT | PATCH | DELETE`
- Rule type constraints: `NOT_NULL | MIN_LENGTH | MAX_LENGTH | FORMAT | RANGE | UNIQUE | PATTERN | POSITIVE | ENUM_VALUE`
- Priority constraints: `MUST | SHOULD | MAY`

**User prompt** includes:
- The requirement title, description, and numbered acceptance criteria
- The exact JSON schema the LLM must populate
- Optional type instruction injected based on `RequirementType` (`ALGORITHM`, `MICROSERVICE`, `BATCH_JOB`)

**Type-aware instructions:**
- `ALGORITHM` — instructs LLM not to generate DB entities or REST endpoints; focus on algorithm logic, edge cases, input/output contracts
- `MICROSERVICE` — instructs to use `httpMethod=EVENT` for Kafka topics, model message payloads not JPA entities
- `BATCH_JOB` — instructs to focus on input source, processing, output; skip REST endpoints unless explicit

---

### Gemini Gateway

**Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`

**Free tier:** 15 RPM, 1500 requests/day — no credit card required. Get key at `aistudio.google.com`.

**Request structure:**
```json
{
  "systemInstruction": { "parts": [{ "text": "...system prompt..." }] },
  "contents": [{ "role": "user", "parts": [{ "text": "...user prompt..." }] }],
  "generationConfig": {
    "responseMimeType": "application/json",
    "temperature": 0.1,
    "maxOutputTokens": 4096
  }
}
```

**Key feature:** `responseMimeType: "application/json"` forces Gemini to output valid JSON only, bypassing markdown code fences.

**Response parsing:** Extracts `candidates[0].content.parts[0].text`.

---

### Groq Gateway

**Endpoint:** `POST https://api.groq.com/openai/v1/chat/completions`

**Free tier:** 30 RPM, 14,400 requests/day. Get key at `console.groq.com`.

**OpenAI-compatible request:**
```json
{
  "model": "llama-3.3-70b-versatile",
  "messages": [
    { "role": "system", "content": "...system prompt..." },
    { "role": "user", "content": "...user prompt..." }
  ],
  "temperature": 0.1,
  "max_tokens": 4096,
  "stream": false,
  "response_format": { "type": "json_object" }
}
```

**Key feature:** `response_format: { type: "json_object" }` forces structured JSON output.

**Auth:** `Authorization: Bearer {apiKey}` header.

**Response parsing:** Extracts `choices[0].message.content`.

---

### Ollama Gateway

**Endpoint:** `POST http://localhost:11434/api/chat`

**Cost:** Free, fully local. Requires Ollama running: `ollama pull llama3.2`.

**Request:**
```json
{
  "model": "llama3.2",
  "messages": [
    { "role": "system", "content": "...system prompt..." },
    { "role": "user", "content": "...user prompt..." }
  ],
  "stream": false,
  "format": "json",
  "options": { "temperature": 0.1 }
}
```

**Key feature:** `format: "json"` activates Ollama's JSON mode.

**Response parsing:** Extracts `message.content`. The `done_reason` field indicates completion.

**Version string:** `ollama-llama3.2` (prefixed with "ollama-" + model name).

---

### JSON Parsing & Fallback Logic

`LlmRequirementAnalyzer.parseResponse()` handles imperfect LLM output:

1. Strips markdown code fences (` ```json ... ``` `) if the model adds them despite instructions
2. Finds the outermost `{...}` by scanning for first `{` and last `}`
3. Deserializes into `RequirementKnowledgeModel` using Jackson
4. On failure throws `LlmParseException` → caught by `FallbackRequirementAnalyzer` → falls back to rule-based

---

## 8. Phase 4 — Code Generator

### API

**`POST /api/v1/requirements/{id}/code`** — generate all Java source files for this requirement

**`GET /api/v1/requirements/{id}/code`** — fetch the generated bundle

### Flow

```
CodeGeneratorService.generate(requirementId)
    |
    v
analyzerService.getAnalysis(requirementId)     -- load RequirementKnowledgeModel from DB
    |
    v
model.resolvedType()                            -- detect ALGORITHM vs SPRING_CRUD vs REST_API etc.
    |
    |-- ALGORITHM  --> AlgorithmCodeGenerator.generate()  -- LeetCode-style solution class
    |-- SPRING_CRUD / REST_API --> generateCrud()         -- full Spring layer set per entity
    |-- others --> generateCrud() (fallback)
    |
    v
bundleRepository.save(GeneratedCodeBundle)      -- upsert
    |
    v
indexingService.indexGeneratedCode()            -- async Elasticsearch index (if enabled)
```

### CRUD Generation — Per Entity, 6 Files

For each entity in the knowledge model, `JavaCodeTemplates` generates:

| File | Description |
|---|---|
| `{Entity}.java` | JPA `@Entity` with `@Id`, all fields, `@Column` constraints |
| `{Entity}Repository.java` | `JpaRepository<Entity, UUID>` interface |
| `Create{Entity}Request.java` | Request DTO with Bean Validation annotations derived from `validationRules` |
| `{Entity}Response.java` | Response DTO with all fields |
| `{Entity}Service.java` | `@Service` with create, getById, list, update, delete methods |
| `{Entity}Controller.java` | `@RestController` mapping endpoints from `apiEndpoints` in the knowledge model |

**Validation rule → annotation mapping in `CreateRequest`:**
- `NOT_NULL` → `@NotBlank` (strings) or `@NotNull` (others)
- `MAX_LENGTH` → `@Size(max = N)`
- `MIN_LENGTH` → `@Size(min = N)`
- `FORMAT` with "email" → `@Email`
- `RANGE` → `@Min(N) @Max(M)`
- `POSITIVE` → `@Positive`

All files are stored as `GeneratedFile` records with `{ fileName, fileType, packagePath, content }`.

---

## 9. Phase 5 — GitHub Integration & PR Creation

### API

**`POST /api/v1/requirements/{id}/pull-request`** — create branch, commit files, open PR

**`GET /api/v1/requirements/{id}/pull-request`** — fetch PR metadata

### Full PR Creation Flow

```
GitHubPrService.createPr(requirementId)
    |
    1. Load RequirementStory from DB
    2. Load GeneratedCodeBundle from DB
    3. CompileValidationService.validate(files)      -- JavaParser syntax check (fail fast)
    |
    4. Build branch name:
       "feat/{externalKey.lower}-{title-slug-40-chars}"
       e.g. "feat/proj-42-user-registration-with-email"
    |
    5. GitHubClient.getBaseBranchSha()              -- GET /repos/{owner}/{repo}/branches/main
    6. GitHubClient.createBranch(name, sha)         -- POST /repos/{owner}/{repo}/git/refs
    |
    7. For each GeneratedFile:
       GitHubClient.upsertFile(branch, path, content, message)
       -- PUT /repos/{owner}/{repo}/contents/src/main/java/{path}
       -- commit message: "feat(PROJ-42): add generated OrderController.java"
    |
    8. GitHubClient.createPullRequest(title, body, branch)
       -- POST /repos/{owner}/{repo}/pulls
       -- title: "[PROJ-42] User Registration with Email Verification"
       -- body: requirement description + acceptance criteria + file list
    |
    9. CreatedPullRequest.create(...) → save to pull_request table
```

### GitHub Contents API (file commit)

Uses PUT to `https://api.github.com/repos/{owner}/{repo}/contents/{path}`:
- If the file doesn't exist: creates it
- If the file exists: fetches the current SHA first, then updates with the SHA in the request body (GitHub requires this for updates)

---

### GitHub Webhook Handler

`GitHubWebhookController` receives `POST /webhook/github`:
- Verifies the `X-Hub-Signature-256` HMAC-SHA256 header using the configured `GITHUB_WEBHOOK_SECRET`
- Routes to `GitHubWebhookHandler` based on the `X-GitHub-Event` header
- Currently handles `pull_request` events (opened, synchronize, closed)
- Designed extension point for auto-triggering compliance/risk on PR updates

---

## 10. Phase 6 — Jira Integration

### API

**`POST /api/v1/requirements/import/jira/{issueKey}`** — import a Jira issue as a requirement

Example: `POST /api/v1/requirements/import/jira/PROJ-42`

### Flow

```
JiraImportController
    |
    v
JiraIssueImporter.importIssue("PROJ-42")
    |
    1. JiraClient.fetchIssue("PROJ-42")
       GET {jiraBaseUrl}/rest/api/3/issue/PROJ-42
       Auth: Basic base64(email:apiToken)
    |
    2. Extract title from fields.summary
    |
    3. Extract description from fields.description (Atlassian Document Format)
       ADF is a recursive JSON structure: doc → paragraph → text nodes
       extractAdfText() walks the node tree and concatenates text values
    |
    4. Extract acceptance criteria from fields.customfield_10016
       This field can be:
       - ADF document (same recursive walk)
       - Plain string (split by newline/bullet)
       - List of strings
       - null (falls back to sentence extraction from description)
    |
    5. If AC is empty/numeric: extractSentencesAsAC(description)
       Splits description on [.!?\\n•] — each sentence > 10 chars becomes an AC
    |
    6. requirementService.create(issueKey, title, description, acceptanceCriteria)
       Same intake path as manual POST — triggers async analysis
```

### Jira Client Details

- **Auth:** HTTP Basic with `Base64(email:apiToken)` in `Authorization` header
- **API version:** REST API v3 (`/rest/api/3/issue/{key}`)
- **ADF parsing:** Handles `text`, `hardBreak`, `paragraph`, `listItem` node types recursively
- **Custom field:** `customfield_10016` is configured via `JIRA_AC_FIELD` env var (default `customfield_10016`)

---

## 11. Phase 7 — Compliance Engine

### API

**`POST /api/v1/requirements/{id}/compliance`** — run compliance check against the PR diff

**`GET /api/v1/requirements/{id}/compliance`** — fetch the compliance report

### What Compliance Checks

The `ComplianceChecker` fetches the PR's changed files via GitHub API (`GET /repos/{owner}/{repo}/pulls/{number}/files`) and checks the actual Java code against what the knowledge model expected.

```
ComplianceChecker.check(knowledgeModel, prFiles)
    |
    |-- For each entity in knowledgeModel.entities():
    |   Check that the entity name appears in the PR diff
    |   Missing entity -> ComplianceGap(MISSING_ENTITY, CRITICAL)
    |
    |-- For each apiEndpoint in knowledgeModel.apiEndpoints():
    |   Check that the endpoint path appears in the diff
    |   Missing endpoint -> ComplianceGap(MISSING_ENDPOINT, HIGH)
    |
    |-- For each validationRule in knowledgeModel.validationRules():
    |   Check that the field name appears in the diff
    |   Missing validation -> ComplianceGap(MISSING_VALIDATION, MEDIUM)
    |
    |-- For each securityRequirement in knowledgeModel.securityRequirements():
    |   Check for category-specific keywords in the diff
    |   e.g. AUTHENTICATION -> looks for @Secured, @PreAuthorize, JWT, Bearer
    |   Missing security -> ComplianceGap(MISSING_SECURITY, CRITICAL)
    |
    v
score = 100 - (sum of weighted gap penalties)
CRITICAL=20pts, HIGH=10pts, MEDIUM=5pts, LOW=2pts
Clamped to [0, 100]
```

**Gap categories:**

| Category | Severity | Description |
|---|---|---|
| `MISSING_ENTITY` | CRITICAL | A domain entity from the requirement is absent in the PR |
| `MISSING_ENDPOINT` | HIGH | An API endpoint is missing |
| `MISSING_VALIDATION` | MEDIUM | A validation rule field is absent |
| `MISSING_SECURITY` | CRITICAL | A security requirement is unaddressed |

After saving the report, `ElasticsearchIndexingService.updateComplianceScore()` is called asynchronously to update the search index.

---

## 12. Phase 8 — Risk Scoring Engine

### API

**`POST /api/v1/requirements/{id}/risk`** — compute risk score (requires compliance report first)

**`GET /api/v1/requirements/{id}/risk`** — fetch the risk score

### Scoring Formula

```
Risk components (all inverted — higher means more risky):

complianceRisk    = 100 - complianceScore              // 40% weight
securityRisk      = min(100, criticalSecurityGaps * 25) // 35% weight
completenessRisk  = (missingEntities / totalEntities) * 100  // 25% weight

overallScore = (complianceRisk * 0.40)
             + (securityRisk   * 0.35)
             + (completenessRisk * 0.25)
             [clamped to 0-100]
```

### Risk Levels

| Score | Level | Recommendation |
|---|---|---|
| 70-100 | CRITICAL | DO NOT MERGE. Address all CRITICAL and HIGH gaps. |
| 50-69 | HIGH | Block merge until HIGH severity gaps resolved. |
| 25-49 | MEDIUM | Merge with caution. Ensure test coverage. |
| 0-24 | LOW | Safe to merge. Minor improvements possible. |

### PR Comment

After computing the score, `GitHubClient.createReviewComment()` posts a markdown comment to the PR:

```
## [emoji] AI Engineering Productivity Platform — Risk Assessment

| Metric         | Score        |
|----------------|--------------|
| Risk Score     | 35/100 (MEDIUM) |
| Compliance     | 78/100       |
| Gaps Found     | 4            |

### Critical/High Gaps
- [CRITICAL] Missing AUTHENTICATION security implementation...

*Generated by AI Engineering Productivity Platform*
```

---

## 13. Phase 9 — AI PR Review

### API

**`POST /api/v1/requirements/{id}/ai-review`** — run LLM-powered code review (requires LLM provider set)

**`GET /api/v1/requirements/{id}/ai-review`** — fetch latest review

**`GET /api/v1/requirements/{id}/ai-review/history`** — full review history (all runs)

### What It Does

`AiPrReviewService` sends the requirement knowledge model + the full PR diff to the LLM and asks it to review the code:

**System prompt instructs the LLM to review for:**
- `SECURITY` — injection vulnerabilities, missing auth, unencrypted sensitive fields, OWASP Top 10
- `VALIDATION` — fields that should be validated but aren't, missing `@NotBlank/@Email/@Size`
- `CODE_SMELL` — complex methods, missing error handling, hardcoded values, magic strings
- `ARCHITECTURE` — wrong layer responsibility, missing abstractions, tight coupling
- `PERFORMANCE` — N+1 queries, missing indexes, synchronous I/O where async is needed
- `REQUIREMENT_GAP` — things in the requirement not implemented in the PR

**User prompt contains:**
- Entities, endpoints, validation rules, security requirements, functional requirements (from knowledge model)
- The PR diff (truncated to 8000 chars if larger)

**LLM response schema:**
```json
{
  "summary": "one paragraph overall review",
  "approved": false,
  "issues": [
    {
      "category": "SECURITY",
      "severity": "CRITICAL",
      "description": "what the issue is",
      "suggestion": "how to fix it",
      "location": "ClassName.methodName"
    }
  ]
}
```

**After review:** Posts a formatted markdown table of issues as a PR comment. Every review is stored with `modelId` + `promptVersion` for audit trails as models are upgraded.

**Note:** This endpoint throws `IllegalStateException` if no LLM gateway is configured (`ANALYZER_PROVIDER=rule-based`). An LLM is required for AI review.

---

## 14. Phase 10 — Build Validation

### API

**`POST /api/v1/requirements/{id}/build-validation`** — validate the generated code bundle

**`GET /api/v1/requirements/{id}/build-validation`** — fetch the validation report

### Four-Layer Validation

`BuildValidationService` runs four checks in sequence:

#### Check 1: Syntax (JavaParser)

Uses `CompileValidationService` which parses each `.java` file with JavaParser's AST parser. If the file fails to parse (syntax error, unclosed braces, etc.), it is recorded as an error.

Result: `PASS "All 12 files parsed successfully"` or `FAIL "3 syntax error(s): OrderController.java: ..."`.

#### Check 2: Structural Integrity

For each entity found (by `FileType.ENTITY`), verifies these companion files exist:

- `{Entity}Repository.java`
- `{Entity}Service.java`
- `{Entity}Controller.java`
- `Create{Entity}Request.java`
- `{Entity}Response.java`

Missing files become `WARN` entries (not errors — partial generation is still useful).

#### Check 3: Annotation Presence

Checks that critical Spring/JPA annotations are present in each file:

| FileType | Expected annotation/import |
|---|---|
| `ENTITY` | `@Entity` |
| `REPOSITORY` | `JpaRepository` |
| `SERVICE` | `@Service` |
| `CONTROLLER` | `@RestController` |

Missing annotations become `WARN` entries.

#### Check 4: Package Consistency

Verifies every file has a `package` declaration. Missing package declarations become `WARN` entries.

**Final status:** `FAILED` (any error > 0), `WARNING` (only warnings), `PASSED` (all clear).

---

## 15. Elasticsearch Indexing

### When It's Active

ES indexing is **disabled by default** (`ELASTICSEARCH_ENABLED=false`). Enable with:

```bash
ELASTICSEARCH_ENABLED=true
ELASTICSEARCH_URIS=http://localhost:9200
```

`ElasticsearchIndexingService` is only created when ES repositories are enabled (`@ConditionalOnProperty`). All other services inject it as `Optional<ElasticsearchIndexingService>` — no code change needed when ES is off.

### Two Index Types

**`requirement-search` index:**
```
id, externalKey, title, description, acceptanceCriteria[],
status, entityNames[], endpointPaths[], validationFields[],
securityCategories[], complianceScore, riskScore, riskLevel,
embedding (float[]), indexedAt
```

**`generated-code-search` index:**
```
id (requirementId + "-" + fileName),
requirementId, externalKey, fileName, fileType,
content (full source code), embedding (float[]), indexedAt
```

### Indexing Timeline

| Stage | Event | Action |
|---|---|---|
| Analysis complete | `@Async("analysisExecutor")` | Index full requirement doc with embedding |
| Code generated | `@Async("pipelineExecutor")` | Index each generated file with embedding |
| Compliance checked | `@Async("pipelineExecutor")` | Update `complianceScore` in requirement doc |
| Risk scored | `@Async("pipelineExecutor")` | Update `riskScore` and `riskLevel` in requirement doc |

### Embeddings

`EmbeddingService.embed(text)` generates vector embeddings for semantic search. When Ollama is running, it calls the `/api/embeddings` endpoint. Returns an empty float array if embedding fails — never blocks the main pipeline.

### Search API

**`GET /api/v1/search?q={query}&type=requirement|code`** — full-text search across indexed documents

---

## 16. Pipeline Report API

### Single Requirement Report

**`GET /api/v1/requirements/{id}/report`**

Aggregates all pipeline stages into a single response:

```json
{
  "generatedAt": "2026-06-13T10:30:00Z",
  "requirement": {
    "id": "...", "externalKey": "PROJ-42", "title": "...",
    "status": "ANALYZED", "createdAt": "..."
  },
  "analysis": {
    "analyzerVersion": "llm-gemini-2.0-flash-v1",
    "analyzedAt": "...",
    "entities": [...],
    "apiEndpoints": [...],
    "validationRules": [...],
    "securityRequirements": [...],
    "functionalRequirements": [...],
    "edgeCases": [...]
  },
  "codeGeneration": {
    "targetPackage": "com.example.generated",
    "fileCount": 12,
    "generatedAt": "...",
    "files": [{ "fileName": "Order.java", "fileType": "ENTITY" }]
  },
  "pullRequest": {
    "prNumber": 7, "htmlUrl": "https://github.com/...",
    "headBranch": "feat/proj-42-...", "createdAt": "..."
  },
  "compliance": {
    "complianceScore": 85, "gapCount": 2,
    "criticalGaps": 0, "checkedAt": "...", "gaps": [...]
  },
  "risk": {
    "riskLevel": "LOW", "overallScore": 18,
    "complianceContribution": 15, "securityContribution": 0,
    "completenessContribution": 0, "recommendation": "Safe to merge.",
    "scoredAt": "..."
  },
  "aiReview": {
    "modelId": "gemini-2.0-flash", "summary": "...",
    "approved": true, "issueCount": 1,
    "criticalCount": 0, "issues": [...], "reviewedAt": "..."
  }
}
```

Stages that haven't run yet return empty `{}` objects — no 404.

---

## 17. Complete API Reference

| Method | Path | Description | Prerequisites |
|---|---|---|---|
| `POST` | `/api/v1/requirements` | Create requirement + auto-trigger analysis | None |
| `GET` | `/api/v1/requirements/{id}` | Fetch requirement | None |
| `POST` | `/api/v1/requirements/{id}/analysis` | Trigger/re-trigger analysis | Requirement exists |
| `GET` | `/api/v1/requirements/{id}/analysis` | Get knowledge model | Analysis complete |
| `POST` | `/api/v1/requirements/{id}/code` | Generate Java code | Analysis complete |
| `GET` | `/api/v1/requirements/{id}/code` | Get generated bundle | Code generated |
| `POST` | `/api/v1/requirements/{id}/build-validation` | Validate generated code | Code generated |
| `GET` | `/api/v1/requirements/{id}/build-validation` | Get validation report | Build validated |
| `POST` | `/api/v1/requirements/{id}/pull-request` | Create GitHub PR | Code generated, syntax valid |
| `GET` | `/api/v1/requirements/{id}/pull-request` | Get PR metadata | PR created |
| `POST` | `/api/v1/requirements/{id}/compliance` | Check PR compliance | PR created |
| `GET` | `/api/v1/requirements/{id}/compliance` | Get compliance report | Compliance checked |
| `POST` | `/api/v1/requirements/{id}/risk` | Score PR risk | Compliance report exists |
| `GET` | `/api/v1/requirements/{id}/risk` | Get risk score | Risk scored |
| `POST` | `/api/v1/requirements/{id}/ai-review` | LLM code review | PR created, LLM configured |
| `GET` | `/api/v1/requirements/{id}/ai-review` | Get latest AI review | AI review run |
| `GET` | `/api/v1/requirements/{id}/ai-review/history` | Full review history | At least one AI review |
| `GET` | `/api/v1/requirements/{id}/report` | Full pipeline report | None (partial results OK) |
| `POST` | `/api/v1/requirements/import/jira/{issueKey}` | Import from Jira | Jira credentials configured |
| `GET` | `/api/v1/search` | Search requirements/code | Elasticsearch enabled |
| `POST` | `/webhook/github` | GitHub webhook receiver | Webhook secret configured |

### Error Responses

| Status | When |
|---|---|
| `400 Bad Request` | Bean Validation failure, malformed JSON |
| `404 Not Found` | Requirement, analysis, code, PR, or report not found |
| `409 Conflict` | `externalKey` already exists |
| `500 Internal Server Error` | Unexpected error (LLM timeout, GitHub API error, etc.) |

Error body follows `ApiError` structure:
```json
{
  "status": 404,
  "message": "Requirement story not found: 550e8400-...",
  "timestamp": "2026-06-13T10:00:00Z"
}
```

---

## 18. End-to-End Flow Walkthrough

This section traces a single requirement through the entire pipeline.

### Step 1 — Create Requirement

```http
POST /api/v1/requirements
{
  "externalKey": "SHOP-101",
  "title": "Product Catalog Management",
  "description": "The system must allow admins to create, update, and delete products...",
  "acceptanceCriteria": ["Product name must be unique", "Price must be positive"]
}
```

**DB writes:**
- `requirement_story` row: `id=UUID, status=ANALYSIS_PENDING`
- `requirement_acceptance_criterion` rows: 2 rows with criterion_order 0 and 1

**Async:** `RequirementCreatedEvent` fires after commit → `analysisExecutor` thread begins LLM/rule-based analysis.

---

### Step 2 — Analysis (Async, ~2-5 seconds for LLM)

`LlmRequirementAnalyzer` sends the requirement to Gemini and receives JSON back.

**DB writes:**
- `requirement_analysis` row: `knowledge_model="{entities:[{name:'Product',...}], ...}"`
- `requirement_story.status` updates to `ANALYZED`

If ES is enabled: requirement document indexed asynchronously with embeddings.

---

### Step 3 — Generate Code

```http
POST /api/v1/requirements/{id}/code
```

`CodeGeneratorService` reads the `RequirementKnowledgeModel`, routes to CRUD generator for `Product` entity, produces 6 Java files.

**DB writes:**
- `generated_code_bundle` row: `generated_files=JSON array of 6 files`

---

### Step 4 — Build Validation

```http
POST /api/v1/requirements/{id}/build-validation
```

JavaParser parses all 6 files, structural check confirms all layers exist, annotations verified.

**DB writes:**
- `build_validation_report` row: `status=PASSED, checks=[4 check results]`

---

### Step 5 — Create Pull Request

```http
POST /api/v1/requirements/{id}/pull-request
```

`GitHubPrService`:
1. Validates syntax (fast-fail before touching GitHub)
2. Creates branch `feat/shop-101-product-catalog-management`
3. Commits 6 files via GitHub Contents API
4. Opens PR `[SHOP-101] Product Catalog Management`

**DB writes:**
- `pull_request` row: `pr_number=12, html_url=https://github.com/...`

---

### Step 6 — Compliance Check

```http
POST /api/v1/requirements/{id}/compliance
```

`ComplianceService` fetches PR file list from GitHub, checks each entity/endpoint/validation/security requirement against the diff.

**DB writes:**
- `compliance_report` row: `compliance_score=90, gaps=[1 MEDIUM gap]`

**ES update:** `complianceScore=90` updated asynchronously.

---

### Step 7 — Risk Score

```http
POST /api/v1/requirements/{id}/risk
```

`RiskScoringService`:
- Compliance risk: `100 - 90 = 10` × 0.40 = 4
- Security risk: `0 critical gaps × 25 = 0` × 0.35 = 0
- Completeness risk: `0 missing/1 total = 0%` × 0.25 = 0
- Overall: `4` → Level: `LOW`

Posts PR comment with score table.

**DB writes:**
- `risk_score` row: `overall_score=4, risk_level=LOW, recommendation="Safe to merge."`

---

### Step 8 — AI Review

```http
POST /api/v1/requirements/{id}/ai-review
```

`AiPrReviewService` sends knowledge model + PR diff to Gemini. Receives structured review.

Posts markdown review table as PR comment.

**DB writes:**
- `ai_pr_review` row: `approved=true, issues=[], model_id=gemini-2.0-flash`

---

### Step 9 — View Full Report

```http
GET /api/v1/requirements/{id}/report
```

Returns all 7 pipeline stages in one JSON response. The React dashboard (`/frontend/`) renders this as a full pipeline detail page with compliance gaps, generated files, and review issues.

---

*This document covers all 10 phases of the AI Engineering Productivity Platform backend as of June 2026.*
