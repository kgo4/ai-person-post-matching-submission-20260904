package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.matching.MatchingBlackWhiteList;

import java.util.List;

public interface MatchingBlackWhiteListService extends IService<MatchingBlackWhiteList> {

    IPage<MatchingBlackWhiteList> pageList(IPage<MatchingBlackWhiteList> page, Long empId, Long postId);

    List<MatchingBlackWhiteList> listByPostId(Long postId);
}
