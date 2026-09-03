package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Excel 导入批次 Mapper
 * <p>
 * 幂等抢占：claim 为条件更新，更新条数不是 1 时消费者直接返回，不执行分析。
 */
@Mapper
public interface PostImportBatchMapper extends BaseMapper<PostImportBatch> {

    /**
     * 抢占批次进入 AI 分析（import_status 0=待解析 -> 1=AI解析中）
     *
     * @return 1=抢占成功；0=已被其他消费者抢占或状态不允许
     */
    @Update("""
            UPDATE post_import_batch
            SET import_status = 1, processing_started_at = NOW(), last_error_type = NULL, last_error_message = NULL
            WHERE id = #{id} AND import_status = 0
            """)
    int claimAnalysis(@Param("id") Long id);

    /**
     * 幂等确认导入：仅当批次处于"分析完成待确认"(2) 时允许进入"导入中"(3)。
     * 条件更新保证重复确认/并发确认只有一个能成功，防止同一批次重复创建岗位。
     *
     * @return 1=确认成功；0=批次不存在或状态不允许（已导入/分析中/失败）
     */
    @Update("""
            UPDATE post_import_batch
            SET import_status = 3, processing_started_at = NULL,
                last_error_type = NULL, last_error_message = NULL, error_message = NULL
            WHERE id = #{id} AND import_status = 2
            """)
    int confirmImport(@Param("id") Long id);

    /** 抢占确认导入任务，避免重复消息重复创建岗位。 */
    @Update("""
            UPDATE post_import_batch
            SET processing_started_at = NOW(), last_error_type = NULL, last_error_message = NULL,
                error_message = NULL
            WHERE id = #{id} AND import_status = 3
              AND processing_started_at IS NULL
            """)
    int claimImportExecution(@Param("id") Long id);

    /** 保存确认请求载荷；状态必须已经由 2 抢占为 3。 */
    @Update("""
            UPDATE post_import_batch
            SET confirm_payload = #{payload}
            WHERE id = #{id} AND import_status = 3
            """)
    int saveConfirmPayload(@Param("id") Long id, @Param("payload") String payload);

    /** 标记异步导入失败，供消费者异常和前端轮询展示。 */
    @Update("""
            UPDATE post_import_batch
            SET import_status = 5, error_message = #{message},
                last_error_type = #{errorType}, last_error_message = #{message}
            WHERE id = #{id} AND import_status = 3
            """)
    int markImportFailed(@Param("id") Long id, @Param("errorType") String errorType,
                         @Param("message") String message);

    @Update("""
            UPDATE post_import_batch
            SET import_status = 3, processing_started_at = NULL, retry_count = COALESCE(retry_count, 0) + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND import_status = 3 AND processing_started_at < #{before}
              AND COALESCE(retry_count, 0) < 3
            """)
    int recoverImportZombie(@Param("id") Long id, @Param("before") LocalDateTime before,
                            @Param("errorType") String errorType, @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE post_import_batch
            SET import_status = 5, error_message = #{errorMessage},
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND import_status = 3 AND processing_started_at < #{before}
            """)
    int failImportZombie(@Param("id") Long id, @Param("before") LocalDateTime before,
                         @Param("errorType") String errorType, @Param("errorMessage") String errorMessage);

    /**
     * 僵尸恢复：PROCESSING 超时且未达重试上限 -> 回到待解析并递增次数
     *
     * @return 1=已恢复；0=状态已变化或重试耗尽
     */
    @Update("""
            UPDATE post_import_batch
            SET import_status = 0, retry_count = retry_count + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND import_status = 1 AND retry_count < 3
            """)
    int recoverZombie(@Param("id") Long id, @Param("errorType") String errorType,
                      @Param("errorMessage") String errorMessage);

    /**
     * 僵尸恢复：PROCESSING 超时且重试耗尽 -> 失败终态
     */
    @Update("""
            UPDATE post_import_batch
            SET import_status = 5,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND import_status = 1
            """)
    int failZombie(@Param("id") Long id, @Param("errorType") String errorType,
                   @Param("errorMessage") String errorMessage);

    /**
     * 扫描处理中超时的批次（僵尸）
     */
    @Select("""
            SELECT * FROM post_import_batch
            WHERE import_status IN (1, 3) AND processing_started_at < #{before}
            """)
    List<PostImportBatch> selectZombieBatches(@Param("before") LocalDateTime before);
}
