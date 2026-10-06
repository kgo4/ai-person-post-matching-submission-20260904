package com.example.matching.service.employee;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.vo.employee.EmpAbilityProfileVO;

import java.util.List;

/**
 * 员工能力 服务接口
 */
public interface EmpAbilityService extends IService<EmpAbility> {

    /** 保存能力记录 */
    void saveAbility(EmpAbilitySaveDTO dto);

    /** 获取员工能力画像 */
    EmpAbilityProfileVO getProfile(Long empId);

    /** 按员工ID查询能力列表 */
    List<EmpAbility> listByEmpId(Long empId);

    /** Returns raw source claims that are awaiting Harness review before profile fusion. */
    List<PersonAbilityClaim> listPendingClaims(Long empId);

    /** 批量保存能力 */
    void batchSave(List<EmpAbilitySaveDTO> list);
}
