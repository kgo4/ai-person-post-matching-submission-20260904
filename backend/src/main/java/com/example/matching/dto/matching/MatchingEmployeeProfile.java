package com.example.matching.dto.matching;

import java.util.List;

/**
 * 匹配专用员工画像（M-12）
 *
 * @param empId        员工ID
 * @param empCode      员工工号（可空）
 * @param realName     员工姓名
 * @param level        职级（如 P5-P7，可空）
 * @param gender       性别（0-女 1-男，可空）
 * @param extendFields 扩展档案字段（JSON 字符串，硬条件检查用，可空）
 * @param abilities    员工能力快照列表
 */
public record MatchingEmployeeProfile(
        Long empId,
        String empCode,
        String realName,
        String level,
        Integer gender,
        String extendFields,
        List<MatchingAbilitySnapshot> abilities
) {
}
