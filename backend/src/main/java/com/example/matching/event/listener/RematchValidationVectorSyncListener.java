package com.example.matching.event.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.event.VectorSyncCompletedEvent;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.service.matching.MatchingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RematchValidationVectorSyncListener {

    private static final String WAIT_VECTOR_SYNC = "WAIT_VECTOR_SYNC";
    private static final String SUBMITTING = "SUBMITTING";
    private static final String PENDING = "PENDING";

    private final MatchingRematchValidationMapper validationMapper;
    private final MatchingTaskService matchingTaskService;

    @EventListener
    public void onVectorSyncCompleted(VectorSyncCompletedEvent event) {
        if (!"EMPLOYEE".equals(event.entityType()) || event.entityId() == null) {
            return;
        }
        List<MatchingRematchValidation> validations = validationMapper.selectList(
                Wrappers.<MatchingRematchValidation>lambdaQuery()
                        .eq(MatchingRematchValidation::getEmpId, event.entityId())
                        .eq(MatchingRematchValidation::getValidationStatus, WAIT_VECTOR_SYNC));
        for (MatchingRematchValidation validation : validations) {
            int claimed = validationMapper.update(null,
                    Wrappers.<MatchingRematchValidation>lambdaUpdate()
                            .eq(MatchingRematchValidation::getId, validation.getId())
                            .eq(MatchingRematchValidation::getValidationStatus, WAIT_VECTOR_SYNC)
                            .set(MatchingRematchValidation::getValidationStatus, SUBMITTING));
            if (claimed == 0) {
                continue;
            }
            try {
                MatchingExecuteDTO.MatchingPair pair = new MatchingExecuteDTO.MatchingPair();
                pair.setEmpId(validation.getEmpId());
                pair.setPostId(validation.getPostId());
                MatchingExecuteDTO executeDTO = new MatchingExecuteDTO();
                executeDTO.setPairs(List.of(pair));
                executeDTO.setMode("SINGLE_EVAL");
                executeDTO.setEnableAiMatching(true);
                String taskId = matchingTaskService.submitTask(executeDTO);

                validation.setTaskId(taskId);
                validation.setValidationStatus(PENDING);
                validationMapper.updateById(validation);
            } catch (Exception e) {
                validationMapper.update(null,
                        Wrappers.<MatchingRematchValidation>lambdaUpdate()
                                .eq(MatchingRematchValidation::getId, validation.getId())
                                .eq(MatchingRematchValidation::getValidationStatus, SUBMITTING)
                                .set(MatchingRematchValidation::getValidationStatus, WAIT_VECTOR_SYNC)
                                .set(MatchingRematchValidation::getFailReason, truncate(e.getMessage())));
                log.warn("Failed to submit rematch after vector sync: validationId={}, empId={}, postId={}",
                        validation.getId(), validation.getEmpId(), validation.getPostId(), e);
            }
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "rematch submission failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
