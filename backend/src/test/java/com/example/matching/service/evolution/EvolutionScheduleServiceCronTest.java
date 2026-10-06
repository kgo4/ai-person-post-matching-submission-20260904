package com.example.matching.service.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.evolution.PostEvolutionScheduleConfig;
import com.example.matching.mapper.evolution.PostEvolutionScheduleConfigMapper;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.service.evolution.impl.EvolutionScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * M2/M3 行为测试：cron 表达式按 next(lastRunTime) <= now 判断执行；
 * 保存配置时校验 cron 合法性（非法 cron 直接拒绝）。
 */
class EvolutionScheduleServiceCronTest {

    private EvolutionScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        PostEvolutionScheduleConfigMapper configMapper = mock(PostEvolutionScheduleConfigMapper.class);
        DistributedLockService lockService = mock(DistributedLockService.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new EvolutionScheduleServiceImpl(configMapper,
                mock(com.example.matching.service.evolution.PostEvolutionAgentService.class),
                lockService, transactionTemplate);
    }

    private PostEvolutionScheduleConfig config(String cron, LocalDateTime lastRun) {
        PostEvolutionScheduleConfig config = new PostEvolutionScheduleConfig();
        config.setId(1L);
        config.setPostId(1L);
        config.setCronExpression(cron);
        config.setLastRunTime(lastRun);
        return config;
    }

    @Test
    void weeklyCronDoesNotFireDaily() {
        // 每周一 0 点执行；上次运行刚刚过去的上周一 → next 是下周一，不应执行
        java.time.DayOfWeek today = LocalDateTime.now().getDayOfWeek();
        LocalDateTime lastMonday = LocalDateTime.now()
                .with(java.time.temporal.ChronoField.DAY_OF_WEEK, today.getValue())
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        PostEvolutionScheduleConfig config = config("0 0 0 * * MON", lastMonday);
        Boolean should = ReflectionTestUtils.invokeMethod(service, "shouldExecute", config);
        // next(lastMonday) = 下周一 0:00 > now，不应执行
        assertThat(should).isFalse();
    }

    @Test
    void hourlyCronFiresAfterOneHour() {
        // 每小时执行；上次运行 61 分钟前 → 应执行
        PostEvolutionScheduleConfig config = config("0 0 * * * ?", LocalDateTime.now().minusMinutes(61));
        Boolean should = ReflectionTestUtils.invokeMethod(service, "shouldExecute", config);
        assertThat(should).isTrue();
    }

    @Test
    void neverRunConfigAlwaysExecutes() {
        PostEvolutionScheduleConfig config = config("0 0 2 * * ?", null);
        Boolean should = ReflectionTestUtils.invokeMethod(service, "shouldExecute", config);
        assertThat(should).isTrue();
    }

    @Test
    void invalidCronIsRejectedWhenSavingConfig() {
        com.example.matching.dto.evolution.EvolutionScheduleConfigDTO dto =
                new com.example.matching.dto.evolution.EvolutionScheduleConfigDTO();
        dto.setPostId(1L);
        dto.setCronExpression("not-a-cron");

        assertThatThrownBy(() -> service.createConfig(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cron 表达式不合法");
    }
}
