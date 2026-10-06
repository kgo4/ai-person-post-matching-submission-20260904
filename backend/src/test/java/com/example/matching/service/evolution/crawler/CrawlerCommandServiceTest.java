package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.CrawlerSystemProperties;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.dto.evolution.api.CrawlerCommandCreateRequest;
import com.example.matching.dto.evolution.api.CrawlerCommandView;
import com.example.matching.dto.evolution.api.CrawlerTaskResultRequest;
import com.example.matching.entity.evolution.CrawlerCommand;
import com.example.matching.mapper.evolution.CrawlerCommandMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 采集命令队列的生命周期与并发语义测试（对接文档 §9 的落地实现）。
 *
 * @author system
 */
class CrawlerCommandServiceTest {

    /**
     * 生产环境里 {@code CrawlerCommand} 的 TableInfo 由 MyBatis-Plus 在启动期注册，
     * 纯 Mockito 单测没有这步，使用 {@code LambdaUpdateWrapper.set(SFunction, ...)} 时会抛
     * {@code can not find lambda cache for this entity}。这里显式初始化，保证测的是真实逻辑。
     */
    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.example.matching.entity.evolution.CrawlerCommand.class);
    }

    private final CrawlerCommandMapper commandMapper = mock(CrawlerCommandMapper.class);
    private final CrawlerAgentService agentService = mock(CrawlerAgentService.class);
    private final MarketJdCrawlerProperties properties = new MarketJdCrawlerProperties();
    private final CrawlerCommandService service = new CrawlerCommandService(
            commandMapper, agentService, properties, new CrawlerSystemProperties(), new ObjectMapper());

    @Test
    void createRejectsEmptySources() {
        CrawlerCommandCreateRequest request = new CrawlerCommandCreateRequest();
        request.setSources(List.of("  "));

        assertThatThrownBy(() -> service.create(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采集来源不能为空");
        verify(commandMapper, never()).insert(any(CrawlerCommand.class));
    }

    @Test
    void createRejectsExcessiveMaxItems() {
        CrawlerCommandCreateRequest request = new CrawlerCommandCreateRequest();
        request.setSources(List.of("jd"));
        request.setMaxItems(100000);

        assertThatThrownBy(() -> service.create(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maxItems 不能超过");
    }

    @Test
    void createGeneratesPendingCommandWithExpiryAndPayload() {
        CrawlerCommandCreateRequest request = new CrawlerCommandCreateRequest();
        request.setSources(List.of("jd", "remoteok"));
        request.setKeywords(List.of("Java"));
        request.setCities(List.of("北京"));
        request.setMaxItems(30);

        CrawlerCommandView view = service.create(request, 42L);

        ArgumentCaptor<CrawlerCommand> captor = ArgumentCaptor.forClass(CrawlerCommand.class);
        verify(commandMapper).insert(captor.capture());
        CrawlerCommand saved = captor.getValue();
        assertThat(view.getCommandId()).startsWith("cmd-");
        assertThat(saved.getStatus()).isEqualTo(CrawlerCommand.STATUS_PENDING);
        assertThat(saved.getCommandType()).isEqualTo(CrawlerCommand.TYPE_COLLECT);
        assertThat(saved.getMaxItems()).isEqualTo(30);
        assertThat(saved.getRequestedBy()).isEqualTo(42L);
        assertThat(saved.getExpireTime()).isAfter(LocalDateTime.now());
        assertThat(saved.getPayload()).contains("COLLECT").contains("remoteok");
        assertThat(view.getStatusText()).isEqualTo("待爬虫领取");
    }

    @Test
    void cancelRejectsCommandAlreadyDispatched() {
        CrawlerCommand command = command("cmd-1", CrawlerCommand.STATUS_DISPATCHED, null);
        when(commandMapper.selectOne(any())).thenReturn(command);

        assertThatThrownBy(() -> service.cancel("cmd-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有待领取的命令可以取消");
        verify(commandMapper, never()).updateById(any(CrawlerCommand.class));
    }

    @Test
    void cancelPendingMarksCancelled() {
        CrawlerCommand command = command("cmd-2", CrawlerCommand.STATUS_PENDING, null);
        when(commandMapper.selectOne(any())).thenReturn(command);

        CrawlerCommandView view = service.cancel("cmd-2");

        assertThat(view.getStatus()).isEqualTo(CrawlerCommand.STATUS_CANCELLED);
        assertThat(view.getStatusText()).isEqualTo("已取消");
        verify(commandMapper).updateById(command);
    }

    @Test
    void claimSkipsCommandsAlreadyTakenByAnotherAgent() {
        CrawlerCommand taken = command("cmd-3", CrawlerCommand.STATUS_PENDING, null);
        taken.setId(100L);
        CrawlerCommand free = command("cmd-4", CrawlerCommand.STATUS_PENDING, null);
        free.setId(101L);
        when(commandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(taken, free));
        // 第一条已被并发领走（影响行数 0），第二条领取成功
        when(commandMapper.claimIfPending(100L, "agent-1")).thenReturn(0);
        when(commandMapper.claimIfPending(101L, "agent-1")).thenReturn(1);
        CrawlerCommand claimed = command("cmd-4", CrawlerCommand.STATUS_DISPATCHED, "agent-1");
        claimed.setId(101L);
        when(commandMapper.selectById(101L)).thenReturn(claimed);

        List<CrawlerCommandView> result = service.claim("agent-1", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCommandId()).isEqualTo("cmd-4");
        verify(agentService).touchLastCommand("agent-1", "cmd-4");
        verify(commandMapper, never()).selectById(100L);
    }

    @Test
    void applyTaskResultIsIdempotentOnTerminalCommand() {
        CrawlerCommand command = command("cmd-5", CrawlerCommand.STATUS_SUCCEEDED, "agent-2");
        when(commandMapper.selectOne(any())).thenReturn(command);

        CrawlerTaskResultRequest request = new CrawlerTaskResultRequest();
        request.setCommandId("cmd-5");
        request.setAgentId("agent-2");
        request.setStatus(CrawlerCommand.STATUS_SUCCEEDED);

        CrawlerCommandView view = service.applyTaskResult(request);

        assertThat(view.getStatus()).isEqualTo(CrawlerCommand.STATUS_SUCCEEDED);
        verify(commandMapper, never()).updateById(any(CrawlerCommand.class));
    }

    @Test
    void applyTaskResultRejectsUnknownStatus() {
        when(commandMapper.selectOne(any())).thenReturn(command("cmd-6", CrawlerCommand.STATUS_DISPATCHED, null));

        CrawlerTaskResultRequest request = new CrawlerTaskResultRequest();
        request.setCommandId("cmd-6");
        request.setAgentId("agent-1");
        request.setStatus("WHATEVER");

        assertThatThrownBy(() -> service.applyTaskResult(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 只能是");
    }

    @Test
    void applyTaskResultMarksRunningThenSucceeded() {
        CrawlerCommand command = command("cmd-7", CrawlerCommand.STATUS_DISPATCHED, "agent-1");
        when(commandMapper.selectOne(any())).thenReturn(command);

        CrawlerTaskResultRequest running = new CrawlerTaskResultRequest();
        running.setCommandId("cmd-7");
        running.setAgentId("agent-1");
        running.setStatus("running");
        assertThat(service.applyTaskResult(running).getStatus()).isEqualTo(CrawlerCommand.STATUS_RUNNING);

        CrawlerTaskResultRequest done = new CrawlerTaskResultRequest();
        done.setCommandId("cmd-7");
        done.setAgentId("agent-1");
        done.setStatus("SUCCEEDED");
        done.setPushedCount(12);
        CrawlerCommandView view = service.applyTaskResult(done);

        assertThat(view.getStatus()).isEqualTo(CrawlerCommand.STATUS_SUCCEEDED);
        assertThat(view.getStatusText()).isEqualTo("抓取完成");
        assertThat(view.getResultJson()).contains("\"pushedCount\":12");
        assertThat(view.getFinishedTime()).isNotNull();
        verify(commandMapper, times(2)).updateById(command);
    }

    @Test
    void expireStaleCommandsMarksExpired() {
        when(commandMapper.update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class)))
                .thenReturn(3);

        assertThat(service.expireStaleCommands()).isEqualTo(3);
    }

    private static CrawlerCommand command(String commandId, String status, String agentId) {
        CrawlerCommand command = new CrawlerCommand();
        command.setCommandId(commandId);
        command.setStatus(status);
        command.setAgentId(agentId);
        command.setCommandType(CrawlerCommand.TYPE_COLLECT);
        command.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return command;
    }
}
