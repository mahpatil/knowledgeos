///usr/bin/env java --source 25 "$0" "$@"; exit
// KnowledgeOS CLI — Java 25 single-file script
// Usage:  java kos.java <command> [options]
//    or:  chmod +x kos.java && ./kos.java <command> [options]

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

// ── ANSI colours ──────────────────────────────────────────────────────────────

static final String RESET  = "\033[0m";
static final String BOLD   = "\033[1m";
static final String GREEN  = "\033[0;32m";
static final String YELLOW = "\033[0;33m";
static final String CYAN   = "\033[0;36m";
static final String RED    = "\033[0;31m";
static final String DIM    = "\033[2m";

// ── Config (override with env vars) ───────────────────────────────────────────

static final String KOS_API     = System.getenv().getOrDefault("KOS_API",     "http://localhost:8080");
static final String KOS_API_KEY = System.getenv().getOrDefault("KOS_API_KEY", "dev-local-key");
static final String COMPOSE_FILE = "docker-compose.infra.yml";

// ── Entry point ───────────────────────────────────────────────────────────────

void main(String[] args) throws Exception {
    if (args.length == 0) { printHelp(); return; }

    var flags = parseFlags(args);
    var command = flags.command();

    switch (command) {
        // Infrastructure
        case "infra-up"          -> infraUp(flags);
        case "infra-down"        -> infraDown(flags);
        case "infra-status"      -> infraStatus();

        // Backend / frontend
        case "backend-run"       -> backendRun(flags);
        case "frontend-run"      -> frontendRun(flags);

        // Kubernetes cluster
        case "cluster-up"        -> clusterUp(flags);
        case "cluster-down"      -> clusterDown();
        case "agent-image"       -> agentImage(flags);
        case "load-agent-image"  -> loadAgentImage();
        case "create-ai-secret"  -> createAiSecret();

        // Testing
        case "test"              -> runTests(flags);
        case "test-phase2"       -> testPhase2(flags);
        case "test-mcp"          -> testMcp(flags);

        // MCP server
        case "mcp-build"         -> mcpBuild();
        case "mcp-start"         -> mcpStart(flags);

        // Utilities
        case "clean"             -> clean();
        case "logs"              -> logs(flags);
        case "help", "--help", "-h" -> printHelp();

        default -> {
            err("Unknown command: " + command);
            println();
            printHelp();
            System.exit(1);
        }
    }
}

// ── Infrastructure commands ───────────────────────────────────────────────────

void infraUp(Flags f) throws Exception {
    header("Starting infrastructure");
    exec("docker", "compose", "-f", COMPOSE_FILE, "up", "-d");

    if (!f.has("no-wait")) {
        info("Waiting for services to become healthy...");
        waitForHealth();
    }

    ok("Infrastructure is up");
    println();
    step("Next", "java kos.java backend-run");
}

void infraDown(Flags f) throws Exception {
    header("Stopping infrastructure");
    var cmd = f.has("volumes")
        ? new String[]{"docker", "compose", "-f", COMPOSE_FILE, "down", "-v"}
        : new String[]{"docker", "compose", "-f", COMPOSE_FILE, "down"};
    exec(cmd);
    ok("Infrastructure stopped" + (f.has("volumes") ? " (volumes removed)" : ""));
}

void infraStatus() throws Exception {
    header("Infrastructure status");
    exec("docker", "compose", "-f", COMPOSE_FILE, "ps");
}

void waitForHealth() throws Exception {
    var services = List.of(
        new HealthCheck("postgres", "docker", "exec", "knowledgeos-postgres", "pg_isready", "-U", "knowledgeos"),
        new HealthCheck("redis",    "docker", "exec", "knowledgeos-redis",    "redis-cli", "ping"),
        new HealthCheck("qdrant",   "docker", "exec", "knowledgeos-qdrant",   "curl", "-sf", "http://localhost:6333/healthz")
    );

    for (var svc : services) {
        print("  " + DIM + "Waiting for " + svc.name() + "..." + RESET);
        boolean ready = false;
        for (int i = 0; i < 30; i++) {
            try {
                var p = new ProcessBuilder(svc.cmd())
                    .redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.exitValue() == 0) { ready = true; break; }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        System.out.print("\r");
        if (ready) ok("  " + svc.name() + " ready");
        else warn("  " + svc.name() + " did not become healthy — continuing anyway");
    }
}

// ── Backend / frontend commands ───────────────────────────────────────────────

void backendRun(Flags f) throws Exception {
    if (!f.has("no-infra")) {
        header("Checking infrastructure");
        exec("docker", "compose", "-f", COMPOSE_FILE, "up", "-d");
    }
    header("Starting backend");
    info("Backend will be available at: " + CYAN + KOS_API + RESET);
    info("Swagger UI:                   " + CYAN + KOS_API + "/swagger-ui" + RESET);
    // Forward to interactive process
    execInteractive("backend", "./gradlew", "run");
}

void frontendRun(Flags f) throws Exception {
    header("Starting frontend");
    info("Frontend will be available at: " + CYAN + "http://localhost:5173" + RESET);
    execInteractive("frontend", "npm", "run", "dev");
}

// ── Kubernetes commands ───────────────────────────────────────────────────────

void clusterUp(Flags f) throws Exception {
    header("Creating kind cluster");
    exec("kind", "create", "cluster", "--name", "knowledgeos",
         "--config", "infra/kind/cluster-config.yaml");
    exec("kubectl", "label", "node", "knowledgeos-worker",  "role=worker", "--overwrite");
    exec("kubectl", "label", "node", "knowledgeos-worker2", "role=worker", "--overwrite");
    exec("kubectl", "create", "namespace", "knowledgeos-system");
    exec("kubectl", "apply", "-f", "infra/k8s/system/");
    exec("kubectl", "apply", "-f", "infra/k8s/base/");

    if (!f.has("no-wait")) {
        info("Waiting for cluster pods...");
        exec("kubectl", "wait", "--for=condition=ready", "pod",
             "-l", "app=postgres", "-n", "knowledgeos-system", "--timeout=120s");
        exec("kubectl", "wait", "--for=condition=ready", "pod",
             "-l", "app=redis",    "-n", "knowledgeos-system", "--timeout=120s");
    }
    ok("Cluster ready");
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
    ok("Image loaded into cluster");
}

void createAiSecret() throws Exception {
    var key = System.getenv("ANTHROPIC_API_KEY");
    if (key == null || key.isBlank()) {
        err("ANTHROPIC_API_KEY env var is not set");
        System.exit(1);
    }
    header("Creating AI secret in k8s");
    exec("kubectl", "create", "secret", "generic", "ai-api-keys",
         "--from-literal=ANTHROPIC_API_KEY=" + key,
         "--from-literal=OPENAI_API_KEY=" + System.getenv().getOrDefault("OPENAI_API_KEY", ""),
         "-n", "knowledgeos-system",
         "--dry-run=client", "-o", "yaml");
    ok("Secret applied");
}

// ── Test commands ─────────────────────────────────────────────────────────────

void runTests(Flags f) throws Exception {
    header("Running test suite");
    var filter = f.get("filter", null);
    var cmd = filter != null
        ? new String[]{"./gradlew", "cleanTest", "test", "--tests", filter}
        : new String[]{"./gradlew", "cleanTest", "test"};
    execInteractive("backend", cmd);
}

/** End-to-end Phase 2 smoke test — creates real resources via REST. */
void testPhase2(Flags f) throws Exception {
    header("Phase 2 smoke test — ChangeSets, FileLocks, Memory, Timeline");
    println();

    var api = new KosClient(KOS_API, KOS_API_KEY);
    var baseUrl = f.get("api", KOS_API);

    // 1. Create project
    step("1/9", "Create project");
    var project = api.post("/api/v1/projects",
        """
        {"name":"Phase2-SmokeTest","type":"software"}
        """);
    var projectId = extract(project, "id");
    ok("Project created: " + projectId);

    // 2. Create agent
    step("2/9", "Create local agent");
    var agent = api.post("/api/v1/projects/" + projectId + "/agents",
        """
        {"name":"smoke-agent","model":"claude-sonnet-4-6","role":"Implementer","agentType":"local"}
        """);
    var agentId = extract(agent, "id");
    ok("Agent created: " + agentId);

    // 3. Acquire lock
    step("3/9", "Acquire write lock");
    var lock = api.post("/api/v1/projects/" + projectId + "/locks",
        """
        {"filePath":"src/PaymentService.java","lockType":"write","durationSeconds":60,"agentId":"%s"}
        """.formatted(agentId));
    var lockId = extract(lock, "id");
    ok("Lock acquired: " + lockId + " → src/PaymentService.java");

    // 4. Duplicate lock → expect 409
    step("4/9", "Duplicate lock → expect 409 Conflict");
    var conflictStatus = api.postStatus("/api/v1/projects/" + projectId + "/locks",
        """
        {"filePath":"src/PaymentService.java","lockType":"write","agentId":"%s"}
        """.formatted(agentId));
    if (conflictStatus == 409) ok("Got 409 Conflict as expected");
    else fail("Expected 409, got " + conflictStatus);

    // 5. Submit changeset on locked file → expect 409
    step("5/9", "Changeset on locked file → expect 409");
    var csConflict = api.postStatus("/api/v1/projects/" + projectId + "/changesets",
        """
        {"intent":"Test","filesChanged":["src/PaymentService.java"],
         "diff":"--- a/f\\n+++ b/f\\n@@ -0,0 +1 @@\\n+x","autoApplyPolicy":"never"}
        """);
    if (csConflict == 409) ok("Got 409 Conflict as expected");
    else fail("Expected 409, got " + csConflict);

    // 6. Release lock
    step("6/9", "Release lock");
    api.delete("/api/v1/projects/" + projectId + "/locks/" + lockId);
    ok("Lock released");

    // 7. Submit changeset (policy=never → human_review)
    step("7/9", "Submit changeset (policy=never)");
    var cs = api.post("/api/v1/projects/" + projectId + "/changesets",
        """
        {"intent":"Add payment validation","filesChanged":["src/PaymentService.java"],
         "diff":"--- a/f\\n+++ b/f\\n@@ -0,0 +1 @@\\n+// validated",
         "agentId":"%s","autoApplyPolicy":"never"}
        """.formatted(agentId));
    var csId    = extract(cs, "id");
    var csStatus = extract(cs, "status");
    if ("human_review".equals(csStatus)) ok("ChangeSet " + csId + " → human_review");
    else fail("Expected human_review, got " + csStatus);

    // 8. Approve → apply
    step("8/9", "Approve + apply changeset");
    var approved = api.put("/api/v1/projects/" + projectId + "/changesets/" + csId + "/approve");
    ok("Approved: " + extract(approved, "status"));
    var applied = api.put("/api/v1/projects/" + projectId + "/changesets/" + csId + "/apply");
    ok("Applied:  " + extract(applied, "status"));

    // 9. Timeline
    step("9/9", "Check timeline events");
    var timeline = api.get("/api/v1/projects/" + projectId + "/timeline");
    var eventCount = countEvents(timeline);
    ok("Timeline contains " + eventCount + " events");

    // Memory (bonus)
    println();
    info("Bonus: Memory write + search");
    var mem = api.post("/api/v1/projects/" + projectId + "/memory",
        """
        {"title":"Architecture Decision","content":"Use PostgreSQL for ACID guarantees",
         "justification":"Architecture review 2026","layer":"canonical"}
        """);
    ok("Memory written: " + extract(mem, "id"));

    println();
    banner("Phase 2 smoke test PASSED");
    println(DIM + "  Project: " + KOS_API + " → /api/v1/projects/" + projectId + RESET);
    println(DIM + "  Timeline: " + KOS_API + "/api/v1/projects/" + projectId + "/timeline" + RESET);
}

/** Test the MCP server tool list. */
void testMcp(Flags f) throws Exception {
    header("MCP server — verify dist exists and tool count");

    var distIndex = Path.of("mcp-server/dist/index.js");
    if (!Files.exists(distIndex)) {
        warn("mcp-server/dist/index.js not found — building first");
        execInteractive("mcp-server", "npm", "run", "build");
    }
    ok("mcp-server/dist/index.js exists");

    println();
    info("To test interactively, set KOS_PROJECT_ID and run:");
    step("Run", "KOS_PROJECT_ID=<project-id> node mcp-server/dist/index.js");
}

// ── MCP server commands ───────────────────────────────────────────────────────

void mcpBuild() throws Exception {
    header("Building MCP server");
    execInteractive("mcp-server", "npm", "run", "build");
    ok("MCP server built → mcp-server/dist/index.js");
}

void mcpStart(Flags f) throws Exception {
    var projectId = f.get("project-id", System.getenv().getOrDefault("KOS_PROJECT_ID", ""));
    if (projectId.isBlank()) {
        err("Provide --project-id=<uuid> or set KOS_PROJECT_ID env var");
        System.exit(1);
    }
    header("Starting MCP server (project=" + projectId + ")");
    info("Claude Code will auto-load this via .mcp.json");
    info("Or run manually: node mcp-server/dist/index.js");
    var env = new HashMap<>(System.getenv());
    env.put("KOS_PROJECT_ID", projectId);
    env.put("KOS_API",        f.get("api",     KOS_API));
    env.put("KOS_API_KEY",    f.get("api-key", KOS_API_KEY));
    execInteractiveEnv("mcp-server", env, "node", "dist/index.js");
}

// ── Utilities ─────────────────────────────────────────────────────────────────

void logs(Flags f) throws Exception {
    var service = f.get("service", null);
    if (service != null) {
        exec("docker", "compose", "-f", COMPOSE_FILE, "logs", "-f", service);
    } else {
        exec("docker", "compose", "-f", COMPOSE_FILE, "logs", "-f");
    }
}

void clean() throws Exception {
    header("Cleaning build artifacts");
    execInteractive("backend", "./gradlew", "clean");
    if (Files.exists(Path.of("mcp-server/dist"))) {
        exec("rm", "-rf", "mcp-server/dist");
        ok("mcp-server/dist removed");
    }
    ok("Clean complete");
}

// ── KOS REST client ───────────────────────────────────────────────────────────

record KosClient(String base, String apiKey) {
    HttpClient http() { return HttpClient.newHttpClient(); }

    HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .header("Content-Type", "application/json")
            .header("X-KOS-API-Key", apiKey);
    }

    String get(String path) throws Exception {
        var resp = http().send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new RuntimeException("GET " + path + " → " + resp.statusCode() + "\n" + resp.body());
        return resp.body();
    }

    String post(String path, String body) throws Exception {
        var resp = http().send(req(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                               HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new RuntimeException("POST " + path + " → " + resp.statusCode() + "\n" + resp.body());
        return resp.body();
    }

    int postStatus(String path, String body) throws Exception {
        return http().send(req(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                           HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    String put(String path) throws Exception {
        var resp = http().send(req(path).PUT(HttpRequest.BodyPublishers.noBody()).build(),
                               HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new RuntimeException("PUT " + path + " → " + resp.statusCode() + "\n" + resp.body());
        return resp.body();
    }

    void delete(String path) throws Exception {
        var resp = http().send(req(path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400 && resp.statusCode() != 404)
            throw new RuntimeException("DELETE " + path + " → " + resp.statusCode());
    }
}

// ── JSON helpers (no external deps) ──────────────────────────────────────────

String extract(String json, String key) {
    var needle = "\"" + key + "\"";
    var idx = json.indexOf(needle);
    if (idx < 0) throw new RuntimeException("Key '" + key + "' not found in: " + json);
    var after = json.indexOf(":", idx + needle.length());
    var start = after + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
    if (json.charAt(start) == '"') {
        var end = json.indexOf('"', start + 1);
        return json.substring(start + 1, end);
    }
    var end = start;
    while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) end++;
    return json.substring(start, end).trim();
}

int countEvents(String json) {
    int count = 0;
    int idx = 0;
    while ((idx = json.indexOf("\"type\"", idx + 1)) >= 0) count++;
    return count;
}

// ── Process execution helpers ─────────────────────────────────────────────────

void exec(String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    var pb = new ProcessBuilder(cmd)
        .directory(Path.of(".").toFile())
        .redirectErrorStream(true)
        .inheritIO();
    int rc = pb.start().waitFor();
    if (rc != 0) {
        err("Command failed with exit code " + rc + ": " + String.join(" ", cmd));
        System.exit(rc);
    }
}

void execInteractive(String workdir, String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    var dir = Path.of(workdir).toFile();
    if (!dir.exists()) dir = Path.of(".").toFile();
    int rc = new ProcessBuilder(cmd)
        .directory(dir)
        .inheritIO()
        .start()
        .waitFor();
    if (rc != 0) {
        err("Command failed (exit " + rc + ")");
        System.exit(rc);
    }
}

void execInteractiveEnv(String workdir, Map<String,String> env, String... cmd) throws Exception {
    println(DIM + "  $ " + String.join(" ", cmd) + RESET);
    var dir = Path.of(workdir).toFile();
    if (!dir.exists()) dir = Path.of(".").toFile();
    var pb = new ProcessBuilder(cmd).directory(dir).inheritIO();
    pb.environment().putAll(env);
    pb.start().waitFor();
}

// ── Flag / arg parsing ─────────────────────────────────────────────────────────

record Flags(String command, Map<String,String> opts) {
    boolean has(String key) { return opts.containsKey(key); }
    String  get(String key, String def) { return opts.getOrDefault(key, def); }
}

Flags parseFlags(String[] args) {
    var command = args[0];
    var opts = new HashMap<String,String>();
    for (int i = 1; i < args.length; i++) {
        var arg = args[i];
        if (arg.startsWith("--")) {
            var eq = arg.indexOf('=');
            if (eq >= 0) opts.put(arg.substring(2, eq), arg.substring(eq + 1));
            else          opts.put(arg.substring(2), "true");
        }
    }
    return new Flags(command, opts);
}

// ── Output helpers ────────────────────────────────────────────────────────────

record HealthCheck(String name, String... cmd) {}

void println()              { System.out.println(); }
void println(String s)      { System.out.println(s); }
void print(String s)        { System.out.print(s); }

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

void ok(String msg)   { println(GREEN  + "  ✓ " + RESET + msg); }
void info(String msg) { println(CYAN   + "  ℹ " + RESET + msg); }
void warn(String msg) { println(YELLOW + "  ⚠ " + RESET + msg); }
void err(String msg)  { System.err.println(RED + "  ✗ " + RESET + msg); }
void step(String label, String msg) {
    println("  " + BOLD + "[" + label + "]" + RESET + " " + msg);
}

void fail(String msg) {
    err(msg);
    System.exit(1);
}

// ── Help ──────────────────────────────────────────────────────────────────────

void printHelp() {
    println(BOLD + """

    KnowledgeOS CLI
    """ + RESET + DIM + "  java kos.java <command> [--flag=value]\n" + RESET);

    section("Infrastructure");
    cmd("infra-up",          "Start PostgreSQL, Redis, Qdrant via Docker Compose");
    cmd("infra-up",          "--no-wait            Skip health-check waiting");
    cmd("infra-down",        "Stop infrastructure");
    cmd("infra-down",        "--volumes            Also remove data volumes");
    cmd("infra-status",      "Show container status");
    cmd("logs",              "Tail all container logs");
    cmd("logs",              "--service=postgres   Tail a specific service");

    section("Development");
    cmd("backend-run",       "Start backend (auto-starts infra first)");
    cmd("backend-run",       "--no-infra           Skip infra start");
    cmd("frontend-run",      "Start Vite dev server (http://localhost:5173)");
    cmd("mcp-build",         "Compile MCP server TypeScript → dist/");
    cmd("mcp-start",         "Run MCP server (needs --project-id=<uuid>)");
    cmd("mcp-start",         "--project-id=<uuid>  Project to bind to");

    section("Testing");
    cmd("test",              "Run backend test suite (./gradlew cleanTest test)");
    cmd("test",              "--filter=ClassName   Run specific test class");
    cmd("test-phase2",       "End-to-end Phase 2 smoke test via REST API");
    cmd("test-phase2",       "--api=http://...     Override API base URL");
    cmd("test-mcp",          "Verify MCP server build");

    section("Kubernetes (kind)");
    cmd("cluster-up",        "Create kind cluster + apply infra manifests");
    cmd("cluster-up",        "--no-wait            Skip pod readiness wait");
    cmd("cluster-down",      "Delete kind cluster");
    cmd("agent-image",       "Build agent-runner Docker image");
    cmd("agent-image",       "--tag=name:tag       Custom image tag");
    cmd("load-agent-image",  "Load image into kind cluster");
    cmd("create-ai-secret",  "Push ANTHROPIC_API_KEY → k8s secret (pod agents only, not Claude Code)");

    section("Utilities");
    cmd("clean",             "Remove backend build artifacts and mcp-server/dist");
    cmd("help",              "Show this help");

    section("Environment Variables");
    println("  " + DIM + "KOS_API      " + RESET + "Backend URL       (default: http://localhost:8080)");
    println("  " + DIM + "KOS_API_KEY  " + RESET + "API auth key      (default: dev-local-key)");
    println("  " + DIM + "KOS_PROJECT_ID" + RESET + " Project UUID    (used by mcp-start)");

    section("Quick Start");
    println("  " + DIM + "java kos.java infra-up && java kos.java backend-run" + RESET);

    section("Phase 2 Test");
    println("  " + DIM + "java kos.java test-phase2" + RESET);
    println();
}

void section(String title) {
    println();
    println("  " + BOLD + title + RESET);
}

void cmd(String name, String desc) {
    println("    " + CYAN + String.format("%-20s", name) + RESET + " " + desc);
}
