package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.evolution.EvolutionScheduleConfigDTO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.entity.evolution.PostEvolutionScheduleConfig;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.mapper.evolution.PostEvolutionScheduleConfigMapper;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.schedule.ScheduledTaskRunner;
import com.example.matching.service.evolution.EvolutionScheduleService;
import com.example.matching.service.evolution.PostEvolutionAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 演化定时服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionScheduleServiceImpl implements EvolutionScheduleService {

    private final PostEvolutionScheduleConfigMapper scheduleConfigMapper;
    private final PostEvolutionAgentService postEvolutionAgentService;
    private final DistributedLockService distributedLockService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    private static final String DEFAULT_CRON = "0 0 2 * * ?";

    @Override
    @Transactional
    public PostEvolutionScheduleConfig createConfig(EvolutionScheduleConfigDTO dto, Long operatorId) {
        log.info("创建定时演化配置: postId={}", dto.getPostId());

        PostEvolutionScheduleConfig config = new PostEvolutionScheduleConfig();
        config.setPostId(dto.getPostId());
        config.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : 1);
        String cron = dto.getCronExpression() != null ? dto.getCronExpression() : DEFAULT_CRON;
        validateCron(cron);
        config.setCronExpression(cron);
        config.setIndustry(dto.getIndustry());
        config.setBusinessDomain(dto.getBusinessDomain());
        config.setIncludeWhitepaper(dto.getIncludeWhitepaper() != null ? dto.getIncludeWhitepaper() : 1);
        config.setIncludeCloudKnowledge(dto.getIncludeCloudKnowledge() != null ? dto.getIncludeCloudKnowledge() : 1);
        config.setIncludeMarketJd(dto.getIncludeMarketJd() != null ? dto.getIncludeMarketJd() : 0);
        config.setRunCount(0);
        config.setCreatedBy(operatorId);

        scheduleConfigMapper.insert(config);

        log.info("定时演化配置创建成功: id={}, postId={}", config.getId(), config.getPostId());
        return config;
    }

    @Override
    @Transactional
    public PostEvolutionScheduleConfig updateConfig(Long id, EvolutionScheduleConfigDTO dto) {
        PostEvolutionScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "定时配置不存在: " + id);
        }

        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getCronExpression() != null) {
            validateCron(dto.getCronExpression());
            config.setCronExpression(dto.getCronExpression());
        }
        if (dto.getIndustry() != null) {
            config.setIndustry(dto.getIndustry());
        }
        if (dto.getBusinessDomain() != null) {
            config.setBusinessDomain(dto.getBusinessDomain());
        }
        if (dto.getIncludeWhitepaper() != null) {
            config.setIncludeWhitepaper(dto.getIncludeWhitepaper());
        }
        if (dto.getIncludeCloudKnowledge() != null) {
            config.setIncludeCloudKnowledge(dto.getIncludeCloudKnowledge());
        }
        if (dto.getIncludeMarketJd() != null) {
            config.setIncludeMarketJd(dto.getIncludeMarketJd());
        }
        scheduleConfigMapper.updateById(config);

        log.info("定时演化配置更新成功: id={}", id);
        return config;
    }

    @Override
    public IPage<PostEvolutionScheduleConfig> pageConfigs(Page<PostEvolutionScheduleConfig> page, Long postId) {
        LambdaQueryWrapper<PostEvolutionScheduleConfig> wrapper = new LambdaQueryWrapper<>();
        if (postId != null) {
            wrapper.eq(PostEvolutionScheduleConfig::getPostId, postId);
        }
        wrapper.orderByDesc(PostEvolutionScheduleConfig::getCreatedTime);
        return scheduleConfigMapper.selectPage(page, wrapper);
    }

    @Override
    public PostEvolutionScheduleConfig getConfigById(Long id) {
        PostEvolutionScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "定时配置不存在: " + id);
        }
        return config;
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        PostEvolutionScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "定时配置不存在: " + id);
        }
        scheduleConfigMapper.deleteById(id);
        log.info("定时演化配置删除成功: id={}", id);
    }

    @Override
    @Transactional
    public Long runNow(Long id) {
        PostEvolutionScheduleConfig config = getConfigById(id);
        log.info("立即执行定时演化: configId={}, postId={}", id, config.getPostId());

        // 构建 Agent 请求
        PostEvolutionAgentRequest request = buildAgentRequest(config);
        request.setTriggerType("MANUAL_RUN");

        // 执行 Agent
        PostEvolutionTask task = postEvolutionAgentService.runEvolutionAndCreateTask(request);

        // 更新配置的执行记录
        config.setLastRunTime(LocalDateTime.now());
        config.setLastTaskId(task.getId());
        config.setRunCount(config.getRunCount() + 1);
        scheduleConfigMapper.updateById(config);

        log.info("定时演化执行完成: configId={}, taskId={}", id, task.getId());
        return task.getId();
    }

    /**
     * M2: 每分钟扫描一次配置，由每条配置的 cron 表达式决定是否执行；
     * M13: 扫描本身不加长事务，每个配置的执行记录用独立事务提交。
     */
    @Override
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void executeScheduledEvolution() {
        if (taskRunner != null) {
            taskRunner.run("evolution_schedule", this::executeScheduledEvolutionInternal);
            return;
        }
        try {
            executeScheduledEvolutionInternal();
        } catch (Exception e) {
            log.error("定时演化扫描失败", e);
        }
    }

    private void executeScheduledEvolutionInternal() {
        var lock = distributedLockService.tryAcquire("evolution-schedule");
        if (lock == null) {
            log.debug("Evolution schedule already running on another instance, skipping");
            return;
        }
        try {
            log.info("开始执行定时演化扫描");

            // 查询所有启用的配置
            LambdaQueryWrapper<PostEvolutionScheduleConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PostEvolutionScheduleConfig::getEnabled, 1);
            List<PostEvolutionScheduleConfig> configs = scheduleConfigMapper.selectList(wrapper);

            if (configs.isEmpty()) {
                log.info("没有启用的定时演化配置");
                return;
            }

        log.info("找到 {} 个启用的定时演化配置", configs.size());

        for (PostEvolutionScheduleConfig config : configs) {
            try {
                // 检查是否需要执行（基于 cron 表达式简化判断）
                if (shouldExecute(config)) {
                    executeConfig(config);
                }
            } catch (Exception e) {
                log.error("定时演化执行失败: configId={}, postId={}, error={}",
                        config.getId(), config.getPostId(), e.getMessage(), e);
            }
        }
        } finally {
            lock.close();
        }

        log.info("定时演化扫描完成");
    }

    /**
     * 判断是否应该执行（M2）：cron.next(lastRunTime) <= now 时执行。
     */
    private boolean shouldExecute(PostEvolutionScheduleConfig config) {
        if (config.getLastRunTime() == null) {
            return true; // 从未执行过
        }
        if (config.getCronExpression() == null || config.getCronExpression().isBlank()) {
            return false;
        }
        try {
            org.springframework.scheduling.support.CronExpression cron =
                    org.springframework.scheduling.support.CronExpression.parse(config.getCronExpression());
            LocalDateTime next = cron.next(config.getLastRunTime());
            return next != null && !next.isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("cron 表达式解析失败，跳过该配置: configId={}, cron={}, error={}",
                    config.getId(), config.getCronExpression(), e.getMessage());
            return false;
        }
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "cron 表达式不能为空");
        }
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "cron 表达式不合法: " + cron);
        }
    }

    /**
     * 执行单个配置（M13）：LLM/Agent 调用在数据库事务外，配置执行记录用独立事务提交，
     * 一个配置失败不影响其他配置。
     */
    private void executeConfig(PostEvolutionScheduleConfig config) {
        log.info("执行定时演化: configId={}, postId={}", config.getId(), config.getPostId());

        PostEvolutionAgentRequest request = buildAgentRequest(config);
        request.setTriggerType("SCHEDULED");

        // Agent 调用（含 LLM）在事务外
        PostEvolutionTask task = postEvolutionAgentService.runEvolutionAndCreateTask(request);

        // 配置执行记录独立事务
        transactionTemplate.execute(status -> {
            PostEvolutionScheduleConfig fresh = scheduleConfigMapper.selectById(config.getId());
            if (fresh == null) {
                log.warn("定时演化配置已被删除，跳过执行记录更新: configId={}", config.getId());
                return null;
            }
            fresh.setLastRunTime(LocalDateTime.now());
            fresh.setLastTaskId(task.getId());
            fresh.setRunCount(fresh.getRunCount() != null ? fresh.getRunCount() + 1 : 1);
            scheduleConfigMapper.updateById(fresh);
            return null;
        });

        log.info("定时演化执行完成: configId={}, taskId={}", config.getId(), task.getId());
    }

    /**
     * 构建 Agent 请求
     */
    private PostEvolutionAgentRequest buildAgentRequest(PostEvolutionScheduleConfig config) {
        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();
        request.setPostId(config.getPostId());
        request.setIndustry(config.getIndustry());
        request.setBusinessDomain(config.getBusinessDomain());
        request.setIncludeWhitepaper(config.getIncludeWhitepaper() == 1);
        request.setIncludeCloudKnowledge(config.getIncludeCloudKnowledge() == 1);
        request.setIncludeMarketJd(config.getIncludeMarketJd() == 1);
        // 定时配置暂未提供知乎开关，避免定时任务隐式使用外部来源。
        request.setIncludeZhihu(false);
        return request;
    }
}
