package com.example.archfixture.service.system;

import com.example.matching.mapper.employee.EmpAbilityMapper;
import org.springframework.stereotype.Service;

/**
 * 故意违反跨域 Mapper 规则的 fixture 类，用于验证 ArchitectureRulesTest 的规则能捕获违规。
 * <p>
 * 此 fixture 模拟一个业务服务直接注入 employee 领域 Mapper。
 * 它不会被生产代码引用，仅用于规则自检。
 */
@Service
public class CrossDomainMapperViolationFixture {

    private final EmpAbilityMapper empAbilityMapper;

    public CrossDomainMapperViolationFixture(EmpAbilityMapper empAbilityMapper) {
        this.empAbilityMapper = empAbilityMapper;
    }
}
