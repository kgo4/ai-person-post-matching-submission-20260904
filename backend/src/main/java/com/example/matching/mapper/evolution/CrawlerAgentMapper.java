package com.example.matching.mapper.evolution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.evolution.CrawlerAgent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地爬虫心跳台账 Mapper
 *
 * @author system
 */
@Mapper
public interface CrawlerAgentMapper extends BaseMapper<CrawlerAgent> {
}
