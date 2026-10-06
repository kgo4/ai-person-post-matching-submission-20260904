package com.example.matching.dto.employee.api;

import java.time.LocalDateTime;

/**
 * PMS 人员花名册的一行：一个 PMS 平台用户 + 它在本系统的绑定与分析情况。
 *
 * <p>【为什么需要它】PMS 平台上的人与本平台无关，同步过来时**没有本系统账号**。
 * 既有的 {@code pms_user_mapping} 只能表达「已绑定」（{@code emp_id} 非空唯一），
 * 表达不了「已同步但还没绑定」这个中间态 —— 而那正是真实场景：
 * PMS 人员先同步过来，等员工注册之后再由 HR 绑定成一个员工。
 * 本 DTO 把「PMS 侧的人」与「已绑定到哪个本地员工」放在同一行，让这个中间态可见。
 *
 * @param pmsUserId         PMS 用户ID（来自 PMS 库，本系统只读）
 * @param pmsUsername       PMS 登录名
 * @param pmsNickname       PMS 昵称
 * @param pmsEmployeeId     PMS 工号
 * @param pmsEmail          PMS 邮箱
 * @param pmsPhone          PMS 手机号
 * @param pmsRole           PMS 角色
 * @param bound             是否已绑定本地员工
 * @param empId             绑定的本地员工ID；未绑定时为 null
 * @param empName           绑定的本地员工姓名；未绑定时为 null
 * @param empCode           绑定的本地员工编号；未绑定时为 null
 * @param empHasAccount     绑定的本地员工是否已有登录账号；未绑定时为 null
 * @param analysisCount     该项目人员的累计分析次数（无任务时为 0）
 * @param lastAnalysisStatus 最近一次分析状态；从未分析过为 null
 * @param lastAnalysisTime   最近一次分析时间；从未分析过为 null
 */
public record PmsRosterItemResponse(
        Long pmsUserId,
        String pmsUsername,
        String pmsNickname,
        String pmsEmployeeId,
        String pmsEmail,
        String pmsPhone,
        String pmsRole,
        boolean bound,
        Long empId,
        String empName,
        String empCode,
        Boolean empHasAccount,
        Integer analysisCount,
        Integer lastAnalysisStatus,
        LocalDateTime lastAnalysisTime) {
}
