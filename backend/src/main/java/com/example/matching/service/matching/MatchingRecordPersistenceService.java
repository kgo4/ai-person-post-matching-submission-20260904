package com.example.matching.service.matching;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 匹配记录落库入口（同步执行与异步任务共用的唯一写入点）。
 *
 * <p><b>【2026-09-04 修复】这里必须驱逐匹配列表缓存，否则「匹配已完成但列表里看不到」。</b>
 *
 * <p>故障现象：员工自助发起匹配后，HR 在「匹配结果」页反复刷新都看不到新记录 ——
 * 最长要等 10 分钟（{@code matching:record:page} 的 TTL）或者被别的写操作顺带清掉才出现。
 *
 * <p>根因：{@code MatchingRecordServiceImpl#executeMatching} 是**唯一**声明了
 * {@code @CacheEvict(MATCHING_RECORD_PAGE)} 的入口，但 Controller 的同步链路
 * （{@code MatchingRecordApiFacade#execute} / {@code #executeSelfMatching}）直接调
 * {@code MatchingExecuteService#execute(...)}，从不经过它；而
 * {@code MatchingRecordServiceImpl#pageRecords} 带 {@code @Cacheable}，
 * 于是「落库了但缓存没失效」—— 表现为列表里没有新记录。
 *
 * <p>修在这里而不是调用方：同步 HR 发起、员工自助、异步任务三条链路最终都汇聚到
 * {@link #saveAll}；且本类是独立 Bean，{@code MatchingExecuteServiceImpl#saveRecords}
 * 是**跨 Bean 调用**，缓存注解会正常走代理（同类自调用不会，见项目既有教训）。
 *
 * <p>缓存注解在 {@code spring.redis.enabled=false} 时整体失效（{@code @EnableCaching}
 * 随之不加载），因此对未启用 Redis 的环境是无副作用的空操作。
 */
@Service
public class MatchingRecordPersistenceService extends ServiceImpl<MatchingRecordMapper, MatchingRecord> {

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void saveAll(List<MatchingRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        saveBatch(records, 500);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void updateAll(List<MatchingRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        updateBatchById(records, 500);
    }
}
