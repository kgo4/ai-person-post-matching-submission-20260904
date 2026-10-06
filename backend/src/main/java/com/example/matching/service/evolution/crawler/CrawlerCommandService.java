package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.config.CrawlerSystemProperties;
import com.example.matching.dto.evolution.api.CrawlerAvailabilityView;
import com.example.matching.dto.evolution.api.CrawlerCommandCreateRequest;
import com.example.matching.dto.evolution.api.CrawlerCommandView;
import com.example.matching.dto.evolution.api.CrawlerTaskResultRequest;
import com.example.matching.entity.evolution.CrawlerCommand;
import com.example.matching.mapper.evolution.CrawlerCommandMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 采集命令队列。
 * <p>
 * 上游对接文档 §1.1 明确「主系统不能、也不需要访问本地电脑」，因此采集成败不再由主系统反向调用
 * 本地 8081 决定，而是：
 * <pre>
 * 管理端创建命令(PENDING) → 本地爬虫轮询领取(DISPATCHED) → 执行(RUNNING) → 回传结果(SUCCEEDED/FAILED)
 * </pre>
 * 本服务是这条链路的唯一权威实现，替代原 {@code CrawlerSystemClient} 的反向代理方向。
 * <p>
 * 并发安全：多个 agent 可能同时轮询，{@link CrawlerCommandMapper#claimIfPending} 用
 * 「条件更新 + 影响行数」保证一条命令只会被一个 agent 领走。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerCommandService {

    private static final DateTimeFormatter COMMAND_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 单条命令允许的抓取上限硬上限，避免误填 100000 打爆本地爬虫与接收接口 */
    private static final int MAX_ITEMS_HARD_LIMIT = 500;

    private static final int DEFAULT_MAX_ITEMS = 50;

    private static final int SOURCE_MAX = 8;
    private static final int KEYWORD_MAX = 20;
    private static final int CITY_MAX = 20;

    private final CrawlerCommandMapper commandMapper;
    private final CrawlerAgentService agentService;
    private final MarketJdCrawlerProperties properties;
    private final CrawlerSystemProperties crawlerSystemProperties;
    private final ObjectMapper objectMapper;

    // ===== 管理端 =====

    /**
     * 采集通道可用性：命令队列是否接入、是否有在线爬虫、以及过渡期反向代理开关状态。
     * <p>
     * 前端据此决定「下发采集命令」是否可点，并在无在线爬虫时给出明确指引，
     * 而不是让用户点了一个永远不会被执行的按钮。
     */
    public CrawlerAvailabilityView availability() {
        CrawlerAvailabilityView view = new CrawlerAvailabilityView();
        view.setQueueAvailable(properties.isApiKeyConfigured());
        view.setAgentsOnline(agentService.onlineCount());
        view.setLastHeartbeatTime(agentService.lastHeartbeatTime());
        view.setLegacyProxyEnabled(crawlerSystemProperties.isLegacyProxyEnabled());
        return view;
    }

    /**
     * 创建采集命令（{@code PENDING}），等待本地爬虫轮询领取。
     *
     * @param requestedBy 发起人 sys_user.id，可为 null
     */
    @Transactional
    public CrawlerCommandView create(CrawlerCommandCreateRequest request, Long requestedBy) {
        List<String> sources = normalizeList(request.getSources(), SOURCE_MAX);
        if (sources.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "采集来源不能为空");
        }
        List<String> keywords = normalizeList(request.getKeywords(), KEYWORD_MAX);
        List<String> cities = normalizeList(request.getCities(), CITY_MAX);
        int maxItems = resolveMaxItems(request.getMaxItems());

        String commandId = generateCommandId();
        LocalDateTime now = LocalDateTime.now();
        CrawlerCommand command = new CrawlerCommand();
        command.setCommandId(commandId);
        command.setAgentId(blankToNull(request.getAgentId()));
        command.setCommandType(CrawlerCommand.TYPE_COLLECT);
        command.setSources(writeJson(sources));
        command.setKeywords(writeJson(keywords));
        command.setCities(writeJson(cities));
        command.setMaxItems(maxItems);
        command.setStatus(CrawlerCommand.STATUS_PENDING);
        command.setPayload(writeJson(buildPayload(commandId, sources, keywords, cities, maxItems)));
        command.setRequestedBy(requestedBy);
        command.setExpireTime(now.plusSeconds(properties.getCommandExpireSeconds()));
        commandMapper.insert(command);
        log.info("采集命令已创建: commandId={}, sources={}, keywords={}, cities={}, maxItems={}, expireTime={}",
                commandId, sources, keywords, cities, maxItems, command.getExpireTime());
        return toView(command);
    }

    /** 最近命令（倒序），用于管理端列表轮询。 */
    @Transactional
    public List<CrawlerCommandView> list(int limit) {
        expireStaleCommands();
        int safeLimit = limit < 1 ? 20 : Math.min(limit, 100);
        List<CrawlerCommand> commands = commandMapper.selectList(new LambdaQueryWrapper<CrawlerCommand>()
                .orderByDesc(CrawlerCommand::getId)
                .last("LIMIT " + safeLimit));
        return commands.stream().map(this::toView).toList();
    }

    /** 单条命令状态；不存在返回 404。 */
    @Transactional
    public CrawlerCommandView get(String commandId) {
        expireStaleCommands();
        return toView(requireCommand(commandId));
    }

    /** 取消尚未被领取的命令；已被领取/已结束返回 409。 */
    @Transactional
    public CrawlerCommandView cancel(String commandId) {
        CrawlerCommand command = requireCommand(commandId);
        if (!CrawlerCommand.STATUS_PENDING.equals(command.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "命令当前状态为「" + statusText(command.getStatus()) + "」，只有待领取的命令可以取消");
        }
        command.setStatus(CrawlerCommand.STATUS_CANCELLED);
        command.setFinishedTime(LocalDateTime.now());
        command.setErrorMessage("已被管理端取消");
        commandMapper.updateById(command);
        log.info("采集命令已取消: commandId={}", commandId);
        return toView(command);
    }

    // ===== 本地爬虫（内部接口） =====

    /**
     * 领取待执行命令：原子置 {@code DISPATCHED}，避免多实例重复执行。
     *
     * @param agentId 请求方实例标识
     * @return 领取到的命令（可能为空列表）
     */
    @Transactional
    public List<CrawlerCommandView> claim(String agentId, int limit) {
        expireStaleCommands();
        int safeLimit = limit < 1 ? 1 : Math.min(limit, 10);
        List<CrawlerCommand> candidates = commandMapper.selectList(new LambdaQueryWrapper<CrawlerCommand>()
                .eq(CrawlerCommand::getStatus, CrawlerCommand.STATUS_PENDING)
                .and(wrapper -> wrapper.isNull(CrawlerCommand::getAgentId)
                        .or().eq(CrawlerCommand::getAgentId, agentId))
                .orderByAsc(CrawlerCommand::getId)
                .last("LIMIT " + safeLimit));

        List<CrawlerCommandView> claimed = new ArrayList<>();
        for (CrawlerCommand candidate : candidates) {
            if (commandMapper.claimIfPending(candidate.getId(), agentId) == 1) {
                CrawlerCommand fresh = commandMapper.selectById(candidate.getId());
                if (fresh != null) {
                    agentService.touchLastCommand(agentId, fresh.getCommandId());
                    claimed.add(toView(fresh));
                    log.info("采集命令已下发: commandId={}, agentId={}", fresh.getCommandId(), agentId);
                }
            }
        }
        return claimed;
    }

    /**
     * 回传命令执行结果。
     * <p>
     * 幂等：命令已处于终态时不报错，直接返回当前状态——爬虫补报/重报不会造成失败。
     */
    @Transactional
    public CrawlerCommandView applyTaskResult(CrawlerTaskResultRequest request) {
        CrawlerCommand command = requireCommand(request.getCommandId());
        String targetStatus = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        switch (targetStatus) {
            case CrawlerCommand.STATUS_RUNNING -> {
                if (isTerminal(command.getStatus())) {
                    log.warn("忽略命令终态后的 RUNNING 上报: commandId={}, current={}",
                            request.getCommandId(), command.getStatus());
                    return toView(command);
                }
                if (command.getDispatchedTime() == null) {
                    command.setDispatchedTime(LocalDateTime.now());
                }
                command.setStatus(CrawlerCommand.STATUS_RUNNING);
            }
            case CrawlerCommand.STATUS_SUCCEEDED, CrawlerCommand.STATUS_FAILED -> {
                if (isTerminal(command.getStatus())) {
                    log.info("命令已处于终态，忽略重复上报: commandId={}, current={}",
                            request.getCommandId(), command.getStatus());
                    return toView(command);
                }
                command.setStatus(targetStatus);
                command.setFinishedTime(LocalDateTime.now());
                if (CrawlerCommand.STATUS_FAILED.equals(targetStatus)) {
                    command.setErrorMessage(truncate(blankToNull(request.getMessage()), 500));
                }
            }
            default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "status 只能是 RUNNING、SUCCEEDED 或 FAILED");
        }
        command.setResultJson(writeJson(buildResult(request, command.getStatus())));
        commandMapper.updateById(command);
        log.info("采集命令结果已回传: commandId={}, agentId={}, status={}, pushedCount={}, failedCount={}",
                request.getCommandId(), request.getAgentId(), command.getStatus(),
                request.getPushedCount(), request.getFailedCount());
        return toView(command);
    }

    /** 过期未领取的命令标记为 {@code EXPIRED}（幂等，返回受影响行数）。 */
    @Transactional
    public int expireStaleCommands() {
        try {
            return commandMapper.update(null, new LambdaUpdateWrapper<CrawlerCommand>()
                    .eq(CrawlerCommand::getStatus, CrawlerCommand.STATUS_PENDING)
                    .lt(CrawlerCommand::getExpireTime, LocalDateTime.now())
                    .set(CrawlerCommand::getStatus, CrawlerCommand.STATUS_EXPIRED)
                    .set(CrawlerCommand::getFinishedTime, LocalDateTime.now())
                    .set(CrawlerCommand::getErrorMessage, "超过有效期仍未被本地爬虫领取"));
        } catch (Exception exception) {
            log.warn("采集命令过期处理失败（不影响查询）", exception);
            return 0;
        }
    }

    // ===== 内部 =====

    private CrawlerCommand requireCommand(String commandId) {
        if (commandId == null || commandId.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "commandId 不能为空");
        }
        CrawlerCommand command = commandMapper.selectOne(new LambdaQueryWrapper<CrawlerCommand>()
                .eq(CrawlerCommand::getCommandId, commandId)
                .last("LIMIT 1"));
        if (command == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "采集命令不存在：" + commandId);
        }
        return command;
    }

    private static boolean isTerminal(String status) {
        return CrawlerCommand.STATUS_SUCCEEDED.equals(status)
                || CrawlerCommand.STATUS_FAILED.equals(status)
                || CrawlerCommand.STATUS_CANCELLED.equals(status)
                || CrawlerCommand.STATUS_EXPIRED.equals(status);
    }

    private int resolveMaxItems(Integer requested) {
        if (requested == null) {
            return Math.min(DEFAULT_MAX_ITEMS, properties.getMaxItems());
        }
        if (requested < 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "maxItems 必须大于 0");
        }
        if (requested > MAX_ITEMS_HARD_LIMIT) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "maxItems 不能超过 " + MAX_ITEMS_HARD_LIMIT + "，当前 " + requested);
        }
        return requested;
    }

    private String generateCommandId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "cmd-" + LocalDateTime.now().format(COMMAND_ID_TIME)
                    + "-" + randomSuffix();
            Long count = commandMapper.selectCount(new LambdaQueryWrapper<CrawlerCommand>()
                    .eq(CrawlerCommand::getCommandId, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        // 极小概率连续撞号：退化为纳秒时间戳，保证不会返回重复值。
        return "cmd-" + LocalDateTime.now().format(COMMAND_ID_TIME) + "-" + System.nanoTime();
    }

    private static String randomSuffix() {
        int value = ThreadLocalRandom.current().nextInt(36 * 36);
        return String.format("%02d", value);
    }

    private Map<String, Object> buildPayload(String commandId, List<String> sources, List<String> keywords,
                                             List<String> cities, int maxItems) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", commandId);
        payload.put("type", CrawlerCommand.TYPE_COLLECT);
        payload.put("sources", sources);
        payload.put("keywords", keywords);
        payload.put("cities", cities);
        payload.put("maxItems", maxItems);
        return payload;
    }

    private Map<String, Object> buildResult(CrawlerTaskResultRequest request, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("taskId", request.getTaskId());
        result.put("pushedCount", request.getPushedCount());
        result.put("failedCount", request.getFailedCount());
        result.put("message", request.getMessage());
        result.put("reportedAt", LocalDateTime.now().toString());
        return result;
    }

    private CrawlerCommandView toView(CrawlerCommand entity) {
        CrawlerCommandView view = new CrawlerCommandView();
        view.setCommandId(entity.getCommandId());
        view.setCommandType(entity.getCommandType());
        view.setAgentId(entity.getAgentId());
        view.setSources(readStringList(entity.getSources()));
        view.setKeywords(readStringList(entity.getKeywords()));
        view.setCities(readStringList(entity.getCities()));
        view.setMaxItems(entity.getMaxItems());
        view.setStatus(entity.getStatus());
        view.setStatusText(statusText(entity.getStatus()));
        view.setResultJson(entity.getResultJson());
        view.setErrorMessage(entity.getErrorMessage());
        view.setCreatedTime(entity.getCreatedTime());
        view.setDispatchedTime(entity.getDispatchedTime());
        view.setFinishedTime(entity.getFinishedTime());
        view.setExpireTime(entity.getExpireTime());
        return view;
    }

    static String statusText(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case CrawlerCommand.STATUS_PENDING -> "待爬虫领取";
            case CrawlerCommand.STATUS_DISPATCHED -> "已下发给爬虫";
            case CrawlerCommand.STATUS_RUNNING -> "抓取中";
            case CrawlerCommand.STATUS_SUCCEEDED -> "抓取完成";
            case CrawlerCommand.STATUS_FAILED -> "抓取失败";
            case CrawlerCommand.STATUS_EXPIRED -> "已过期";
            case CrawlerCommand.STATUS_CANCELLED -> "已取消";
            default -> status;
        };
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            log.warn("采集命令 JSON 字段解析失败，按空列表处理: value={}", json);
            return List.of();
        }
    }

    private static List<String> normalizeList(List<String> values, int max) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = blankToNull(value);
            if (trimmed != null && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
            if (normalized.size() >= max) {
                break;
            }
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "命令内容序列化失败");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}


