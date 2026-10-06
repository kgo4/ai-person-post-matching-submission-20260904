package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.post.PostModelUnmatchedAbility;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.mapper.post.PostModelUnmatchedAbilityMapper;
import com.example.matching.mapper.post.PostModelVersionItemMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostModelVersionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-07 测试：未匹配能力绑定标签流程
 */
@ExtendWith(MockitoExtension.class)
class PostModelUnmatchedAbilityServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                PostModelUnmatchedAbility.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                PostModelVersion.class);
    }

    @Mock private PostModelUnmatchedAbilityMapper unmatchedAbilityMapper;
    @Mock private PostModelVersionItemMapper versionItemMapper;
    @Mock private PostModelVersionService modelVersionService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PostModelUnmatchedAbilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostModelUnmatchedAbilityServiceImpl(
                unmatchedAbilityMapper, versionItemMapper, modelVersionService,
                abilityTagMapper, eventPublisher);
    }

    private PostModelUnmatchedAbility record(Long id, String status) {
        PostModelUnmatchedAbility record = new PostModelUnmatchedAbility();
        record.setId(id);
        record.setVersionId(1L);
        record.setAbilityName("数据中台架构");
        record.setReason(PostModelUnmatchedAbility.REASON_TAG_NAME_NOT_FOUND);
        record.setMinRequiredLevel(3);
        record.setWeight(BigDecimal.TEN);
        record.setIsRequired(1);
        record.setIsCore(1);
        record.setReasoning("JD要求掌握数据中台架构");
        record.setStatus(status);
        return record;
    }

    private AbilityTag tag(Long id, Integer status) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setStatus(status);
        tag.setIsDeleted(0);
        tag.setTagName("数据平台架构");
        return tag;
    }

    private PostModelVersion version(String status) {
        PostModelVersion version = new PostModelVersion();
        version.setId(1L);
        version.setPostId(10L);
        version.setStatus(status);
        version.setItemCount(0);
        version.setTotalWeight(BigDecimal.ZERO);
        return version;
    }

    @Test
    @DisplayName("绑定成功后：原子抢占、创建版本明细、更新版本统计、发布事件")
    void bind_success_createsItemUpdatesStatusPublishesEvent() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, 1));
        when(modelVersionService.getById(1L)).thenReturn(version("DRAFT"));
        when(unmatchedAbilityMapper.update(isNull(), any())).thenReturn(1);
        when(modelVersionService.incrementStatisticsForBinding(1L, BigDecimal.TEN)).thenReturn(true);

        service.bind(5L, 20L);

        ArgumentCaptor<PostModelVersionItem> itemCaptor = ArgumentCaptor.forClass(PostModelVersionItem.class);
        verify(versionItemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getTagId()).isEqualTo(20L);
        assertThat(itemCaptor.getValue().getMinRequiredLevel()).isEqualTo(3);
        assertThat(itemCaptor.getValue().getWeight()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(itemCaptor.getValue().getIsRequired()).isEqualTo(1);
        assertThat(itemCaptor.getValue().getIsCore()).isEqualTo(1);
        assertThat(itemCaptor.getValue().getVersionId()).isEqualTo(1L);

        // 原子抢占：条件更新必须带 status=PENDING 与 boundTagId
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PostModelUnmatchedAbility>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(unmatchedAbilityMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("status")
                .contains("bound_tag_id");

        verify(modelVersionService).incrementStatisticsForBinding(1L, BigDecimal.TEN);

        verify(eventPublisher).publishEvent(any(PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("并发绑定：条件更新抢占失败时抛异常且不创建版本明细")
    void bind_concurrentClaimLost_throwsAndDoesNotCreateItem() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, 1));
        when(modelVersionService.getById(1L)).thenReturn(version("DRAFT"));
        // 另一个请求已抢先置为 TAG_BOUND：条件更新影响 0 行
        when(unmatchedAbilityMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.bind(5L, 20L))
                .isInstanceOf(BusinessException.class);
        verify(versionItemMapper, never()).insert(any(PostModelVersionItem.class));
        verify(modelVersionService, never()).incrementStatisticsForBinding(any(), any());
        verify(eventPublisher, never()).publishEvent(any(PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("REVIEW_REQUIRED 版本绑定后恢复为 DRAFT")
    void bind_reviewRequiredVersion_becomesDraft() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, 1));
        when(modelVersionService.getById(1L)).thenReturn(version("REVIEW_REQUIRED"));
        when(unmatchedAbilityMapper.update(isNull(), any())).thenReturn(1);
        when(modelVersionService.incrementStatisticsForBinding(1L, BigDecimal.TEN)).thenReturn(true);

        service.bind(5L, 20L);

        verify(modelVersionService).incrementStatisticsForBinding(1L, BigDecimal.TEN);
    }

    @Test
    @DisplayName("版本在绑定期间变为不可编辑时回滚，不发布变更事件")
    void bind_versionNoLongerEditable_throws() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, 1));
        when(modelVersionService.getById(1L)).thenReturn(version("DRAFT"));
        when(unmatchedAbilityMapper.update(isNull(), any())).thenReturn(1);
        when(modelVersionService.incrementStatisticsForBinding(1L, BigDecimal.TEN)).thenReturn(false);

        assertThatThrownBy(() -> service.bind(5L, 20L))
                .isInstanceOf(BusinessException.class);

        verify(versionItemMapper).insert(any(PostModelVersionItem.class));
        verify(eventPublisher, never()).publishEvent(any(PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("标签不存在时绑定失败")
    void bind_tagNotFound_throws() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(null);

        assertThatThrownBy(() -> service.bind(5L, 20L))
                .isInstanceOf(BusinessException.class);
        verify(versionItemMapper, never()).insert(any(PostModelVersionItem.class));
    }

    @Test
    @DisplayName("标签未启用时绑定失败")
    void bind_tagDisabled_throws() {
        when(unmatchedAbilityMapper.selectById(5L)).thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_PENDING));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, 0));

        assertThatThrownBy(() -> service.bind(5L, 20L))
                .isInstanceOf(BusinessException.class);
        verify(versionItemMapper, never()).insert(any(PostModelVersionItem.class));
    }

    @Test
    @DisplayName("非 PENDING 记录不能绑定")
    void bind_notPending_throws() {
        when(unmatchedAbilityMapper.selectById(5L))
                .thenReturn(record(5L, PostModelUnmatchedAbility.STATUS_IGNORED));

        assertThatThrownBy(() -> service.bind(5L, 20L))
                .isInstanceOf(BusinessException.class);
        verify(versionItemMapper, never()).insert(any(PostModelVersionItem.class));
    }

    @Test
    @DisplayName("记录不存在时绑定失败")
    void bind_recordNotFound_throws() {
        when(unmatchedAbilityMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.bind(99L, 20L))
                .isInstanceOf(BusinessException.class);
    }
}
