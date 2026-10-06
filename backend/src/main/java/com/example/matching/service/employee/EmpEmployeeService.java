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
     * 启用 / 禁用员工（HR 对员工档案唯一的“下线”手段）。
     *
     * <p>与锁定（{@code is_locked}）不同：禁用会改变员工的有效状态（{@code status=0}），
     * 使其不再作为在册人员参与匹配与评估运营；锁定只是防误改标记。</p>
     *
     * @param id     员工档案ID
     * @param status 0=禁用，1=启用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 作废人员档案（逻辑删除，{@code is_deleted=1}）。
     *
     * <p>仅用于误建档案纠错：在职人员必须先禁用才能作废。作废后档案从列表与查询中隐藏，
     * 但历史业务数据（能力、匹配、学习记录）全部保留，可在数据库层面恢复。</p>
     *
     * @param id     员工档案ID
     * @param reason 作废原因，必填（留痕用）
     */
    void archive(Long id, String reason);

    /**
     * 根据绑定的系统用户ID查找员工（移动端使用）。
     *
     * @param userId 系统用户ID
     * @return 对应的员工实体，未绑定时返回null
     */
    EmpEmployee getByUserId(Long userId);

    /**
     * 按绑定的系统用户ID更新员工档案邮箱。
     * <p>
     * 用途：员工在个人中心自助修改邮箱时，把 {@code sys_user.email} 的新值同步到
     * {@code emp_employee.email}。视频终面邀请邮件的收件人取自后者，
     * 不同步会导致「员工改了邮箱但邀请仍发到旧地址」。
     *
     * @param userId 系统用户ID
     * @param email  新邮箱；{@code null} 表示清空（个人中心传空串即为此意）
     * @return 是否命中了员工档案；账号未绑定档案时返回 {@code false}
     */
    boolean updateEmailByUserId(Long userId, String email);

    /**
     * 校验员工工号是否已存在（含逻辑删除行：物理行仍占用 uk_emp_code 唯一索引）。
     *
     * @param empCode 工号
     * @return true=已存在，不允许重复使用
     */
    boolean isEmpCodeDuplicate(String empCode);
}
