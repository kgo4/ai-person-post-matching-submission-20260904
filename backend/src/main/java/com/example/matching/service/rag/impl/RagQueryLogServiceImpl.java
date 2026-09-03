package com.example.matching.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.rag.RagQueryLog;
import com.example.matching.mapper.rag.RagQueryLogMapper;
import com.example.matching.service.rag.RagQueryLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG查询日志服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryLogServiceImpl implements RagQueryLogService {

    private final RagQueryLogMapper queryLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQueryLog(RagQueryLog log) {
        queryLogMapper.insert(log);
    }

    @Override
    public IPage<RagQueryLog> pageLogs(Page<RagQueryLog> page, String scenario) {
        LambdaQueryWrapper<RagQueryLog> wrapper = new LambdaQueryWrapper<>();
        if (scenario != null && !scenario.isBlank()) {
            wrapper.eq(RagQueryLog::getScenario, scenario);
        }
        wrapper.orderByDesc(RagQueryLog::getCreatedTime);
        return queryLogMapper.selectPage(page, wrapper);
    }

    @Override
    public RagQueryLog getLogById(Long id) {
        RagQueryLog log = queryLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "RAG查询日志不存在: " + id);
        }
        return log;
    }
}
