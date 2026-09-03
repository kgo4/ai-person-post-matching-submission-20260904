package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识图谱快照实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kg_graph_snapshot")
public class KgGraphSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 快照编码 */
    private String snapshotCode;

    /** 快照名称 */
    private String snapshotName;

    /** 快照类型：FULL/POST/EMPLOYEE/CONTEST_DEMO */
    private String snapshotType;

    /** 节点数量 */
    private Integer nodeCount;

    /** 边数量 */
    private Integer edgeCount;

    /** 图JSON快照 */
    private String snapshotJson;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
