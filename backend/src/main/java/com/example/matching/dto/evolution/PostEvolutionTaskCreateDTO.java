package com.example.matching.dto.evolution;

import lombok.Data;

/**
 * 岗位演化任务创建DTO
 *
 * @author system
 */
@Data
public class PostEvolutionTaskCreateDTO {

    /** 岗位ID */
    private Long postId;

    /** 任务名称 */
    private String taskName;

    /** 新JD或市场数据文本 */
    private String newJdText;
}
