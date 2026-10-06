package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试对话状态管理服务
 * <p>
 * 负责当前会话处于主问题、追问、评估、下一题、结束中的哪个阶段。
 * 使用内存 ConcurrentHashMap 存储，同时持久化到 emp_video_interview_session.conversation_state。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewConversationStateService {

    private final EmpVideoInterviewSessionMapper sessionMapper;

    /** 内存状态存储：sessionId -> 当前状态 */
    private final ConcurrentHashMap<Long, InterviewConversationState> stateMap = new ConcurrentHashMap<>();

    /**
     * 获取当前状态
     */
    public InterviewConversationState getState(Long sessionId) {
        InterviewConversationState state = stateMap.get(sessionId);
        if (state == null) {
            // 从数据库恢复
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session != null && session.getConversationState() != null) {
                try {
                    state = InterviewConversationState.valueOf(session.getConversationState());
                } catch (IllegalArgumentException e) {
                    state = InterviewConversationState.PRESET_QUESTION;
                }
            } else {
                state = InterviewConversationState.PRESET_QUESTION;
            }
            stateMap.put(sessionId, state);
        }
        return state;
    }

    /**
     * 状态转换（带校验）
     *
     * @param sessionId 会话ID
     * @param from      期望的当前状态（null 表示不校验）
     * @param to        目标状态
     */
    public boolean transition(Long sessionId, InterviewConversationState from, InterviewConversationState to) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("状态转换失败，会话不存在，sessionId={}, target={}", sessionId, to);
            return false;
        }

        InterviewConversationState current = parseState(session.getConversationState());
        if (from != null && current != from) {
            log.warn("状态转换校验失败，sessionId={}, 期望={}, 实际={}, 目标={}",
                    sessionId, from, current, to);
            return false;
        }

        long version = session.getSessionVersion() == null ? 0L : session.getSessionVersion();
        int updated = sessionMapper.compareAndSetConversationState(
                sessionId, session.getConversationState(), version, to.name());
        if (updated != 1) {
            log.warn("状态转换冲突，sessionId={}, state={}, version={}, target={}",
                    sessionId, current, version, to);
            return false;
        }

        stateMap.put(sessionId, to);
        log.info("会话状态转换，sessionId={}, {} -> {}", sessionId, current, to);
        return true;
    }

    /**
     * 初始化状态（面试开始时调用）
     */
    public void initState(Long sessionId) {
        stateMap.put(sessionId, InterviewConversationState.PRESET_QUESTION);
        persistState(sessionId, InterviewConversationState.PRESET_QUESTION);
    }

    /**
     * 清理状态（面试结束时调用）
     */
    public void clearState(Long sessionId) {
        stateMap.remove(sessionId);
    }

    /**
     * 持久化状态到数据库
     */
    private void persistState(Long sessionId, InterviewConversationState state) {
        try {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setConversationState(state.name());
                sessionMapper.updateById(session);
            }
        } catch (Exception e) {
            log.warn("持久化会话状态失败，sessionId={}, state={}: {}", sessionId, state, e.getMessage());
        }
    }

    private InterviewConversationState parseState(String value) {
        if (value == null) {
            return InterviewConversationState.PRESET_QUESTION;
        }
        try {
            return InterviewConversationState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return InterviewConversationState.PRESET_QUESTION;
        }
    }
}
