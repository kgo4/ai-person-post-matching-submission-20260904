package com.example.matching.dto.matching.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 匹配记录响应契约守护。
 *
 * <p>背景：`MatchingRecordResponse` 曾经漏了 {@code publishStatus}，而 HR「匹配结果」列表的
 * 「推送状态」列读的正是这个字段 —— 前端拿到的一直是 undefined，
 * 于是**点推送后列表恒显示「未推送」**（数据库其实已经写入）。
 * 这是「字段漏在 DTO 里」导致的静默故障：编译能过、接口 200、只是数据无声丢失。
 *
 * <p>本测试把「推送状态必须随响应下发」固化下来，避免以后重构 DTO 时再次漏掉。
 */
class MatchingRecordResponseTest {

    private static List<String> componentNames() {
        return Arrays.stream(MatchingRecordResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }

    @Test
    @DisplayName("响应必须下发推送状态，否则前端「推送状态」列恒为未推送")
    void responseExposesPublishStatus() {
        assertThat(componentNames())
                .as("推送状态是员工侧可见性的唯一开关，必须随列表/详情响应下发")
                .contains("publishStatus");
    }

    @Test
    @DisplayName("审批状态与推送状态是两个正交动作，必须同时存在")
    void responseExposesBothApprovalAndPublishStatus() {
        assertThat(componentNames())
                .contains("approvalStatus", "publishStatus");
    }
}
