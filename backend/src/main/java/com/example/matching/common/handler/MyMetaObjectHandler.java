package com.example.matching.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.example.matching.utils.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * <p>
 * 自动填充 createdBy/createdTime/updatedBy/updatedTime
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, getCurrentUserId());
        this.strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
    }

    private Long getCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId != null ? userId : 0L;
    }
}
