package com.example.matching.dto.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知视图对象（返回给接收人本人，不含内部字段）。
 *
 * @param id          通知ID
 * @param type        通知类型
 * @param title       标题
 * @param content     正文
 * @param bizType     业务类型（前端跳转依据）
 * @param bizId       业务主键
 * @param readStatus  0未读 1已读
 * @param createdTime 发送时间
 * @param readTime    已读时间
 * @author system
 */
@Schema(description = "站内通知")
public record NotificationVO(
    Long id,
    String type,
    String title,
    String content,
    String bizType,
    Long bizId,
    Integer readStatus,
    LocalDateTime createdTime,
    LocalDateTime readTime
) implements Serializable {

    public static NotificationVO from(com.example.matching.entity.notification.SysNotification n) {
        return new NotificationVO(
            n.getId(), n.getType(), n.getTitle(), n.getContent(),
            n.getBizType(), n.getBizId(), n.getReadStatus(),
            n.getCreatedTime(), n.getReadTime()
        );
    }
}
