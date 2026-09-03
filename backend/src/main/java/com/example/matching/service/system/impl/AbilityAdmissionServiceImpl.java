package com.example.matching.service.system.impl;

import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityAdmissionService;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力准入服务实现
 * <p>
 * 提供统一的能力准入处理流程，业务服务只需提出能力主张，
 * 由本服务统一决策是否准入、创建标签、进入候选池或拒绝。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityAdmissionServiceImpl implements AbilityAdmissionService {

    private final AbilityTagService abilityTagService;

    @Override
    @Transactional
    public TagAdmissionResult processAbilityClaim(TagAdmissionContext context) {
        return abilityTagService.admitNewTag(context);
    }

    @Override
    @Transactional
    public AbilityImportResultDTO processAbilityClaims(List<TagAdmissionContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return AbilityImportResultDTO.empty();
        }

        int total = contexts.size();
        int imported = 0, reused = 0, created = 0, candidate = 0, rejected = 0;
        List<Long> importedAbilityIds = new ArrayList<>();
        List<Long> candidateIds = new ArrayList<>();
        List<AbilityImportResultDTO.RejectionDetail> rejections = new ArrayList<>();

        for (TagAdmissionContext context : contexts) {
            try {
                TagAdmissionResult result = abilityTagService.admitNewTag(context);

                switch (result.getDecision()) {
                    case EXISTING_TAG_REUSED:
                        imported++;
                        reused++;
                        if (result.getResolvedTagId() != null) {
                            importedAbilityIds.add(result.getResolvedTagId());
                        }
                        break;

                    case FORMAL_TAG_CREATED:
                        imported++;
                        created++;
                        if (result.getResolvedTagId() != null) {
                            importedAbilityIds.add(result.getResolvedTagId());
                        }
                        break;

                    case CANDIDATE_CREATED:
                        candidate++;
                        if (result.getCandidateId() != null) {
                            candidateIds.add(result.getCandidateId());
                        }
                        break;

                    case REJECTED:
                        rejected++;
                        rejections.add(AbilityImportResultDTO.RejectionDetail.builder()
                                .tagName(context.getTagName())
                                .reason(result.getReason())
                                .build());
                        break;
                }

                log.debug("能力主张处理完成: tagName={}, decision={}, reason={}",
                        context.getTagName(), result.getDecision(), result.getReason());

            } catch (Exception e) {
                log.error("处理能力主张异常: tagName={}, error={}", context.getTagName(), e.getMessage(), e);
                rejected++;
                rejections.add(AbilityImportResultDTO.RejectionDetail.builder()
                        .tagName(context.getTagName())
                        .reason("处理异常: " + e.getMessage())
                        .build());
            }
        }

        AbilityImportResultDTO result = AbilityImportResultDTO.builder()
                .total(total)
                .imported(imported)
                .reused(reused)
                .created(created)
                .candidate(candidate)
                .rejected(rejected)
                .importedAbilityIds(importedAbilityIds)
                .candidateIds(candidateIds)
                .rejections(rejections)
                .build();
        result.setMessage(result.buildMessage());

        log.info("批量能力准入处理完成: total={}, imported={}, candidate={}, rejected={}, message={}",
                total, imported, candidate, rejected, result.getMessage());

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> processAbilityClaimsWithDetails(List<TagAdmissionContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("importResult", AbilityImportResultDTO.empty());
            emptyResult.put("admissionResults", new ArrayList<TagAdmissionResult>());
            return emptyResult;
        }

        int total = contexts.size();
        int imported = 0, reused = 0, created = 0, candidate = 0, rejected = 0;
        List<Long> importedAbilityIds = new ArrayList<>();
        List<Long> candidateIds = new ArrayList<>();
        List<AbilityImportResultDTO.RejectionDetail> rejections = new ArrayList<>();
        List<TagAdmissionResult> admissionResults = new ArrayList<>();

        for (TagAdmissionContext context : contexts) {
            try {
                TagAdmissionResult result = abilityTagService.admitNewTag(context);
                admissionResults.add(result);

                switch (result.getDecision()) {
                    case EXISTING_TAG_REUSED:
                        imported++;
                        reused++;
                        if (result.getResolvedTagId() != null) {
                            importedAbilityIds.add(result.getResolvedTagId());
                        }
                        break;

                    case FORMAL_TAG_CREATED:
                        imported++;
                        created++;
                        if (result.getResolvedTagId() != null) {
                            importedAbilityIds.add(result.getResolvedTagId());
                        }
                        break;

                    case CANDIDATE_CREATED:
                        candidate++;
                        if (result.getCandidateId() != null) {
                            candidateIds.add(result.getCandidateId());
                        }
                        break;

                    case REJECTED:
                        rejected++;
                        rejections.add(AbilityImportResultDTO.RejectionDetail.builder()
                                .tagName(context.getTagName())
                                .reason(result.getReason())
                                .build());
                        break;
                }

                log.debug("能力主张处理完成: tagName={}, decision={}, reason={}",
                        context.getTagName(), result.getDecision(), result.getReason());

            } catch (Exception e) {
                log.error("处理能力主张异常: tagName={}, error={}", context.getTagName(), e.getMessage(), e);
                rejected++;
                admissionResults.add(TagAdmissionResult.rejected("处理异常: " + e.getMessage()));
                rejections.add(AbilityImportResultDTO.RejectionDetail.builder()
                        .tagName(context.getTagName())
                        .reason("处理异常: " + e.getMessage())
                        .build());
            }
        }

        AbilityImportResultDTO importResult = AbilityImportResultDTO.builder()
                .total(total)
                .imported(imported)
                .reused(reused)
                .created(created)
                .candidate(candidate)
                .rejected(rejected)
                .importedAbilityIds(importedAbilityIds)
                .candidateIds(candidateIds)
                .rejections(rejections)
                .build();
        importResult.setMessage(importResult.buildMessage());

        log.info("批量能力准入处理完成: total={}, imported={}, candidate={}, rejected={}, message={}",
                total, imported, candidate, rejected, importResult.getMessage());

        Map<String, Object> result = new HashMap<>();
        result.put("importResult", importResult);
        result.put("admissionResults", admissionResults);
        return result;
    }
}
