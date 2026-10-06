package com.example.matching.service.post.impl;

import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.post.PostModelUnmatchedAbility;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.post.PostPrototypeTagMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import com.example.matching.service.post.PostModelVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-07 测试：generateFromJD 对未匹配能力标签的保留与展示
 */
@ExtendWith(MockitoExtension.class)
class PostModelGenerationServiceImplTest {

    @Mock private PostModelVersionService modelVersionService;
    @Mock private PostPrototypeTagMapper prototypeTagMapper;
    @Mock private PostAbilityModelService postAbilityModelService;
    @Mock private PostCapabilityGenerationService capabilityGenerationService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostModelUnmatchedAbilityService unmatchedAbilityService;

    private PostModelGenerationServiceImpl service;

    private PostModelVersion version;

    @BeforeEach
    void setUp() {
        service = new PostModelGenerationServiceImpl(
                modelVersionService, prototypeTagMapper, postAbilityModelService,
                capabilityGenerationService, abilityTagMapper, postPostMapper,
                unmatchedAbilityService);
        version = new PostModelVersion();
        version.setId(1L);
        version.setPostId(10L);
        version.setStatus("DRAFT");
        version.setItemCount(0);
        version.setTotalWeight(BigDecimal.ZERO);
        PostPost post = new PostPost();
        post.setId(10L);
        post.setPostName("Java工程师");
        when(postPostMapper.selectById(10L)).thenReturn(post);
        when(modelVersionService.createDraft(eq(10L), eq("JD_AI"), any())).thenReturn(version);
        when(modelVersionService.getVersionDetail(1L)).thenReturn(version);
    }

    private JdAbilityItemDTO ability(Long matchedTagId, String name) {
        JdAbilityItemDTO ability = new JdAbilityItemDTO();
        ability.setMatchedTagId(matchedTagId);
        ability.setSuggestedName(name);
        ability.setMinRequiredLevel(3);
        ability.setWeight(BigDecimal.TEN);
        ability.setIsRequired(1);
        ability.setIsCore(1);
        ability.setReasoning("JD中要求熟练掌握" + name);
        return ability;
    }

    private AbilityTag tag(Long id) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setStatus(1);
        tag.setIsDeleted(0);
        tag.setTagLevel(2);
        tag.setTagName("标签" + id);
        return tag;
    }

    @Test
    @DisplayName("matchedTagId 有效时进入已匹配列表并生成版本明细")
    void matchedTagIdValid_createsVersionItem() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(5L, "Java")));
        when(abilityTagMapper.selectById(5L)).thenReturn(tag(5L));

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelVersionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelVersionService).saveVersionItems(eq(1L), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getTagId()).isEqualTo(5L);
        assertThat(itemsCaptor.getValue().get(0).getMinRequiredLevel()).isEqualTo(3);
        verify(unmatchedAbilityService, never()).saveAll(any(), any());
    }

    @Test
    @DisplayName("名称匹配成功时进入已匹配列表")
    void nameLookupSucceeds_createsVersionItem() {
        JdAbilityItemDTO ability = ability(null, "Java并发");
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(List.of(ability));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(7L)));

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelVersionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelVersionService).saveVersionItems(eq(1L), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getTagId()).isEqualTo(7L);
        verify(unmatchedAbilityService, never()).saveAll(any(), any());
    }

    @Test
    @DisplayName("matchedTagId 无效时进入未匹配列表（MATCHED_TAG_ID_NOT_FOUND）")
    void invalidMatchedTagId_goesToUnmatchedList() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(99L, "数据中台架构")));
        when(abilityTagMapper.selectById(99L)).thenReturn(null);

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelUnmatchedAbility>> unmatchedCaptor = ArgumentCaptor.forClass(List.class);
        verify(unmatchedAbilityService).saveAll(eq(1L), unmatchedCaptor.capture());
        assertThat(unmatchedCaptor.getValue()).hasSize(1);
        PostModelUnmatchedAbility record = unmatchedCaptor.getValue().get(0);
        assertThat(record.getAbilityName()).isEqualTo("数据中台架构");
        assertThat(record.getReason()).isEqualTo(PostModelUnmatchedAbility.REASON_MATCHED_TAG_ID_NOT_FOUND);
        assertThat(record.getStatus()).isEqualTo(PostModelUnmatchedAbility.STATUS_PENDING);
        assertThat(record.getMinRequiredLevel()).isEqualTo(3);
        assertThat(record.getWeight()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(record.getIsRequired()).isEqualTo(1);
        assertThat(record.getIsCore()).isEqualTo(1);
        verify(modelVersionService, never()).saveVersionItems(any(), any());
    }

    @Test
    @DisplayName("名称找不到时进入未匹配列表（TAG_NAME_NOT_FOUND）")
    void nameNotFound_goesToUnmatchedList() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(null, "量子计算基础")));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of());

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelUnmatchedAbility>> unmatchedCaptor = ArgumentCaptor.forClass(List.class);
        verify(unmatchedAbilityService).saveAll(eq(1L), unmatchedCaptor.capture());
        assertThat(unmatchedCaptor.getValue()).hasSize(1);
        assertThat(unmatchedCaptor.getValue().get(0).getReason())
                .isEqualTo(PostModelUnmatchedAbility.REASON_TAG_NAME_NOT_FOUND);
    }

    @Test
    @DisplayName("名称匹配到多个启用标签时进入未匹配列表（TAG_NAME_AMBIGUOUS）")
    void ambiguousName_goesToUnmatchedList() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(null, "项目管理")));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(30L), tag(31L)));

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelUnmatchedAbility>> unmatchedCaptor = ArgumentCaptor.forClass(List.class);
        verify(unmatchedAbilityService).saveAll(eq(1L), unmatchedCaptor.capture());
        assertThat(unmatchedCaptor.getValue()).hasSize(1);
        assertThat(unmatchedCaptor.getValue().get(0).getReason())
                .isEqualTo(PostModelUnmatchedAbility.REASON_TAG_NAME_AMBIGUOUS);
        verify(modelVersionService, never()).saveVersionItems(any(), any());
    }

    @Test
    @DisplayName("名称匹配到唯一启用标签时进入已匹配列表（禁用/删除项被过滤）")
    void nameMatchFiltersDisabledAndDeleted() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(null, "Java")));
        AbilityTag disabled = new AbilityTag();
        disabled.setId(40L);
        disabled.setStatus(0);
        disabled.setIsDeleted(0);
        disabled.setTagName("Java");
        AbilityTag enabled = tag(7L);
        enabled.setTagName("Java");
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(disabled, enabled));

        service.generateFromJD(10L, "jd", "desc");

        ArgumentCaptor<List<PostModelVersionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelVersionService).saveVersionItems(eq(1L), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getTagId()).isEqualTo(7L);
        verify(unmatchedAbilityService, never()).saveAll(any(), any());
    }

    @Test
    @DisplayName("全部未匹配时创建 REVIEW_REQUIRED 草稿而不是抛异常")
    void allUnmatched_createsReviewRequiredDraftInsteadOfThrowing() {
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenReturn(List.of(ability(99L, "未知能力A"), ability(98L, "未知能力B")));
        when(abilityTagMapper.selectById(99L)).thenReturn(null);
        when(abilityTagMapper.selectById(98L)).thenReturn(null);

        PostModelVersion result = service.generateFromJD(10L, "jd", "desc");

        assertThat(result).isNotNull();
        ArgumentCaptor<PostModelVersion> versionCaptor = ArgumentCaptor.forClass(PostModelVersion.class);
        verify(modelVersionService).updateById(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo("REVIEW_REQUIRED");
        ArgumentCaptor<List<PostModelUnmatchedAbility>> unmatchedCaptor = ArgumentCaptor.forClass(List.class);
        verify(unmatchedAbilityService).saveAll(eq(1L), unmatchedCaptor.capture());
        assertThat(unmatchedCaptor.getValue()).hasSize(2);
        verify(modelVersionService, never()).saveVersionItems(any(), any());
    }
}
