package com.example.matching.ai.context.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.entity.AiContextPackageSnapshot;
import com.example.matching.ai.context.mapper.AiContextPackageSnapshotMapper;
import com.example.matching.ai.context.service.AiContextSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI上下文快照服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextSnapshotServiceImpl implements AiContextSnapshotService {

    private final AiContextPackageSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void saveSnapshot(AiContextPackageDTO context) {
        if (context == null || context.getScenario() == null || context.getMatchingRecordId() == null) {
            return;
        }

        try {
            AiContextPackageSnapshot snapshot = new AiContextPackageSnapshot();
            snapshot.setScenario(context.getScenario());
            snapshot.setBusinessKey("MATCHING_RECORD:" + context.getMatchingRecordId());
            snapshot.setContextHash(context.getContextHash());
            snapshot.setTokenEstimate(context.getTokenEstimate());
            snapshot.setSourceRefCount(context.getSourceRefs() != null ? context.getSourceRefs().size() : 0);
            snapshot.setPackageJson(objectMapper.writeValueAsString(context));

            snapshotMapper.insert(snapshot);

            log.info("保存AI上下文快照: scenario={}, businessKey={}, tokenEstimate={}",
                    snapshot.getScenario(), snapshot.getBusinessKey(), snapshot.getTokenEstimate());
        } catch (JsonProcessingException e) {
            log.error("序列化上下文包失败", e);
        }
    }

    @Override
    public AiContextPackageSnapshot findLatest(String scenario, String businessKey) {
        LambdaQueryWrapper<AiContextPackageSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiContextPackageSnapshot::getScenario, scenario)
                .eq(AiContextPackageSnapshot::getBusinessKey, businessKey)
                .orderByDesc(AiContextPackageSnapshot::getCreatedTime)
                .last("LIMIT 1");
        return snapshotMapper.selectOne(wrapper);
    }

    @Override
    public AiContextPackageSnapshot findByHash(String contextHash) {
        if (contextHash == null || contextHash.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<AiContextPackageSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiContextPackageSnapshot::getContextHash, contextHash)
                .orderByDesc(AiContextPackageSnapshot::getCreatedTime)
                .last("LIMIT 1");
        return snapshotMapper.selectOne(wrapper);
    }
}
