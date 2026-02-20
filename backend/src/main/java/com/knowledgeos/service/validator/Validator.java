package com.knowledgeos.service.validator;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.dto.ValidatorResultResponse;

/**
 * Contract for project-type-specific validators.
 *
 * Implementations:
 *   SoftwareValidator  — runs test suite (Gradle/Maven/npm) via kubectl or shell
 *   ContentValidator   — checks word count and Flesch-Kincaid readability
 *   MigrationValidator — compares legacy/expected-output vs target/output
 */
public interface Validator {

    /**
     * Run validation for the given changeset.
     *
     * @param changeset     the changeset being validated
     * @param agent         the agent that submitted the changeset (nullable for system submissions)
     * @param workspacePath absolute path to the workspace on the local file system
     * @return              validation result with pass/fail and optional failure messages
     */
    ValidatorResultResponse run(ChangeSet changeset, Agent agent, String workspacePath);
}
