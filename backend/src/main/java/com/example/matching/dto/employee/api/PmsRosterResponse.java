package com.example.matching.dto.employee.api;

import java.util.List;

/**
 * PMS 项目分析页的花名册响应：一次性给出人员清单 + 页面需要的口径统计。
 *
 * <p>为什么统计与清单同一次返回而不是分成两个接口：
 * 页面顶部的「已绑定 / 未绑定 / 已分析」必须与列表里的行**同源**，
 * 分成两个接口时只要其中一个失败，用户就会看到「统计说 3 人已绑定、列表里 0 行」这种自相矛盾的页面。
 *
 * <p>为什么带 {@code message}：本页的数据源是外部 PMS 库，
 * 连不上或库里没有用户时会得到空列表。空列表必须带可执行的原因说明，
 * 否则用户无法区分「PMS 里确实没人」和「PMS 连不上」。
 *
 * @param pmsConnected    PMS 库是否连通
 * @param totalPmsUsers   PMS 用户总数
 * @param boundCount      已绑定本地员工的人数
 * @param unboundCount    已同步但尚未绑定的人数
 * @param analyzedCount   至少成功分析过一次的人数
 * @param boundEmpIds     当前被占用的本地员工ID集合（绑定弹窗据此即时提示冲突，避免提交后才报错）
 * @param items           花名册明细
 * @param message         空结果时的诊断说明；有数据时为 null
 */
public record PmsRosterResponse(
        boolean pmsConnected,
        int totalPmsUsers,
        int boundCount,
        int unboundCount,
        int analyzedCount,
        List<Long> boundEmpIds,
        List<PmsRosterItemResponse> items,
        String message) {

    /** 带诊断说明的空结果：宁可给出原因，也不要静默返回 200 + 空数组。 */
    public static PmsRosterResponse empty(String message) {
        return new PmsRosterResponse(false, 0, 0, 0, 0, List.of(), List.of(), message);
    }
}
