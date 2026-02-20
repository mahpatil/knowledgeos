package com.knowledgeos.service.validator;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.dto.ValidatorResultResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validates migration/research changesets by comparing expected vs actual output.
 *
 * Looks for:
 *   {workspacePath}/legacy/expected-output/   — reference output files
 *   {workspacePath}/target/output/            — produced output files
 *
 * Each file in expected-output must exist in target/output with identical content.
 * Equivalence % = (matching files / total expected files) × 100.
 */
@Singleton
public class MigrationValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(MigrationValidator.class);

    @Override
    public ValidatorResultResponse run(ChangeSet changeset, Agent agent, String workspacePath) {
        long start = System.currentTimeMillis();

        Path expectedDir = Path.of(workspacePath, "legacy", "expected-output");
        Path actualDir   = Path.of(workspacePath, "target",  "output");

        if (!Files.exists(expectedDir) || !Files.exists(actualDir)) {
            long dur = System.currentTimeMillis() - start;
            return new ValidatorResultResponse(false,
                List.of("Missing required directories: legacy/expected-output or target/output"),
                dur, false);
        }

        ComparisonResult comparison = compareDirectories(expectedDir, actualDir);
        long durationMs = System.currentTimeMillis() - start;

        List<String> failures = new ArrayList<>();
        if (!comparison.failures().isEmpty()) {
            failures.add(String.format("Equivalence: %.1f%% (%d/%d files matched)",
                comparison.equivalencePct(), comparison.passedCases(), comparison.totalCases()));
            failures.addAll(comparison.failures());
        }

        log.info("MigrationValidator: {}% equivalence in {}ms",
            String.format("%.1f", comparison.equivalencePct()), durationMs);
        return new ValidatorResultResponse(failures.isEmpty(), failures, durationMs, false);
    }

    // ── Package-private for unit tests ──────────────────────────────────────

    ComparisonResult compareDirectories(Path expectedDir, Path actualDir) {
        int total  = 0;
        int passed = 0;
        List<String> failures = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(expectedDir)) {
            List<Path> expectedFiles = walk.filter(Files::isRegularFile).toList();

            for (Path expected : expectedFiles) {
                total++;
                Path relative = expectedDir.relativize(expected);
                Path actual   = actualDir.resolve(relative);

                if (!Files.exists(actual)) {
                    failures.add("Missing output file: " + relative);
                    continue;
                }
                try {
                    String exp = Files.readString(expected).trim();
                    String act = Files.readString(actual).trim();
                    if (exp.equals(act)) {
                        passed++;
                    } else {
                        failures.add("Output mismatch: " + relative);
                    }
                } catch (IOException e) {
                    failures.add("Cannot read: " + relative + " — " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Directory walk failed: {}", e.getMessage());
            failures.add("Cannot compare directories: " + e.getMessage());
        }

        double pct = total > 0 ? (double) passed / total * 100.0 : 0.0;
        return new ComparisonResult(pct, passed, total, failures);
    }

    record ComparisonResult(
        double equivalencePct,
        int passedCases,
        int totalCases,
        List<String> failures
    ) {}
}
