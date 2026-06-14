# AI Engineering Productivity Platform

Backend foundation for an AI-assisted software delivery platform that connects requirements, source code, pull requests, reviews, and delivery risk.

The product's central question is:

> Does the implementation satisfy the original requirement?

## Current Foundation

- Java 21 and Spring Boot 3
- Requirement intake vertical slice
- PostgreSQL persistence with Flyway
- Kafka, Elasticsearch, and Redis dependencies ready for later modules
- Actuator health and metrics endpoints
- Docker Compose development infrastructure
- Integration tests for the requirement API

## Run Locally

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Create a requirement:

```bash
curl --request POST http://localhost:8080/api/v1/requirements \
  --header 'Content-Type: application/json' \
  --data '{
    "externalKey": "JIRA-123",
    "title": "Create Customer API",
    "description": "Create customer management endpoints",
    "acceptanceCriteria": [
      "Email validation is mandatory",
      "Pagination is required",
      "Customer records use soft delete"
    ]
  }'
```

Run the complete local infrastructure when its modules are implemented:

```bash
docker compose --profile platform up -d
ELASTICSEARCH_HEALTH_ENABLED=true REDIS_HEALTH_ENABLED=true mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

## Architecture

The initial implementation is a modular monolith. Each capability owns its domain, application services, adapters, and API. This keeps local development and transactions simple while preserving boundaries that can become independently deployed services when scale or team ownership justifies it.

See [docs/architecture.md](docs/architecture.md) and [docs/roadmap.md](docs/roadmap.md).
# Requirements-to-Code-Intelligence-Platform
# Requirements-to-Code-Intelligence-Platform
