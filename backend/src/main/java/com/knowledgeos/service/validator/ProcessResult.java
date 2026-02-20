package com.knowledgeos.service.validator;

/**
 * Result of a shell command executed by {@link ProcessRunner}.
 */
public record ProcessResult(int exitCode, String output) {

    /** Returns true if the process exited with code 0. */
    public boolean success() {
        return exitCode == 0;
    }
}
