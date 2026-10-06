package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 市场JD历史版本快照。
 * <p>
 * 爬虫对同一 {@code sourcePlatform + externalId} 岗位做更新时，先将被替换的旧正文写入本表，
 * 保证 JD 变更可追溯。本表只服务于可追溯性，不参与分析链路。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("market_jd_data_version")
public class MarketJdDataVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对应 market_jd_data.id */
    private Long marketJdId;

    /** 版本号，从 1 开始递增 */
    private Integer versionNo;

    /** 产生该版本的爬虫批次号 */
    private String batchNo;

    private String postName;

    private String companyName;

    private String city;

    private String salaryRange;

    private String jobDescription;

    private String requirements;

    private String sourceUrl;

    /** 该版本正文哈希 */
    private String textHash;

    /** 变更原因：CRAWLER_UPDATE 等 */
    private String changeReason;

    /** 创建时间，由数据库默认值写入 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdTime;
}
