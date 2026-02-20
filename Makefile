.PHONY: cluster-up cluster-down agent-image load-agent-image create-ai-secret \
        infra-up infra-down backend-run frontend-run test clean

CLUSTER_NAME    := knowledgeos
KIND_CONFIG     := infra/kind/cluster-config.yaml
AGENT_IMAGE     := knowledgeos/agent-runner:latest
AI_SECRET_NAME  := ai-api-keys
NAMESPACE       := knowledgeos-system

# ── Cluster lifecycle ──────────────────────────────────────────────────────────
cluster-up:
	kind create cluster --name $(CLUSTER_NAME) --config $(KIND_CONFIG)
	kubectl label node $(CLUSTER_NAME)-worker  role=worker 2>/dev/null || true
	kubectl label node $(CLUSTER_NAME)-worker2 role=worker 2>/dev/null || true
	kubectl create namespace $(NAMESPACE) 2>/dev/null || true
	kubectl apply -f infra/k8s/system/
	kubectl apply -f infra/k8s/base/
	@echo "Cluster ready — waiting for pods..."
	kubectl wait --for=condition=ready pod -l app=postgres -n $(NAMESPACE) --timeout=120s
	kubectl wait --for=condition=ready pod -l app=redis    -n $(NAMESPACE) --timeout=120s

cluster-down:
	kind delete cluster --name $(CLUSTER_NAME)

# ── Agent image ────────────────────────────────────────────────────────────────
agent-image:
	docker build -t $(AGENT_IMAGE) infra/agent-runner/

load-agent-image:
	kind load docker-image $(AGENT_IMAGE) --name $(CLUSTER_NAME)

# ── Secrets ────────────────────────────────────────────────────────────────────
create-ai-secret:
	@test -n "$$ANTHROPIC_API_KEY" || (echo "ANTHROPIC_API_KEY not set" && exit 1)
	kubectl create secret generic $(AI_SECRET_NAME) \
	  --from-literal=ANTHROPIC_API_KEY=$$ANTHROPIC_API_KEY \
	  --from-literal=OPENAI_API_KEY=$${OPENAI_API_KEY:-""} \
	  -n $(NAMESPACE) \
	  --dry-run=client -o yaml | kubectl apply -f -

# ── Local infra ────────────────────────────────────────────────────────────────
infra-up:
	docker compose -f docker-compose.infra.yml up -d

infra-down:
	docker compose -f docker-compose.infra.yml down

# ── Development ────────────────────────────────────────────────────────────────
backend-run: infra-up
	cd backend && ./gradlew run

frontend-run:
	cd frontend && npm run dev

test:
	cd backend && ./gradlew test
	cd frontend && npm test

# ── Cleanup ────────────────────────────────────────────────────────────────────
clean:
	cd backend && ./gradlew clean
	cd frontend && rm -rf dist coverage
	docker compose -f docker-compose.infra.yml down -v 2>/dev/null || true
