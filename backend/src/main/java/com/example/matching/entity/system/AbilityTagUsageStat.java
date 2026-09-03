package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 标签使用统计表实体
 * <p>
 * 记录每个标签的使用热度，支撑：
 * - 标签推荐排序
 * - 新兴岗位标签候选召回
 * - 标签治理优先级
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_tag_usage_stat")
public class AbilityTagUsageStat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 能力标签ID */
    private Long tagId;

    /** 被多少个岗位引用 */
    private Integer usedByPostCount;

    /** 被多少个员工引用 */
    private Integer usedByEmpCount;

    /** 来源分布JSON，如{"RESUME":10,"AI_TEST":5} */
    private String sourceDistributionJson;

    /** 最近使用时间 */
    private LocalDateTime lastUsedTime;

    /** 热度分数，综合使用频次和时效 */
    private BigDecimal heatScore;

    /** 统计日期 */
    private LocalDate statDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    // ===== 非数据库字段，由Service层填充 =====

    /** 标签名称（关联查询填充） */
    @TableField(exist = false)
    private String tagName;

    /** 标签分类（关联查询填充） */
    @TableField(exist = false)
    private String tagCategory;
}
