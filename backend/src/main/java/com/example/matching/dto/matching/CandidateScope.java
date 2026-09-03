package com.example.matching.dto.matching;

/**
 * 匹配候选范围
 * <p>
 * 默认 {@link #ALL_ACTIVE}：全量在职员工参与候选池（分页/分批，无硬截断）。
 * {@link #VECTOR_RECALL} 仅作为明确的性能模式：候选只取向量召回结果
 * （受 topK 限制，响应中标记 truncated）；{@link #EXPLICIT_EMPLOYEES}
 * 为显式员工列表模式。
 */
public enum CandidateScope {

    /** 全量在职员工（默认）：不允许静默截断候选池 */
    ALL_ACTIVE,

    /** 显式性能模式：仅向量召回员工（允许 topK 截断并在响应中标记） */
    VECTOR_RECALL,

    /** 显式员工列表 */
    EXPLICIT_EMPLOYEES
}
