package com.knowledgeos.service.validator;

import com.knowledgeos.domain.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Selects the appropriate {@link Validator} for a given project type.
 *
 * Project type → Validator mapping:
 *   "software"            → {@link SoftwareValidator}  (runs test suite)
 *   "content"             → {@link ContentValidator}   (readability + word count)
 *   "migration","research"→ {@link MigrationValidator} (output equivalence %)
 */
@Singleton
public class ValidatorFactory {

    @Inject SoftwareValidator softwareValidator;
    @Inject ContentValidator contentValidator;
    @Inject MigrationValidator migrationValidator;

    public Validator forProject(Project project) {
        return switch (project.getType()) {
            case "software"              -> softwareValidator;
            case "content"               -> contentValidator;
            case "migration", "research" -> migrationValidator;
            default -> throw new IllegalArgumentException(
                "Unknown project type: " + project.getType());
        };
    }
}
