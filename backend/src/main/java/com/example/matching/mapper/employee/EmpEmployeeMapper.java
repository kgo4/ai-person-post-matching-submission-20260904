package com.example.matching.mapper.employee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.employee.EmpEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工基础信息 Mapper
 */
@Mapper
public interface EmpEmployeeMapper extends BaseMapper<EmpEmployee> {

    /**
     * 忽略逻辑删除查询当日最大编号（原生 SQL，不受 @TableLogic 过滤）。
     * 原因：物理行即使 is_deleted=1 仍占用 uk_emp_code 唯一索引，编号生成必须把已逻辑删除的行计入，
     * 否则会重复生成被占用的编号导致 INSERT 冲突（Duplicate entry ... uk_emp_code）。
     */
    @Select("SELECT emp_code FROM emp_employee WHERE emp_code LIKE CONCAT(#{prefix}, '%') ORDER BY emp_code DESC LIMIT 1")
    String selectMaxEmpCodeLikePrefix(@Param("prefix") String prefix);

    /**
     * 忽略逻辑删除校验编号是否被任何物理行占用（含已逻辑删除行）。
     */
    @Select("SELECT COUNT(*) FROM emp_employee WHERE emp_code = #{empCode}")
    long countByEmpCodeIncludingDeleted(@Param("empCode") String empCode);
}
