package com.example.matching.service.matching.evaluation;

/**
 * 匹配评估方向枚举
 * <p>
 * 统一评估管道支持三个入口：
 * - POST_TO_EMP：为岗位推荐员工（岗找人）
 * - EMP_TO_POST：为员工推荐岗位（人找岗）
 * - SINGLE_EVAL：正式评估单个员工-岗位对
 */
public enum MatchEvaluationDirection {

    /** 岗位推荐员工 */
    POST_TO_EMP,

    /** 员工推荐岗位 */
    EMP_TO_POST,

    /** 单对正式评估 */
    SINGLE_EVAL
}
