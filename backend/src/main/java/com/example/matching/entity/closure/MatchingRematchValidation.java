package com.example.matching.entity.closure;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_rematch_validation")
public class MatchingRematchValidation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String closureBusinessKey;

    private Long originalMatchingRecordId;

    private String taskId;

    private Long newMatchingRecordId;

    private BigDecimal oldScore;

    private Integer oldMatchStatus;

    private BigDecimal newScore;

    private Integer newMatchStatus;

    private String validationStatus;

    private String failReason;

    private Long empId;

    private Long postId;

    private Long tagId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
