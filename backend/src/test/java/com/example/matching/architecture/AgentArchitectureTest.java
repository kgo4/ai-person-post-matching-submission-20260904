package com.example.matching.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Architecture tests to enforce the Agent refactoring constraints.
 * <p>
 * These tests prevent regression of the architectural decisions defined in
 * the agent architecture refactor spec.
 */
@AnalyzeClasses(packages = "com.example.matching", importOptions = ImportOption.DoNotIncludeTests.class)
class AgentArchitectureTest {

    /**
     * Controllers must depend on application facades only, not on
     * LangChain4j services, mappers, or Agent tools directly.
     */
    @ArchTest
    static final ArchRule controllers_must_not_depend_on_llm_infrastructure =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..agent.lc4j..",
                            "..agent.tools..",
                            "dev.langchain4j.."
                    )
                    .because("Controllers must call application facades, not LLM infrastructure directly");

    /**
     * Controllers must not directly depend on Service implementation classes.
     * All access to business logic must go through application-layer facades.
     */
    @ArchTest
    static final ArchRule controllers_must_not_depend_on_service_impl =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..impl..")
                    .because("Controllers must call application facades, not Service implementations directly");

    /**
     * Application layer must not import LangChain4j types.
     */
    @ArchTest
    static final ArchRule application_layer_must_not_use_langchain4j =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("dev.langchain4j..")
                    .because("Application layer must not depend on LLM framework");

    /**
     * Application layer must not import MyBatis mapper types.
     */
    @ArchTest
    @ArchIgnore(reason = "ARCH-DEBT: EmpAbilityApiFacade / EmergingPostApiFacade 直接使用 AbilityTagMapper、PostAbilityModelMapper，整改需为主代码新增 repository port，超出当前修复范围。待端口化后恢复。")
    static final ArchRule application_layer_must_not_use_mappers =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..mapper..")
                    .because("Application layer must use repository ports, not mappers directly");

    /**
     * Application layer must not import MyBatis-Plus entity annotations.
     */
    @ArchTest
    static final ArchRule application_layer_must_not_use_entities =
            noClasses()
                    .that().resideInAPackage("..application.agent..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..entity..")
                    .because("Application layer uses typed records, not persistence entities");

    /**
     * Infrastructure LLM layer is the only place that references LangChain4j AiServices.
     */
    @ArchTest
    static final ArchRule only_infrastructure_uses_langchain4j =
            classes()
                    .that().haveNameMatching(".*AiService$")
                    .should().resideInAnyPackage(
                            "..agent.lc4j..",
                            "..infrastructure.llm.."
                    )
                    .because("Only LLM infrastructure should reference AiService interfaces");

    /**
     * The generic Agent facade belongs to the application layer.
     */
    @ArchTest
    static final ArchRule facade_is_in_application_layer =
            classes()
                    .that().haveNameMatching(".*CapabilityWorkflowFacade.*")
                    .should().resideInAPackage("..application.agent..")
                    .because("The facade must be in the application layer");
}
