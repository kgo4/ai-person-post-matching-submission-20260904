package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.dto.evolution.api.CrawlerAgentView;
import com.example.matching.dto.evolution.api.CrawlerHeartbeatRequest;
import com.example.matching.entity.evolution.CrawlerAgent;
import com.example.matching.mapper.evolution.CrawlerAgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地爬虫实例心跳台账。
 * <p>
 * 上游对接文档 §1.1 要求主系统**不访问本地电脑**，因此「本地爬虫是否在线」只能靠爬虫主动上报的
 * 心跳判断。前端据此给出「未上线」提示，而不是让「立即抓取」按钮点了没反应。
 * <p>
 * 所有方法都必须容错：心跳与查询失败不能让采集命令功能整体不可用。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerAgentService {

    private static final int FIELD_MAX = 255;

    private final CrawlerAgentMapper agentMapper;
    private final MarketJdCrawlerProperties properties;

    /**
     * 处理一次心跳：按 {@code agentId} upsert。
     *
     * @return 是否首次注册（true = 新实例上线，用于打一条 info 日志）
     */
    @Transactional
    public boolean heartbeat(CrawlerHeartbeatRequest request) {
        LocalDateTime now = LocalDateTime.now();
        CrawlerAgent existing = agentMapper.selectOne(new LambdaQueryWrapper<CrawlerAgent>()
                .eq(CrawlerAgent::getAgentId, request.getAgentId())
                .last("LIMIT 1"));
        if (existing == null) {
            CrawlerAgent agent = new CrawlerAgent();
            agent.setAgentId(truncate(request.getAgentId(), FIELD_MAX));
            agent.setAgentName(truncate(request.getAgentName(), FIELD_MAX));
            agent.setHostInfo(truncate(request.getHostInfo(), FIELD_MAX));
            agent.setAgentVersion(truncate(request.getVersion(), FIELD_MAX));
            agent.setLastHeartbeatTime(now);
            agent.setUpdatedTime(now);
            agentMapper.insert(agent);
            log.info("本地爬虫首次上报心跳: agentId={}, name={}, version={}",
                    request.getAgentId(), request.getAgentName(), request.getVersion());
            return true;
        }
        existing.setAgentName(truncate(request.getAgentName(), FIELD_MAX));
        existing.setHostInfo(truncate(request.getHostInfo(), FIELD_MAX));
        existing.setAgentVersion(truncate(request.getVersion(), FIELD_MAX));
        existing.setLastHeartbeatTime(now);
        existing.setUpdatedTime(now);
        agentMapper.updateById(existing);
        return false;
    }

    /** 记录某实例最近领取的命令，便于运维定位「命令给了谁」。 */
    @Transactional
    public void touchLastCommand(String agentId, String commandId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        try {
            CrawlerAgent existing = agentMapper.selectOne(new LambdaQueryWrapper<CrawlerAgent>()
                    .eq(CrawlerAgent::getAgentId, agentId)
                    .last("LIMIT 1"));
            if (existing == null) {
                return;
            }
            existing.setLastCommandId(truncate(commandId, FIELD_MAX));
            existing.setUpdatedTime(LocalDateTime.now());
            agentMapper.updateById(existing);
        } catch (Exception exception) {
            log.warn("回写爬虫最近命令失败: agentId={}, commandId={}", agentId, commandId, exception);
        }
    }

    /** 全部实例（含离线），按最近心跳倒序。 */
    public List<CrawlerAgentView> listAgents() {
        List<CrawlerAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<CrawlerAgent>()
                .orderByDesc(CrawlerAgent::getLastHeartbeatTime)
                .last("LIMIT 50"));
        return agents.stream().map(this::toView).toList();
    }

    /** 在线上报的实例数。 */
    public int onlineCount() {
        LocalDateTime threshold = onlineThreshold();
        Long count = agentMapper.selectCount(new LambdaQueryWrapper<CrawlerAgent>()
                .ge(CrawlerAgent::getLastHeartbeatTime, threshold));
        return count == null ? 0 : count.intValue();
    }

    /** 全部实例中最近一次心跳时间；从未上报返回 null。 */
    public LocalDateTime lastHeartbeatTime() {
        CrawlerAgent latest = agentMapper.selectOne(new LambdaQueryWrapper<CrawlerAgent>()
                .orderByDesc(CrawlerAgent::getLastHeartbeatTime)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getLastHeartbeatTime();
    }

    private CrawlerAgentView toView(CrawlerAgent entity) {
        CrawlerAgentView view = new CrawlerAgentView();
        view.setAgentId(entity.getAgentId());
        view.setAgentName(entity.getAgentName());
        view.setHostInfo(entity.getHostInfo());
        view.setAgentVersion(entity.getAgentVersion());
        view.setLastHeartbeatTime(entity.getLastHeartbeatTime());
        view.setLastCommandId(entity.getLastCommandId());
        if (entity.getLastHeartbeatTime() != null) {
            long seconds = Duration.between(entity.getLastHeartbeatTime(), LocalDateTime.now()).getSeconds();
            view.setSecondsSinceHeartbeat(Math.max(seconds, 0L));
            view.setOnline(seconds <= properties.getAgentOnlineThresholdSeconds());
        } else {
            view.setOnline(false);
        }
        return view;
    }

    private LocalDateTime onlineThreshold() {
        return LocalDateTime.now().minusSeconds(properties.getAgentOnlineThresholdSeconds());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}


