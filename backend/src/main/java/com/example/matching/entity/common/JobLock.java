package com.example.matching.entity.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分布式任务锁实体
 */
@Data
@TableName("job_lock")
public class JobLock {
    @TableId(value = "lock_name", type = IdType.INPUT)
    private String lockName;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdTime;
}
