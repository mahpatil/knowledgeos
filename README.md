# Knowledge OS — MCP Platform

A project-centric knowledge-work operating system where multiple AI agents (Claude, Codex, future models) safely collaborate on shared workspaces.

## Architecture

- **Backend**: Micronaut 4.x + Gradle (Groovy DSL), PostgreSQL + Flyway, Redis, Qdrant
- **Frontend**: React + TypeScript (Vite), xterm.js, Zustand, SWR
- **Infrastructure**: Kubernetes (kind), fabric8 client, agent pods with PVC-backed workspaces

## Quick Start

### Local Dev (no k8s)
```bash
docker compose -f docker-compose.infra.yml up -d
cd backend && ./gradlew run
cd frontend && npm run dev
```

### Full k8s Cluster
```bash
make cluster-up          # kind create + kubectl apply infra
make agent-image         # docker build agent-runner
make load-agent-image    # kind load into cluster
make create-ai-secret    # ANTHROPIC_API_KEY → k8s secret
```

## Project Types

- `software` — Code repositories with test/lint validation
- `content` — Books, blogs, marketing copy with readability checks
- `marketing` — Campaign content
- `migration` — Legacy-to-modern stack translations
- `research` — Research and analysis

## Development Methodology

Every phase: **Spec → Test → Build**

1. Write OpenAPI YAML + stub controllers (501)
2. Write all tests (fail at commit)
3. Implement until tests are green

## API Docs

Available at `http://localhost:8080/swagger-ui` when running locally.
