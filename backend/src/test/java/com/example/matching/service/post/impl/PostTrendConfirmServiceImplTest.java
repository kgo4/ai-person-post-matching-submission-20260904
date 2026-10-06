package com.example.matching.service.post.impl;

import com.example.matching.application.post.PostApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.dto.post.api.PostResponse;
import com.example.matching.dto.post.api.PostTrendCandidateUpdateRequest;
import com.example.matching.dto.post.api.PostTrendLandResult;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.service.post.support.TrendAbilityDiffService;
import com.example.matching.service.post.support.TrendCandidatePayloadCodec;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 趋势候选落地单测（S4）。
 * <p>
 * 核心回归点有三个：
 * <ol>
 *   <li><b>幂等</b> —— {@code markLanded} 的 CAS 返回 0 时必须中止，
 *       否则两个管理员同时点同一张卡会建出两个同名岗位；</li>
 *   <li><b>全量写能力</b> —— 能力变更落地的清单必须包含既有能力，
 *       只写变更项等于把材料没提到的能力全删了；</li>
 *   <li><b>权重合法</b> —— 合计必须落在 batchConfig 接受的区间，否则整次落地失败。</li>
 * </ol>
 */
@DisplayName("趋势候选落地（S4）")
class PostTrendConfirmServiceImplTest {

    private final PostTrendCandidateMapper candidateMapper = mock(PostTrendCandidateMapper.class);
    private final PostApiFacade postApiFacade = mock(PostApiFacade.class);
    private final PostAbilityModelService postAbilityModelService = mock(PostAbilityModelService.class);
    private final PostTrendTaskService postTrendTaskService = mock(PostTrendTaskService.class);
    private final AbilityTagCandidateService abilityTagCandidateService = mock(AbilityTagCandidateService.class);
    private final AbilityTagMapper abilityTagMapper = mock(AbilityTagMapper.class);

    private final TrendCandidatePayloadCodec codec = new TrendCandidatePayloadCodec(new ObjectMapper());
    private final TrendAbilityDiffService diffService = new TrendAbilityDiffService(postAbilityModelService);

    private final PostTrendConfirmServiceImpl service = new PostTrendConfirmServiceImpl(
            candidateMapper, postApiFacade, postAbilityModelService, postTrendTaskService,
            diffService, codec, abilityTagCandidateService, abilityTagMapper);

    // ---------------------------------------------------------------- 新岗位候选

    @Test
    @DisplayName("新岗位候选：建岗位 + 写能力画像 + 回写候选，返回 createdNewPost=true")
    void newPostCandidateCreatesPostAndWritesAbilityModel() {
        PostTrendCandidate candidate = newPostCandidate();
        when(candidateMapper.selectById(5L)).thenReturn(candidate);
        when(postApiFacade.createAndReturnId(any())).thenReturn(100L);
        when(candidateMapper.markLanded(eq(5L), eq(100L), anyString(), any())).thenReturn(1);

        PostTrendLandResult result = service.land(5L, null, 9L);

        assertThat(result.createdNewPost()).isTrue();
        assertThat(result.postId()).isEqualTo(100L);
        assertThat(result.abilityCount()).isEqualTo(2);
        // 抢占与建岗在同一事务里：CAS 失败会让建岗一起回滚
        verify(candidateMapper).markLanded(5L, 100L, "岗位管理员确认趋势候选", 9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(captor.capture());
        List<PostAbilityModelConfigDTO> written = captor.getValue();
        assertThat(written).hasSize(2);
        assertThat(written).extracting(PostAbilityModelConfigDTO::getPostId).containsOnly(100L);
        BigDecimal total = written.stream()
                .map(PostAbilityModelConfigDTO::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100");
        verify(postTrendTaskService).refreshConfirmStatus(41L);
    }

    @Test
    @DisplayName("重复确认：CAS 未命中时抛冲突且一行能力都不写，绝不二次建岗")
    void concurrentConfirmDoesNotCreateSecondPost() {
        when(candidateMapper.selectById(5L)).thenReturn(newPostCandidate());
        when(postApiFacade.createAndReturnId(any())).thenReturn(100L);
        when(candidateMapper.markLanded(eq(5L), eq(100L), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.land(5L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他操作处理");

        verify(postAbilityModelService, never()).batchConfig(any());
        verify(postTrendTaskService, never()).refreshConfirmStatus(any());
    }

    @Test
    @DisplayName("已处理的候选直接拒绝，不进入建岗流程")
    void alreadyHandledCandidateIsRejected() {
        PostTrendCandidate candidate = newPostCandidate();
        candidate.setConfirmStatus(PostTrendCandidate.CONFIRM_APPROVED);
        when(candidateMapper.selectById(5L)).thenReturn(candidate);

        assertThatThrownBy(() -> service.land(5L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已由他人处理");

        verify(postApiFacade, never()).createAndReturnId(any());
    }

    @Test
    @DisplayName("候选没有任何能力项时放弃落地，不留下「有岗位、无能力」的脏数据")
    void candidateWithoutAbilitiesIsRejected() {
        PostTrendCandidate candidate = newPostCandidate();
        candidate.setCandidatePayload(codec.write(new TrendCandidatePayload()));
        when(candidateMapper.selectById(5L)).thenReturn(candidate);
        when(postApiFacade.createAndReturnId(any())).thenReturn(100L);
        when(candidateMapper.markLanded(eq(5L), eq(100L), anyString(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.land(5L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有任何能力项");

        verify(postAbilityModelService, never()).batchConfig(any());
    }

    @Test
    @DisplayName("候选不存在时返回 404 语义")
    void missingCandidateIsReported() {
        when(candidateMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.land(5L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("趋势候选不存在");
    }

    // ---------------------------------------------------------------- 能力变更候选

    @Test
    @DisplayName("能力变更：写回的是「既有能力 + 候选能力」的并集，不因 batchConfig 先删后写而丢能力")
    void abilityChangeWritesUnionOfExistingAndProposed() {
        PostTrendCandidate candidate = changeCandidate();
        when(candidateMapper.selectById(6L)).thenReturn(candidate);
        when(postApiFacade.get(7L)).thenReturn(existingPost());
        when(candidateMapper.markLanded(eq(6L), eq(7L), anyString(), any())).thenReturn(1);
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(
                ability("内容安全", 3, 60),
                ability("数据标注", 2, 40)));

        PostTrendLandResult result = service.land(6L, "按最新政策要求升级", 9L);

        assertThat(result.createdNewPost()).isFalse();
        assertThat(result.postId()).isEqualTo(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(captor.capture());
        assertThat(captor.getValue()).extracting(PostAbilityModelConfigDTO::getAbilityName)
                .containsExactlyInAnyOrder("内容安全", "数据标注", "模型评测");
    }

    @Test
    @DisplayName("能力变更缺少目标岗位时明确失败，不做无主写入")
    void changeCandidateWithoutTargetPostIsRejected() {
        PostTrendCandidate candidate = changeCandidate();
        candidate.setMatchedPostId(null);
        when(candidateMapper.selectById(6L)).thenReturn(candidate);

        assertThatThrownBy(() -> service.land(6L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少目标岗位");
    }

    @Test
    @DisplayName("目标岗位已被删除时拒绝落地，避免写出孤儿能力画像")
    void changeCandidateWithDeletedPostIsRejected() {
        PostTrendCandidate candidate = changeCandidate();
        when(candidateMapper.selectById(6L)).thenReturn(candidate);
        when(postApiFacade.get(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.land(6L, null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标岗位已不存在");

        verify(postAbilityModelService, never()).batchConfig(any());
    }

    // ---------------------------------------------------------------- 调整与审核

    @Test
    @DisplayName("已落地的候选不接受改写，避免候选与岗位画像不一致")
    void updatePayloadRejectsHandledCandidate() {
        PostTrendCandidate candidate = newPostCandidate();
        candidate.setConfirmStatus(PostTrendCandidate.CONFIRM_APPROVED);
        when(candidateMapper.selectById(5L)).thenReturn(candidate);

        PostTrendCandidateUpdateRequest request = new PostTrendCandidateUpdateRequest(
                "新名字", "新描述", List.of());

        assertThatThrownBy(() -> service.updatePayload(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已处理");

        verify(candidateMapper, never()).updatePendingPayload(any(), any(), any(), any());
    }

    @Test
    @DisplayName("调整时能力清单不允许清空，否则落地会产出没有任何能力要求的岗位")
    void updatePayloadRejectsEmptyAbilityList() {
        when(candidateMapper.selectById(5L)).thenReturn(newPostCandidate());

        PostTrendCandidateUpdateRequest request = new PostTrendCandidateUpdateRequest(
                null, null, List.of());

        assertThatThrownBy(() -> service.updatePayload(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力清单不能为空");
    }

    @Test
    @DisplayName("只标记通过（不落地）时任务状态同样要重新收敛")
    void reviewRefreshesTaskStatus() {
        PostTrendCandidate candidate = newPostCandidate();
        when(candidateMapper.selectById(5L)).thenReturn(candidate);
        when(candidateMapper.reviewPending(eq(5L), eq(PostTrendCandidate.CONFIRM_REJECTED), anyString(), any()))
                .thenReturn(1);

        boolean changed = service.review(5L, PostTrendCandidate.CONFIRM_REJECTED, "材料依据不足", 9L);

        assertThat(changed).isTrue();
        verify(postTrendTaskService).refreshConfirmStatus(41L);
        verify(postAbilityModelService, never()).batchConfig(any());
    }

    @Test
    @DisplayName("非法的审核结论被拒绝，避免写入不可识别的状态")
    void reviewRejectsUnknownStatus() {
        when(candidateMapper.selectById(5L)).thenReturn(newPostCandidate());

        assertThatThrownBy(() -> service.review(5L, "MAYBE", null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("APPROVED 或 REJECTED");
    }

    // ---------------------------------------------------------------- 夹具

    private PostTrendCandidate newPostCandidate() {
        PostTrendCandidate candidate = baseCandidate();
        candidate.setCandidateType(PostTrendCandidate.TYPE_NEW_POST);
        candidate.setMatchedPostId(null);
        candidate.setMatchedPostName(null);
        candidate.setCandidatePayload(codec.write(payload(
                item("内容安全", 4, 40, null),
                item("模型评测", 5, 60, null))));
        return candidate;
    }

    private PostTrendCandidate changeCandidate() {
        PostTrendCandidate candidate = baseCandidate();
        candidate.setCandidateType(PostTrendCandidate.TYPE_ABILITY_CHANGE);
        candidate.setMatchedPostId(7L);
        candidate.setMatchedPostName("AI 训练师");
        candidate.setCandidatePayload(codec.write(payload(
                item("内容安全", 4, 60, null),
                item("模型评测", 5, 40, null))));
        return candidate;
    }

    private PostTrendCandidate baseCandidate() {
        PostTrendCandidate candidate = new PostTrendCandidate();
        candidate.setId(5L);
        candidate.setTaskId(41L);
        candidate.setPostName("人工智能训练师");
        candidate.setPostDescription("负责模型数据与效果调优");
        candidate.setConfirmStatus(PostTrendCandidate.CONFIRM_PENDING);
        candidate.setHarnessDecision(PostTrendCandidate.HARNESS_PASS);
        return candidate;
    }

    private TrendCandidatePayload payload(TrendCandidatePayload.TrendAbilityItem... items) {
        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().addAll(List.of(items));
        payload.setEffectiveEmphasisScore(BigDecimal.valueOf(8));
        return payload;
    }

    private TrendCandidatePayload.TrendAbilityItem item(String name, Integer level, int weight, Long tagId) {
        TrendCandidatePayload.TrendAbilityItem item = new TrendCandidatePayload.TrendAbilityItem();
        item.setAbilityName(name);
        item.setTagId(tagId);
        item.setSuggestedLevel(level);
        item.setSuggestedWeight(BigDecimal.valueOf(weight));
        return item;
    }

    private PostAbilityModel ability(String name, int level, int weight) {
        PostAbilityModel model = new PostAbilityModel();
        model.setAbilityName(name);
        model.setMinRequiredLevel(level);
        model.setWeight(BigDecimal.valueOf(weight));
        return model;
    }

    private PostResponse existingPost() {
        return new PostResponse(7L, "P0007", "AI 训练师", "既有岗位描述", 1, null, null, null, null);
    }
}
