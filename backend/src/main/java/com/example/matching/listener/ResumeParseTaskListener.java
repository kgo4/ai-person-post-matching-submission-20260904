package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.service.employee.ResumeParseService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 简历解析任务消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseTaskListener {

    private final ResumeParseService resumeParseService;

    @RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE, containerFactory = "slowRabbitListenerContainerFactory")
    public void handleResumeParseTask(Long parseId) {
        log.info("收到简历解析任务消息: parseId={}", parseId);
        SecurityUtils.setSystemContext();
        try {
            resumeParseService.processQueuedParse(parseId);
        } catch (Exception e) {
            // Resume parsing is one assessment stage. Isolate unexpected failures so
            // a malformed task cannot block the listener container or other employees.
            log.error("简历解析任务发生未处理异常，已隔离: parseId={}, error={}",
                    parseId, e.getMessage(), e);
        } finally {
            SecurityUtils.clear();
        }
    }
}
