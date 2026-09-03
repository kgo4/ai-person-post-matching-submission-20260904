package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.ability.PersonAbilityGovernanceEventMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonAbilityGovernanceServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                EmpAbility.class);
    }

    @Test
    void changeLevel_distinguishesUnchangedAndPreviouslyUnknownLevels() {
        EmpAbilityMapper abilityMapper = mock(EmpAbilityMapper.class);
        AbilityTagService tagService = mock(AbilityTagService.class);
        PersonAbilityGovernanceServiceImpl service = new PersonAbilityGovernanceServiceImpl(
                mock(PersonAbilityGovernanceEventMapper.class), abilityMapper,
                mock(PersonAbilityProfileMapper.class), tagService,
                mock(AgentMemoryService.class), new ObjectMapper());
        AbilityTag tag = new AbilityTag();
        tag.setId(2L);
        tag.setTagName("Java");
        when(tagService.getById(2L)).thenReturn(tag);

        EmpAbility ability = new EmpAbility();
        ability.setEmpId(1L);
        ability.setTagId(2L);
        ability.setMasteryLevel(3);
        when(abilityMapper.selectOne(any())).thenReturn(ability);

        PersonAbilityGovernanceEvent unchanged = service.changeLevel(1L, 2L, 3, "confirm", 9L);
        assertThat(unchanged.getModifyType()).isEqualTo("LEVEL_UNCHANGED");

        ability.setMasteryLevel(null);
        PersonAbilityGovernanceEvent initialized = service.changeLevel(1L, 2L, 3, "initialize", 9L);
        assertThat(initialized.getModifyType()).isEqualTo("LEVEL_SET");
    }

    @Test
    void claimNormalizerPreservesSameNameClaimsInSameGroupAndDifferentNamesInSeparateGroups() throws Exception {
        // 验证 normalizer 对等价名称的分组行为：
        // 相同 normalizedAbilityName 的 claims 应在同一组；不同的应在不同组。
        com.example.matching.common.util.PersonAbilityClaimNormalizer normalizer =
                new com.example.matching.common.util.PersonAbilityClaimNormalizer(new ObjectMapper());

        // 模拟 AI 返回三个 claims：Redis、Redis（重复）、Redis Cluster
        String json = """
                {
                  "claims": [
                    {"abilityName": "Redis", "normalizedAbilityName": "Redis", "masteryLevel": 3,
                     "evidenceText": "Used Redis for caching", "sourceRefs": ["source:RESUME_PARSE:1"]},
                    {"abilityName": "Redis", "normalizedAbilityName": "Redis", "masteryLevel": 4,
                     "evidenceText": "Redis cluster management", "sourceRefs": ["source:RESUME_PARSE:1"]},
                    {"abilityName": "Redis Cluster", "normalizedAbilityName": "Redis Cluster", "masteryLevel": 4,
                     "evidenceText": "Managed Redis Cluster deployment", "sourceRefs": ["source:RESUME_PARSE:1"]}
                  ]
                }
                """;

        com.example.matching.agent.dto.person.PersonAbilityExtractionResult result =
                normalizer.normalize(json);

        // 三个 claims 都应保留
        assertThat(result.getClaims()).hasSize(3);
        // 按 normalizedAbilityName 分组，"Redis" 应有 2 条，"Redis Cluster" 有 1 条
        long redisGroupCount = result.getClaims().stream()
                .filter(c -> "Redis".equals(c.getNormalizedAbilityName())).count();
        long redisClusterGroupCount = result.getClaims().stream()
                .filter(c -> "Redis Cluster".equals(c.getNormalizedAbilityName())).count();
        assertThat(redisGroupCount).isEqualTo(2);
        assertThat(redisClusterGroupCount).isEqualTo(1);
        // 独立源证据均保留
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("Used Redis for caching");
        assertThat(result.getClaims().get(1).getEvidenceText()).isEqualTo("Redis cluster management");
        assertThat(result.getClaims().get(2).getEvidenceText()).isEqualTo("Managed Redis Cluster deployment");
    }
}
