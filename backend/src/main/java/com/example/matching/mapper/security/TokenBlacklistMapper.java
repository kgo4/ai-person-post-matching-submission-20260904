package com.example.matching.mapper.security;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.security.TokenBlacklist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {
}
