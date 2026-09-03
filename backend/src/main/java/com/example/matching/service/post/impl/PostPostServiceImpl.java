package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.post.PostPostService;
import com.example.matching.service.post.support.PostPostWriteValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostPostServiceImpl extends ServiceImpl<PostPostMapper, PostPost> implements PostPostService {

    private final PostPostWriteValidator writeValidator;

    @Override
    @Cacheable(cacheNames = RedisCacheNames.POST_POST_PAGE,
               key = "'page:' + #page.current + ':' + #page.size + ':' + (#keyword != null ? #keyword : '') + ':' + (#status != null ? #status : '')", sync = true)
    public IPage<PostPost> pagePosts(IPage<PostPost> page, String keyword, Integer status) {
        LambdaQueryWrapper<PostPost> wrapper = Wrappers.<PostPost>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PostPost::getPostCode, keyword).or().like(PostPost::getPostName, keyword));
        }
        if (status != null) {
            wrapper.eq(PostPost::getStatus, status);
        }
        wrapper.orderByAsc(PostPost::getPostCode);
        return page(page, wrapper);
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.POST_ENABLED, key = "'all'", sync = true)
    public List<PostPost> listEnabled() {
        return list(Wrappers.<PostPost>lambdaQuery().eq(PostPost::getStatus, 1));
    }

    @Override
    @Transactional
    @Deprecated
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_ENABLED, key = "'all'"),
            @CacheEvict(cacheNames = RedisCacheNames.POST_POST_PAGE, allEntries = true)
    })
    public boolean save(PostPost entity) {
        writeValidator.validateBeforeSave(entity);
        return super.save(entity);
    }

    @Override
    @Transactional
    @Deprecated
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_ENABLED, key = "'all'"),
            @CacheEvict(cacheNames = RedisCacheNames.POST_POST_PAGE, allEntries = true)
    })
    public boolean updateById(PostPost entity) {
        writeValidator.validateBeforeUpdate(entity);
        return super.updateById(entity);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_ENABLED, key = "'all'"),
            @CacheEvict(cacheNames = RedisCacheNames.POST_POST_PAGE, allEntries = true)
    })
    public boolean removeById(PostPost entity) {
        return super.removeById(entity);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_ENABLED, key = "'all'"),
            @CacheEvict(cacheNames = RedisCacheNames.POST_POST_PAGE, allEntries = true)
    })
    public boolean removeById(java.io.Serializable id) {
        return super.removeById(id);
    }
}
