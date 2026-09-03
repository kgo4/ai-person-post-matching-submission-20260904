package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.post.PostPostWriteService;
import com.example.matching.service.post.support.PostPostWriteValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostPostWriteServiceImpl implements PostPostWriteService {

    private final PostPostWriteValidator writeValidator;
    private final PostPostMapper postPostMapper;

    @Override
    @Transactional
    public PostPost save(PostPost entity) {
        writeValidator.validateBeforeSave(entity);
        postPostMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public PostPost update(PostPost entity) {
        writeValidator.validateBeforeUpdate(entity);
        postPostMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public List<PostPost> batchSave(List<PostPost> entities) {
        List<PostPost> result = new ArrayList<>();

        for (PostPost entity : entities) {
            writeValidator.validateBeforeSave(entity);
        }

        Set<String> seenCodes = new HashSet<>();
        for (PostPost entity : entities) {
            if (!seenCodes.add(entity.getPostCode())) {
                throw new BusinessException(ErrorCodeEnum.POST_CODE_DUPLICATE,
                        "本批次内存在重复岗位编码: " + entity.getPostCode());
            }
        }

        for (PostPost entity : entities) {
            postPostMapper.insert(entity);
            result.add(entity);
        }

        log.info("批量保存岗位完成: count={}", result.size());
        return result;
    }
}
