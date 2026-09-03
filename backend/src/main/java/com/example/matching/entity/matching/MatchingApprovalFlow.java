package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匹配审批流程表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_approval_flow")
public class MatchingApprovalFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 审批节点顺序 */
    private Integer nodeOrder;

    /** 审批节点名称 */
    private String nodeName;

    /** 审批人ID */
    private Long approverId;

    /** 审批状态：0待审批，1审批通过，2审批驳回 */
    private Integer approvalStatus;

    /** 审批意见 */
    private String approvalRemark;

    /** 审批时间 */
    private LocalDateTime approvalTime;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
