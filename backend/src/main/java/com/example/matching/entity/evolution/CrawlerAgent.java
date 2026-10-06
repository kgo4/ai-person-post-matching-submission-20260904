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
 * 本地爬虫心跳台账。
 * <p>
 * 上游对接文档 §9 的 {@code POST /api/internal/crawler/heartbeat} 落库目标，
 * 用于判断「本地爬虫是否在线」，从而在不访问本地电脑的前提下告知前端能否下发采集命令。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("crawler_agent")
public class CrawlerAgent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 本地爬虫实例标识 */
    private String agentId;

    /** 实例名称 */
    private String agentName;

    /** 主机/版本等描述信息 */
    private String hostInfo;

    /** 爬虫版本号 */
    private String agentVersion;

    /** 最近一次心跳时间 */
    private LocalDateTime lastHeartbeatTime;

    /** 最近领取的命令ID */
    private String lastCommandId;

    @TableField(insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
