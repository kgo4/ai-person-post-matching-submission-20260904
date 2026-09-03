package com.example.matching.schedule;

import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.system.PostAbilityTagGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 历史岗位能力标签治理补偿任务。
 * 只扫描 tagId 为空的岗位能力，不改变岗位能力主字段和全景图谱逻辑。
 */
@Slf4j
@Component
public class PostAbilityTagGovernanceBackfillScheduler {

    private final PostQueryPort postQueryPort;
    private final ApplicationEventPublisher eventPublisher;
    private final PostAbilityTagGovernanceService governanceService;

    @Autowired
    public PostAbilityTagGovernanceBackfillScheduler(PostQueryPort postQueryPort,
                                                     ApplicationEventPublisher eventPublisher,
                                                     PostAbilityTagGovernanceService governanceService) {
        this.postQueryPort = postQueryPort;
        this.eventPublisher = eventPublisher;
        this.governanceService = governanceService;
    }

    /** 兼容已有单元测试构造方式。 */
    public PostAbilityTagGovernanceBackfillScheduler(PostQueryPort postQueryPort,
                                                     ApplicationEventPublisher eventPublisher) {
        this(postQueryPort, eventPublisher, event -> { });
    }

    @Value("${tag-governance.backfill.enabled:true}")
    private boolean enabled;

    @Value("${tag-governance.backfill.batch-size:500}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${tag-governance.backfill.fixed-delay-ms:600000}", initialDelay = 30000)
    public void scan() {
        if (!enabled) return;
        runOnce();
    }

    /** 执行一批历史岗位能力回填；可由管理端手动触发，便于首次建设标签库。 */
    public int runOnce() {
        int published = 0;
        try {
            for (PostQueryPort.PostAbilityDTO ability : postQueryPort.listUntaggedPostAbilityModels(Math.max(1, batchSize))) {
                if (ability == null || ability.postId() == null
                        || !isGovernableAbilityName(ability.abilityName())) continue;
                // 这是非 AI 的旁路同步，直接执行可以避免事件线程/AI 线程拥塞导致标签库长期为空。
                governanceService.govern(new PostAbilityTagGovernanceRequestedEvent(
                        ability.postId(), ability.abilityName(), "TECHNICAL", "JD_IMPORT", ability.id(),
                        ability.remark(), "历史岗位能力补偿治理"));
                published++;
            }
            if (published > 0) log.info("历史岗位能力标签治理事件已发布: count={}", published);
        } catch (Exception e) {
            log.warn("历史岗位能力标签治理扫描失败，不影响岗位主流程: error={}", e.getMessage());
        }
        return published;
    }

    private boolean isGovernableAbilityName(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return false;
        String normalized = abilityName.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.matches("能力#?(null|未命名能力)?")
                && !normalized.equals("未命名能力")
                && !normalized.equals("unknown")
                && !normalized.equals("null");
    }
}
