package com.example.matching.service.learning;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.kg.KnowledgeDomainService;
import com.example.matching.service.kg.support.KnowledgeNodeDependencyResolver;
import com.example.matching.service.learning.impl.LearningPathEnhancedServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPathEnhancedServiceTest {

    @Mock private KnowledgeDomainService domainService;
    @Mock private LearningQuizService quizService;
    @Mock private LearningResourceMapper resourceMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Spy private KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver =
            new KnowledgeNodeDependencyResolver(new ObjectMapper());

    @InjectMocks
    private LearningPathEnhancedServiceImpl service;

    @Test
    void getNodeLearningOrderKeepsPrerequisitesBeforeWeakerDependentNodes() {
        KnowledgeNode foundation = node(1L, 1, null);
        KnowledgeNode application = node(2L, 2, "[1]");
        when(domainService.getNodesByDomainId(10L))
                .thenReturn(new ArrayList<>(List.of(application, foundation)))
                .thenReturn(new ArrayList<>(List.of(application, foundation)));
        when(quizService.calculateMasteryScoreByNodeId(7L, 1L)).thenReturn(90.0);
        when(quizService.calculateMasteryScoreByNodeId(7L, 2L)).thenReturn(10.0);

        List<KnowledgeNode> ordered = service.getNodeLearningOrder(7L, 10L);

        assertThat(ordered).extracting(KnowledgeNode::getId).containsExactly(1L, 2L);
    }

    @Test
    void generateLearningPathByKnowledgeGraphUsesPrerequisiteOrder() {
        KnowledgeDomain domain = new KnowledgeDomain();
        domain.setId(10L);
        domain.setDomainName("AI");
        KnowledgeNode foundation = node(1L, 3, null);
        foundation.setNodeName("Foundation");
        KnowledgeNode application = node(2L, 1, "[1]");
        application.setNodeName("Application");
        when(domainService.getAllDomains()).thenReturn(List.of(domain));
        when(domainService.getNodesByDomainId(10L))
                .thenReturn(new ArrayList<>(List.of(application, foundation)));
        when(resourceMapper.selectList(any())).thenReturn(List.of());
        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(List.of("AI"));

        List<LearningPathItemDTO> result = service.generateLearningPathByKnowledgeGraph(request);

        assertThat(result).extracting(LearningPathItemDTO::getTitle)
                .containsExactly("Foundation", "Application");
    }

    @Test
    void generateLearningPathByMasteryUsesPostAbilityNameWhenSystemTagIsMissing() {
        PostAbilityModel requirement = new PostAbilityModel();
        requirement.setPostId(2L);
        requirement.setTagId(99L);
        requirement.setAbilityName("独立岗位能力");
        requirement.setMinRequiredLevel(3);
        requirement.setIsCore(1);

        EmpAbility employeeAbility = new EmpAbility();
        employeeAbility.setEmpId(1L);
        employeeAbility.setTagId(99L);
        employeeAbility.setAbilityName("独立岗位能力");
        employeeAbility.setAbilityLevel(1);

        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(requirement));
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(employeeAbility));

        List<LearningPathItemDTO> result = service.generateLearningPathByMastery(1L, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAbilityName()).isEqualTo("独立岗位能力");
    }

    private KnowledgeNode node(Long id, int level, String prerequisitesJson) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(id);
        node.setNodeLevel(level);
        node.setSortOrder(level);
        node.setPrerequisitesJson(prerequisitesJson);
        return node;
    }
}
