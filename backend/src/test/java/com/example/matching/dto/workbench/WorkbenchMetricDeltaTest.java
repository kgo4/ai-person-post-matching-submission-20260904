package com.example.matching.dto.workbench;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作台环比 DTO 单测。
 *
 * <p>核心约定：无基线数据时必须返回 {@code null}，不能退化成 0——
 * 「没有对比数据」与「本期和对比期相等」是两件事，前者应隐藏环比行。</p>
 */
class WorkbenchMetricDeltaTest {

    @Test
    void returnsNullWhenEitherSideMissing() {
        assertThat(WorkbenchMetricDelta.of(null, 10L)).isNull();
        assertThat(WorkbenchMetricDelta.of(10L, null)).isNull();
        assertThat(WorkbenchMetricDelta.of(null, null)).isNull();
    }

    @Test
    void marksUpWhenCurrentGreater() {
        WorkbenchMetricDelta delta = WorkbenchMetricDelta.of(12L, 3L);

        assertThat(delta).isNotNull();
        assertThat(delta.direction()).isEqualTo("up");
        assertThat(delta.diff()).isEqualTo(9L);
        assertThat(delta.current()).isEqualTo(12L);
        assertThat(delta.previous()).isEqualTo(3L);
        assertThat(delta.basis()).isEqualTo(WorkbenchMetricDelta.BASIS_DAY);
    }

    @Test
    void marksDownWhenCurrentSmallerAndKeepsDiffAbsolute() {
        WorkbenchMetricDelta delta = WorkbenchMetricDelta.of(3L, 12L);

        assertThat(delta).isNotNull();
        assertThat(delta.direction()).isEqualTo("down");
        // diff 恒为非负，方向由 direction 单独表达
        assertThat(delta.diff()).isEqualTo(9L);
    }

    @Test
    void marksFlatWhenEqual() {
        WorkbenchMetricDelta delta = WorkbenchMetricDelta.of(5L, 5L);

        assertThat(delta).isNotNull();
        assertThat(delta.direction()).isEqualTo("flat");
        assertThat(delta.diff()).isZero();
    }

    @Test
    void supportsCustomBasis() {
        WorkbenchMetricDelta delta = WorkbenchMetricDelta.of(20L, 10L, "较上周");

        assertThat(delta).isNotNull();
        assertThat(delta.basis()).isEqualTo("较上周");
    }

    @Test
    void previousZeroIsAValidBaseline() {
        // 对比期为 0 是合法基线（0 → 12），必须产出环比而不是当作缺数据
        WorkbenchMetricDelta delta = WorkbenchMetricDelta.of(12L, 0L);

        assertThat(delta).isNotNull();
        assertThat(delta.direction()).isEqualTo("up");
        assertThat(delta.diff()).isEqualTo(12L);
    }
}
