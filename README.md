# KnowledgeOS — MCP Platform

A project-centric knowledge-work OS where multiple AI agents (Claude Code, Codex, future models) safely collaborate on shared workspaces. Not a chat system — a general-purpose platform for software engineering, content creation, and stack migrations.

```
┌─────────────────────────────────────────────────────────────────┐
│                        KnowledgeOS UI                           │
│         (real-time via WebSocket — same view for all agents)    │
└─────────────────────┬───────────────────────────────────────────┘
                      │ REST + WebSocket
┌─────────────────────▼───────────────────────────────────────────┐
│                   KnowledgeOS Backend                           │
│        (Micronaut 4.x — projects, agents, changesets, memory)   │
└──────────┬──────────────────────────────┬───────────────────────┘
           │ fabric8                      │ REST API
┌──────────▼──────────┐      ┌────────────▼──────────────────────┐
│  Kubernetes Pods    │      │  KnowledgeOS MCP Server           │
│  (agent-runner +    │      │  (mcp-server/ — TypeScript)       │
│   claude CLI)       │      │  exposes KOS tools to Claude      │
└─────────────────────┘      └────────────┬──────────────────────┘
                                          │ MCP protocol
                             ┌────────────▼──────────────────────┐
                             │  Claude Code (local CLI)          │
                             │  auto-registers as KOS agent,     │
                             │  locks files, submits changesets   │
                             └───────────────────────────────────┘
```

## Stack

| Layer | Technology |
|---|---|
| Backend | Micronaut 4.7.6, Java 25 (GraalVM), Gradle |
| Database | PostgreSQL 16 + Flyway migrations |
| Cache / Locks | Redis 7 |
| Vector Memory | Qdrant 1.9 |
| Agent Infra | Kubernetes (kind), fabric8 |
| MCP Server | Node.js 22, TypeScript, `@modelcontextprotocol/sdk` |
| CLI | Java 25 single-file script (`kos.java`) |

---

## CLI — `kos.java`

All operations go through a single Java 25 script. No make required.

```bash
# Run any command
java kos.java <command> [--flag=value]

# Or make it executable
chmod +x kos.java
./kos.java <command>
```

### Infrastructure

```bash
java kos.java infra-up                  # Start Postgres, Redis, Qdrant
java kos.java infra-up --no-wait        # Skip health-check polling
java kos.java infra-down                # Stop containers
java kos.java infra-down --volumes      # Stop + wipe data volumes
java kos.java infra-status             # Show container health
java kos.java logs                      # Tail all container logs
java kos.java logs --service=postgres  # Tail one service
```

### Development

```bash
java kos.java backend-run               # Start backend (auto-starts infra)
java kos.java backend-run --no-infra    # Skip infra start
java kos.java frontend-run             # Vite dev server → http://localhost:5173
java kos.java mcp-build                # Compile TypeScript MCP server
java kos.java mcp-start --project-id=<uuid>   # Run MCP server for a project
```

### Testing

```bash
java kos.java test                             # Full backend test suite
java kos.java test --filter=ChangeSetServiceTest  # Single test class
java kos.java test-phase2                      # End-to-end Phase 2 smoke test
java kos.java test-phase2 --api=http://...    # Against a remote backend
java kos.java test-mcp                         # Verify MCP server build
```

### Kubernetes

```bash
java kos.java cluster-up                # Create kind cluster + apply manifests
java kos.java cluster-up --no-wait      # Skip pod readiness wait
java kos.java cluster-down              # Delete cluster
java kos.java agent-image               # Build agent-runner Docker image
java kos.java agent-image --tag=my:tag  # Custom tag
java kos.java load-agent-image          # Load image into kind
java kos.java create-ai-secret          # Push ANTHROPIC_API_KEY → k8s secret (pod agents only)
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `KOS_API` | `http://localhost:8080` | Backend base URL |
| `KOS_API_KEY` | `dev-local-key` | API authentication key |
| `KOS_PROJECT_ID` | — | Project UUID (used by `mcp-start`) |
| `ANTHROPIC_API_KEY` | — | Only needed for `create-ai-secret` (pod agents in k8s — not required for Claude Code / local agents) |

---

## Quick Start (Local — no Kubernetes)

```bash
# 1. Start infra (Postgres + Redis + Qdrant)
java kos.java infra-up

# 2. Start backend  (new terminal)
java kos.java backend-run

# 3. Browse API docs
open http://localhost:8080/swagger-ui

# 4. (Optional) Start frontend  (new terminal)
java kos.java frontend-run
```

---

## Deploying & Testing Phase 2

Phase 2 covers **ChangeSets**, **FileLocks**, and **Memory**.

### Prerequisites

```bash
java kos.java infra-up
java kos.java backend-run    # keep this running in a separate terminal
```

### Automated smoke test (recommended)

```bash
java kos.java test-phase2
```

This creates a real project, agent, locks, changeset (submit → approve → apply), and memory entry via the live REST API and verifies each step.

### Manual curl walkthrough

**1. Create a project**
```bash
curl -s -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d '{"name":"My Project","type":"software"}' | jq .

export PROJECT_ID=<id from response>
```

**2. Create a local agent**
```bash
curl -s -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/agents \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d '{"name":"Claude","model":"claude-sonnet-4-6","role":"Implementer","agentType":"local"}' | jq .

export AGENT_ID=<id from response>
```

**3. Acquire a write lock**
```bash
curl -s -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/locks \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d "{\"filePath\":\"src/PaymentService.java\",\"lockType\":\"write\",\"durationSeconds\":300,\"agentId\":\"$AGENT_ID\"}" | jq .

export LOCK_ID=<id from response>
```

**4. Verify conflict detection (expect 409)**
```bash
curl -o /dev/null -w "%{http_code}\n" -X POST \
  http://localhost:8080/api/v1/projects/$PROJECT_ID/locks \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d "{\"filePath\":\"src/PaymentService.java\",\"lockType\":\"write\",\"agentId\":\"$AGENT_ID\"}"
# → 409
```

**5. Release the lock**
```bash
curl -X DELETE http://localhost:8080/api/v1/projects/$PROJECT_ID/locks/$LOCK_ID \
  -H "X-KOS-API-Key: dev-local-key"
```

**6. Submit a changeset (policy=never → human_review)**
```bash
curl -s -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/changesets \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d "{
    \"intent\": \"Add payment validation\",
    \"filesChanged\": [\"src/PaymentService.java\"],
    \"diff\": \"--- a/f\n+++ b/f\n@@ -0,0 +1 @@\n+// validated\",
    \"agentId\": \"$AGENT_ID\",
    \"autoApplyPolicy\": \"never\"
  }" | jq .

export CS_ID=<id from response>
```

**7. Approve → Apply**
```bash
curl -s -X PUT http://localhost:8080/api/v1/projects/$PROJECT_ID/changesets/$CS_ID/approve \
  -H "X-KOS-API-Key: dev-local-key" | jq .status

curl -s -X PUT http://localhost:8080/api/v1/projects/$PROJECT_ID/changesets/$CS_ID/apply \
  -H "X-KOS-API-Key: dev-local-key" | jq .status
```

**8. Rollback**
```bash
curl -s -X PUT http://localhost:8080/api/v1/projects/$PROJECT_ID/changesets/$CS_ID/rollback \
  -H "X-KOS-API-Key: dev-local-key" | jq .status
```

**9. Write memory**
```bash
curl -s -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/memory \
  -H "Content-Type: application/json" \
  -H "X-KOS-API-Key: dev-local-key" \
  -d '{
    "title": "Architecture Decision",
    "content": "Use PostgreSQL for ACID guarantees",
    "justification": "Architecture review 2026",
    "layer": "canonical"
  }' | jq .
```

**10. Check timeline**
```bash
curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/timeline" \
  -H "X-KOS-API-Key: dev-local-key" | jq '[.events[].type]'
```

---

## Two Agent Modes

| | **local** (Claude Code) | **pod** (Kubernetes) |
|---|---|---|
| Where it runs | Developer machine | k8s container |
| Auth | Your existing `~/.claude/` credentials | `ANTHROPIC_API_KEY` in k8s secret `ai-api-keys` |
| How it starts | Opens terminal → `.mcp.json` auto-loads | `AgentPodManager.spawnPod()` via fabric8 |
| `ANTHROPIC_API_KEY` needed? | **No** | **Yes** (only for k8s) |
| KOS sees it as | `agentType: local`, no `podName` | `agentType: pod`, has `podName` |
| Terminal access | Your terminal | WebSocket `/ws/terminal/{projectId}/{agentId}` |

For local development and Claude Code integration you only need `infra-up` + `backend-run`. The k8s stack is for autonomous pod agents running headlessly in production.

---

## Using Claude Code as an Agent (MCP)

When you open a terminal in this repo, Claude Code auto-loads `.mcp.json` and gets access to KOS tools.

**Typical session flow:**

```
1. User creates a project via API or UI → gets PROJECT_ID
2. Update .mcp.json: set KOS_PROJECT_ID to that value
3. Open Claude Code in the workspace
4. Claude calls kos_register_agent → appears in project dashboard as "local" agent
5. Claude calls kos_acquire_lock("src/Foo.java") before editing
6. Claude edits the file
7. Claude calls kos_submit_changeset(intent, diff, files)
8. Changeset appears in UI awaiting human review
9. Claude calls kos_release_lock(lockId)
10. All steps appear in Timeline in real-time
```

**Available MCP tools:**

| Tool | Description |
|---|---|
| `kos_register_agent` | Register Claude Code as a local agent |
| `kos_list_agents` | See all agents on the project |
| `kos_acquire_lock` | Lock a file before editing |
| `kos_release_lock` | Release a lock when done |
| `kos_submit_changeset` | Submit a diff for human review |
| `kos_write_memory` | Save a decision or finding |
| `kos_search_memory` | Semantic search over memory |
| `kos_get_timeline` | Get recent project activity |
| `kos_list_projects` | List all projects |
| `kos_create_project` | Create a new project |

---

## Project Types

| Type | Validator | Auto-apply condition |
|---|---|---|
| `software` | Runs `gradlew test` / `mvn test` / `npm test` | `on_tests_pass` → tests green |
| `content` | Readability + word count checks | `on_tests_pass` → checks pass |
| `migration` | Compares legacy/expected vs target/output | `on_tests_pass` → equivalence % |
| `research` | Same as migration | Same |

---

## ChangeSet Lifecycle

```
submit → [policy=always]       → auto_applied
       → [policy=never]        → human_review
       → [policy=on_tests_pass, tests pass, no review required] → auto_applied
       → [policy=on_tests_pass, requires human review]          → human_review
       → [policy=on_tests_pass, tests fail]                     → agent_review
       → [write lock conflict]  → 409 Conflict

human_review → approve → approved → apply → applied → rollback → rolled_back
human_review → reject  → rejected
agent_review → approve → approved  (same flow)
```

---

## Database Migrations (Flyway)

| Migration | Contents |
|---|---|
| V1 | `projects` table |
| V2 | `workspaces` table |
| V3 | `agents` table |
| V4 | `changesets`, `file_locks` |
| V5 | `memory_entries` |
| V6 | (reserved) |
| V7 | `agent_type` column on agents, `validator_results` |
| V8 | `timeline_events` |
| V9 | Fix `timeline_events.agent_id` FK → `ON DELETE SET NULL` |

---

## API Reference

Full interactive docs: `http://localhost:8080/swagger-ui`

```
POST/GET     /api/v1/projects
GET/PUT      /api/v1/projects/{id}
DELETE       /api/v1/projects/{id}   (archives)

POST/GET     /api/v1/projects/{id}/workspaces
GET          /api/v1/projects/{id}/workspaces/{wid}

POST/GET     /api/v1/projects/{id}/agents
GET/PUT/DEL  /api/v1/projects/{id}/agents/{aid}
POST         /api/v1/projects/{id}/agents/{aid}/stop
POST         /api/v1/projects/{id}/agents/{aid}/restart

POST/GET     /api/v1/projects/{id}/changesets
DEL          /api/v1/projects/{id}/changesets/{csid}
PUT          /api/v1/projects/{id}/changesets/{csid}/approve
PUT          /api/v1/projects/{id}/changesets/{csid}/reject
PUT          /api/v1/projects/{id}/changesets/{csid}/apply
PUT          /api/v1/projects/{id}/changesets/{csid}/rollback
POST         /api/v1/projects/{id}/changesets/{csid}/validate

POST/GET     /api/v1/projects/{id}/locks
DEL          /api/v1/projects/{id}/locks/{lockId}
POST         /api/v1/projects/{id}/locks/{lockId}/reclaim

POST/GET     /api/v1/projects/{id}/memory
DEL          /api/v1/projects/{id}/memory/{memId}
POST         /api/v1/projects/{id}/memory/search

GET          /api/v1/projects/{id}/timeline
GET          /api/v1/projects/{id}/timeline/{eid}

WS           /ws/events/{projectId}    (real-time timeline stream)
WS           /ws/terminal/{projectId}/{agentId}
```

All `/api/v1/**` endpoints require the header: `X-KOS-API-Key: <key>`

---

## Repository Structure

```
knowledgeos/
├── kos.java                    ← CLI (Java 25 single-file script)
├── .mcp.json                   ← MCP server config (auto-loaded by Claude Code)
├── docker-compose.infra.yml    ← Local infra (Postgres, Redis, Qdrant)
├── backend/                    ← Micronaut 4.x + Gradle
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/knowledgeos/
│       │   ├── controller/     ← REST endpoints
│       │   ├── domain/         ← JPA entities
│       │   ├── dto/            ← Request/response records
│       │   ├── infra/          ← ApiKeyFilter
│       │   ├── k8s/            ← AgentPodManager, NamespaceManager
│       │   ├── memory/         ← QdrantMemoryStore
│       │   ├── repository/     ← Micronaut Data repositories
│       │   ├── service/        ← Business logic + validators
│       │   └── websocket/      ← AgentEventWebSocket
│       └── main/resources/
│           ├── application.yml
│           └── db/migration/   ← Flyway V1–V9
├── mcp-server/                 ← TypeScript MCP server
│   ├── src/
│   │   ├── index.ts            ← Entry point + tool dispatcher
│   │   ├── client.ts           ← Typed KOS REST client
│   │   └── tools/              ← Tool definitions per domain
│   └── package.json
├── frontend/                   ← React + TypeScript (Vite)
└── infra/                      ← Kubernetes manifests + Dockerfile
    ├── kind/
    ├── k8s/
    └── agent-runner/
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused :8080` | `java kos.java backend-run` |
| `Connection refused :5432` | `java kos.java infra-up` |
| `401 Unauthorized` | Add `-H "X-KOS-API-Key: dev-local-key"` |
| Flyway checksum error | Restart backend — Flyway re-applies on clean DB |
| Memory search returns empty list | Qdrant needs an embedding model; falls back to DB listing |
| MCP server not loading | Run `java kos.java mcp-build` then restart Claude Code |
| Tests fail with `NoSuchBeanException` | Check that `application-test.yml` sets `app.api-key: ""` |

---

## Build Status

| Phase | Tests | Status |
|---|---|---|
| Phase 1 — Projects, Workspaces, Agents | 21 | ✅ |
| Phase 2 — ChangeSets, FileLocks, Memory | 23 | ✅ |
| Phase 2.5 — Timeline, ApiKeyFilter, MCP Server | 8 | ✅ |
| Phase 3 — Validators (software / content / migration) | 17 | ✅ |
| Phase 4 — Multi-workspace Migrations | — | 🔜 |
| Phase 5 — Cost Tracking, GraalVM Native | — | 🔜 |
| Frontend | — | 🔜 |

**Total: 69 tests green**
