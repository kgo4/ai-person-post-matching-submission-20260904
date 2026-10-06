package com.example.matching.converter.employee;

import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 员工对象转换器（MapStruct自动生成实现）
 */
@Mapper(componentModel = "spring")
public interface EmpEmployeeConverter {

    /**
     * M17：Entity 基础字段 -> 能力画像VO（业务明细由调用方补充）
     */
    @Mapping(source = "id", target = "empId")
    EmpAbilityProfileVO toAbilityProfileVO(EmpEmployee entity);
}
