///usr/bin/env java --source 25 "$0" "$@"; exit
// KnowledgeOS CLI — Java 25 single-file script
// Usage:  java kos.java <command> [options]
//    or:  chmod +x kos.java && ./kos.java <command> [options]
//
// Infra mode (applies to infra-up / infra-down / infra-status / backend-run):
//   Default : k8s — uses the current kubectl context (no new cluster created)
//   --compose: Docker Compose via infra/docker-compose.yml

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

// ── ANSI colours ──────────────────────────────────────────────────────────────

static final String RESET  = "\033[0m";
static final String BOLD   = "\033[1m";
static final String GREEN  = "\033[0;32m";
static final String YELLOW = "\033[0;33m";
static final String CYAN   = "\033[0;36m";
static final String RED    = "\033[0;31m";
static final String DIM    = "\033[2m";

// ── Config ────────────────────────────────────────────────────────────────────

static final String KOS_API      = env("KOS_API",      "http://localhost:8080");
static final String KOS_API_KEY  = env("KOS_API_KEY",  "dev-local-key");
static final String K8S_NS       = env("KOS_NAMESPACE", "knowledgeos-system");
static final String COMPOSE_FILE = "infra/docker-compose.yml";

static String env(String key, String def) {
    return System.getenv().getOrDefault(key, def);
}

// ── Mode detection ────────────────────────────────────────────────────────────

/** Returns "k8s" or "compose". k8s is default when kubectl has a current context. */
String detectMode(Flags f) {
    if (f.has("compose")) return "compose";
    if (f.has("k8s"))     return "k8s";
    try {
        var p = new ProcessBuilder("kubectl", "config", "current-context")
            .redirectErrorStream(true).start();
        if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) return "k8s";
    } catch (Exception ignored) {}
    warn("No active kubectl context found — falling back to --compose mode");
    return "compose";
}

// ── Entry point ───────────────────────────────────────────────────────────────

void main(String[] args) throws Exception {
    if (args.length == 0) { printHelp(); return; }
    var f = parseFlags(args);

    switch (f.command()) {
        // Infrastructure
        case "infra-up"          -> infraUp(f);
        case "infra-down"        -> infraDown(f);
        case "infra-status"      -> infraStatus(f);
        case "infra-forward"     -> infraForward(f);

        // Development
        case "backend-run"       -> backendRun(f);
        case "frontend-run"      -> frontendRun(f);
        case "mcp-build"         -> mcpBuild();
        case "mcp-start"         -> mcpStart(f);

        // Kubernetes cluster management (explicit — separate from infra-up)
        case "cluster-up"        -> clusterUp(f);
        case "cluster-down"      -> clusterDown();
        case "agent-image"       -> agentImage(f);
        case "load-agent-image"  -> loadAgentImage();
        case "create-ai-secret"  -> createAiSecret();

        // Testing
        case "test"              -> runTests(f);
        case "test-phase2"       -> testPhase2(f);
        case "test-mcp"          -> testMcp();

        // Utilities
        case "logs"              -> logs(f);
        case "clean"             -> clean();
        case "help", "--help", "-h" -> printHelp();

        default -> {
            err("Unknown command: " + f.command());
            println();
            printHelp();
            System.exit(1);
        }
    }
}

// ── Infrastructure — unified entry points ─────────────────────────────────────

void infraUp(Flags f) throws Exception {
    switch (detectMode(f)) {
        case "k8s"     -> infraUpK8s(f);
        case "compose" -> infraUpCompose(f);
    }
}

void infraDown(Flags f) throws Exception {
    switch (detectMode(f)) {
        case "k8s"     -> infraDownK8s(f);
        case "compose" -> infraDownCompose(f);
    }
}

void infraStatus(Flags f) throws Exception {
    switch (detectMode(f)) {
        case "k8s"     -> infraStatusK8s();
        case "compose" -> infraStatusCompose();
    }
}

// ── Infrastructure — Kubernetes mode ─────────────────────────────────────────

void infraUpK8s(Flags f) throws Exception {
    header("Starting infrastructure on current k8s cluster");
    showCurrentContext();

    // Ensure namespace exists (idempotent)
    execSilent("kubectl", "create", "namespace", K8S_NS);

    exec("kubectl", "apply", "-f", "infra/k8s/system/");
    exec("kubectl", "apply", "-f", "infra/k8s/base/rbac.yaml",
                             "-f", "infra/k8s/base/network-policy.yaml");

    if (!f.has("no-wait")) {
        info("Waiting for pods to be ready in namespace " + K8S_NS + "...");
        for (var app : List.of("postgres", "redis", "qdrant")) {
            exec("kubectl", "wait", "--for=condition=ready", "pod",
                 "-l", "app=" + app, "-n", K8S_NS, "--timeout=120s");
        }
    }

    ok("Infrastructure ready in k8s");
    println();
    step("Next", "java kos.java infra-forward   # port-forward services to localhost");
    step("Then", "java kos.java backend-run      # start backend");
}

void infraDownK8s(Flags f) throws Exception {
    header("Removing infrastructure from current k8s cluster");
    showCurrentContext();
    exec("kubectl", "delete", "-f", "infra/k8s/system/", "--ignore-not-found");
    exec("kubectl", "delete", "-f", "infra/k8s/base/rbac.yaml",
                              "-f", "infra/k8s/base/network-policy.yaml",
                              "--ignore-not-found");
    if (f.has("namespace")) {
        exec("kubectl", "delete", "namespace", K8S_NS, "--ignore-not-found");
        ok("Namespace " + K8S_NS + " deleted");
    }
    ok("Infrastructure removed from k8s");
}

void infraStatusK8s() throws Exception {
    header("Infrastructure status (k8s / " + K8S_NS + ")");
    showCurrentContext();
    exec("kubectl", "get", "pods,svc,pvc", "-n", K8S_NS);
}

/**
 * Port-forward all infra services to localhost so the backend can connect.
 * Runs in the foreground — use a dedicated terminal tab.
 * Ctrl-C stops all forwards.
 */
void infraForward(Flags f) throws Exception {
    header("Port-forwarding k8s services → localhost");
    showCurrentContext();
    info("Forwarding postgres:5432, redis:6379, qdrant:6333");
    info("Keep this terminal open. Ctrl-C to stop.");
    println();

    var procs = new ArrayList<Process>();
    var forwards = List.of(
        new String[]{"postgres", "5432:5432"},
        new String[]{"redis",    "6379:6379"},
        new String[]{"qdrant",   "6333:6333", "6334:6334"}
    );

    for (var fwd : forwards) {
        var cmd = new ArrayList<String>();
        cmd.addAll(List.of("kubectl", "port-forward",
                           "svc/" + fwd[0], "-n", K8S_NS));
        for (int i = 1; i < fwd.length; i++) cmd.add(fwd[i]);
        ok("  " + fwd[0] + " → " + fwd[1]);
        procs.add(new ProcessBuilder(cmd).inheritIO().start());
    }

    // Block until Ctrl-C, then kill all
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        procs.forEach(Process::destroy);
        System.out.println("\nPort-forwards stopped.");
    }));
    procs.get(0).waitFor();  // block on first; hook kills rest on exit
}

// ── Infrastructure — Compose mode ─────────────────────────────────────────────

void infraUpCompose(Flags f) throws Exception {
    header("Starting infrastructure (Docker Compose)");
    exec("docker", "compose", "-f", COMPOSE_FILE, "up", "-d");

    if (!f.has("no-wait")) {
        info("Waiting for services...");
        waitForPort("postgres", "localhost", 5432);
        waitForPort("redis",    "localhost", 6379);
        waitForHttp("qdrant",   "http://localhost:6333/healthz");
    }

    ok("Infrastructure is up");
    println();
    step("Next", "java kos.java backend-run");
}

void infraDownCompose(Flags f) throws Exception {
    header("Stopping infrastructure (Docker Compose)");
    var cmd = f.has("volumes")
        ? new String[]{"docker", "compose", "-f", COMPOSE_FILE, "down", "-v"}
        : new String[]{"docker", "compose", "-f", COMPOSE_FILE, "down"};
    exec(cmd);
    ok("Infrastructure stopped" + (f.has("volumes") ? " (volumes removed)" : ""));
}

void infraStatusCompose() throws Exception {
    header("Infrastructure status (Docker Compose)");
    exec("docker", "compose", "-f", COMPOSE_FILE, "ps");
}

// ── Health checks — from host (no container exec) ─────────────────────────────

/** TCP connect poll — works for postgres and redis. */
void waitForPort(String name, String host, int port) throws Exception {
    print("  " + DIM + "Waiting for " + name + "..." + RESET);
    for (int i = 0; i < 30; i++) {
        try (var s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 1000);
            System.out.print("\r");
            ok("  " + name + " ready");
            return;
        } catch (Exception ignored) {
            Thread.sleep(1000);
        }
    }
    System.out.print("\r");
    warn("  " + name + " not ready after 30s — continuing anyway");
}

/** HTTP GET poll — used for qdrant /healthz. */
void waitForHttp(String name, String url) throws Exception {
    print("  " + DIM + "Waiting for " + name + "..." + RESET);
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    var req  = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    for (int i = 0; i < 30; i++) {
        try {
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 400) {
                System.out.print("\r");
                ok("  " + name + " ready");
                return;
            }
        } catch (Exception ignored) {
            Thread.sleep(1000);
        }
    }
    System.out.print("\r");
    warn("  " + name + " not ready after 30s — continuing anyway");
}

// ── Backend / frontend ────────────────────────────────────────────────────────

void backendRun(Flags f) throws Exception {
    var mode = detectMode(f);
    if ("compose".equals(mode) && !f.has("no-infra")) {
        infraUpCompose(f);
    } else if ("k8s".equals(mode) && !f.has("no-forward")) {
        header("Tip");
        info("If services aren't port-forwarded yet, run in a separate terminal:");
        step("Run", "java kos.java infra-forward");
        println();
    }
    header("Starting backend  (" + KOS_API + ")");
    info("Swagger UI: " + CYAN + KOS_API + "/swagger-ui" + RESET);
    execInteractive("backend", "./gradlew", "run");
}

void frontendRun(Flags f) throws Exception {
    header("Starting frontend");
    info("URL: " + CYAN + "http://localhost:5173" + RESET);
    execInteractive("frontend", "npm", "run", "dev");
}

// ── Kubernetes cluster management (explicit cluster lifecycle) ────────────────

void clusterUp(Flags f) throws Exception {
    header("Creating kind cluster 'knowledgeos'");
    exec("kind", "create", "cluster", "--name", "knowledgeos",
         "--config", "infra/kind/cluster-config.yaml");
    exec("kubectl", "label", "node", "knowledgeos-worker",  "role=worker", "--overwrite");
    exec("kubectl", "label", "node", "knowledgeos-worker2", "role=worker", "--overwrite");
    info("Cluster created. To deploy infra, run:");
    step("Next", "java kos.java infra-up");
}

void clusterDown() throws Exception {
    header("Deleting kind cluster");
    exec("kind", "delete", "cluster", "--name", "knowledgeos");
    ok("Cluster deleted");
}

void agentImage(Flags f) throws Exception {
    header("Building agent-runner image");
    var tag = f.get("tag", "knowledgeos/agent-runner:latest");
    exec("docker", "build", "-t", tag, "infra/agent-runner/");
    ok("Image built: " + tag);
}

void loadAgentImage() throws Exception {
    header("Loading agent image into kind");
    exec("kind", "load", "docker-image", "knowledgeos/agent-runner:latest",
         "--name", "knowledgeos");
    ok("Image loaded");
}

void createAiSecret() throws Exception {
    var key = System.getenv("ANTHROPIC_API_KEY");
    if (key == null || key.isBlank()) {
        err("ANTHROPIC_API_KEY env var is not set");
        info("Note: only needed for pod-type agents in k8s, not for Claude Code (local agents).");
        System.exit(1);
    }
    header("Pushing AI API keys → k8s secret (pod agents only)");
    showCurrentContext();
    exec("kubectl", "create", "secret", "generic", "ai-api-keys",
         "--from-literal=ANTHROPIC_API_KEY=" + key,
         "--from-literal=OPENAI_API_KEY=" + System.getenv().getOrDefault("OPENAI_API_KEY", ""),
         "-n", K8S_NS,
         "--dry-run=client", "-o", "yaml");
    // Pipe to kubectl apply (dry-run already printed it; re-run without dry-run)
    var pb = new ProcessBuilder("kubectl", "create", "secret", "generic", "ai-api-keys",
        "--from-literal=ANTHROPIC_API_KEY=" + key,
        "--from-literal=OPENAI_API_KEY=" + System.getenv().getOrDefault("OPENAI_API_KEY", ""),
        "-n", K8S_NS, "--save-config", "--dry-run=client", "-o", "yaml")
        .redirectErrorStream(true).start();
    var yaml = new String(pb.getInputStream().readAllBytes());
    pb.waitFor();
    var apply = new ProcessBuilder("kubectl", "apply", "-f", "-")
        .redirectErrorStream(true).inheritIO().start();
    apply.getOutputStream().write(yaml.getBytes());
    apply.getOutputStream().close();
    apply.waitFor();
    ok("Secret applied");
}

// ── Testing ───────────────────────────────────────────────────────────────────

void runTests(Flags f) throws Exception {
    header("Running test suite");
    var filter = f.get("filter", null);
    var cmd = filter != null
        ? new String[]{"./gradlew", "cleanTest", "test", "--tests", filter}
        : new String[]{"./gradlew", "cleanTest", "test"};
    execInteractive("backend", cmd);
}

void testPhase2(Flags f) throws Exception {
    header("Phase 2 smoke test — ChangeSets, FileLocks, Memory, Timeline");
    println();

    var api = new KosClient(f.get("api", KOS_API), KOS_API_KEY);

    step("1/9", "Create project");
    var project   = api.post("/api/v1/projects", """
        {"name":"Phase2-SmokeTest","type":"software"}""");
    var projectId = extract(project, "id");
    ok("Project: " + projectId);

    step("2/9", "Create local agent");
    var agent   = api.post("/api/v1/projects/" + projectId + "/agents", """
        {"name":"smoke-agent","model":"claude-sonnet-4-6","role":"Implementer","agentType":"local"}""");
    var agentId = extract(agent, "id");
    ok("Agent: " + agentId);

    step("3/9", "Acquire write lock");
    var lock   = api.post("/api/v1/projects/" + projectId + "/locks",
        """
        {"filePath":"src/PaymentService.java","lockType":"write","durationSeconds":60,"agentId":"%s"}
        """.formatted(agentId));
    var lockId = extract(lock, "id");
    ok("Lock: " + lockId + " → src/PaymentService.java");

    step("4/9", "Duplicate lock → expect 409");
    var code4 = api.postStatus("/api/v1/projects/" + projectId + "/locks",
        """
        {"filePath":"src/PaymentService.java","lockType":"write","agentId":"%s"}
        """.formatted(agentId));
    if (code4 == 409) ok("409 Conflict ✓");
    else fail("Expected 409, got " + code4);

    step("5/9", "Changeset on locked file → expect 409");
    var code5 = api.postStatus("/api/v1/projects/" + projectId + "/changesets",
        """
        {"intent":"Test","filesChanged":["src/PaymentService.java"],
         "diff":"--- a/f\\n+++ b/f\\n@@ -0,0 +1 @@\\n+x","autoApplyPolicy":"never"}
        """);
    if (code5 == 409) ok("409 Conflict ✓");
    else fail("Expected 409, got " + code5);

    step("6/9", "Release lock");
    api.delete("/api/v1/projects/" + projectId + "/locks/" + lockId);
    ok("Lock released");

    step("7/9", "Submit changeset (policy=never → human_review)");
    var cs       = api.post("/api/v1/projects/" + projectId + "/changesets",
        """
        {"intent":"Add payment validation","filesChanged":["src/PaymentService.java"],
         "diff":"--- a/f\\n+++ b/f\\n@@ -0,0 +1 @@\\n+// validated",
         "agentId":"%s","autoApplyPolicy":"never"}
        """.formatted(agentId));
    var csId     = extract(cs, "id");
    var csStatus = extract(cs, "status");
    if ("human_review".equals(csStatus)) ok("ChangeSet " + csId + " → human_review ✓");
    else fail("Expected human_review, got " + csStatus);

    step("8/9", "Approve → apply changeset");
    var approved = api.put("/api/v1/projects/" + projectId + "/changesets/" + csId + "/approve");
    ok("Approved: " + extract(approved, "status"));
    var applied  = api.put("/api/v1/projects/" + projectId + "/changesets/" + csId + "/apply");
    ok("Applied:  " + extract(applied, "status"));

    step("9/9", "Check timeline");
    var timeline   = api.get("/api/v1/projects/" + projectId + "/timeline");
    var eventCount = countOccurrences(timeline, "\"type\"");
    ok("Timeline: " + eventCount + " events");

    println();
    info("Bonus: memory write");
    var mem = api.post("/api/v1/projects/" + projectId + "/memory",
        """
        {"title":"Architecture Decision","content":"Use PostgreSQL",
         "justification":"Architecture review 2026","layer":"canonical"}
        """);
    ok("Memory: " + extract(mem, "id"));

    println();
    banner("Phase 2 smoke test PASSED");
    println(DIM + "  " + KOS_API + "/api/v1/projects/" + projectId + "/timeline" + RESET);
}

void testMcp() throws Exception {
    header("MCP server check");
    if (!Files.exists(Path.of("mcp-server/dist/index.js"))) {
        warn("dist not found — building");
        execInteractive("mcp-server", "npm", "run", "build");
    }
    ok("mcp-server/dist/index.js exists");
    info("Set KOS_PROJECT_ID and run: node mcp-server/dist/index.js");
}

// ── MCP server ────────────────────────────────────────────────────────────────

void mcpBuild() throws Exception {
    header("Building MCP server");
    execInteractive("mcp-server", "npm", "run", "build");
    ok("Built → mcp-server/dist/index.js");
}

void mcpStart(Flags f) throws Exception {
    var pid = f.get("project-id", env("KOS_PROJECT_ID", ""));
    if (pid.isBlank()) {
        err("Provide --project-id=<uuid> or set KOS_PROJECT_ID");
        System.exit(1);
    }
    header("Starting MCP server (project=" + pid + ")");
    var e = new HashMap<>(System.getenv());
    e.put("KOS_PROJECT_ID", pid);
    e.put("KOS_API",        f.get("api",     KOS_API));
    e.put("KOS_API_KEY",    f.get("api-key", KOS_API_KEY));
    execInteractiveEnv("mcp-server", e, "node", "dist/index.js");
}

// ── Utilities ─────────────────────────────────────────────────────────────────

void logs(Flags f) throws Exception {
    var svc = f.get("service", null);
    var mode = detectMode(f);
    if ("k8s".equals(mode)) {
        var cmd = svc != null
            ? new String[]{"kubectl", "logs", "-n", K8S_NS, "-l", "app=" + svc, "-f", "--tail=100"}
            : new String[]{"kubectl", "logs", "-n", K8S_NS, "--selector=role in (agent,infra)", "-f", "--tail=50"};
        exec(cmd);
    } else {
        var cmd = svc != null
            ? new String[]{"docker", "compose", "-f", COMPOSE_FILE, "logs", "-f", svc}
            : new String[]{"docker", "compose", "-f", COMPOSE_FILE, "logs", "-f"};
        exec(cmd);
    }
}

void clean() throws Exception {
    header("Cleaning build artifacts");
    execInteractive("backend", "./gradlew", "clean");
    if (Files.exists(Path.of("mcp-server/dist"))) {
        exec("rm", "-rf", "mcp-server/dist");
        ok("mcp-server/dist removed");
    }
    ok("Done");
}

void showCurrentContext() throws Exception {
    try {
        var p = new ProcessBuilder("kubectl", "config", "current-context")
            .redirectErrorStream(true).start();
        var ctx = new String(p.getInputStream().readAllBytes()).trim();
        info("kubectl context: " + CYAN + ctx + RESET);
    } catch (Exception ignored) {}
}

// ── KOS REST client ───────────────────────────────────────────────────────────

record KosClient(String base, String key) {
    private HttpClient http() { return HttpClient.newHttpClient(); }
    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .header("Content-Type", "application/json")
            .header("X-KOS-API-Key", key)
            .timeout(Duration.ofSeconds(15));
    }
    String get(String path) throws Exception {
        var r = http().send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new RuntimeException("GET " + path + " → " + r.statusCode() + "\n" + r.body());
        return r.body();
    }
    String post(String path, String body) throws Exception {
        var r = http().send(req(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                            HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new RuntimeException("POST " + path + " → " + r.statusCode() + "\n" + r.body());
        return r.body();
    }
    int postStatus(String path, String body) throws Exception {
        return http().send(req(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                           HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    String put(String path) throws Exception {
        var r = http().send(req(path).PUT(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new RuntimeException("PUT " + path + " → " + r.statusCode() + "\n" + r.body());
        return r.body();
    }
    void delete(String path) throws Exception {
        var r = http().send(req(path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400 && r.statusCode() != 404)
            throw new RuntimeException("DELETE " + path + " → " + r.statusCode());
    }
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

String extract(String json, String key) {
    var needle = "\"" + key + "\"";
    var idx = json.indexOf(needle);
    if (idx < 0) throw new RuntimeException("Key '" + key + "' not in: " + json);
    var colon = json.indexOf(":", idx + needle.length());
    var start = colon + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
    if (json.charAt(start) == '"') {
        var end = json.indexOf('"', start + 1);
        return json.substring(start + 1, end);
    }
    var end = start;
    while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
    return json.substring(start, end).trim();
}

int countOccurrences(String s, String needle) {
    int n = 0, idx = 0;
    while ((idx = s.indexOf(needle, idx + 1)) >= 0) n++;
    return n;
}

// ── Process execution ─────────────────────────────────────────────────────────

void exec(String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    int rc = new ProcessBuilder(cmd).inheritIO().start().waitFor();
    if (rc != 0) { err("Exit " + rc + ": " + String.join(" ", cmd)); System.exit(rc); }
}

/** Same as exec but suppresses stderr (for idempotent kubectl create). */
void execSilent(String... cmd) throws Exception {
    new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();
}

void execInteractive(String workdir, String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    var dir = Path.of(workdir).toFile();
    if (!dir.exists()) dir = Path.of(".").toFile();
    int rc = new ProcessBuilder(cmd).directory(dir).inheritIO().start().waitFor();
    if (rc != 0) { err("Exit " + rc); System.exit(rc); }
}

void execInteractiveEnv(String workdir, Map<String,String> envOverrides, String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    var dir = Path.of(workdir).toFile();
    if (!dir.exists()) dir = Path.of(".").toFile();
    var pb = new ProcessBuilder(cmd).directory(dir).inheritIO();
    pb.environment().putAll(envOverrides);
    pb.start().waitFor();
}

// ── Flags ─────────────────────────────────────────────────────────────────────

record Flags(String command, Map<String,String> opts) {
    boolean has(String k)           { return opts.containsKey(k); }
    String  get(String k, String d) { return opts.getOrDefault(k, d); }
}

Flags parseFlags(String[] args) {
    var opts = new HashMap<String,String>();
    for (int i = 1; i < args.length; i++) {
        var a = args[i];
        if (a.startsWith("--")) {
            var eq = a.indexOf('=');
            if (eq >= 0) opts.put(a.substring(2, eq), a.substring(eq + 1));
            else          opts.put(a.substring(2), "true");
        }
    }
    return new Flags(args[0], opts);
}

// ── Output helpers ────────────────────────────────────────────────────────────

void println()         { System.out.println(); }
void println(String s) { System.out.println(s); }
void print(String s)   { System.out.print(s); }

void header(String msg) {
    println();
    println(BOLD + CYAN + "▶  " + msg + RESET);
    println(DIM + "─".repeat(50) + RESET);
}
void banner(String msg) {
    println();
    println(BOLD + GREEN + "╔" + "═".repeat(msg.length() + 4) + "╗" + RESET);
    println(BOLD + GREEN + "║  " + msg + "  ║" + RESET);
    println(BOLD + GREEN + "╚" + "═".repeat(msg.length() + 4) + "╝" + RESET);
}
void ok(String m)   { println(GREEN  + "  ✓ " + RESET + m); }
void info(String m) { println(CYAN   + "  ℹ " + RESET + m); }
void warn(String m) { println(YELLOW + "  ⚠ " + RESET + m); }
void err(String m)  { System.err.println(RED + "  ✗ " + RESET + m); }
void step(String l, String m) { println("  " + BOLD + "[" + l + "]" + RESET + " " + m); }
void fail(String m) { err(m); System.exit(1); }

// ── Help ──────────────────────────────────────────────────────────────────────

void printHelp() {
    println(BOLD + """

    KnowledgeOS CLI
    """ + RESET + DIM + "  java kos.java <command> [--flag]\n" + RESET);

    println("  " + BOLD + "Infra mode" + RESET + " (default: k8s with current kubectl context)");
    println("  " + DIM  + "  --k8s      force k8s mode" + RESET);
    println("  " + DIM  + "  --compose  force Docker Compose mode" + RESET);
    println();

    section("Infrastructure");
    cmd("infra-up",         "Deploy infra to current k8s cluster (or compose with --compose)");
    cmd("infra-up",         "--no-wait          Skip readiness wait");
    cmd("infra-down",       "Remove infra");
    cmd("infra-down",       "--volumes          Also delete PVCs/volumes");
    cmd("infra-down",       "--namespace        Also delete the k8s namespace");
    cmd("infra-status",     "Show pod/container status");
    cmd("infra-forward",    "Port-forward k8s services to localhost (k8s mode only)");
    cmd("logs",             "Stream logs  [--service=postgres|redis|qdrant]");

    section("Development");
    cmd("backend-run",      "Start backend (auto-starts compose infra or reminds to port-forward)");
    cmd("backend-run",      "--no-infra / --no-forward  Skip infra/forward hint");
    cmd("frontend-run",     "Start Vite dev server → http://localhost:5173");
    cmd("mcp-build",        "Compile MCP server TypeScript → mcp-server/dist/");
    cmd("mcp-start",        "--project-id=<uuid>  Run MCP server for a project");

    section("Testing");
    cmd("test",             "Run backend test suite");
    cmd("test",             "--filter=ClassName  Run a specific test class");
    cmd("test-phase2",      "End-to-end Phase 2 smoke test via live REST API");
    cmd("test-phase2",      "--api=http://...    Override backend URL");
    cmd("test-mcp",         "Verify MCP server build");

    section("Kind Cluster (explicit lifecycle — separate from infra-up)");
    cmd("cluster-up",       "Create a new kind cluster named 'knowledgeos'");
    cmd("cluster-up",       "--no-wait          Skip pod readiness wait");
    cmd("cluster-down",     "Delete the kind cluster");
    cmd("agent-image",      "Build agent-runner Docker image");
    cmd("agent-image",      "--tag=name:tag      Custom image tag");
    cmd("load-agent-image", "Load agent image into kind cluster");
    cmd("create-ai-secret", "Push ANTHROPIC_API_KEY → k8s secret (pod agents only, not Claude Code)");

    section("Utilities");
    cmd("clean",            "Remove backend build dir and mcp-server/dist");
    cmd("help",             "Show this help");

    section("Environment Variables");
    println("  " + DIM + String.format("%-20s", "KOS_API")        + RESET + "Backend URL          (default: http://localhost:8080)");
    println("  " + DIM + String.format("%-20s", "KOS_API_KEY")    + RESET + "API auth key         (default: dev-local-key)");
    println("  " + DIM + String.format("%-20s", "KOS_NAMESPACE")  + RESET + "k8s namespace        (default: knowledgeos-system)");
    println("  " + DIM + String.format("%-20s", "KOS_PROJECT_ID") + RESET + "Project UUID         (used by mcp-start)");
    println("  " + DIM + String.format("%-20s", "ANTHROPIC_API_KEY") + RESET + "Only for pod agents in k8s (not for Claude Code)");

    section("Typical Workflows");
    println("  " + DIM + "# k8s (default)");
    println("  java kos.java infra-up");
    println("  java kos.java infra-forward   # separate terminal");
    println("  java kos.java backend-run      # separate terminal");
    println();
    println("  # Docker Compose");
    println("  java kos.java infra-up --compose && java kos.java backend-run --compose" + RESET);
    println();
}

void section(String t) { println(); println("  " + BOLD + t + RESET); }
void cmd(String n, String d) { println("    " + CYAN + String.format("%-20s", n) + RESET + " " + d); }
