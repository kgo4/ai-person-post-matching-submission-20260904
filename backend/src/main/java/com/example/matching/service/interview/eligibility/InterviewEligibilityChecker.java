package com.example.matching.service.interview.eligibility;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.service.interview.AIInterviewAgent.InterviewEligibilityCheck;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEligibilityChecker {

    private final EmpResumeParseMapper resumeParseMapper;
    private final com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper interviewSessionMapper;
    private final ObjectMapper objectMapper;

    public InterviewEligibilityCheck checkInterviewEligibility(Long empId) {
        log.info("检查候选人面试资格，empId={}", empId);

        // 修复：同一员工存在进行中/待开始的面试会话时拒绝重复开新面试
        // （原 isInterviewInProgress 逻辑存在但无调用方，同人多会话并发会画像融合乱序）
        Long activeSessionId = findActiveSession(empId);
        if (activeSessionId != null) {
            return new InterviewEligibilityCheck(
                    false,
                    "该候选人已有进行中的面试（sessionId=" + activeSessionId + "），请先完成或结束该面试",
                    null, null, null, null
            );
        }

        List<EmpResumeParse> resumeParses = resumeParseMapper.selectList(
                Wrappers.<EmpResumeParse>lambdaQuery()
                        .eq(EmpResumeParse::getEmpId, empId)
                        .orderByDesc(EmpResumeParse::getCreatedTime)
        );

        if (resumeParses.isEmpty()) {
            return new InterviewEligibilityCheck(
                    false,
                    "候选人没有上传简历，请先上传简历后再进入AI面试",
                    null, null, null, null
            );
        }

        EmpResumeParse latestParse = resumeParses.get(0);
        if (latestParse.getStatus() != 2) {
            return new InterviewEligibilityCheck(
                    false,
                    "候选人简历尚未完成解析，请等待解析完成后再进入AI面试",
                    null, null, null, null
            );
        }

        String resumeText = latestParse.getParsedContent();
        String resumeStructuredData = latestParse.getAiAnalysisResult();

        String resumeAbilityClaims = extractAbilityClaimsFromResume(latestParse);

        return new InterviewEligibilityCheck(
                true,
                null,
                latestParse.getId(),
                resumeText,
                resumeStructuredData,
                resumeAbilityClaims
        );
    }

    /**
     * 查询员工是否存在进行中(1=题目已生成, 2=面试中, 4=分析中)的面试会话。
     */
    private Long findActiveSession(Long empId) {
        try {
            com.example.matching.entity.employee.EmpVideoInterviewSession active = interviewSessionMapper.selectOne(
                    Wrappers.<com.example.matching.entity.employee.EmpVideoInterviewSession>lambdaQuery()
                            .eq(com.example.matching.entity.employee.EmpVideoInterviewSession::getEmpId, empId)
                            .in(com.example.matching.entity.employee.EmpVideoInterviewSession::getStatus, 1, 2, 4)
                            .orderByDesc(com.example.matching.entity.employee.EmpVideoInterviewSession::getCreatedTime)
                            .last("LIMIT 1"));
            return active != null ? active.getId() : null;
        } catch (Exception e) {
            log.warn("查询进行中面试会话失败: empId={}, error={}", empId, e.getMessage());
            return null;
        }
    }

    private String extractAbilityClaimsFromResume(EmpResumeParse resumeParse) {
        if (resumeParse.getAiAnalysisResult() == null) {
            return "[]";
        }

        try {
            Map<String, Object> analysisResult = objectMapper.readValue(
                    resumeParse.getAiAnalysisResult(),
                    new TypeReference<Map<String, Object>>() {}
            );

            List<Map<String, Object>> abilities = (List<Map<String, Object>>) analysisResult.get("abilities");
            if (abilities == null) {
                return "[]";
            }

            return objectMapper.writeValueAsString(abilities);
        } catch (Exception e) {
            log.warn("提取简历能力声称失败: {}", e.getMessage());
            return "[]";
        }
    }
}
