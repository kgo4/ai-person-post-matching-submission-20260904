package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 能力标签别名表实体
 * <p>
 * 用于标签归并后保留历史标签名称，避免引用失效。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_tag_alias")
public class AbilityTagAlias implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 主标签ID（归并后的目标标签） */
    private Long tagId;

    /** 别名（原标签名称） */
    private String aliasName;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
