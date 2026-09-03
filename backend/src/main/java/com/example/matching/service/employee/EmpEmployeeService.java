package com.example.matching.service.employee;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.employee.EmpEmployee;

import java.util.List;

/**
 * Personnel profile service for matching.
 */
public interface EmpEmployeeService extends IService<EmpEmployee> {

    IPage<EmpEmployee> pageEmployees(IPage<EmpEmployee> page, String keyword, Integer status);

    int batchImport(List<EmpEmployee> list);

    void lockEmployee(Long id);

    void unlockEmployee(Long id);

    /**
     * 根据绑定的系统用户ID查找员工（移动端使用）。
     *
     * @param userId 系统用户ID
     * @return 对应的员工实体，未绑定时返回null
     */
    EmpEmployee getByUserId(Long userId);

    /**
     * 校验员工工号是否已存在（含逻辑删除行：物理行仍占用 uk_emp_code 唯一索引）。
     *
     * @param empCode 工号
     * @return true=已存在，不允许重复使用
     */
    boolean isEmpCodeDuplicate(String empCode);
}
