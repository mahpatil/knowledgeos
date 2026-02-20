package com.knowledgeos.service.validator;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Production {@link ProcessRunner} that delegates to {@link ProcessBuilder}.
 * stdout and stderr are merged into a single output string.
 */
@Singleton
public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessRunner.class);

    @Override
    public ProcessResult run(List<String> command, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);

            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();

            log.debug("Command '{}' exited {} in {}", String.join(" ", command), code, workingDir);
            return new ProcessResult(code, output);

        } catch (Exception e) {
            log.warn("Process execution failed: {}", e.getMessage());
            return new ProcessResult(1, e.getMessage() != null ? e.getMessage() : "Process execution error");
        }
    }
}
