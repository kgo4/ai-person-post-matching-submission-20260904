package com.example.matching.mapper.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.common.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
