# KnowledgeOS — Build Progress

## Overview
Multi-agent AI workspace operating system built with Micronaut 4.7.6 + Gradle.
Agents (Claude Code, Codex) collaborate on shared projects with safety rails —
file locks, change-set review, semantic memory, and a real-time timeline.

---

## Phase 1 — Core Skeleton ✅ (21 tests)
| Area | What was built |
|------|---------------|
| Projects | CRUD + k8s namespace provisioning |
| Workspaces | PVC scaffold, `.mcp/` directory structure |
| Agents | CRUD + Kubernetes pod spawning via fabric8 |
| WebSocket | Terminal gateway stub (`/ws/terminal/{pid}/{aid}`) |
| Infrastructure | GlobalExceptionHandler, KubernetesClientFactory |
| Flyway | V1 (projects), V2 (workspaces), V3 (agents) |

**Key lessons:** `RETURNS_DEEP_STUBS` on fabric8 client needs try-catch in
`provision()` — mock chain returns null for deeply parameterized generics.

---

## Phase 2 — Safety and Memory ✅ (44 tests, +23)
| Area | What was built |
|------|---------------|
| ChangeSets | 8-state lifecycle (pending→auto_applied/human_review/agent_review→approved/rejected→applied/rolled_back) |
| FileLocks | Write-lock conflict detection via `SELECT FOR UPDATE` inside `@Transactional` |
| Memory | 3-layer system (canonical/feature/scratch) with 4h TTL on scratch |
| Qdrant | HTTP client for vector store; placeholder sin-based embeddings; graceful fallback |
| DTOs | `CreateChangeSetRequest`, `AcquireLockRequest`, `FileLockResponse`, `ChangeSetResponse` |
| Flyway | V4 (changesets + file_locks with partial unique index), V5 (memory_entries) |

**Key lessons:**
- `@JdbcTypeCode(SqlTypes.JSON)` required for JSONB columns in Hibernate 6
- `Argument.listOf(Map.class)` for HTTP list responses (not raw `List.class`)
- `HttpClient.create(new URL(...))` for dynamic URLs — `@Client("${prop}")` doesn't work

---

## Phase 3 — Validators ✅ (61 tests, +17)
| Area | What was built |
|------|---------------|
| Agent types | `agentType: pod\|local` on agents; local agents skip pod spawning |
| ValidatorFactory | Routes project type → validator (software/content/migration/research) |
| SoftwareValidator | kubectl exec (pod) or ProcessBuilder (local); auto-detects Gradle/Maven/npm |
| ContentValidator | Word count ≥100 + Flesch Reading Ease ≥30; `editorial_approval` policy flag |
| MigrationValidator | `legacy/expected-output/` vs `target/output/` equivalence % |
| ProcessRunner | `@FunctionalInterface` wrapping ProcessBuilder — `@MockBean` in tests |
| ChangeSetService | `on_tests_pass` branch now runs validator; new `POST /{csid}/validate` endpoint |
| Flyway | V7 (agent_type column + validator_results table) |

---

## Phase 2.5 — Timeline + MCP Server 🚧 IN PROGRESS
| Area | Status |
|------|--------|
| Flyway V8 (timeline_events) | ⏳ |
| TimelineService + wiring into all services | ⏳ |
| TimelineController (cursor-paginated) | ⏳ |
| AgentEventWebSocket.broadcast() | ⏳ |
| ApiKeyFilter (`X-KOS-API-Key`) | ⏳ |
| CLAUDE.md generation on project create | ⏳ |
| MCP Server (TypeScript, 10 tools) | ⏳ |
| `.mcp.json` at workspace root | ⏳ |

---

## Phase 4 — Multi-workspace Migrations ⏳
- Flyway V9 (verification_reports)
- Multi-PVC pod mounting from `agent.workspace_mounts`
- `createMigrationProject()` — provisions legacy, target, mapping PVCs
- Role-specific ConfigMaps for ReverseEngineer / Translator / Verifier

## Phase 5 — UX & Power Features ⏳
- Token usage + cost tracking per model
- Scheduled cleanup jobs (lock expiry 60s, scratch memory 1h, agent health 30s)
- GraalVM native image

## Frontend ⏳
- Phase 1: Project/Workspace/Agent CRUD UI
- Phase 2: Changeset review workflow
- Phase 2.5: Real-time Timeline view (WebSocket)
- Phase 3: Validator results panel, ProjectCreationWizard
- Phase 5: CostDashboard, Cmd+K palette, pod vs local agent type badges

---

## Architecture Notes
```
KnowledgeOS UI ──REST+WS──► Backend (Micronaut)
                                  │ fabric8
                             Kubernetes Pods (agent-runner + tmux + claude CLI)
                                  │ REST
                             MCP Server (TypeScript) ──MCP──► Claude Code (local)
```

Both pod agents (Kubernetes) and local agents (Claude Code on developer machine)
appear in the same project, generate the same timeline events, and are tracked
by the same UI. The only difference is that local agents skip pod provisioning.

## Repo
https://github.com/mahpatil/knowledgeos
