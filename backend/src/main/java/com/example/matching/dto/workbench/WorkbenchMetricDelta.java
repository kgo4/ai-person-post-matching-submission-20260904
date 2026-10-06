package com.example.matching.dto.workbench;

/**
 * 指标环比（对应前端工作台 KPI 卡右下角的「较昨日 ↑12」）。
 *
 * <p>语义约定：{@code previous} 是本对象的必要前提——只有当对比期值确实存在（例如
 * 库中最早数据早于对比期）时才会产出该对象。无法计算环比时上层应传 {@code null}
 * 而不是传 0，避免把"没有基线数据"显示成"↑0%"误导使用者。</p>
 *
 * @param current  本期值
 * @param previous 对比期值
 * @param direction 变化方向：up 增长 / down 下降 / flat 持平
 * @param diff     本期与对比期的差值（绝对值）
 * @param basis    对比期文案，例如「较昨日」「较上周」
 */
public record WorkbenchMetricDelta(
        long current,
        long previous,
        String direction,
        long diff,
        String basis
) {

    /** 对比期文案默认「较昨日」。 */
    public static final String BASIS_DAY = "较昨日";

    /**
     * 构建环比。
     *
     * @param current  本期值
     * @param previous 对比期值，null 表示无基线数据 → 返回 null（前端隐藏环比行）
     * @return 环比对象；无基线时返回 null
     */
    public static WorkbenchMetricDelta of(Long current, Long previous) {
        return of(current, previous, BASIS_DAY);
    }

    public static WorkbenchMetricDelta of(Long current, Long previous, String basis) {
        if (current == null || previous == null) {
            return null;
        }
        long diff = current - previous;
        String direction;
        if (diff > 0) {
            direction = "up";
        } else if (diff < 0) {
            direction = "down";
        } else {
            direction = "flat";
        }
        return new WorkbenchMetricDelta(current, previous, direction, Math.abs(diff), basis);
    }
}
