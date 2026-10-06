package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匹配黑白名单表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_black_white_list")
public class MatchingBlackWhiteList implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 名单类型：1白名单（强制匹配），2黑名单（强制排除） */
    private Integer listType;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 设置原因备注 */
    private String remark;

    /** 状态：0失效，1生效 */
    private Integer status;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 设置人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 设置时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
