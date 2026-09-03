package com.example.matching.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * M-02 架构约束：RAG 检索路径统一。
 * <p>
 * 业务代码（service/agent/application）只能通过 {@code RagRetrievalService} 检索知识，
 * {@code RagContextService} 仅为兼容门面，不允许新业务代码直接注入。
 */
@AnalyzeClasses(packages = "com.example.matching", importOptions = ImportOption.DoNotIncludeTests.class)
class RagRetrievalArchitectureTest {

    /**
     * 业务层不允许直接依赖 RagContextService 兼容门面。
     */
    @ArchTest
    static final ArchRule business_code_must_not_depend_on_legacy_rag_context_service =
            noClasses()
                    .that().resideInAnyPackage("..service..", "..agent..", "..application..")
                    .and().resideOutsideOfPackage("..service.rag..")
                    .should().dependOnClassesThat()
                    .haveSimpleNameStartingWith("RagContextService")
                    .because("M-02: 业务代码应统一通过 RagRetrievalService 检索，RagContextService 仅为兼容门面");

    /**
     * 业务层不得直接依赖 RAG Provider 实现（MySQL/Volcengine 检索）。
     */
    @ArchTest
    static final ArchRule business_code_must_not_depend_on_rag_providers =
            noClasses()
                    .that().resideInAnyPackage("..service..", "..agent..", "..application..")
                    .and().resideOutsideOfPackage("..service.rag..")
                    .should().dependOnClassesThat()
                    .haveSimpleName("MysqlKnowledgeSearchProvider")
                    .orShould().dependOnClassesThat()
                    .haveSimpleName("VolcengineKnowledgeSearchProvider")
                    .because("M-02: Provider 仅允许被 RagRetrievalServiceImpl 调用");
}
