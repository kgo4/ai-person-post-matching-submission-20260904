package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.mapper.matching.MatchingBlackWhiteListMapper;
import com.example.matching.service.matching.MatchingBlackWhiteListService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchingBlackWhiteListServiceImpl extends ServiceImpl<MatchingBlackWhiteListMapper, MatchingBlackWhiteList> implements MatchingBlackWhiteListService {

    @Override
    public IPage<MatchingBlackWhiteList> pageList(IPage<MatchingBlackWhiteList> page, Long empId, Long postId) {
        LambdaQueryWrapper<MatchingBlackWhiteList> wrapper = Wrappers.<MatchingBlackWhiteList>lambdaQuery();
        if (empId != null) {
            wrapper.eq(MatchingBlackWhiteList::getEmpId, empId);
        }
        if (postId != null) {
            wrapper.eq(MatchingBlackWhiteList::getPostId, postId);
        }
        wrapper.orderByDesc(MatchingBlackWhiteList::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    public List<MatchingBlackWhiteList> listByPostId(Long postId) {
        return list(Wrappers.<MatchingBlackWhiteList>lambdaQuery()
                .eq(MatchingBlackWhiteList::getPostId, postId)
                .eq(MatchingBlackWhiteList::getStatus, 1));
    }
}
