package com.example.matching.dto.post.api;

import java.io.Serializable;

/**
 * 「采用现有标签」：把 AI 提出的能力对接到一个既有标签上。
 * <p>
 * 与「忽略」的区别是语义：忽略 = 认为这个能力名不该进标签体系；
 * 采用 = 能力本身有效，只是命名不同，应归到某个既有标签。
 */
public record TrendTagAdoptRequest(Long tagId, String comment) implements Serializable {
}
