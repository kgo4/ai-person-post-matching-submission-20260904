package com.example.matching.service.common;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.ApprovalStatusEnum;
import com.example.matching.common.enums.MatchStatusEnum;
import com.example.matching.dto.excel.EmpAbilityExcelDTO;
import com.example.matching.dto.excel.EmpExcelDTO;
import com.example.matching.dto.excel.MatchResultExcelDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.utils.ExcelListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入导出服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final com.example.matching.service.ability.PersonAbilityClaimAdmissionService personClaimAdmissionService;
    private final MatchingRecordMapper matchingRecordMapper;
    private final PostPostMapper postPostMapper;
    private final EmpCodeGenerator empCodeGenerator;

    // ==================== 员工导入导出 ====================

    /**
     * 从 Excel 导入员工
     */
    @Transactional
    public int importEmployees(String filename, InputStream inputStream) {
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 格式");
        }

        List<EmpExcelDTO> list = new ArrayList<>();
        ExcelListener<EmpExcelDTO> listener = new ExcelListener<>(list::addAll);
        EasyExcel.read(inputStream, EmpExcelDTO.class, listener).sheet().doRead();
        list.addAll(listener.getAllData());

        int success = 0;
        for (EmpExcelDTO dto : list) {
            if (dto.getRealName() == null || dto.getRealName().isBlank()) continue;

            String empCode = dto.getEmpCode();
            // 工号为空时自动生成
            if (empCode == null || empCode.isBlank()) {
                empCode = empCodeGenerator.generateNext();
            } else {
                empCode = empCode.trim();
                // 含逻辑删除行：物理行仍占用 uk_emp_code 唯一索引
                long exist = empEmployeeMapper.countByEmpCodeIncludingDeleted(empCode);
                if (exist > 0) continue;
            }

            EmpEmployee emp = new EmpEmployee();
            emp.setEmpCode(empCode);
            emp.setRealName(dto.getRealName().trim());
            emp.setGender(dto.getGender());
            emp.setPhone(dto.getPhone());
            emp.setEmail(dto.getEmail());
            emp.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
            empEmployeeMapper.insert(emp);
            success++;
        }
        return success;
    }

    /**
     * 导出员工到 Excel
     */
    public byte[] exportEmployees() {
        List<EmpEmployee> list = empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .orderByDesc(EmpEmployee::getCreatedTime));

        List<EmpExcelDTO> exportList = list.stream().map(emp -> {
            EmpExcelDTO dto = new EmpExcelDTO();
            dto.setEmpCode(emp.getEmpCode());
            dto.setRealName(emp.getRealName());
            dto.setGender(emp.getGender());
            dto.setPhone(emp.getPhone());
            dto.setEmail(emp.getEmail());
            dto.setStatus(emp.getStatus());
            return dto;
        }).toList();

        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        EasyExcel.write(output, EmpExcelDTO.class).sheet("employees").doWrite(exportList);
        return output.toByteArray();
    }

    // ==================== 能力导入导出 ====================

    /**
     * 从 Excel 导入员工能力
     *
     * @deprecated 已废弃。员工能力正式写入统一走能力评估工作流或 GovernedAdmissionServiceImpl 治理准入，
     * 此 Excel 批量人工导入旧链路已无调用方，保留仅供兼容，勿新增调用。
     */
    @Deprecated
    @Transactional
    public int importAbilities(MultipartFile file) throws IOException {
        List<EmpAbilityExcelDTO> list = new ArrayList<>();
        try (InputStream is = file.getInputStream()) {
            ExcelListener<EmpAbilityExcelDTO> listener = new ExcelListener<>(list::addAll);
            EasyExcel.read(is, EmpAbilityExcelDTO.class, listener).sheet().doRead();
            list.addAll(listener.getAllData());
        }

        int success = 0;
        for (EmpAbilityExcelDTO dto : list) {
            if (dto.getEmpCode() == null || dto.getTagCode() == null) continue;

            EmpEmployee emp = empEmployeeMapper.selectOne(
                    Wrappers.<EmpEmployee>lambdaQuery().eq(EmpEmployee::getEmpCode, dto.getEmpCode().trim()));
            if (emp == null) continue;

            AbilityTag tag = abilityTagMapper.selectOne(
                    Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getTagCode, dto.getTagCode().trim()));
            if (tag == null) continue;

            // Task9：正式写入统一经治理入口（PersonAbilityClaimAdmissionService），
            // 不直接写 emp_ability；人工导入视为 Harness PASS 的受信数据
            com.example.matching.agent.dto.person.PersonAbilityClaim claim =
                    new com.example.matching.agent.dto.person.PersonAbilityClaim();
            claim.setEmpId(emp.getId());
            claim.setAbilityName(tag.getTagName());
            claim.setAbilityTagId(tag.getId());
            claim.setMasteryLevel(dto.getMasteryLevel() != null ? dto.getMasteryLevel() : 1);
            claim.setSourceType(dto.getEvaluationSource() != null ? dto.getEvaluationSource() : "MANUAL");
            claim.setEvidenceText(dto.getRemark());
            claim.setSourceRefs(List.of("source:MANUAL:" + emp.getId()));
            com.example.matching.dto.harness.AiHarnessDecisionDTO decision =
                    new com.example.matching.dto.harness.AiHarnessDecisionDTO();
            decision.setDecision("PASS");
            decision.setReasons(List.of("Excel 人工导入"));
            personClaimAdmissionService.admitWithoutSideEffects(claim, decision);
            personClaimAdmissionService.completeBatchForEmployee(emp.getId());
            success++;
        }
        return success;
    }

    // ==================== 匹配结果导出 ====================

    // ==================== 模板下载 ====================

    /** Builds a match-result workbook without depending on the servlet API. */
    public byte[] buildMatchResultsExcel(Long postId) {
        List<MatchingRecord> records = matchingRecordMapper.selectList(
                Wrappers.<MatchingRecord>lambdaQuery()
                        .eq(postId != null, MatchingRecord::getPostId, postId)
                        .orderByDesc(MatchingRecord::getAiMatchScore));
        List<MatchResultExcelDTO> rows = records.stream().map(record -> {
            MatchResultExcelDTO dto = new MatchResultExcelDTO();
            dto.setBatchNo(record.getBatchNo());
            EmpEmployee employee = empEmployeeMapper.selectById(record.getEmpId());
            dto.setEmpCode(employee == null ? "" : employee.getEmpCode());
            dto.setEmpName(employee == null ? "" : employee.getRealName());
            dto.setAiMatchScore(record.getAiMatchScore());
            dto.setFinalMatchScore(record.getFinalMatchScore());
            dto.setMatchStatus(MatchStatusEnum.getNameByCode(record.getMatchStatus()));
            dto.setApprovalStatus(ApprovalStatusEnum.getNameByCode(record.getApprovalStatus()));
            dto.setIsLocked(record.getIsLocked() != null && record.getIsLocked() == 1 ? "yes" : "no");
            dto.setManualRemark(record.getManualRemark());
            return dto;
        }).toList();
        if (postId != null) {
            var post = postPostMapper.selectById(postId);
            String postName = post == null ? "" : post.getPostName();
            rows.forEach(dto -> dto.setPostName(postName));
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        EasyExcel.write(output, MatchResultExcelDTO.class).sheet("matching-results").doWrite(rows);
        return output.toByteArray();
    }

    public byte[] downloadEmployeeTemplate() {
        List<EmpExcelDTO> demo = List.of(new EmpExcelDTO());
        demo.get(0).setEmpCode("留空自动生成");
        demo.get(0).setRealName("示例：张三");
        demo.get(0).setGender(1);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        EasyExcel.write(output, EmpExcelDTO.class).sheet("employees").doWrite(demo);
        return output.toByteArray();
    }

    public void downloadAbilityTemplate(HttpServletResponse response) throws IOException {
        List<EmpAbilityExcelDTO> demo = List.of(new EmpAbilityExcelDTO());
        demo.get(0).setEmpCode("示例：E00001");
        demo.get(0).setTagCode("示例：TECH_JAVA");
        demo.get(0).setMasteryLevel(3);
        demo.get(0).setEvaluationSource("MANUAL");
        setExcelResponse(response, "能力导入模板.xlsx");
        EasyExcel.write(response.getOutputStream(), EmpAbilityExcelDTO.class).sheet("员工能力").doWrite(demo);
    }

    // ==================== 工具方法 ====================

    private void setExcelResponse(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }
}
