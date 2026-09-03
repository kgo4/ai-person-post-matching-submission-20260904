package com.example.matching.service.rag;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.rag.RagQueryLog;

/**
 * RAG查询日志服务接口
 *
 * @author system
 */
public interface RagQueryLogService {

    /**
     * 记录RAG查询日志
     *
     * @param log 日志实体
     */
    void saveQueryLog(RagQueryLog log);

    /**
     * 分页查询日志
     *
     * @param page     分页参数
     * @param scenario 场景过滤
     * @return 分页结果
     */
    IPage<RagQueryLog> pageLogs(Page<RagQueryLog> page, String scenario);

    /**
     * 获取日志详情
     *
     * @param id 日志ID
     * @return 日志实体
     */
    RagQueryLog getLogById(Long id);
}
