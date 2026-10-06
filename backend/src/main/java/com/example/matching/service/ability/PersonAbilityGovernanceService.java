package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;

import java.util.List;

/**
 * 人员能力治理服务接口
 * <p>
 * 处理人工对最终入库能力标签的修改，记录治理事件，生成 Agent 记忆。
 *
 * @author system
 */
public interface PersonAbilityGovernanceService {

    /**
     * 修改最终能力标签（标签替换）
     *
     * @param empId      员工ID
     * @param oldTagId   原标签ID
     * @param newTagId   新标签ID
     * @param reason     修改原因
     * @param operatorId 操作人ID
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent replaceTag(Long empId, Long oldTagId, Long newTagId, String reason, Long operatorId);

    /**
     * 修改最终能力标签（标签替换），可选是否泛化为全局规则。
     *
     * @param empId          员工ID
     * @param oldTagId       原标签ID
     * @param newTagId       新标签ID
     * @param reason         修改原因
     * @param operatorId     操作人ID
     * @param generalizeRule 是否勾选"作为同类提取规则"，true时直接生成ACTIVE记忆
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent replaceTag(Long empId, Long oldTagId, Long newTagId, String reason, Long operatorId, boolean generalizeRule);

    /**
     * 修改最终能力等级
     *
     * @param empId      员工ID
     * @param tagId      标签ID
     * @param newLevel   新等级
     * @param reason     修改原因
     * @param operatorId 操作人ID
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent changeLevel(Long empId, Long tagId, Integer newLevel, String reason, Long operatorId);

    /**
     * 修改最终能力等级，可选是否泛化为全局规则。
     *
     * @param empId          员工ID
     * @param tagId          标签ID
     * @param newLevel       新等级
     * @param reason         修改原因
     * @param operatorId     操作人ID
     * @param generalizeRule 是否泛化规则
     * @param maxLevelCap    自定义等级上限（null则不设上限）
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent changeLevel(Long empId, Long tagId, Integer newLevel, String reason, Long operatorId,
                                              boolean generalizeRule, Integer maxLevelCap);

    /**
     * 删除最终能力标签
     *
     * @param empId      员工ID
     * @param tagId      标签ID
     * @param reason     删除原因
     * @param operatorId 操作人ID
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent removeTag(Long empId, Long tagId, String reason, Long operatorId);

    /**
     * 删除最终能力标签，可选是否泛化规则。
     *
     * @param empId          员工ID
     * @param tagId          标签ID
     * @param reason         删除原因
     * @param operatorId     操作人ID
     * @param generalizeRule 是否勾选"以后拒绝此映射"
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent removeTag(Long empId, Long tagId, String reason, Long operatorId, boolean generalizeRule);

    /**
     * 标签重命名（影响所有引用）
     *
     * @param tagId      标签ID
     * @param newName    新标签名称
     * @param reason     重命名原因
     * @param operatorId 操作人ID
     * @return 治理事件列表
     */
    List<PersonAbilityGovernanceEvent> renameTag(Long tagId, String newName, String reason, Long operatorId);

    /**
     * 获取员工的治理事件历史
     *
     * @param empId 员工ID
     * @return 治理事件列表
     */
    List<PersonAbilityGovernanceEvent> getGovernanceHistory(Long empId);

    /**
     * 获取标签相关的治理事件
     *
     * @param tagId 标签ID
     * @return 治理事件列表
     */
    List<PersonAbilityGovernanceEvent> getGovernanceByTag(Long tagId);

    /**
     * 创建治理事件（直接保存事件对象）
     *
     * @param event 治理事件对象
     * @return 保存后的事件
     */
    PersonAbilityGovernanceEvent createEvent(PersonAbilityGovernanceEvent event);

    /**
     * 根据治理事件生成Agent记忆草稿
     *
     * @param event 治理事件
     */
    void generateAgentMemory(PersonAbilityGovernanceEvent event);
}
