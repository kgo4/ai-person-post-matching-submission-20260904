package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 技能→能力规则映射表实体
 * <p>
 * 用于把 agent 提取的技能词（如 Vue3、SpringBoot）确定性归属到能力层（L1）标签，
 * 是标签分层体系中的高置信快速通道。人工维护 source=MANUAL，AI 建议 source=AI_SUGGEST。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("skill_taxonomy_map")
public class SkillTaxonomyMap implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 技能词（如 Vue3、SpringBoot、MySQL） */
    private String skillName;

    /** 归属的能力标签ID（L1） */
    private Long abilityTagId;

    /** 分类：TECHNICAL/SOFT/BUSINESS */
    private String category;

    /** 规则置信度：人工维护=1.00，AI建议<1.00 */
    private BigDecimal confidence;

    /** 来源：MANUAL/AI_SUGGEST/VECTOR_AUTO */
    private String source;

    /** 状态：0停用，1启用 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
