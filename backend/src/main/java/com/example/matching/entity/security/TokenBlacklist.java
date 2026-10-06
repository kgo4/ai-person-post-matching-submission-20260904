package com.example.matching.entity.security;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Token 黑名单表实体，用于 Redis 不可用时的 JWT 撤销兜底。
 * 只写不读（定期同步到 Redis 或本地缓存），核心校验走 Redis + 本地 map。
 */
@Data
@TableName("token_blacklist")
public class TokenBlacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDateTime invalidatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
