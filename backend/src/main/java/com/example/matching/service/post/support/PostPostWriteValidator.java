package com.example.matching.service.post.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 岗位写入验证器
 * <p>
 * 统一规范化 postCode/postName/jobDescription 字段，并按规范化后的 postCode 查重。
 * 所有导入、API、后台写入必须复用此 save/updateById 入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostPostWriteValidator {

    private final PostPostMapper postPostMapper;

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NUMERIC_ONLY_PATTERN = Pattern.compile("^[0-9]+(?:[.、)）])?$");

    /**
     * 插入前规范化字段，按规范化后的 postCode 查重。
     */
    public void validateBeforeSave(PostPost entity) {
        normalizeEntity(entity);
        checkDuplicatePostCode(entity.getPostCode(), null);
    }

    /**
     * 更新前规范化字段，按规范化后的 postCode 查重并排除自身 ID。
     */
    public void validateBeforeUpdate(PostPost entity) {
        normalizeEntity(entity);
        checkDuplicatePostCode(entity.getPostCode(), entity.getId());
    }

    private void normalizeEntity(PostPost entity) {
        entity.setPostCode(normalizePostCode(entity.getPostCode()));
        entity.setPostName(normalizePostName(entity.getPostName()));
        entity.setJobDescription(normalizeJobDescription(entity.getJobDescription()));
    }

    String normalizePostCode(String postCode) {
        if (postCode == null) return null;
        return postCode.trim().toUpperCase();
    }

    String normalizePostName(String postName) {
        if (postName == null || postName.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位名称不能为空");
        }
        String normalized = Normalizer.normalize(postName, Normalizer.Form.NFKC);
        normalized = WHITESPACE_PATTERN.matcher(normalized.trim()).replaceAll(" ");
        if (NUMERIC_ONLY_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位名称不能使用序号或纯数字");
        }
        return normalized;
    }

    String normalizeJobDescription(String jobDescription) {
        if (jobDescription == null) return null;
        String normalized = TextSanitizationPolicy.normalizeLineBreaks(jobDescription);
        normalized = TextSanitizationPolicy.removeControlChars(normalized);
        return normalized;
    }

    private void checkDuplicatePostCode(String normalizedPostCode, Long excludeId) {
        if (normalizedPostCode == null || normalizedPostCode.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位编码不能为空");
        }

        LambdaQueryWrapper<PostPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostPost::getPostCode, normalizedPostCode);
        if (excludeId != null) {
            wrapper.ne(PostPost::getId, excludeId);
        }

        PostPost existing = postPostMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.POST_CODE_DUPLICATE,
                    "岗位编码已存在: " + normalizedPostCode);
        }
    }
}
