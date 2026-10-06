package com.example.matching.event.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.event.MatchingTaskCompletedEvent;
import com.example.matching.event.MatchingTaskFailedEvent;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RematchValidationCompletionListener {

    private final MatchingRematchValidationMapper rematchValidationMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final com.example.matching.service.matching.MatchingCacheInvalidator matchingCacheInvalidator;

    @EventListener
    public void onMatchingTaskCompleted(MatchingTaskCompletedEvent event) {
        String taskId = event.taskId();
        List<MatchingRematchValidation> validations = rematchValidationMapper.selectList(
                Wrappers.<MatchingRematchValidation>lambdaQuery()
                        .eq(MatchingRematchValidation::getTaskId, taskId)
                        .eq(MatchingRematchValidation::getValidationStatus, "PENDING"));

        for (MatchingRematchValidation v : validations) {
            try {
                MatchingRecord newRecord = matchingRecordMapper.selectOne(
                        Wrappers.<MatchingRecord>lambdaQuery()
                                .eq(MatchingRecord::getBatchNo, event.batchNo())
                                .eq(MatchingRecord::getEmpId, v.getEmpId())
                                .eq(MatchingRecord::getPostId, v.getPostId())
                                .eq(MatchingRecord::getIsDeleted, 0)
                                .orderByDesc(MatchingRecord::getId)
                                .last("LIMIT 1"));

                if (newRecord != null) {
                    v.setNewMatchingRecordId(newRecord.getId());
                    v.setNewScore(newRecord.getAiMatchScore());
                    v.setNewMatchStatus(newRecord.getMatchStatus());
                    v.setValidationStatus("COMPLETED");
                    // 修复：再评结果回流到原始匹配记录（学习后能力提升应反映在原记录上），
                    // 仅当原记录未被 HR 锁定/删除时更新，且只更新 AI 分与状态，不动人工分
                    backfillOriginalRecord(v, newRecord);
                } else {
                    v.setValidationStatus("FAILED");
                    v.setFailReason("No matching record found for batch " + event.batchNo());
                }
            } catch (Exception e) {
                log.error("Failed to update rematch validation: taskId={}", taskId, e);
                v.setValidationStatus("FAILED");
                v.setFailReason("Error: " + e.getMessage());
            }
            rematchValidationMapper.updateById(v);
        }
    }

    /**
     * 把再评新分数回写原匹配记录（未锁定才回写；不回写人工分 finalMatchScore/manualRemark）。
     */
    private void backfillOriginalRecord(MatchingRematchValidation v, MatchingRecord newRecord) {
        if (v.getOriginalMatchingRecordId() == null || newRecord == null) {
            return;
        }
        try {
            int rows = matchingRecordMapper.update(null, Wrappers.<MatchingRecord>lambdaUpdate()
                    .eq(MatchingRecord::getId, v.getOriginalMatchingRecordId())
                    .eq(MatchingRecord::getIsDeleted, 0)
                    .eq(MatchingRecord::getIsLocked, 0)
                    .set(newRecord.getAiMatchScore() != null,
                            MatchingRecord::getAiMatchScore, newRecord.getAiMatchScore())
                    .set(newRecord.getMatchStatus() != null,
                            MatchingRecord::getMatchStatus, newRecord.getMatchStatus()));
            if (rows == 1) {
                log.info("再评分数已回写原匹配记录: originalRecordId={}, newScore={}, newStatus={}",
                        v.getOriginalMatchingRecordId(), newRecord.getAiMatchScore(), newRecord.getMatchStatus());
                // 回写成功后清理详情/列表/仪表盘缓存，避免前端展示旧 AI 分
                try {
                    matchingCacheInvalidator.evictAfterAiScore(v.getOriginalMatchingRecordId());
                } catch (Exception e) {
                    log.warn("再评分回写后缓存清理失败（不影响回写结果）: originalRecordId={}, error={}",
                            v.getOriginalMatchingRecordId(), e.getMessage());
                }
            } else {
                log.info("原匹配记录已锁定/删除，跳过再评分回写: originalRecordId={}",
                        v.getOriginalMatchingRecordId());
            }
        } catch (Exception e) {
            log.warn("回写再评分数失败（不影响验证记录状态）: originalRecordId={}, error={}",
                    v.getOriginalMatchingRecordId(), e.getMessage());
        }
    }

    @EventListener
    public void onMatchingTaskFailed(MatchingTaskFailedEvent event) {
        List<MatchingRematchValidation> validations = rematchValidationMapper.selectList(
                Wrappers.<MatchingRematchValidation>lambdaQuery()
                        .eq(MatchingRematchValidation::getTaskId, event.taskId())
                        .eq(MatchingRematchValidation::getValidationStatus, "PENDING"));
        for (MatchingRematchValidation validation : validations) {
            validation.setValidationStatus("FAILED");
            validation.setFailReason(event.reason());
            rematchValidationMapper.updateById(validation);
        }
    }
}
