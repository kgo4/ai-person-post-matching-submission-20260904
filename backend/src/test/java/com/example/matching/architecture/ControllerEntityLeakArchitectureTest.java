package com.example.matching.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M17/M18：Controller 不直接返回 Entity 类型。
 * <p>
 * 高频公开 API（AiContext、RAG、匹配、员工）必须以 DTO/Response 暴露数据，
 * 禁止把逻辑删除、版本、审计字段等 Entity 内部字段直接泄漏到前端。
 * 新增的 Controller 公开方法若直接返回 Entity 包类型将在此失败。
 */
class ControllerEntityLeakArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
                .importPackages("com.example.matching.controller");
    }

    @Test
    void publicControllerMethodsMustNotReturnEntityTypes() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .and().arePublic()
                .should().haveRawReturnType(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..entity.."))
                .as("Controller 公开方法不得直接返回 ..entity.. 包类型（须经 DTO/Response 暴露）");

        rule.check(importedClasses);
    }

    @Test
    void matchingEmployeePostControllersReturnOnlyApiDtos() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(
                        "..controller.matching..", "..controller.employee..", "..controller.post..")
                .and().arePublic()
                .should().haveRawReturnType(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..entity.."))
                .as("匹配/员工/岗位 Controller 公开方法不得返回 Entity");

        rule.check(importedClasses);
    }
}
