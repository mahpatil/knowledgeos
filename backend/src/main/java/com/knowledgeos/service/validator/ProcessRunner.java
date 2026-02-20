package com.knowledgeos.service.validator;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction over {@link ProcessBuilder} so validators can be tested without
 * actually spawning processes.
 *
 * The default production implementation is {@link DefaultProcessRunner}.
 * Tests replace this with a {@code @MockBean}.
 */
@FunctionalInterface
public interface ProcessRunner {

    /**
     * Run a command in the given working directory.
     *
     * @param command     the command and its arguments (e.g. ["./gradlew", "test"])
     * @param workingDir  directory in which to run the command
     * @return            exit code and combined stdout+stderr output
     */
    ProcessResult run(List<String> command, Path workingDir);
}
