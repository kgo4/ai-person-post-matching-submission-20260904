package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.service.employee.EmpEmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.io.Serializable;

@Slf4j
@Service
public class EmpEmployeeServiceImpl extends ServiceImpl<EmpEmployeeMapper, EmpEmployee> implements EmpEmployeeService {

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public boolean save(EmpEmployee entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public boolean updateById(EmpEmployee entity) {
        return super.updateById(entity);
    }

    /**
     * 逻辑删除（归档）—— 收敛为不可见的档案标记，不再级联物理删除业务数据。
     *
     * <p><b>历史实现的问题</b>：原实现在置位 {@code is_deleted} 之前，会先对该员工的
     * Harness 校验日志、能力画像、能力声明与等级决策、能力工作流、AI 测试与简历解析、
     * 学习记录、匹配记录等 25 张表执行 {@code DELETE FROM} 物理删除。这条路径不可逆，
     * 与「HR 只能禁用员工、不允许删除员工」的业务口径直接冲突——一次误点即造成
     * 该员工全部历史数据永久丢失。</p>
     *
     * <p>现改为纯逻辑删除：仅写 {@code is_deleted=1}，所有历史业务数据保留，
     * 可在数据库层面恢复。业务口径见 docs/hr-matching-closed-loop-design.md S1/G1。</p>
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        return super.removeById(id);
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE,
               key = "'page:' + #page.current + ':' + #page.size + ':' + (#keyword != null ? #keyword : '') + ':' + (#status != null ? #status : '')", sync = true)
    public IPage<EmpEmployee> pageEmployees(IPage<EmpEmployee> page, String keyword, Integer status) {
        LambdaQueryWrapper<EmpEmployee> wrapper = Wrappers.<EmpEmployee>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(EmpEmployee::getEmpCode, keyword)
                    .or().like(EmpEmployee::getRealName, keyword));
        }
        if (status != null) {
            wrapper.eq(EmpEmployee::getStatus, status);
        }
        wrapper.orderByDesc(EmpEmployee::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public int batchImport(List<EmpEmployee> list) {
        int successCount = 0;
        for (EmpEmployee emp : list) {
            long count = count(Wrappers.<EmpEmployee>lambdaQuery().eq(EmpEmployee::getEmpCode, emp.getEmpCode()));
            if (count > 0) {
                continue;
            }
            emp.setDepartmentId(null);
            emp.setCurrentPostId(null);
            emp.setEntryDate(null);
            emp.setLevel(null);
            if (emp.getStatus() == null) {
                emp.setStatus(1);
            }
            save(emp);
            successCount++;
        }
        return successCount;
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public void lockEmployee(Long id) {
        EmpEmployee emp = getById(id);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }
        emp.setIsLocked(1);
        updateById(emp);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public void unlockEmployee(Long id) {
        EmpEmployee emp = getById(id);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }
        emp.setIsLocked(0);
        updateById(emp);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "状态只能是 0（禁用）或 1（启用）");
        }
        EmpEmployee emp = getById(id);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }
        emp.setStatus(status);
        updateById(emp);
        log.info("员工状态变更: empId={}, empCode={}, status={}", emp.getId(), emp.getEmpCode(), status);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public void archive(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "作废原因不能为空");
        }
        EmpEmployee emp = getById(id);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }
        // 在职人员必须先禁用：避免把在册员工误作废成“查不到又没标记离职”的悬空档案
        if (emp.getStatus() != null && emp.getStatus() == 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "该员工仍在职，请先『禁用』；作废档案仅用于误建档案纠错");
        }
        log.warn("员工档案作废（逻辑删除，历史业务数据保留）: empId={}, empCode={}, reason={}",
                emp.getId(), emp.getEmpCode(), reason);
        super.removeById(id);
    }

    @Override
    public EmpEmployee getByUserId(Long userId) {
        return getBaseMapper().selectOne(
                Wrappers.<EmpEmployee>lambdaQuery().eq(EmpEmployee::getUserId, userId));
    }

    @Override
    public boolean updateEmailByUserId(Long userId, String email) {
        if (userId == null) {
            return false;
        }
        // 必须用 set 而不是 updateById：个人中心允许把邮箱清空（空串 → null），
        // 而 MP 的 updateById 会忽略 null 字段 —— 清空会静默失败，档案邮箱保持旧值，
        // 邀请邮件继续发到旧地址。
        return update(Wrappers.<EmpEmployee>lambdaUpdate()
                .eq(EmpEmployee::getUserId, userId)
                .set(EmpEmployee::getEmail, email));
    }

    @Override
    public boolean isEmpCodeDuplicate(String empCode) {
        return getBaseMapper().countByEmpCodeIncludingDeleted(empCode) > 0;
    }
}
