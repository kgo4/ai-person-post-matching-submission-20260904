package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试会话状态支持：问题推进、状态机转换、会话持久化。
 * <p>
 * 从 InterviewSessionManager（804 行）中拆分的状态组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSessionStateSupport {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final InterviewConversationStateService stateService;
    private final AbilityTagMapper abilityTagMapper;
    private final ObjectMapper objectMapper;

    private final Map<String, Integer> questionIndex = new ConcurrentHashMap<>();
    public void persistInterviewStartedAt(Long sessionId, LocalDateTime interviewStartedAt) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setInterviewStartedAt(interviewStartedAt);
            sessionMapper.updateById(session);
        }
    }

    public void persistQuestionAwaitingRead(Long sessionId, Integer questionOrder) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalStateException("面试会话不存在");
        }
        session.setCurrentQuestionOrder(questionOrder);
        session.setQuestionStartedAt(null);
        session.setQuestionDeadlineAt(null);
        sessionMapper.updateById(session);
    }

    public int findQuestionIndex(List<EmpVideoInterviewQuestion> questions, Integer questionOrder) {
        for (int index = 0; index < questions.size(); index++) {
            if (questionOrder.equals(questions.get(index).getQuestionOrder())) {
                return index;
            }
        }
        return -1;
    }

    public Integer resolveCurrentQuestionIndex(Long sessionId, String sessionKey,
                                                List<EmpVideoInterviewQuestion> questions) {
        Integer index = questionIndex.get(sessionKey);
        if (index != null) {
            return index;
        }
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getCurrentQuestionOrder() == null) {
            return null;
        }
        int restoredIndex = findQuestionIndex(questions, session.getCurrentQuestionOrder());
        if (restoredIndex < 0) {
            return null;
        }
        questionIndex.put(sessionKey, restoredIndex);
        return restoredIndex;
    }

    public void requireStateTransition(Long sessionId, InterviewConversationState from,
                                         InterviewConversationState to) {
        if (!stateService.transition(sessionId, from, to)) {
            throw new IllegalStateException("面试会话状态已变更，请刷新后重试");
        }
    }

    public List<EmpVideoInterviewQuestion> loadQuestions(Long sessionId) {
        return questionMapper.selectList(
                Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
        );
    }

    public void recordQuestionEnd(Long sessionId, int index, String endedBy) {
        List<EmpVideoInterviewQuestion> questions = loadQuestions(sessionId);
        if (index < questions.size()) {
            EmpVideoInterviewQuestion question = questions.get(index);
            if (question.getEndSecond() != null) {
                return;
            }
            question.setEndSecond((int) (System.currentTimeMillis() / 1000));
            question.setEndedBy(endedBy);
            questionMapper.updateById(question);
        }
    }

    public String[] getAbilityInfo(EmpVideoInterviewQuestion question) {
        String abilityName = "通用能力";
        String abilityRequirement = "无特定要求";

        String tagsJson = question.getExpectedTagsJson();
        if (tagsJson != null && !tagsJson.isBlank()) {
            try {
                List<?> tagIds = objectMapper.readValue(tagsJson, List.class);
                if (!tagIds.isEmpty()) {
                    Object firstTag = tagIds.get(0);
                    Long tagId = null;
                    if (firstTag instanceof Number n) {
                        tagId = n.longValue();
                    }
                    if (tagId != null) {
                        AbilityTag tag = abilityTagMapper.selectById(tagId);
                        if (tag != null) {
                            abilityName = tag.getTagName();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return new String[]{abilityName, abilityRequirement};
    }

    public String getAbilityName(Long tagId) {
        if (tagId == null) return "通用能力";
        AbilityTag tag = abilityTagMapper.selectById(tagId);
        return tag != null ? tag.getTagName() : "通用能力";
    }

    public void putQuestionIndex(String sessionKey, int index) {
        questionIndex.put(sessionKey, index);
    }

    public Integer getQuestionIndex(String sessionKey) {
        return questionIndex.get(sessionKey);
    }

    public void removeQuestionIndex(String sessionKey) {
        questionIndex.remove(sessionKey);
    }

    public boolean containsQuestionIndex(String sessionKey) {
        return questionIndex.containsKey(sessionKey);
    }
}