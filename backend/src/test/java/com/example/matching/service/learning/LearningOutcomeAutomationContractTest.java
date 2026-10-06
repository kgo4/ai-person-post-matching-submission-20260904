package com.example.matching.service.learning;

import com.example.matching.service.learning.impl.AiLearningSuggestionServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 学习链路自动化契约测试。
 *
 * <p>本测试原本保护的是「项目复核通过 → 经 MQ 触发能力闭环」这条自动化链路。
 * 该链路已被**有意移除**：它构成**第二条能写能力画像的路径**，且不经 HR 复核
 * （项目复核本身就是那个"独立复核"）。现在项目材料提交即归档，
 * 能力等级只在员工发起「能力提升申请」、由 HR 复核通过时更新。
 *
 * <p>因此下面的断言方向已反转 —— 从「保证链路存在」变为「保证链路不会被加回来」。
 * 🔴 <b>若 {@link #learningProjectApprovalPathStaysRemoved()} 失败</b>，
 * 说明有人重新引入了「提交材料就能改能力画像」的旁路，那会让员工绕开 HR 复核，
 * 必须改回「随能力提升申请一起复核」。
 *
 * @author system
 */
class LearningOutcomeAutomationContractTest {

    @Test
    void aiSuggestionsCanCreateTheMatchingLinkedLearningPlan() {
        Field planService = Arrays.stream(AiLearningSuggestionServiceImpl.class.getDeclaredFields())
                .filter(field -> field.getName().equals("learningPathPlanService"))
                .findFirst()
                .orElse(null);

        assertThat(planService).isNotNull();
    }

    /**
     * 项目材料的独立复核链路必须保持移除状态。
     */
    @Test
    void learningProjectApprovalPathStaysRemoved() {
        assertThatThrownBy(() -> Class.forName("com.example.matching.event.LearningProjectApprovedEvent"))
                .as("「项目复核通过」专用事件已删除：项目材料不再经人工审核触发能力闭环")
                .isInstanceOf(ClassNotFoundException.class);

        assertThatThrownBy(() -> Class.forName("com.example.matching.event.listener.LearningProjectApprovedListener"))
                .as("该事件的 MQ 消费者已删除：能力闭环的唯一触发点是能力提升申请复核")
                .isInstanceOf(ClassNotFoundException.class);
    }

    /**
     * 服务接口上不得再出现独立复核动作。
     */
    @Test
    void projectMaterialSubmissionNoLongerExposesAReviewOperation() {
        assertThat(Arrays.stream(LearningProjectTaskService.class.getDeclaredMethods())
                .map(Method::getName))
                .as("项目材料不再有独立复核动作，提交即归档")
                .doesNotContain("review");
    }
}
