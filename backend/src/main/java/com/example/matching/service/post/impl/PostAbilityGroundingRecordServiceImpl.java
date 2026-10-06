package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.entity.post.PostAbilityGroundingRecord;
import com.example.matching.mapper.post.PostAbilityGroundingRecordMapper;
import com.example.matching.service.post.PostAbilityGroundingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostAbilityGroundingRecordServiceImpl implements PostAbilityGroundingRecordService {
    private final PostAbilityGroundingRecordMapper mapper;

    @Override
    public void append(List<PostAbilityClaim> claims, String status) {
        if (claims == null) return;
        for (PostAbilityClaim claim : claims) {
            if (claim == null || claim.getPostId() == null) continue;
            PostAbilityGroundingRecord record = new PostAbilityGroundingRecord();
            record.setPostId(claim.getPostId());
            record.setAbilityName(claim.getAbilityName());
            record.setNormalizedAbilityName(claim.getNormalizedAbilityName());
            record.setAbilityTagId(claim.getAbilityTagId());
            record.setSourceType(claim.getSourceType());
            record.setSourceRefId(claim.getSourceRefId());
            record.setEvidenceText(claim.getEvidenceText());
            record.setEvidenceAnchor(claim.getEvidenceAnchor());
            record.setEvidenceStart(claim.getEvidenceStart());
            record.setEvidenceEnd(claim.getEvidenceEnd());
            record.setValidationStatus(status);
            record.setValidationReason(claim.getExtractReason());
            mapper.insert(record);
        }
    }

    @Override
    public void resolveForModel(Long postId, String abilityName) {
        if (postId == null || abilityName == null || abilityName.isBlank()) {
            return;
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<PostAbilityGroundingRecord>()
                .eq(PostAbilityGroundingRecord::getPostId, postId)
                .eq(PostAbilityGroundingRecord::getAbilityName, abilityName)
                .in(PostAbilityGroundingRecord::getValidationStatus, "SUBMITTED", "DEFERRED")
                .set(PostAbilityGroundingRecord::getValidationStatus, "RESOLVED"));
        if (updated > 0) {
            log.info("岗位能力人工处理后台账标记 RESOLVED: postId={}, abilityName={}, updated={}",
                    postId, abilityName, updated);
        }
    }
}
