package com.example.matching.architecture;

import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingScoreService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * M-12 架构约束：匹配层与持久层隔离。
 * <p>
 * 评分层不直接依赖 Mapper；MatchingDataQueryService 只有一个实现（持久层适配器），
 * 匹配 DTO 由该适配器在边界处转换。
 */
@AnalyzeClasses(packages = "com.example.matching", importOptions = ImportOption.DoNotIncludeTests.class)
class MatchingLayerArchitectureTest {

    /**
     * 评分服务不得直接依赖 Mapper（持久层细节）。
     */
    @ArchTest
    static final ArchRule score_service_must_not_depend_on_mappers =
            noClasses()
                    .that().areAssignableTo(MatchingScoreService.class)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..mapper..")
                    .because("M-12: 评分层只消费匹配专用 DTO，持久化细节由 MatchingDataQueryService 适配器承担");

    /**
     * MatchingDataQueryService 只允许有一个实现（Entity -> DTO 转换集中在适配器）。
     */
    @ArchTest
    static final ArchRule data_query_service_has_single_impl =
            classes()
                    .that().implement(MatchingDataQueryService.class)
                    .should().haveSimpleName("MatchingDataQueryServiceImpl")
                    .because("M-12: 匹配数据查询只允许一个持久层适配器实现，Entity 不越过该边界");

    /**
     * 匹配专用 DTO 不允许被持久层实体替换（算法/评分层引用 DTO 而非 Entity 参数）。
     */
    @ArchTest
    static final ArchRule matching_dtos_are_immutable_records =
            classes()
                    .that().resideInAPackage("..dto.matching..")
                    .and().haveSimpleNameStartingWith("Matching")
                    .and().haveSimpleNameEndingWith("Snapshot")
                    .should().beRecords()
                    .because("M-12: 匹配专用快照必须是不可变 record，避免跨事务传可变实体");

    /**
     * M-09：业务代码只能依赖核心 Service 接口，禁止直接依赖实现类。
     */
    @ArchTest
    static final ArchRule business_code_must_not_depend_on_core_service_impls =
            noClasses()
                    .that().resideInAnyPackage("..controller..", "..application..", "..service..", "..agent..")
                    .and().resideOutsideOfPackage("..service..impl..")
                    .should().dependOnClassesThat()
                    .haveSimpleName("MatchingScoreServiceImpl")
                    .orShould().dependOnClassesThat()
                    .haveSimpleName("MatchingAlgorithmServiceImpl")
                    .orShould().dependOnClassesThat()
                    .haveSimpleName("EventOutboxDispatcherImpl")
                    .orShould().dependOnClassesThat()
                    .haveSimpleName("VectorSyncTaskServiceImpl")
                    .because("M-09: 调用方统一注入 XxxService 接口，禁止依赖 XxxServiceImpl");

    /**
     * M-12：算法层（算法服务实现与 algorithm 子包）不得消费 EmpAbility / PostAbilityModel 实体。
     */
    @ArchTest
    static final ArchRule algorithm_layer_must_not_consume_ability_entities =
            noClasses()
                    .that().implement(MatchingAlgorithmService.class)
                    .or().resideInAPackage("..service.matching.algorithm..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.example.matching.entity.employee.EmpAbility")
                    .orShould().dependOnClassesThat()
                    .haveFullyQualifiedName("com.example.matching.entity.post.PostAbilityModel")
                    .because("M-12: 算法层只消费匹配专用 DTO（MatchingAbilitySnapshot / MatchingRequirementSnapshot）");
}
