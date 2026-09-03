package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.port.talent.TalentQueryPort.ResumeParseDTO;
import com.example.matching.port.talent.TalentQueryPort.ResumeParseDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TalentQueryPortAdapter implements TalentQueryPort {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final EmpResumeParseMapper empResumeParseMapper;

    @Override
    public EmployeeDTO getEmployeeById(Long empId) {
        EmpEmployee e = empEmployeeMapper.selectById(empId);
        return e != null ? EmployeeDTO.from(e) : null;
    }

    @Override
    public List<EmployeeDTO> batchGetEmployees(List<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) return List.of();
        return empEmployeeMapper.selectBatchIds(empIds).stream()
                .map(EmployeeDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<EmployeeAbilityDTO> listAbilitiesByEmpId(Long empId) {
        return empAbilityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpAbility>()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getIsDeleted, 0)
                        .orderByDesc(EmpAbility::getUpdatedTime)
        ).stream().map(EmployeeAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public EmployeeAbilityDTO getEmpAbilityById(Long abilityId) {
        if (abilityId == null) return null;
        EmpAbility a = empAbilityMapper.selectById(abilityId);
        return a != null ? EmployeeAbilityDTO.from(a) : null;
    }

    @Override
    public EmployeeAbilityDTO getEmpAbility(Long empId, Long tagId, String evaluationSource) {
        if (empId == null || tagId == null) return null;
        EmpAbility a = empAbilityMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpAbility>()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getTagId, tagId)
                        .eq(EmpAbility::getEvaluationSource, evaluationSource)
                        .eq(EmpAbility::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return a != null ? EmployeeAbilityDTO.from(a) : null;
    }

    @Override
    public List<EmployeeAbilityDTO> listActiveAbilities(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return empAbilityMapper.selectList(w).stream().map(EmployeeAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<EmployeeDTO> listActiveEmployees(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpEmployee>()
                .eq(EmpEmployee::getStatus, 1)
                .eq(EmpEmployee::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return empEmployeeMapper.selectList(w).stream().map(EmployeeDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<EmployeeDTO> listEmployeesPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<EmpEmployee>(page, size);
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpEmployee>()
                .eq(EmpEmployee::getStatus, 1)
                .eq(EmpEmployee::getIsDeleted, 0);
        return empEmployeeMapper.selectPage(p, w).getRecords().stream()
                .map(EmployeeDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<EmployeeAbilityDTO> listAbilitiesPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<EmpAbility>(page, size);
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getIsDeleted, 0);
        return empAbilityMapper.selectPage(p, w).getRecords().stream()
                .map(EmployeeAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<ResumeParseDTO> listCompletedResumeParses(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpResumeParse>()
                .eq(EmpResumeParse::getStatus, 2)
                .orderByDesc(EmpResumeParse::getCreatedTime);
        if (limit > 0) w.last("LIMIT " + limit);
        return empResumeParseMapper.selectList(w).stream()
                .map(ResumeParseDTO::from).collect(Collectors.toList());
    }

    @Override
    public ResumeParseDetailDTO findLatestCompletedResumeParse(Long empId) {
        if (empId == null) return null;
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpResumeParse>()
                .eq(EmpResumeParse::getEmpId, empId)
                .eq(EmpResumeParse::getStatus, 2)
                .orderByDesc(EmpResumeParse::getCreatedTime)
                .last("LIMIT 1");
        EmpResumeParse resume = empResumeParseMapper.selectOne(w);
        if (resume == null) return null;
        return new ResumeParseDetailDTO(resume.getId(), resume.getEmpId(),
                resume.getParsedContent(), resume.getAiAnalysisResult());
    }

    @Override
    public long countAllEmployees() {
        Long count = empEmployeeMapper.selectCount(Wrappers.<EmpEmployee>lambdaQuery());
        return count == null ? 0L : count;
    }

    @Override
    public List<EmployeeAbilityDTO> listAllAbilities() {
        return empAbilityMapper.selectList(Wrappers.<EmpAbility>lambdaQuery()).stream()
                .map(EmployeeAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public long countAbilitiesByTagId(Long tagId) {
        if (tagId == null) return 0L;
        Long count = empAbilityMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmpAbility>()
                        .eq(EmpAbility::getTagId, tagId)
                        .eq(EmpAbility::getIsDeleted, 0));
        return count == null ? 0L : count;
    }
}
