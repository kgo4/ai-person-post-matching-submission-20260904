package com.example.matching.service.kg.support;

import com.example.matching.entity.kg.KnowledgeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNodeDependencyResolverTest {

    private final KnowledgeNodeDependencyResolver resolver =
            new KnowledgeNodeDependencyResolver(new ObjectMapper());

    @Test
    void parsePrerequisiteIdsReturnsDistinctValidIds() {
        KnowledgeNode node = node(3L, 1, 3, "[1, 2, 1, null]");

        assertThat(resolver.parsePrerequisiteIds(node)).containsExactly(1L, 2L);
    }

    @Test
    void sortPlacesPrerequisitesBeforeDependentNodes() {
        KnowledgeNode foundation = node(1L, 3, 3, null);
        KnowledgeNode framework = node(2L, 1, 1, "[1]");
        KnowledgeNode application = node(3L, 2, 2, "[2]");

        List<KnowledgeNode> ordered = resolver.sortByPrerequisites(
                List.of(application, framework, foundation),
                KnowledgeNodeDependencyResolver.defaultOrder());

        assertThat(ordered).extracting(KnowledgeNode::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void sortIgnoresMissingReferencesAndRetainsAllNodesWhenCycleExists() {
        KnowledgeNode first = node(1L, 1, 1, "[2, 99]");
        KnowledgeNode second = node(2L, 2, 2, "[1]");
        KnowledgeNode independent = node(3L, 0, 0, null);

        List<KnowledgeNode> ordered = resolver.sortByPrerequisites(
                List.of(first, second, independent),
                KnowledgeNodeDependencyResolver.defaultOrder());

        assertThat(ordered).extracting(KnowledgeNode::getId).containsExactly(3L, 1L, 2L);
    }

    private KnowledgeNode node(Long id, int level, int sortOrder, String prerequisitesJson) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(id);
        node.setNodeLevel(level);
        node.setSortOrder(sortOrder);
        node.setPrerequisitesJson(prerequisitesJson);
        return node;
    }
}
