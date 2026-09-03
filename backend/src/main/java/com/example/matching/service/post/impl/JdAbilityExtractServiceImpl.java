package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;
import com.example.matching.entity.post.JdImportTask;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.JdImportTaskMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.post.JdAbilityExtractService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JD能力提取服务实现（遗留入口）
 * <p>
 * 职责：管理JD导入任务的生命周期（创建任务、记录结果），
 * 核心能力提取逻辑委托给 {@link PostCapabilityGenerationService}。
 * <p>
 * 仅作为遗留 HTTP 门面保留：能力提取与岗位摘要均来自
 * PostCapabilityGenerationService -> PostCapabilityExtractionSupport ->
 * PostAbilityAgentService 单条 Agent 链路（post-ability-extract-system.txt 单一 prompt），
 * 不再发起第二次 LLM 调用（旧 job-summary-extract-prompt.ftl 已弃用）。
 * 生产使用归零后可整体删除本类与 JdAbilityExtractService 接口。
 *
 * @deprecated 岗位能力提取统一入口为 PostCapabilityGenerationService / PostCapabilityExtractionSupport
 */
@Deprecated(forRemoval = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class JdAbilityExtractServiceImpl implements JdAbilityExtractService {

    private final PostCapabilityGenerationService capabilityGenerationService;
    private final PostPostMapper postPostMapper;
    private final JdImportTaskMapper jdImportTaskMapper;
    private final AbilityTagService abilityTagService;

    @Override
    @SuppressWarnings("unchecked")
    public JdAnalyzeResponseDTO analyzeJd(Long postId, String jdText) {
        // 遗留入口调用计数：归零一个发布周期后可删除旧门面
        log.warn("[LEGACY_JD_EXTRACT] 旧 JD 提取入口被调用，统一委托岗位能力 Agent 链路: postId={}", postId);

        // 1. 校验岗位存在
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCodeEnum.POST_NOT_FOUND);
        }

        // 2. 创建分析任务记录
        JdImportTask task = new JdImportTask();
        task.setPostId(postId);
        task.setJdSourceType("PASTE");
        task.setJdRawText(jdText);
        task.setAnalysisStatus(1); // 分析中
        jdImportTaskMapper.insert(task);

        try {
            // 3. 调用统一能力生成服务（带Harness来源上下文，确保审计日志准确）
            //    单次 Agent 调用同时产出能力项与岗位摘要，不再发起旧 job-summary LLM 调用
            PostCapabilityGenerationService.PostAbilityAnalysisResult analysis =
                    capabilityGenerationService.analyzePostTextWithResult(
                            post.getPostName(), jdText,
                            "JD_IMPORT", task.getId(),
                            List.of("source:JD_IMPORT:" + task.getId()));

            // 4. 提取jobSummary（来自同一次Agent提取的summary字段，兜底为截断）
            String jobSummary = hasText(analysis.summary()) ? analysis.summary() : truncateJd(jdText);

            // 5. 更新任务状态
            task.setJdSummary(jobSummary);
            task.setAnalysisStatus(2); // 成功
            jdImportTaskMapper.updateById(task);

            // 6. 构建响应
            JdAnalyzeResponseDTO response = new JdAnalyzeResponseDTO();
            response.setTaskId(task.getId());
            response.setPostId(postId);
            response.setPostName(post.getPostName());
            response.setJobSummary(jobSummary);
            response.setAbilities(analysis.items());
            response.setAnalysisStatus(2);
            return response;

        } catch (Exception e) {
            log.error("JD分析失败: postId={}, error={}", postId, e.getMessage(), e);
            task.setAnalysisStatus(3); // 失败
            task.setErrorMessage(e.getMessage());
            jdImportTaskMapper.updateById(task);

            JdAnalyzeResponseDTO response = new JdAnalyzeResponseDTO();
            response.setTaskId(task.getId());
            response.setPostId(postId);
            response.setPostName(post.getPostName());
            response.setAnalysisStatus(3);
            response.setErrorMessage("AI分析失败: " + e.getMessage());
            return response;
        }
    }

    @Override
    @Transactional
    public void applyAnalysisResult(Long postId, List<JdAbilityItemDTO> items) {
        capabilityGenerationService.applyAbilityItemsToPost(postId, items);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncateJd(String jdText) {
        if (jdText == null) {
            return "";
        }
        return jdText.length() > 200 ? jdText.substring(0, 200) + "..." : jdText;
    }
}
