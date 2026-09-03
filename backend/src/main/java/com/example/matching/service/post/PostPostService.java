package com.example.matching.service.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.post.PostPost;

import java.util.List;

/**
 * Post service for matching.
 */
public interface PostPostService extends IService<PostPost> {

    IPage<PostPost> pagePosts(IPage<PostPost> page, String keyword, Integer status);

    List<PostPost> listEnabled();
}
