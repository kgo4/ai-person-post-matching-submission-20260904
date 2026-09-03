package com.example.matching.service.employee;

import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;

import java.util.List;
import java.util.Map;

/**
 * PMS项目管理系统能力分析服务接口
 * <p>
 * 从PMS系统采集员工项目工作数据，通过AI分析提取能力标签。
 */
public interface PmsAbilityAnalysisService {

    /**
     * 自动映射PMS用户（通过工号匹配）
     *
     * @param empId 本地员工ID
     * @return 映射结果，未找到返回null
     */
    PmsUserMapping autoMapUser(Long empId);

    /**
     * 手动映射PMS用户
     *
     * @param empId     本地员工ID
     * @param pmsUserId PMS用户ID
     * @return 映射结果
     */
    PmsUserMapping manualMapUser(Long empId, Long pmsUserId);

    /**
     * 获取员工的PMS用户映射
     *
     * @param empId 本地员工ID
     * @return 映射信息
     */
    PmsUserMapping getMapping(Long empId);

    /**
     * 分析员工项目数据并提取能力
     *
     * @param empId            本地员工ID
     * @param dateRangeMonths  分析时间范围（月）
     * @return 分析任务
     */
    PmsAnalysisTask analyzeEmployee(Long empId, int dateRangeMonths);

    /**
     * 获取员工的PMS分析历史
     *
     * @param empId 本地员工ID
     * @return 分析任务列表
     */
    List<PmsAnalysisTask> getAnalysisHistory(Long empId);

    /**
     * 获取PMS用户列表（用于手动映射）
     *
     * @return PMS用户列表
     */
    List<Map<String, Object>> listPmsUsers();

    /**
     * 测试PMS数据库连接
     *
     * @return 连接是否成功
     */
    boolean testConnection();

    /**
     * 同步PMS用户到匹配系统
     * <p>
     * 通过工号匹配，为已在匹配系统中的员工自动建立PMS映射。
     * 不会自动创建新员工（避免污染人员库）。
     *
     * @return 同步结果：[0]=成功建立映射数, [1]=PMS用户总数, [2]=已存在映射数, [3]=未匹配数
     */
    int[] syncPmsUsers();

    /**
     * 获取分析结果详情（解析AI返回的能力列表）
     *
     * @param taskId 分析任务ID
     * @return 解析后的能力列表
     */
    Map<String, Object> getAnalysisDetail(Long taskId);

    /**
     * 导入选中的能力到员工档案
     *
     * @param empId   员工ID
     * @param taskId  分析任务ID
     * @param indexes 选中的能力索引列表（null或空表示导入全部）
     * @return 导入的能力数量
     */
    int importAbilities(Long empId, Long taskId, List<Integer> indexes);
}
