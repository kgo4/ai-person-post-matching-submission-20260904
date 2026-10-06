package com.example.matching.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 岗位模型变更事件（当岗位能力模型或模板发生变化时发布）
 */
@Getter
public class PostModelChangeEvent extends ApplicationEvent {

    /** 变更类型：MODEL_CONFIG-模型配置变更，TEMPLATE_CHANGE-模板变更 */
    private final String changeType;

    /** 关联的岗位ID或模板ID */
    private final Long entityId;

    public PostModelChangeEvent(Object source, String changeType, Long entityId) {
        super(source);
        this.changeType = changeType;
        this.entityId = entityId;
    }
}
