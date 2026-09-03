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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.io.Serializable;

@Service
public class EmpEmployeeServiceImpl extends ServiceImpl<EmpEmployeeMapper, EmpEmployee> implements EmpEmployeeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.EMP_EMPLOYEE_PAGE, allEntries = true)
    public boolean removeById(Serializable id) {
        if (id == null) return false;
        long empId = Long.parseLong(String.valueOf(id));
        // 先删除引用员工的业务数据，避免 Harness/工作流留下无法归属的孤儿记录。
        jdbcTemplate.update("DELETE FROM ai_harness_check_log WHERE business_target_type='EMP_ABILITY' "
                + "AND business_target_id IN (SELECT id FROM emp_ability WHERE emp_id=?)", empId);
        jdbcTemplate.update("DELETE h FROM ai_harness_check_log h JOIN emp_resume_parse r "
                + "ON h.source_refs LIKE CONCAT('%RESUME_PARSE:', r.id, '%') WHERE r.emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM ability_harness_batch_item WHERE claim_group_id IN "
                + "(SELECT id FROM person_ability_claim_group WHERE emp_id=?)", empId);
        jdbcTemplate.update("DELETE FROM person_ability_level_decision WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM person_ability_claim WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM person_ability_claim_group WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM person_ability_governance_event WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM person_ability_profile WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM person_capability_workflow WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM emp_video_interview_session WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM interview_ability_observation WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM emp_ai_test WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM emp_resume_parse WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM emp_ability WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM learning_progress_log WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM learning_mastery_log WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM learning_project_submission WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM learning_quiz_record WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM ai_learning_suggestion_log WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM learning_path_plan WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM matching_feedback_dataset WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM matching_black_white_list WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM matching_record WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM pms_analysis_task WHERE emp_id=?", empId);
        jdbcTemplate.update("DELETE FROM pms_user_mapping WHERE emp_id=?", empId);
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
    public EmpEmployee getByUserId(Long userId) {
        return getBaseMapper().selectOne(
                Wrappers.<EmpEmployee>lambdaQuery().eq(EmpEmployee::getUserId, userId));
    }

    @Override
    public boolean isEmpCodeDuplicate(String empCode) {
        return getBaseMapper().countByEmpCodeIncludingDeleted(empCode) > 0;
    }
}
