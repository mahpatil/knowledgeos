package com.knowledgeos.service.validator;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.dto.ValidatorResultResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates content-type changeset quality:
 *   1. Minimum word count (100 words in added content)
 *   2. Flesch Reading Ease score ≥ 30 (not too dense / academic)
 *   3. Editorial approval flag — if the project policySet contains
 *      "editorial_approval":true, the changeset is flagged for human review
 *      even when all other checks pass.
 */
@Singleton
public class ContentValidator implements Validator {

    static final int MIN_WORD_COUNT = 100;
    static final double MIN_READABILITY = 30.0;

    @Override
    public ValidatorResultResponse run(ChangeSet changeset, Agent agent, String workspacePath) {
        long start = System.currentTimeMillis();

        String content = extractAddedContent(changeset.getDiff());
        List<String> failures = new ArrayList<>();

        // Word count
        int wordCount = countWords(content);
        if (wordCount < MIN_WORD_COUNT) {
            failures.add("Word count too low: " + wordCount + " (minimum: " + MIN_WORD_COUNT + ")");
        }

        // Flesch readability
        double readability = fleschReadingEase(content);
        if (readability < MIN_READABILITY) {
            failures.add(String.format("Readability too low: %.1f (minimum: %.1f)", readability, MIN_READABILITY));
        }

        long durationMs = System.currentTimeMillis() - start;
        boolean passed = failures.isEmpty();

        // Editorial approval policy — forces human review even on a passing diff
        boolean requiresHumanReview = passed && hasEditorialApprovalPolicy(changeset.getProject().getPolicySet());

        return new ValidatorResultResponse(passed, failures, durationMs, requiresHumanReview);
    }

    // ── Package-private helpers (used in unit tests) ────────────────────────

    String extractAddedContent(String diff) {
        if (diff == null || diff.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : diff.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                sb.append(line.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /**
     * Flesch Reading Ease:
     *   206.835 − 1.015 × (words/sentences) − 84.6 × (syllables/words)
     * Higher = easier to read (100 = very easy, 0 = college-level).
     */
    double fleschReadingEase(String text) {
        if (text == null || text.isBlank()) return 0;

        String[] words = text.trim().split("\\s+");
        int wordCount = words.length;
        if (wordCount == 0) return 0;

        long sentenceCount = Math.max(1,
            text.chars().filter(c -> c == '.' || c == '!' || c == '?').count());

        int syllables = 0;
        for (String word : words) {
            syllables += countSyllables(word);
        }
        double syllablesPerWord = syllables > 0 ? (double) syllables / wordCount : 1.0;

        return 206.835
            - 1.015 * ((double) wordCount / sentenceCount)
            - 84.6  * syllablesPerWord;
    }

    private int countSyllables(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.isEmpty()) return 1;
        int count = 0;
        boolean lastVowel = false;
        for (char c : word.toCharArray()) {
            boolean vowel = "aeiou".indexOf(c) >= 0;
            if (vowel && !lastVowel) count++;
            lastVowel = vowel;
        }
        if (word.endsWith("e") && count > 1) count--;
        return Math.max(1, count);
    }

    private boolean hasEditorialApprovalPolicy(String policySet) {
        if (policySet == null || policySet.isBlank()) return false;
        // Simple string check — avoids ObjectMapper dependency
        return policySet.contains("\"editorial_approval\"") && policySet.contains("true");
    }
}
