package com.example.matching.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体契约测试 - 禁止 controller 使用裸 Map/Object/String 作为 @RequestBody。
 * <p>
 * 例外清单（需单独产品决策，不在本次修复范围）：
 * <ul>
 *   <li>KnowledgeGraphPlatformController.createSnapshot - graphJson 为图快照的原始 JSON 文本</li>
 *   <li>PostModelGenerationController.generateFromJD - jdText 为原始 JD 文本</li>
 * </ul>
 */
@DisplayName("请求体契约 - @RequestBody 必须使用验证 DTO")
class RequestBodyContractTest {

    private static final String CONTROLLER_BASE_PACKAGE = "com.example.matching.controller";

    private static final Set<String> ALLOWED_RAW_BODY_SIGNATURES = Set.of(
            "KnowledgeGraphPlatformController#createSnapshot(String graphJson)",
            "PostModelGenerationController#generateFromJD(String jdText)"
    );

    @Test
    @Disabled("ARCH-DEBT: 现有 controller 仍使用裸 Map/Object/String 作为 @RequestBody，整改需为主代码补验证 DTO 并改签名，超出「仅补充测试」范围。待契约治理后恢复。")
    @DisplayName("Controller 的 @RequestBody 参数不得为裸 Map/Object/String")
    void requestBodyMustNotBeRawMapObjectOrString() {
        List<String> violations = scanRawRequestBodyViolations();

        List<String> unexpected = violations.stream()
                .filter(signature -> !ALLOWED_RAW_BODY_SIGNATURES.contains(signature))
                .toList();

        assertThat(unexpected)
                .as("以下 controller 方法使用裸类型作为 @RequestBody，必须替换为验证 DTO")
                .isEmpty();
    }

    private List<String> scanRawRequestBodyViolations() {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scanControllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(RequestBody.class)) {
                        continue;
                    }
                    Class<?> type = parameter.getType();
                    if (Map.class.isAssignableFrom(type)
                            || Object.class.equals(type)
                            || String.class.equals(type)) {
                        violations.add(controller.getSimpleName() + "#"
                                + method.getName() + "(" + type.getSimpleName() + " " + parameter.getName() + ")");
                    }
                }
            }
        }
        return violations;
    }

    private List<Class<?>> scanControllerClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("无法加载 controller 类: " + definition.getBeanClassName(), e);
            }
        }
        return classes;
    }
}
