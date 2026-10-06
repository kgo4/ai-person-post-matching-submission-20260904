package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集命令视图（管理端列表 / 详情，也用于下发给爬虫命令体）。
 *
 * @author system
 */
@Data
public class CrawlerCommandView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命令业务ID，前端轮询主键 */
    private String commandId;

    /** COLLECT */
    private String commandType;

    /** 指定执行者；为空表示任意 agent */
    private String agentId;

    private List<String> sources;

    private List<String> keywords;

    private List<String> cities;

    private Integer maxItems;

    /** PENDING/DISPATCHED/RUNNING/SUCCEEDED/FAILED/EXPIRED/CANCELLED */
    private String status;

    /** 状态中文，避免前端各自维护映射 */
    private String statusText;

    /** 爬虫回传结果摘要（JSON 或说明） */
    private String resultJson;

    private String errorMessage;

    private LocalDateTime createdTime;

    private LocalDateTime dispatchedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime expireTime;
}
