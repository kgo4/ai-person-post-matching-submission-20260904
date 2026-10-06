package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * PMS用户映射表实体
 * <p>
 * 用于建立本地员工与PMS系统用户的关联关系。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pms_user_mapping")
public class PmsUserMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 本地员工ID */
    private Long empId;

    /** PMS用户ID */
    private Long pmsUserId;

    /** PMS用户名(冗余) */
    private String pmsUsername;

    /** PMS昵称(冗余) */
    private String pmsNickname;

    /** PMS工号(冗余) */
    private String pmsEmployeeId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
