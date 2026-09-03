package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全局企业 AI 模型配置（单行，主键固定为 1）。
 * <p>
 * 系统只配置一个企业自部署的全局模型，所有文本类 AI 业务统一使用它。
 * apiKey 只以密文保存，接口永不返回明文。
 */
@Data
@TableName("system_ai_model_config")
public class SystemAiModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定为 1 */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /** 是否启用企业模型 */
    private Boolean enabled;

    /** 企业 OpenAI-compatible 网关地址 */
    private String baseUrl;

    /** 模型名称 */
    private String modelName;

    /** 加密保存的密钥 */
    private String apiKeyCiphertext;

    /** 请求超时（秒） */
    private Integer timeoutSeconds;

    /** 默认温度 */
    private BigDecimal temperature;

    /** AI 测试题目数量，由系统统一控制。 */
    private Integer testQuestionCount;

    /** AI 面试题目数量，由系统统一控制。 */
    private Integer interviewQuestionCount;

    /** 未归一岗位能力语义簇达到人工治理前所需的最少成员数。 */
    private Integer postAbilityClusterMinMemberCount;

    /** 未归一岗位能力语义簇达到人工治理前所需覆盖的最少岗位数。 */
    private Integer postAbilityClusterMinPostCount;

    /** 新能力加入既有语义簇的最小余弦相似度。 */
    private BigDecimal postAbilityClusterJoinSimilarity;

    /** 语义簇提升为待治理候选所需的最小平均内聚度。 */
    private BigDecimal postAbilityClusterPromotionCohesion;

    /** 最后更新人 */
    private Long updatedBy;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;
}
