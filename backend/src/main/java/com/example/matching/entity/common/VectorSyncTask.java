package com.example.matching.entity.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 向量同步任务：监听器只负责入队，后台任务负责写入 Milvus。
 * <p>
 * business_key 为业务唯一键（EMPLOYEE:{id} / POST:{id}），保证同一实体仅一条待办记录；
 * 执行逻辑幂等：始终以最新业务数据覆盖旧向量。
 */
@Data
@TableName("vector_sync_task")
public class VectorSyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务唯一键：EMPLOYEE:{empId} / POST:{postId} */
    private String businessKey;

    /** EMPLOYEE / POST */
    private String entityType;

    /** 员工ID或岗位ID */
    private Long entityId;

    /** PENDING/PROCESSING/SUCCEEDED/FAILED */
    private String status;

    private Integer attemptCount;

    private Integer maxAttempts;

    private LocalDateTime nextRetryTime;

    private String errorMessage;

    private LocalDateTime createdTime;

    private LocalDateTime publishedTime;

    private LocalDateTime updatedTime;
}
