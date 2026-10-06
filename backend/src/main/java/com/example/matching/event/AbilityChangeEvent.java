package com.example.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 能力变更事件（当员工能力或标签配置发生变化时发布）
 */
@Getter
public class AbilityChangeEvent extends ApplicationEvent {

    /** 变更类型：EMP_ABILITY-员工能力变更，TAG_CONFIG-标签配置变更 */
    private final String changeType;

    /** 关联的实体ID（员工ID或标签ID） */
    private final Long entityId;

    public AbilityChangeEvent(Object source, String changeType, Long entityId) {
        super(source);
        this.changeType = changeType;
        this.entityId = entityId;
    }
}
