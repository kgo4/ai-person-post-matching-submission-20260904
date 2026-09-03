package com.example.matching.service.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.post.PostAbilityGroundingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.vo.post.PostAbilityInspectionItemVO;
import com.example.matching.vo.post.PostAbilityInspectionPostVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostAbilityInspectionServiceTest {

    @Mock private PostAbilityModelMapper abilityModelMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private PostAbilityGroundingRecordMapper groundingMapper;

    @Test
    void listAbilitiesUsesGovernanceAdmissionIdInsteadOfAbilityId() {
        PostAbilityModel ability = ability(101L, 1L, "Java", "POST_EVOLUTION");
        ability.setGovernanceAdmissionId(901L);
        GovernanceAdmissionRecord admission = new GovernanceAdmissionRecord();
        admission.setId(901L);
        admission.setFinalDecision("BLOCK");
        admission.setRiskLevel("HIGH");
        admission.setHarnessCheckCode("POST_EVOLUTION_901");

        when(abilityModelMapper.selectList(any())).thenReturn(List.of(ability));
        when(admissionMapper.selectBatchIds(List.of(901L))).thenReturn(List.of(admission));
        when(groundingMapper.selectList(any())).thenReturn(List.of());

        PostAbilityInspectionService service = service();

        List<PostAbilityInspectionItemVO> result = service.listAbilities(1L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getHarnessDecision()).isEqualTo("BLOCK");
            assertThat(item.getHarnessCheckCode()).isEqualTo("POST_EVOLUTION_901");
            assertThat(item.getRiskTags()).contains("Harness拦截");
        });
    }

    @Test
    void pagePostsWithOnlyAiReturnsOnlyPostsContainingAiSourceAbilities() {
        PostAbilityModel aiAbility = ability(101L, 1L, "Java", "POST_EVOLUTION");
        PostAbilityModel manualAbility = ability(102L, 2L, "沟通", "MANUAL");

        when(abilityModelMapper.selectList(any())).thenReturn(List.of(aiAbility, manualAbility));
        when(postPostMapper.selectBatchIds(any())).thenReturn(List.of(post(1L, "AI岗位"), post(2L, "人工岗位")));
        when(groundingMapper.selectList(any())).thenReturn(List.of());

        PostAbilityInspectionService service = service();

        IPage<PostAbilityInspectionPostVO> page = service.pagePosts(null, false, true, 1, 10);

        assertThat(page.getRecords()).extracting(PostAbilityInspectionPostVO::getPostId).containsExactly(1L);
    }

    private PostAbilityInspectionService service() {
        return new PostAbilityInspectionService(abilityModelMapper, postPostMapper, admissionMapper, groundingMapper);
    }

    private PostAbilityModel ability(Long id, Long postId, String name, String sourceType) {
        PostAbilityModel model = new PostAbilityModel();
        model.setId(id);
        model.setPostId(postId);
        model.setAbilityName(name);
        model.setSourceType(sourceType);
        model.setMinRequiredLevel(3);
        model.setWeight(new BigDecimal("50"));
        model.setIsDeleted(0);
        return model;
    }

    private PostPost post(Long id, String name) {
        PostPost post = new PostPost();
        post.setId(id);
        post.setPostName(name);
        post.setIsDeleted(0);
        return post;
    }
}
