package com.example.matching.service.common;

import com.example.matching.mapper.employee.EmpEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Generates employee business codes for all employee import and creation workflows. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmpCodeGenerator {

    private static final String PREFIX = "EMP";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RETRIES = 5;

    private final EmpEmployeeMapper empEmployeeMapper;

    public String generateNext() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String code = generateCandidate();
            // 含逻辑删除行计数：物理行即使 is_deleted=1 仍占用 uk_emp_code 唯一索引
            long exists = empEmployeeMapper.countByEmpCodeIncludingDeleted(code);
            if (exists == 0) {
                return code;
            }
            log.warn("Employee code collision for {}, retry {}", code, i + 1);
        }
        throw new IllegalStateException("Unable to generate a unique employee code after " + MAX_RETRIES + " attempts");
    }

    private String generateCandidate() {
        String today = LocalDate.now().format(DATE_FMT);
        // 原生 SQL：含逻辑删除行，避免与唯一索引冲突
        String maxCode = empEmployeeMapper.selectMaxEmpCodeLikePrefix(PREFIX + today);
        int nextSeq = 1;
        if (maxCode != null && !maxCode.isBlank()) {
            String seqPart = maxCode.substring(PREFIX.length() + 8);
            nextSeq = Integer.parseInt(seqPart) + 1;
        }
        return PREFIX + today + String.format("%04d", nextSeq);
    }
}
