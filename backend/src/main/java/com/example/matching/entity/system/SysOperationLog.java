package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人真实姓名 */
    private String realName;

    /** 操作模块 */
    private String operationModule;

    /** 操作类型：INSERT/UPDATE/DELETE/EXPORT */
    private String operationType;

    /** 操作描述 */
    private String operationDesc;

    /** 请求方法：GET/POST */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数，JSON格式 */
    private String requestParams;

    /** 响应结果，JSON格式 */
    private String responseResult;

    /** 修改前数据快照，JSON格式 */
    private String beforeData;

    /** 修改后数据快照，JSON格式 */
    private String afterData;

    /** 操作IP */
    private String operationIp;

    /** 分布式链路追踪ID */
    private String traceId;

    /** 操作时间 */
    private LocalDateTime operationTime;

    /** 请求耗时，毫秒 */
    private Long costTime;
}
