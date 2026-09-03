package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试会话上下文支持：简历摘要、追问 RAG 上下文、Redis 备份。
 * <p>
 * 从 InterviewSessionManager（804 行）中拆分的上下文组件。
 */
@Slf4j
@Component
public class InterviewSessionContextSupport {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpResumeParseMapper resumeParseMapper;
    private final RagRetrievalService ragRetrievalService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_KEY_PREFIX_IDX = "interview:qidx:";
    private static final String REDIS_KEY_PREFIX_START = "interview:start:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(60);

    public InterviewSessionContextSupport(
            EmpVideoInterviewSessionMapper sessionMapper,
            EmpResumeParseMapper resumeParseMapper,
            @Autowired(required = false) RagRetrievalService ragRetrievalService,
            ObjectMapper objectMapper,
            @Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.sessionMapper = sessionMapper;
        this.resumeParseMapper = resumeParseMapper;
        this.ragRetrievalService = ragRetrievalService;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public String getResumeClaimForSession(Long sessionId) {
        if (resumeParseMapper == null) return null;
        try {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null || session.getEmpId() == null) return null;

            List<EmpResumeParse> resumes = resumeParseMapper.selectList(
                    Wrappers.<EmpResumeParse>lambdaQuery()
                            .eq(EmpResumeParse::getEmpId, session.getEmpId())
                            .eq(EmpResumeParse::getStatus, 2)
                            .orderByDesc(EmpResumeParse::getCreatedTime)
                            .last("LIMIT 1"));
            if (resumes.isEmpty()) return null;

            EmpResumeParse latestParse = resumes.get(0);
            if (latestParse.getAiAnalysisResult() != null) {
                try {
                    Map<String, Object> analysisResult = objectMapper.readValue(
                            latestParse.getAiAnalysisResult(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> abilities = (List<Map<String, Object>>) analysisResult.get("abilities");
                    if (abilities != null && !abilities.isEmpty()) {
                        return objectMapper.writeValueAsString(abilities);
                    }
                } catch (Exception exception) {
                    log.debug("解析简历能力声明失败，回退到简历原文: sessionId={}", sessionId, exception);
                }
            }
            String content = latestParse.getParsedContent();
            if (content == null) return null;
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "...[截断]";
            }
            return content;
        } catch (Exception e) {
            log.warn("获取简历声明失败 sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    public String buildFollowUpRagContext(Long sessionId) {
        if (ragRetrievalService == null) return "";

        try {
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null || session.getEmpId() == null) return "";

            Long empId = session.getEmpId();
            StringBuilder ctx = new StringBuilder();
            ctx.append("=== 候选人真实经历（供追问参考） ===\n");

            if (resumeParseMapper != null) {
                List<EmpResumeParse> resumes = resumeParseMapper.selectList(
                    Wrappers.<EmpResumeParse>lambdaQuery()
                        .eq(EmpResumeParse::getEmpId, empId)
                        .eq(EmpResumeParse::getStatus, 2)
                        .orderByDesc(EmpResumeParse::getCreatedTime)
                        .last("LIMIT 1"));
                if (!resumes.isEmpty() && resumes.get(0).getParsedContent() != null) {
                    String content = resumes.get(0).getParsedContent();
                    if (content.length() > 1500) {
                        content = content.substring(0, 1500) + "...[截断]";
                    }
                    ctx.append("[简历内容]\n").append(content).append("\n\n");
                }
            }

            String query = "候选人能力证据 项目经验 技术实践 empId:" + empId;
            String ragResult = ragRetrievalService.retrieveContext(query, RagScenarioEnum.INTERVIEW_FOLLOWUP, 5);
            if (ragResult != null && !ragResult.isBlank()) {
                ctx.append("[RAG知识库证据]\n").append(ragResult).append("\n");
            }

            return ctx.toString();
        } catch (Exception e) {
            log.warn("构建追问上下文失败 sessionId={}: {}", sessionId, e.getMessage());
            return "";
        }
    }

    public void backupQuestionIndexToRedis(String sessionId, int index) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX_IDX + sessionId, String.valueOf(index), REDIS_TTL);
        } catch (Exception e) {
            log.debug("Failed to backup question index to Redis: sessionId={}", sessionId, e);
        }
    }

    public void backupInterviewStartToRedis(String sessionId, LocalDateTime startTime) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX_START + sessionId, startTime.toString(), REDIS_TTL);
        } catch (Exception e) {
            log.debug("Failed to backup interview start to Redis: sessionId={}", sessionId, e);
        }
    }

    public void clearRedisBackups(String sessionId) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX_IDX + sessionId);
            stringRedisTemplate.delete(REDIS_KEY_PREFIX_START + sessionId);
        } catch (Exception e) {
            log.debug("Failed to clear Redis backups: sessionId={}", sessionId, e);
        }
    }
}
