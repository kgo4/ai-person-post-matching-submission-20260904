package com.example.matching.dto.system;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Ability Import Result DTO
 *
 * Provides structured import statistics, letting users know how many abilities were imported,
 * how many entered the candidate pool, and how many were rejected.
 *
 * @author system
 */
@Data
@Builder
public class AbilityImportResultDTO {

    /** Total number of parsed abilities */
    private int total;

    /** Number of successfully imported abilities (reused existing tags + newly created formal tags) */
    private int imported;

    /** Number of reused existing tags */
    private int reused;

    /** Number of newly created formal tags */
    private int created;

    /** Number of abilities that entered the candidate pool */
    private int candidate;

    /** Number of rejected abilities */
    private int rejected;

    /** List of imported ability record IDs */
    private List<Long> importedAbilityIds;

    /** List of candidate tag IDs */
    private List<Long> candidateIds;

    /** List of rejection details */
    private List<RejectionDetail> rejections;

    /** Human-readable summary message */
    private String message;

    /**
     * Rejection detail
     */
    @Data
    @Builder
    public static class RejectionDetail {
        private String tagName;
        private String reason;
    }

    /**
     * Build summary message
     */
    public String buildMessage() {
        StringBuilder sb = new StringBuilder();

        if (imported > 0) {
            sb.append("Successfully imported ").append(imported).append(" items");
            if (reused > 0) {
                sb.append(" (").append(reused).append(" reused existing tags");
                if (created > 0) {
                    sb.append(", ").append(created).append(" new tags created");
                }
                sb.append(")");
            } else if (created > 0) {
                sb.append(" (").append(created).append(" new tags created)");
            }
        }

        if (candidate > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(candidate).append(" new abilities entered candidate tag pool");
        }

        if (rejected > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(rejected).append(" items rejected due to quality issues or insufficient evidence");
        }

        if (sb.length() == 0) {
            sb.append("No valid abilities parsed");
        }

        return sb.toString();
    }

    /**
     * Create empty result
     */
    public static AbilityImportResultDTO empty() {
        return AbilityImportResultDTO.builder()
                .total(0)
                .imported(0)
                .reused(0)
                .created(0)
                .candidate(0)
                .rejected(0)
                .importedAbilityIds(new ArrayList<>())
                .candidateIds(new ArrayList<>())
                .rejections(new ArrayList<>())
                .message("No valid abilities parsed")
                .build();
    }

    /**
     * Merge multiple results
     */
    public static AbilityImportResultDTO merge(List<AbilityImportResultDTO> results) {
        int total = 0, imported = 0, reused = 0, created = 0, candidate = 0, rejected = 0;
        List<Long> importedIds = new ArrayList<>();
        List<Long> candidateIds = new ArrayList<>();
        List<RejectionDetail> rejections = new ArrayList<>();

        for (AbilityImportResultDTO r : results) {
            total += r.getTotal();
            imported += r.getImported();
            reused += r.getReused();
            created += r.getCreated();
            candidate += r.getCandidate();
            rejected += r.getRejected();
            if (r.getImportedAbilityIds() != null) importedIds.addAll(r.getImportedAbilityIds());
            if (r.getCandidateIds() != null) candidateIds.addAll(r.getCandidateIds());
            if (r.getRejections() != null) rejections.addAll(r.getRejections());
        }

        AbilityImportResultDTO merged = AbilityImportResultDTO.builder()
                .total(total)
                .imported(imported)
                .reused(reused)
                .created(created)
                .candidate(candidate)
                .rejected(rejected)
                .importedAbilityIds(importedIds)
                .candidateIds(candidateIds)
                .rejections(rejections)
                .build();
        merged.setMessage(merged.buildMessage());
        return merged;
    }
}
