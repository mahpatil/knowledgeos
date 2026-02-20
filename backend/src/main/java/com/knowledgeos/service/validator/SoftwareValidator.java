package com.knowledgeos.service.validator;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.dto.ValidatorResultResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the project's test suite to validate a changeset.
 *
 * Agent type determines execution path:
 *   pod   → runs tests via "kubectl exec -n {ns} {pod} -- {buildTool} test"
 *   local → runs tests directly in workspacePath via ProcessBuilder
 *
 * Build tool is auto-detected from workspace files (Gradle > Maven > npm > Gradle default).
 */
@Singleton
public class SoftwareValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(SoftwareValidator.class);

    @Inject
    ProcessRunner processRunner;

    @Override
    public ValidatorResultResponse run(ChangeSet changeset, Agent agent, String workspacePath) {
        long start = System.currentTimeMillis();

        ProcessResult result;
        if (agent != null && "pod".equals(agent.getAgentType()) && agent.getPodName() != null) {
            result = runViaPodExec(changeset, agent, workspacePath);
        } else {
            result = runViaShell(workspacePath);
        }

        long durationMs = System.currentTimeMillis() - start;

        if (result.success()) {
            log.info("SoftwareValidator PASSED in {}ms", durationMs);
            return new ValidatorResultResponse(true, List.of(), durationMs, false);
        } else {
            List<String> failures = extractFailures(result.output());
            log.info("SoftwareValidator FAILED in {}ms: {} failures", durationMs, failures.size());
            return new ValidatorResultResponse(false, failures, durationMs, false);
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private ProcessResult runViaPodExec(ChangeSet changeset, Agent agent, String workspacePath) {
        String namespace = changeset.getProject().getNamespace();
        String podName = agent.getPodName();
        String buildTool = detectBuildTool(workspacePath);

        List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("exec");
        cmd.add("-n");
        cmd.add(namespace);
        cmd.add(podName);
        cmd.add("--");
        cmd.addAll(buildCmd(buildTool));

        log.debug("Running tests via kubectl exec: {}", String.join(" ", cmd));
        return processRunner.run(cmd, Path.of(workspacePath));
    }

    private ProcessResult runViaShell(String workspacePath) {
        Path workspace = Path.of(workspacePath);
        String buildTool = detectBuildTool(workspacePath);
        List<String> cmd = buildCmd(buildTool);
        log.debug("Running tests via shell: {}", String.join(" ", cmd));
        return processRunner.run(cmd, workspace);
    }

    /**
     * Detect build tool from workspace files.
     * Precedence: Gradle → Maven → npm → Gradle (default)
     */
    String detectBuildTool(String workspacePath) {
        Path ws = Path.of(workspacePath);
        if (Files.exists(ws.resolve("build.gradle")) || Files.exists(ws.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        if (Files.exists(ws.resolve("pom.xml"))) {
            return "maven";
        }
        if (Files.exists(ws.resolve("package.json"))) {
            return "npm";
        }
        return "gradle";
    }

    private List<String> buildCmd(String buildTool) {
        return switch (buildTool) {
            case "maven" -> List.of("mvn", "-B", "test");
            case "npm"   -> List.of("npm", "test", "--", "--ci");
            default      -> List.of("./gradlew", "test", "--no-daemon");
        };
    }

    private List<String> extractFailures(String output) {
        List<String> failures = new ArrayList<>();
        if (output == null) return failures;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("FAILED") || trimmed.contains("ERROR") || trimmed.contains("FAIL")) {
                failures.add(trimmed);
            }
        }
        if (failures.isEmpty()) {
            failures.add("Build failed (exit code non-zero)");
        }
        return failures;
    }
}
