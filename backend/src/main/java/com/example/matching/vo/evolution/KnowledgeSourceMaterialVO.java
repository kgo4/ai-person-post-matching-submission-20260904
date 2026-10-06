package com.example.matching.vo.evolution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权威材料列表项（材料管理用）。
 *
 * <p>字段刻意收敛到「用户判断这份材料能不能用、要不要删」所需的最小集合，
 * 不把存储路径、内部哈希这类实现细节抛到界面上。
 */
@Data
public class KnowledgeSourceMaterialVO {

    /** 知识源文档 id。发起解析时要把它作为 sourceDocumentIds 提交 */
    private Long documentId;

    /** 材料标题（默认取上传文件名） */
    private String title;

    /** 资料类别，如 POLICY_DOCUMENT */
    private String sourceCategory;

    /** 资料类别的中文名，避免前端各自维护映射 */
    private String sourceCategoryLabel;

    /** 已建立的检索片段数；0 说明还没索引成功 */
    private Integer chunkCount;

    /** 文档状态：PENDING/ACTIVE/FAILED */
    private String status;

    /** 索引状态：PENDING/INDEXED/FAILED */
    private String indexStatus;

    /** 是否仅试算材料（解析任务结束后会被自动清理） */
    private Boolean ephemeral;

    /** 上传时间 */
    private LocalDateTime uploadedTime;

    /** 最后索引时间 */
    private LocalDateTime lastIndexedTime;

    /**
     * 这份材料当前能不能用于发起解析。
     *
     * <p>由后端算好而不是让前端拼条件：片段数为 0 或索引失败的材料参与解析只会得到空结果，
     * 必须在界面上禁用并说明原因，而不是等用户点了才失败。
     */
    private Boolean readyForAnalysis;
}
