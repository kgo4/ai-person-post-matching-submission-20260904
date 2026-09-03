package com.example.matching.application.post;

import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.service.post.PostPanoramaApplicationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostPanoramaApiFacadeTest {

    @Test
    void graphKeepsUntaggedSkillPointUnderTechnologyStackWithoutSyntheticDomain() {
        PostPanoramaApplicationService panoramaService = mock(PostPanoramaApplicationService.class);
        PostPanoramaApiFacade facade = new PostPanoramaApiFacade(panoramaService);
        PostDTO post = new PostDTO(1L, "测试工程师", "QA", "P3", null, 1, null);
        PostAbilityDTO ability = new PostAbilityDTO(101L, 1L, null, 3, BigDecimal.TEN,
                1, 0, null, null, "接口自动化测试", "测试工程", "接口自动化测试");
        when(panoramaService.queryPosts(null, null, 10)).thenReturn(List.of(post));
        when(panoramaService.queryModels(Set.of(1L), null)).thenReturn(List.of(ability));
        when(panoramaService.queryTagMap(Set.of())).thenReturn(Map.of());
        when(panoramaService.queryParentDomains(Set.of())).thenReturn(List.of());

        Map<String, Object> graph = facade.getGraph(null, null, null, null, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        Map<String, Object> abilityNode = nodes.stream()
                .filter(node -> "skillPoint".equals(node.get("type")))
                .findFirst().orElseThrow();
        assertThat(abilityNode.get("id")).isEqualTo("SKILL_POINT:测试工程:接口自动化测试");
        assertThat(abilityNode.get("label")).isEqualTo("接口自动化测试");
        assertThat(nodes).anySatisfy(node -> {
            assertThat(node.get("id")).isEqualTo("TECH_STACK:测试工程");
            assertThat(node.get("type")).isEqualTo("techStack");
        });
        assertThat(nodes).noneMatch(node -> "DOMAIN:NEW_GENERATION_IT".equals(node.get("id")));
        assertThat(nodes).noneMatch(node -> "ability".equals(node.get("type")) || "ABILITY_TAG:20".equals(node.get("id")));
        assertThat(graph.get("edges")).asList().noneMatch(edge -> String.valueOf(((Map<?, ?>) edge).get("type")).contains("SKILL_POINT"));
    }

    @Test
    void graphUsesPostAbilityNameAsTheOnlySkillPointLabel() {
        PostPanoramaApplicationService panoramaService = mock(PostPanoramaApplicationService.class);
        PostPanoramaApiFacade facade = new PostPanoramaApiFacade(panoramaService);
        PostDTO post = new PostDTO(2L, "Java后端工程师", "JAVA", "高级", null, 1, null);
        PostAbilityDTO ability = new PostAbilityDTO(201L, 2L, 99L, 4, BigDecimal.ONE,
                1, 1, null, null, "Spring Boot", "Java", "spring-boot");
        when(panoramaService.queryPosts(null, null, 10)).thenReturn(List.of(post));
        when(panoramaService.queryModels(Set.of(2L), "Java")).thenReturn(List.of(ability));

        Map<String, Object> graph = facade.getGraph(null, null, "Java", null, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        assertThat(nodes).filteredOn(node -> "skillPoint".equals(node.get("type")))
                .singleElement().satisfies(node -> {
                    assertThat(node.get("label")).isEqualTo("Spring Boot");
                    assertThat(node.get("category")).isEqualTo("Java");
                });
        assertThat(nodes).noneMatch(node -> "ability".equals(node.get("type")));
        assertThat(graph.get("edges")).asList().anySatisfy(edge -> {
            Map<?, ?> value = (Map<?, ?>) edge;
            assertThat(value.get("source")).isEqualTo("TECH_STACK:Java");
            assertThat(value.get("target")).isEqualTo("POST:2");
            assertThat(value.get("type")).isEqualTo("TECH_STACK_POST");
        });
    }

    @Test
    void factGraphKeepsUntaggedAbilityAsIndependentPostAbilityModelFact() {
        PostPanoramaApplicationService panoramaService = mock(PostPanoramaApplicationService.class);
        PostPanoramaApiFacade facade = new PostPanoramaApiFacade(panoramaService);
        PostDTO post = new PostDTO(1L, "测试工程师", "QA", "P3", null, 1, null);
        PostAbilityDTO ability = new PostAbilityDTO(101L, 1L, null, 3, BigDecimal.TEN,
                1, 0, null, null, "接口自动化测试");
        when(panoramaService.queryPosts(null, null, 10)).thenReturn(List.of(post));
        when(panoramaService.queryModels(Set.of(1L), null)).thenReturn(List.of(ability));
        when(panoramaService.queryTagMap(Set.of())).thenReturn(Map.of());

        Map<String, Object> graph = facade.getAbilityFactGraph(null, null, null, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        Map<String, Object> fact = nodes.stream()
                .filter(node -> "unnormalizedPostAbilityFact".equals(node.get("type")))
                .findFirst().orElseThrow();
        assertThat(fact.get("id")).isEqualTo("POST_ABILITY_FACT:101");
        assertThat(fact.get("label")).isEqualTo("接口自动化测试");
    }
}
