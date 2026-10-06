package com.example.matching.mapper.evolution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬虫推送批次处理日志 Mapper
 *
 * @author system
 */
@Mapper
public interface MarketJdCrawlerBatchLogMapper extends BaseMapper<MarketJdCrawlerBatchLog> {

    /**
     * 查找僵尸解析批次：停留在「进行中」状态且开始时间早于阈值的行。
     * <p>
     * 判据用 {@code analysis_started_at}（解析开始时间）而不是 {@code created_time}
     * （批次接收时间）—— 后者与解析何时开始无关，用它会把「几小时前接收、现在才手动
     * 重新解析」的正常批次误判成僵尸。{@code analysis_started_at IS NULL} 的行（老代码写入、
     * 或从未进入解析）一律不处理，宁可漏回收也不能误杀。
     *
     * @param state  进行中状态（QUEUED / RUNNING）
     * @param before 开始时间早于该时刻即视为僵尸
     * @param limit  单次回收上限，避免一次扫描锁住过多行
     */
    @Select("SELECT * FROM market_jd_crawler_batch_log "
            + "WHERE analysis_state = #{state} AND analysis_started_at IS NOT NULL "
            + "AND analysis_started_at < #{before} ORDER BY id LIMIT #{limit}")
    List<MarketJdCrawlerBatchLog> findZombieAnalysisLogs(@Param("state") String state,
                                                        @Param("before") LocalDateTime before,
                                                        @Param("limit") int limit);

    /**
     * 把僵尸批次置为终态。
     * <p>
     * 带 {@code analysis_state = #{fromState}} 条件做 CAS：扫描与真正的解析线程可能并发，
     * 若解析在扫描之后恰好完成并写入 SUCCEEDED，无条件的 UPDATE 会把成功结果改写成失败。
     * 只有仍然停在「进行中」的行才允许被回收。
     *
     * @return 受影响行数；0 表示该行已被其它线程推进到终态，本次不应计入回收数
     */
    @Update("UPDATE market_jd_crawler_batch_log SET analysis_state = #{toState}, analysis_note = #{note} "
            + "WHERE id = #{id} AND analysis_state = #{fromState}")
    int finishZombieAnalysis(@Param("id") Long id,
                             @Param("fromState") String fromState,
                             @Param("toState") String toState,
                             @Param("note") String note);
}
