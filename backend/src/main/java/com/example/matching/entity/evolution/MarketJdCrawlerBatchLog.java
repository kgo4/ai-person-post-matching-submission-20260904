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
 * 爬虫推送批次处理日志。
 * <p>
 * 满足上游对接文档 §7.5「记录请求 IP、批次号、来源和处理结果」，同时承载
 * 「推送数据是否已进入分析链路」的可见状态（{@code analysisState}），
 * 让运维在前端直接看到「推了多少、去重多少、解析到什么程度」。
 * <p>
 * 写入失败不影响接收结果（调用侧 try/catch + warn）。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("market_jd_crawler_batch_log")
public class MarketJdCrawlerBatchLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结果状态：批次全部成功 */
    public static final String RESULT_OK = "OK";
    /** 结果状态：部分条目落库失败 */
    public static final String RESULT_PARTIAL_FAILED = "PARTIAL_FAILED";
    /** 结果状态：整批被拒（参数/业务校验不通过） */
    public static final String RESULT_REJECTED = "REJECTED";
    /** 结果状态：历史批次（迁移补登记，无原始接收动作） */
    public static final String RESULT_HISTORICAL = "HISTORICAL";
    /** 结果状态：鉴权失败 */
    public static final String RESULT_UNAUTHORIZED = "UNAUTHORIZED";

    /**
     * 入库通道常量，与 {@link MarketJdData} 共用同一份字面量。
     * <p>
     * 本表原本只登记爬虫批次，现在同时登记人工上传与岗位导入批次，
     * 因此两个实体必须用同一套通道取值，否则批次列表的分类会和数据行对不上。
     */
    public static final String CHANNEL_CRAWLER = MarketJdData.CHANNEL_CRAWLER;
    /** 入库通道：人工上传（粘贴文本 / Excel） */
    public static final String CHANNEL_MANUAL_UPLOAD = MarketJdData.CHANNEL_MANUAL_UPLOAD;
    /** 入库通道：岗位导入连带的市场 JD */
    public static final String CHANNEL_POST_IMPORT = MarketJdData.CHANNEL_POST_IMPORT;

    /** 解析状态：未触发（纯重复批次或开关关闭） */
    public static final String ANALYSIS_NOT_TRIGGERED = "NOT_TRIGGERED";
    /** 解析状态：已排队等待异步执行 */
    public static final String ANALYSIS_QUEUED = "QUEUED";
    /** 解析状态：解析中 */
    public static final String ANALYSIS_RUNNING = "RUNNING";
    /** 解析状态：解析成功 */
    public static final String ANALYSIS_SUCCEEDED = "SUCCEEDED";
    /** 解析状态：解析失败，JD 保持待分析态可重试 */
    public static final String ANALYSIS_FAILED = "FAILED";
    /** 解析状态：跳过（本批无新增/变更，或该批次正在解析中） */
    public static final String ANALYSIS_SKIPPED = "SKIPPED";

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 批次号；爬虫推送与人工导入共用同一列，靠 ingestChannel 区分 */
    private String batchNo;

    /**
     * 入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT。
     * <p>
     * 本表原本只登记爬虫批次，现在同时登记人工导入批次（这样「重新解析」「删除」
     * 对两类批次能走同一条路径）。默认 CRAWLER 以兼容历史行。
     */
    private String ingestChannel;

    /** 来源平台：jd/remoteok/themuse/arbeitnow */
    private String sourcePlatform;

    /** 调用方 IP */
    private String requestIp;

    /** 请求 items 条数 */
    private Integer itemCount;

    private Integer imported;

    private Integer updated;

    private Integer duplicate;

    private Integer failed;

    /** 返回的 HTTP 状态码 */
    private Integer httpStatus;

    /** OK / PARTIAL_FAILED / REJECTED / UNAUTHORIZED */
    private String resultStatus;

    /** 前若干条失败原因摘要（绝不含 token） */
    private String errorSample;

    /** NOT_TRIGGERED / QUEUED / RUNNING / SUCCEEDED / FAILED / SKIPPED */
    private String analysisState;

    /** 解析状态说明（失败原因或跳过理由） */
    private String analysisNote;

    /**
     * 解析开始时间：仅在 {@link #ANALYSIS_QUEUED} / {@link #ANALYSIS_RUNNING} 时写入。
     * <p>
     * 僵尸回收**必须**用它而不是 {@link #createdTime}：后者是「批次接收时间」，
     * 与解析何时开始无关。若按接收时间判定，一个几小时前推来、现在才手动重新解析的批次
     * 会被下一次扫描立刻当成僵尸终止 —— 正常任务被误杀比不回收更糟。
     */
    private LocalDateTime analysisStartedAt;

    /** 处理耗时（毫秒） */
    private Integer costMillis;

    /** 创建时间，由数据库默认值写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdTime;
}
