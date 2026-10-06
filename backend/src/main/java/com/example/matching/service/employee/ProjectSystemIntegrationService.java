package com.example.matching.service.employee;

import com.example.matching.entity.employee.EmpAbility;

import java.util.List;

/**
 * 项目管理系统集成接口
 * <p>
 * 预留接口，用于与外部项目管理系统对接，获取员工在项目中的能力表现数据。
 * 实际实现需要根据具体的项目管理系统API进行开发。
 */
public interface ProjectSystemIntegrationService {

    /**
     * 从项目管理系统获取员工能力数据
     * <p>
     * 通过调用项目管理系统的API，获取员工在项目中表现出的能力数据，
     * 包括项目角色、技术栈使用情况、代码评审结果等。
     *
     * @param empId 员工ID
     * @return 从项目系统获取的能力列表
     */
    List<EmpAbility> fetchAbilitiesFromProject(Long empId);

    /**
     * 同步能力数据到项目管理系统
     * <p>
     * 将本系统中维护的员工能力数据同步到项目管理系统，
     * 用于项目分配、团队组建等场景。
     *
     * @param empId      员工ID
     * @param abilities  能力列表
     */
    void syncAbilitiesToProject(Long empId, List<EmpAbility> abilities);

    /**
     * 检查项目管理系统连接状态
     *
     * @return 是否可用
     */
    boolean isProjectSystemAvailable();
}
