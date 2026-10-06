package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主系统下发给本地爬虫的采集命令。
 * <p>
 * 依据上游对接文档 §9：主系统**不访问本地电脑**，改为「服务器创建命令 → 本地爬虫每 10–30 秒
 * 主动轮询 → 本地执行 → 本地推送」。本表是命令队列的唯一权威存储。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("crawler_command")
public class CrawlerCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待领取 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已被某个 agent 领取 */
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    /** agent 已开始执行 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    /** 超过 expire_time 仍未被领取 */
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 当前唯一命令类型 */
    public static final String TYPE_COLLECT = "COLLECT";

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 命令业务ID */
    private String commandId;

    /** 指定执行者；NULL 表示任意 agent 可领取 */
    private String agentId;

    /** 命令类型，当前仅 COLLECT */
    private String commandType;

    /** 来源平台，JSON 数组 */
    private String sources;

    /** 关键词，JSON 数组 */
    private String keywords;

    /** 城市，JSON 数组 */
    private String cities;

    /** 单次抓取上限 */
    private Integer maxItems;

    /** PENDING/DISPATCHED/RUNNING/SUCCEEDED/FAILED/EXPIRED/CANCELLED */
    private String status;

    /** 下发给爬虫的完整命令体，JSON */
    private String payload;

    /** 爬虫回传的执行结果，JSON */
    private String resultJson;

    /** 失败原因 */
    private String errorMessage;

    /** 发起人 sys_user.id */
    private Long requestedBy;

    @TableField(insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdTime;

    private LocalDateTime dispatchedTime;

    private LocalDateTime finishedTime;

    /** 过期时间，超时未领取转 EXPIRED */
    private LocalDateTime expireTime;
}
