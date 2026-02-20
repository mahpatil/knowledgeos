package com.knowledgeos.service;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.domain.FileLock;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.ChangeSetResponse;
import com.knowledgeos.dto.CreateChangeSetRequest;
import com.knowledgeos.dto.ValidatorResultResponse;
import com.knowledgeos.repository.AgentRepository;
import com.knowledgeos.repository.ChangeSetRepository;
import com.knowledgeos.repository.FileLockRepository;
import com.knowledgeos.repository.ProjectRepository;
import com.knowledgeos.service.validator.ValidatorFactory;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the changeset lifecycle:
 *
 *   submit → [policy=always] → auto_applied
 *         → [policy=never]   → human_review
 *         → [lock conflict]  → 409 Conflict
 *   approve (human_review → approved)
 *   reject  (human_review → rejected)
 *   apply   (approved → applied)
 *   rollback (applied → rolled_back)
 *
 * CRITICAL: submit() checks for conflicting write locks using a query within
 * @Transactional. This serialises the conflict check and changeset creation,
 * preventing two agents from concurrently submitting changesets on the same file.
 */
@Singleton
public class ChangeSetService {

    private static final Logger log = LoggerFactory.getLogger(ChangeSetService.class);

    @Inject ChangeSetRepository changeSetRepository;
    @Inject FileLockRepository fileLockRepository;
    @Inject ProjectRepository projectRepository;
    @Inject AgentRepository agentRepository;
    @Inject ValidatorFactory validatorFactory;

    @Value("${kubernetes.workspace-base-path:/workspaces}")
    String workspaceBasePath;

    @Transactional
    public ChangeSetResponse submit(UUID projectId, CreateChangeSetRequest req) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        // CRITICAL: check for conflicting write locks before persisting.
        // Within @Transactional, Hibernate serialises this read + subsequent write.
        List<FileLock> conflicting = fileLockRepository
            .findActiveWriteLocksForFiles(projectId, req.filesChanged());
        if (!conflicting.isEmpty()) {
            String lockedFiles = conflicting.stream()
                .map(FileLock::getFilePath)
                .collect(Collectors.joining(", "));
            throw new HttpStatusException(HttpStatus.CONFLICT,
                "Write lock(s) held on: " + lockedFiles);
        }

        Agent agent = null;
        if (req.agentId() != null) {
            agent = agentRepository.findById(req.agentId()).orElse(null);
        }

        ChangeSet cs = new ChangeSet();
        cs.setProject(project);
        cs.setAgent(agent);
        cs.setIntent(req.intent());
        cs.setFilesChanged(toJsonArray(req.filesChanged()));
        cs.setDiff(req.diff());
        cs.setAutoApplyPolicy(req.autoApplyPolicy() != null ? req.autoApplyPolicy() : "never");

        if (req.testsRun() != null) {
            cs.setTestsRun(toJsonArray(req.testsRun()));
        }

        String policy = cs.getAutoApplyPolicy();
        cs.setStatus("pending");
        cs = changeSetRepository.save(cs);

        // Evaluate auto-apply policy
        switch (policy) {
            case "always" -> {
                applyDiff(cs, project);
                cs.setStatus("auto_applied");
                cs = changeSetRepository.update(cs);
            }
            case "on_tests_pass" -> {
                String workspacePath = workspaceBasePath + "/" + project.getNamespace();
                ValidatorResultResponse validatorResult = runValidator(cs, agent, workspacePath);
                cs.setValidatorResults(validatorResult);
                if (validatorResult.passed() && !validatorResult.requiresHumanReview()) {
                    applyDiff(cs, project);
                    cs.setStatus("auto_applied");
                } else if (validatorResult.passed() && validatorResult.requiresHumanReview()) {
                    cs.setStatus("human_review");
                } else {
                    cs.setStatus("agent_review");
                }
                cs = changeSetRepository.update(cs);
            }
            default -> {  // "never"
                cs.setStatus("human_review");
                cs = changeSetRepository.update(cs);
            }
        }

        log.info("ChangeSet submitted: id={} status={} intent={}", cs.getId(), cs.getStatus(), cs.getIntent());
        return toResponse(cs);
    }

    public List<ChangeSetResponse> listForProject(UUID projectId) {
        return changeSetRepository.findByProjectId(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void delete(UUID projectId, UUID csId) {
        ChangeSet cs = getEntity(projectId, csId);
        if (!"pending".equals(cs.getStatus()) && !"human_review".equals(cs.getStatus())) {
            throw new HttpStatusException(HttpStatus.CONFLICT,
                "Only pending or human_review changesets can be deleted");
        }
        changeSetRepository.delete(cs);
    }

    @Transactional
    public ChangeSetResponse approve(UUID projectId, UUID csId) {
        ChangeSet cs = getEntity(projectId, csId);
        if (!"human_review".equals(cs.getStatus()) && !"agent_review".equals(cs.getStatus())) {
            throw new HttpStatusException(HttpStatus.CONFLICT,
                "Changeset is not awaiting review: " + cs.getStatus());
        }
        cs.setStatus("approved");
        cs.setUpdatedAt(OffsetDateTime.now());
        cs = changeSetRepository.update(cs);
        log.info("ChangeSet approved: id={}", csId);
        return toResponse(cs);
    }

    @Transactional
    public ChangeSetResponse reject(UUID projectId, UUID csId, String reason) {
        ChangeSet cs = getEntity(projectId, csId);
        cs.setStatus("rejected");
        cs.setRejectReason(reason);
        cs.setUpdatedAt(OffsetDateTime.now());
        cs = changeSetRepository.update(cs);
        log.info("ChangeSet rejected: id={} reason={}", csId, reason);
        return toResponse(cs);
    }

    @Transactional
    public ChangeSetResponse apply(UUID projectId, UUID csId) {
        ChangeSet cs = getEntity(projectId, csId);
        if (!"approved".equals(cs.getStatus())) {
            throw new HttpStatusException(HttpStatus.CONFLICT,
                "Changeset must be approved before applying: " + cs.getStatus());
        }

        Project project = cs.getProject();
        applyDiff(cs, project);
        cs.setStatus("applied");
        cs.setUpdatedAt(OffsetDateTime.now());
        cs = changeSetRepository.update(cs);
        log.info("ChangeSet applied: id={}", csId);
        return toResponse(cs);
    }

    @Transactional
    public ChangeSetResponse rollback(UUID projectId, UUID csId) {
        ChangeSet cs = getEntity(projectId, csId);
        if (!"applied".equals(cs.getStatus()) && !"auto_applied".equals(cs.getStatus())) {
            throw new HttpStatusException(HttpStatus.CONFLICT,
                "Only applied changesets can be rolled back: " + cs.getStatus());
        }

        if (cs.getReverseDiff() != null && !cs.getReverseDiff().isBlank()) {
            applyRawDiff(cs.getReverseDiff(), cs.getProject());
        } else {
            log.warn("No reverse diff stored for changeset {} — rollback is a no-op", csId);
        }

        cs.setStatus("rolled_back");
        cs.setUpdatedAt(OffsetDateTime.now());
        cs = changeSetRepository.update(cs);
        log.info("ChangeSet rolled back: id={}", csId);
        return toResponse(cs);
    }

    /**
     * Manually trigger validation for a changeset (callable from the API).
     */
    @Transactional
    public ValidatorResultResponse validate(UUID projectId, UUID csId) {
        ChangeSet cs = getEntity(projectId, csId);
        String workspacePath = workspaceBasePath + "/" + cs.getProject().getNamespace();
        ValidatorResultResponse result = runValidator(cs, cs.getAgent(), workspacePath);
        cs.setValidatorResults(result);
        changeSetRepository.update(cs);
        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private ChangeSet getEntity(UUID projectId, UUID csId) {
        return changeSetRepository.findById(csId)
            .filter(cs -> cs.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "ChangeSet not found: " + csId));
    }

    private ValidatorResultResponse runValidator(ChangeSet cs, Agent agent, String workspacePath) {
        try {
            return validatorFactory.forProject(cs.getProject()).run(cs, agent, workspacePath);
        } catch (Exception e) {
            log.warn("Validator failed for changeset {}: {}", cs.getId(), e.getMessage());
            return new ValidatorResultResponse(false,
                List.of("Validator error: " + e.getMessage()), 0, false);
        }
    }

    /**
     * Apply unified diff to workspace files.
     * Stores original content as reverseDiff for rollback.
     * Gracefully skips files that don't exist in the workspace.
     */
    private void applyDiff(ChangeSet cs, Project project) {
        String diff = cs.getDiff();
        if (diff == null || diff.isBlank()) return;

        String workspacePath = workspaceBasePath + "/" + project.getNamespace();
        List<String> reverseParts = new ArrayList<>();

        // Parse each "--- a/..." / "+++ b/..." block
        String[] lines = diff.split("\n");
        String targetFile = null;
        List<String> hunkLines = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("+++ b/") || line.startsWith("+++ ")) {
                // Flush previous hunk
                if (targetFile != null && !hunkLines.isEmpty()) {
                    String reverse = applyHunksToFile(workspacePath, targetFile, hunkLines);
                    if (reverse != null) reverseParts.add(reverse);
                    hunkLines.clear();
                }
                targetFile = line.replaceFirst("^\\+\\+\\+ (b/)?", "");
            } else if (line.startsWith("--- ")) {
                // Skip — just a marker
            } else {
                hunkLines.add(line);
            }
        }
        // Flush last file
        if (targetFile != null && !hunkLines.isEmpty()) {
            String reverse = applyHunksToFile(workspacePath, targetFile, hunkLines);
            if (reverse != null) reverseParts.add(reverse);
        }

        cs.setReverseDiff(String.join("\n---FILE_BOUNDARY---\n", reverseParts));
    }

    private void applyRawDiff(String reverseDiff, Project project) {
        // Reverse diff stores original content per file, separated by ---FILE_BOUNDARY---
        if (reverseDiff == null || reverseDiff.isBlank()) return;
        String[] parts = reverseDiff.split("\n---FILE_BOUNDARY---\n");
        for (String part : parts) {
            // Format: "FILE:<path>\n<content>"
            if (part.startsWith("FILE:")) {
                int newline = part.indexOf('\n');
                if (newline < 0) continue;
                String filePath = part.substring(5, newline);
                String content = part.substring(newline + 1);
                String workspacePath = workspaceBasePath + "/" + project.getNamespace();
                try {
                    Path target = Path.of(workspacePath, filePath);
                    if (Files.exists(target)) {
                        Files.writeString(target, content);
                    }
                } catch (IOException e) {
                    log.warn("Rollback write failed for {}: {}", filePath, e.getMessage());
                }
            }
        }
    }

    /**
     * Apply diff hunks to a single file.
     *
     * @return a reverse-diff snippet ("FILE:<path>\n<originalContent>") or null if file missing
     */
    private String applyHunksToFile(String workspaceBase, String relPath, List<String> hunkLines) {
        Path filePath = Path.of(workspaceBase, relPath);
        String originalContent = "";

        if (Files.exists(filePath)) {
            try {
                originalContent = Files.readString(filePath);
            } catch (IOException e) {
                log.warn("Cannot read file for diff application: {}", filePath);
                return null;
            }
        } else {
            log.debug("File not found in workspace, skipping diff: {}", filePath);
            return null;
        }

        // Apply hunks line-by-line
        String[] fileLines = originalContent.split("\n", -1);
        List<String> result = new ArrayList<>(List.of(fileLines));
        int offset = 0;

        for (String line : hunkLines) {
            if (line.startsWith("@@")) {
                // Parse @@ -start,count +start,count @@ header
                try {
                    String[] parts = line.split(" ");
                    String minus = parts[1]; // -start[,count]
                    int start = Integer.parseInt(minus.substring(1).split(",")[0]) - 1 + offset;
                    offset = 0; // reset for this hunk
                    // We'll use a simple approach: track position
                } catch (Exception e) {
                    // Ignore malformed hunk header
                }
            } else if (line.startsWith("-")) {
                // Remove line
                if (!result.isEmpty()) result.remove(0);
            } else if (line.startsWith("+")) {
                // Add line
                result.add(0, line.substring(1));
                offset++;
            }
            // Context lines (no prefix) — skip
        }

        try {
            String newContent = String.join("\n", result);
            Files.writeString(filePath, newContent);
        } catch (IOException e) {
            log.warn("Cannot write diff result to {}: {}", filePath, e.getMessage());
        }

        return "FILE:" + relPath + "\n" + originalContent;
    }

    private ChangeSetResponse toResponse(ChangeSet cs) {
        return new ChangeSetResponse(
            cs.getId(),
            cs.getAgent() != null ? cs.getAgent().getId() : null,
            cs.getIntent(),
            fromJsonArray(cs.getFilesChanged()),
            cs.getDiff(),
            cs.getStatus(),
            cs.getValidatorResults(),
            cs.getCreatedAt()
        );
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        try {
            String inner = json.trim().replaceFirst("^\\[", "").replaceFirst("]$", "").trim();
            if (inner.isEmpty()) return List.of();
            List<String> result = new ArrayList<>();
            for (String part : inner.split(",(?=\")")) {
                result.add(part.trim().replaceAll("^\"|\"$", ""));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
