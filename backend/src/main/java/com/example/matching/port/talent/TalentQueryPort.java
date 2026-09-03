package com.example.matching.port.talent;

import java.util.List;

/**
 * 人才查询端口 — 公开只读接口。
 */
public interface TalentQueryPort {

    /** 员工基本信息 DTO */
    record EmployeeDTO(
            Long id,
            String realName,
            String employeeNo,
            Integer gender,
            String level,
            Long departmentId,
            Long currentPostId,
            Integer status
    ) {
        public static EmployeeDTO from(com.example.matching.entity.employee.EmpEmployee e) {
            return new EmployeeDTO(e.getId(), e.getRealName(), e.getEmpCode(),
                    e.getGender(), e.getLevel(), e.getDepartmentId(),
                    e.getCurrentPostId(), e.getStatus());
        }
    }

    /** 员工能力记录 DTO */
    record EmployeeAbilityDTO(
            Long id,
            Long empId,
            Long tagId,
            Integer masteryLevel,
            String evaluationSource,
            java.math.BigDecimal sourceWeight,
            java.time.LocalDate evaluationDate,
            String remark,
            String abilityName
    ) {
        public EmployeeAbilityDTO(Long id, Long empId, Long tagId, Integer masteryLevel,
                                  String evaluationSource, java.math.BigDecimal sourceWeight,
                                  java.time.LocalDate evaluationDate, String remark) {
            this(id, empId, tagId, masteryLevel, evaluationSource, sourceWeight, evaluationDate, remark, null);
        }

        public static EmployeeAbilityDTO from(com.example.matching.entity.employee.EmpAbility a) {
            return new EmployeeAbilityDTO(a.getId(), a.getEmpId(), a.getTagId(),
                    a.getMasteryLevel(), a.getEvaluationSource(),
                    a.getSourceWeight(), a.getEvaluationDate(), a.getRemark(), a.getAbilityName());
        }
    }

    EmployeeDTO getEmployeeById(Long empId);

    List<EmployeeDTO> batchGetEmployees(List<Long> empIds);

    List<EmployeeAbilityDTO> listAbilitiesByEmpId(Long empId);

    /** 按 ID 查询单条能力记录，未找到返回 null */
    EmployeeAbilityDTO getEmpAbilityById(Long abilityId);

    /** 按员工+标签+评价来源查询单条能力记录，未找到返回 null */
    EmployeeAbilityDTO getEmpAbility(Long empId, Long tagId, String evaluationSource);

    /** 分页列出活跃的能力记录（用于批量回填） */
    List<EmployeeAbilityDTO> listActiveAbilities(int limit);

    /** 分页列出活跃的员工 */
    List<EmployeeDTO> listActiveEmployees(int limit);

    /** 分页列出活跃的员工（支持大数据量） */
    List<EmployeeDTO> listEmployeesPaginated(int page, int size);

    /** 分页列出活跃的能力记录（支持大数据量） */
    List<EmployeeAbilityDTO> listAbilitiesPaginated(int page, int size);

    /** 简历解析记录 DTO */
    record ResumeParseDTO(
            Long id,
            Long empId,
            String fileName,
            String parsedContent
    ) {
        public static ResumeParseDTO from(com.example.matching.entity.employee.EmpResumeParse p) {
            return new ResumeParseDTO(p.getId(), p.getEmpId(), p.getFileName(), p.getParsedContent());
        }
    }

    /** 分页列出已完成（status=2）的简历解析记录，用于证据回填 */
    List<ResumeParseDTO> listCompletedResumeParses(int limit);

    /** 查询员工最新一条已完成（status=2）的简历解析记录，未找到返回 null */
    ResumeParseDetailDTO findLatestCompletedResumeParse(Long empId);

    /** 简历解析详情 DTO（含 AI 分析结果，供硬条件评估等只读消费） */
    record ResumeParseDetailDTO(
            Long id,
            Long empId,
            String parsedContent,
            String aiAnalysisResult
    ) {}

    /** 全量员工数量（报表统计用） */
    long countAllEmployees();

    /** 全量员工能力记录（报表统计用） */
    List<EmployeeAbilityDTO> listAllAbilities();

    /** 按标签统计员工能力记录数量(isDeleted=0) */
    long countAbilitiesByTagId(Long tagId);
}
