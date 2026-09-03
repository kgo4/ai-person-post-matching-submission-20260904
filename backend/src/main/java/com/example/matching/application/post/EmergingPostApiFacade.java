package com.example.matching.application.post;

import com.example.matching.dto.post.*;
import com.example.matching.entity.post.PostPost;
import com.example.matching.service.post.EmergingPostDiscoveryService;
import com.example.matching.service.post.JdQualityDetector;
import com.example.matching.service.post.PostPanoramaApplicationService;
import com.example.matching.service.post.PostPrototypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergingPostApiFacade {

    private final PostPrototypeService postPrototypeService;
    private final PostPanoramaApplicationService panoramaService;
    private final EmergingPostDiscoveryService emergingPostDiscoveryService;
    private final JdQualityDetector jdQualityDetector;
    @Qualifier("applicationTaskExecutor")
    private final Executor applicationTaskExecutor;
    private final Map<String, AnalyzeTask> analyzeTasks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private PostAbilityModelMapper postAbilityModelMapper;
    @Autowired(required = false)
    private AbilityTagMapper abilityTagMapper;

    public List<EmergingPostDiscoveryDTO> discover(int limit) {
        return emergingPostDiscoveryService.discoverEmergingPosts(limit);
    }

    public EmergingPostDiscoveryDTO.MarketInsight getMarketInsight() {
        return emergingPostDiscoveryService.getMarketInsight();
    }

    public JdQualityReport checkJdQuality(JdQualityCheckRequest request) {
        return jdQualityDetector.detectQuality(
                request.getJdText(),
                request.getAbilityCount() != null ? request.getAbilityCount() : 0,
                request.getMaxLevel() != null ? request.getMaxLevel() : 0
        );
    }

    public EmergingPostResponseDTO analyze(EmergingPostRequestDTO request) {
        EmergingPostResponseDTO response = new EmergingPostResponseDTO();

        String textForRecall = request.getPostName() + " " +
                (request.getDescription() != null ? request.getDescription() : "");
        List<PostPrototypeVO> prototypes = postPrototypeService.recallByDescription(textForRecall.trim(), 3);
        response.setRecommendedPrototypes(prototypes);

        String fullText = buildFullText(request);
        List<JdAbilityItemDTO> recalledAbilities = recallExistingAbilities(fullText);
        response.setRecommendedAbilities(recalledAbilities);
        response.setReasoning("基于岗位能力表与系统标签库的既有能力召回；能力名称不会由 AI 新生成");
        response.setCoreResponsibilities(splitLines(request.getKeyResponsibilities()));
        response.setRequiredSkills(recalledAbilities.stream().filter(a -> Integer.valueOf(1).equals(a.getIsRequired())).map(JdAbilityItemDTO::getSuggestedName).toList());
        response.setBonusSkills(recalledAbilities.stream().filter(a -> !Integer.valueOf(1).equals(a.getIsRequired())).map(JdAbilityItemDTO::getSuggestedName).toList());
        response.setIndustryScenarios(splitLines(request.getIndustry()));

        try {
            JdQualityReport qualityReport = jdQualityDetector.detectQuality(
                    fullText,
                    recalledAbilities.size(),
                    recalledAbilities.stream().mapToInt(a -> a.getMinRequiredLevel() != null ? a.getMinRequiredLevel() : 0).max().orElse(0)
            );
            response.setQualityReport(qualityReport);
        } catch (Exception e) {
            log.warn("JD质量检测失败: {}", e.getMessage());
        }

        if (Boolean.TRUE.equals(request.getCreatePost())) {
            Long postId = createPostFromRequest(request, response.getRecommendedAbilities());
            response.setCreatedPostId(postId);
        }

        return response;
    }

    /**
     * 新兴岗位定义只能复用已有能力：岗位能力表和系统标签库是唯一白名单。
     * 标签库为空时仍允许岗位能力表独立提供候选；两者都为空时返回空列表，绝不让 AI 编造能力落库。
     */
    private List<JdAbilityItemDTO> recallExistingAbilities(String text) {
        if (abilityTagMapper == null && postAbilityModelMapper == null) return List.of();
        String query = normalizeAbilityName(text);
        Map<String, JdAbilityItemDTO> unique = new LinkedHashMap<>();
        if (abilityTagMapper != null) {
            abilityTagMapper.selectList(Wrappers.<AbilityTag>lambdaQuery()
                            .eq(AbilityTag::getStatus, 1).eq(AbilityTag::getIsDeleted, 0))
                    .forEach(tag -> addRecalled(unique, tag.getTagName(), tag.getId(), tag.getTagCategory(), query));
        }
        if (postAbilityModelMapper != null) {
            postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()
                            .eq(PostAbilityModel::getIsDeleted, 0))
                    .forEach(model -> addRecalled(unique, model.getAbilityName(), model.getTagId(), null, query));
        }
        List<JdAbilityItemDTO> result = new ArrayList<>(unique.values());
        if (result.size() > 30) result = result.subList(0, 30);
        return result;
    }

    private void addRecalled(Map<String, JdAbilityItemDTO> unique, String name, Long tagId,
                             String category, String query) {
        if (name == null || name.isBlank()) return;
        String normalized = normalizeAbilityName(name);
        if (normalized.isBlank() || normalized.contains("能力#null") || unique.containsKey(normalized)) return;
        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName(name.trim());
        item.setMatchedTagId(tagId);
        item.setMatchedTagName(name.trim());
        item.setMatchStatus("MATCHED");
        item.setTagCategory(category);
        item.setMinRequiredLevel(3);
        item.setWeight(BigDecimal.ONE);
        item.setIsRequired(1);
        item.setIsCore(0);
        item.setConfidenceScore(BigDecimal.valueOf(query.contains(normalized) ? 95 : 70));
        unique.put(normalized, item);
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[\\n,，、；;]"))
                .map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private String normalizeAbilityName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 提交后台分析任务；该任务只做既有能力召回，不调用普通岗位解析 Agent。 */
    public String submitAnalyze(EmergingPostRequestDTO request) {
        String taskId = UUID.randomUUID().toString();
        analyzeTasks.put(taskId, AnalyzeTask.pending());
        applicationTaskExecutor.execute(() -> runAnalyze(taskId, request));
        return taskId;
    }

    public void runAnalyze(String taskId, EmergingPostRequestDTO request) {
        analyzeTasks.put(taskId, AnalyzeTask.running());
        try {
            analyzeTasks.put(taskId, AnalyzeTask.success(analyze(request)));
        } catch (Exception ex) {
            log.error("新兴岗位后台分析失败: taskId={}", taskId, ex);
            analyzeTasks.put(taskId, AnalyzeTask.failed(ex.getMessage()));
        }
    }

    public Map<String, Object> getAnalyzeTask(String taskId) {
        AnalyzeTask task = analyzeTasks.get(taskId);
        if (task == null) return Map.of("taskId", taskId, "status", "NOT_FOUND");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", task.status());
        if (task.result() != null) result.put("result", task.result());
        if (task.error() != null) result.put("error", task.error());
        return result;
    }

    private record AnalyzeTask(String status, EmergingPostResponseDTO result, String error) {
        static AnalyzeTask pending() { return new AnalyzeTask("PENDING", null, null); }
        static AnalyzeTask running() { return new AnalyzeTask("RUNNING", null, null); }
        static AnalyzeTask success(EmergingPostResponseDTO result) { return new AnalyzeTask("SUCCEEDED", result, null); }
        static AnalyzeTask failed(String error) { return new AnalyzeTask("FAILED", null, error); }
    }

    @Transactional
    public Long confirm(EmergingPostConfirmDTO request) {
        PostPost post = new PostPost();
        post.setPostCode("EMG_" + System.currentTimeMillis());
        post.setPostName(request.getPostName());
        post.setJobDescription(request.getDescription());
        post.setStatus(1);
        panoramaService.insertPost(post);

        writeExistingAbilities(post.getId(), request.getAbilities());

        log.info("新兴岗位创建成功: postId={}, postName={}", post.getId(), post.getPostName());
        return post.getId();
    }

    private String buildFullText(EmergingPostRequestDTO request) {
        StringBuilder sb = new StringBuilder();
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            sb.append(request.getDescription());
        }
        if (request.getKeyResponsibilities() != null && !request.getKeyResponsibilities().isBlank()) {
            sb.append("\n\n关键职责：\n").append(request.getKeyResponsibilities());
        }
        if (request.getIndustry() != null && !request.getIndustry().isBlank()) {
            sb.append("\n\n行业方向：").append(request.getIndustry());
        }
        return sb.toString();
    }

    private Long createPostFromRequest(EmergingPostRequestDTO request, List<JdAbilityItemDTO> abilities) {
        PostPost post = new PostPost();
        post.setPostCode("EMG_" + System.currentTimeMillis());
        post.setPostName(request.getPostName());
        post.setJobDescription(request.getDescription());
        post.setStatus(1);
        panoramaService.insertPost(post);

        writeExistingAbilities(post.getId(), abilities);

        return post.getId();
    }

    private void writeExistingAbilities(Long postId, List<JdAbilityItemDTO> abilities) {
        if (postAbilityModelMapper == null || abilities == null || abilities.isEmpty()) return;
        Map<String, AbilityTag> tags = new HashMap<>();
        if (abilityTagMapper != null) {
            abilityTagMapper.selectList(Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1).eq(AbilityTag::getIsDeleted, 0))
                    .forEach(tag -> tags.put(normalizeAbilityName(tag.getTagName()), tag));
        }
        Map<String, PostAbilityModel> existingAbilityCatalog = new HashMap<>();
        postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getIsDeleted, 0))
                .forEach(model -> {
                    String key = normalizeAbilityName(model.getAbilityName());
                    if (!key.isBlank() && !key.contains("能力#null")) {
                        existingAbilityCatalog.putIfAbsent(key, model);
                    }
                });
        Set<String> existing = new HashSet<>();
        postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId))
                .forEach(model -> existing.add(normalizeAbilityName(model.getAbilityName())));
        for (JdAbilityItemDTO item : abilities) {
            String name = item == null ? null : item.getSuggestedName();
            String key = normalizeAbilityName(name);
            if (key.isBlank() || key.contains("能力#null") || existing.contains(key)) continue;
            AbilityTag tag = tags.get(key);
            if (tag == null && item.getMatchedTagName() != null) tag = tags.get(normalizeAbilityName(item.getMatchedTagName()));
            PostAbilityModel catalogModel = existingAbilityCatalog.get(key);
            if (tag == null && catalogModel == null) {
                // 新兴岗位只能复用既有能力，不能由确认接口创建新能力。
                continue;
            }
            PostAbilityModel model = new PostAbilityModel();
            model.setPostId(postId);
            model.setTagId(tag != null ? tag.getId() : catalogModel.getTagId());
            model.setAbilityName(tag != null ? tag.getTagName() : catalogModel.getAbilityName());
            model.setTechStack(item.getTechStack() != null ? item.getTechStack() : catalogModel.getTechStack());
            model.setMinRequiredLevel(item.getMinRequiredLevel() == null ? 3 : item.getMinRequiredLevel());
            model.setWeight(item.getWeight() == null ? BigDecimal.ZERO : item.getWeight());
            model.setIsRequired(item.getIsRequired() == null ? 1 : item.getIsRequired());
            model.setIsCore(item.getIsCore() == null ? 0 : item.getIsCore());
            model.setSourceType("EMERGING_POST");
            model.setIsDeleted(0);
            postAbilityModelMapper.insert(model);
            existing.add(key);
        }
    }
}
