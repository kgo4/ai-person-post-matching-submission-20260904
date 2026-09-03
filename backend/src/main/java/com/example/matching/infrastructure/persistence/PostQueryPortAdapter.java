package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.JdImportTask;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.entity.post.PostPrototypeTag;
import com.example.matching.mapper.post.JdImportTaskMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.post.PostPrototypeMapper;
import com.example.matching.mapper.post.PostPrototypeTagMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.JdImportTaskDTO;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.post.PostQueryPort.PostPrototypeDTO;
import com.example.matching.port.post.PostQueryPort.PostPrototypeTagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostQueryPortAdapter implements PostQueryPort {

    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final PostPrototypeMapper postPrototypeMapper;
    private final PostPrototypeTagMapper postPrototypeTagMapper;
    private final JdImportTaskMapper jdImportTaskMapper;

    @Override
    public PostDTO getPostById(Long postId) {
        PostPost p = postPostMapper.selectById(postId);
        return p != null ? PostDTO.from(p) : null;
    }

    @Override
    public List<PostDTO> batchGetPosts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return List.of();
        return postPostMapper.selectBatchIds(postIds).stream()
                .map(PostDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostAbilityDTO> listRequirementsByPostId(Long postId) {
        return postAbilityModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getIsDeleted, 0)
                        .orderByDesc(PostAbilityModel::getIsCore)
                        .orderByDesc(PostAbilityModel::getIsRequired)
                        .orderByDesc(PostAbilityModel::getWeight)
        ).stream().map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public PostAbilityDTO getPostAbilityModelById(Long modelId) {
        if (modelId == null) return null;
        PostAbilityModel m = postAbilityModelMapper.selectById(modelId);
        return m != null ? PostAbilityDTO.from(m) : null;
    }

    @Override
    public PostAbilityDTO getRequirementByPostAndTag(Long postId, Long tagId) {
        if (postId == null || tagId == null) return null;
        PostAbilityModel m = postAbilityModelMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getTagId, tagId)
                        .eq(PostAbilityModel::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return m != null ? PostAbilityDTO.from(m) : null;
    }

    @Override
    public List<PostAbilityDTO> listRequirementsByTagId(Long tagId) {
        if (tagId == null) return List.of();
        return postAbilityModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getTagId, tagId)
                        .eq(PostAbilityModel::getIsDeleted, 0)
        ).stream().map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostDTO> listActivePosts(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostPost>()
                .eq(PostPost::getStatus, 1)
                .eq(PostPost::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return postPostMapper.selectList(w).stream().map(PostDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostAbilityDTO> listActivePostAbilityModels(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                .eq(PostAbilityModel::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return postAbilityModelMapper.selectList(w).stream().map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostAbilityDTO> listRequirementsByPostIds(java.util.Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return List.of();
        return postAbilityModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .in(PostAbilityModel::getPostId, postIds)
                        .eq(PostAbilityModel::getIsDeleted, 0)
                        .orderByDesc(PostAbilityModel::getIsCore)
                        .orderByDesc(PostAbilityModel::getIsRequired)
                        .orderByDesc(PostAbilityModel::getWeight)
        ).stream().map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostAbilityDTO> listUntaggedPostAbilityModels(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                .eq(PostAbilityModel::getIsDeleted, 0)
                .isNull(PostAbilityModel::getTagId)
                .isNotNull(PostAbilityModel::getAbilityName)
                .ne(PostAbilityModel::getAbilityName, "")
                .orderByAsc(PostAbilityModel::getId);
        if (limit > 0) w.last("LIMIT " + limit);
        return postAbilityModelMapper.selectList(w).stream().map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostPrototypeDTO> listActivePrototypes(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostPrototype>()
                .eq(PostPrototype::getStatus, 1)
                .eq(PostPrototype::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return postPrototypeMapper.selectList(w).stream().map(PostPrototypeDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostPrototypeTagDTO> listAllPrototypeTags() {
        return postPrototypeTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostPrototypeTag>()
        ).stream().map(PostPrototypeTagDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<JdImportTaskDTO> listAnalyzedJdImportTasks(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JdImportTask>()
                .eq(JdImportTask::getAnalysisStatus, 2)
                .eq(JdImportTask::getIsDeleted, 0)
                .isNotNull(JdImportTask::getJdRawText);
        if (limit > 0) w.last("LIMIT " + limit);
        return jdImportTaskMapper.selectList(w).stream().map(JdImportTaskDTO::from).collect(Collectors.toList());
    }

    @Override
    public long countAllPosts() {
        Long count = postPostMapper.selectCount(Wrappers.<PostPost>lambdaQuery());
        return count == null ? 0L : count;
    }

    @Override
    public List<PostDTO> listAllPosts() {
        return postPostMapper.selectList(Wrappers.<PostPost>lambdaQuery()).stream()
                .map(PostDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<PostAbilityDTO> listAllPostAbilityModels() {
        return postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()).stream()
                .map(PostAbilityDTO::from).collect(Collectors.toList());
    }

    @Override
    public long countRequirementsByPostId(Long postId) {
        if (postId == null) return 0L;
        Long count = postAbilityModelMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getPostId, postId));
        return count == null ? 0L : count;
    }

    @Override
    public long countRequirementsByTagId(Long tagId) {
        if (tagId == null) return 0L;
        Long count = postAbilityModelMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getTagId, tagId)
                        .eq(PostAbilityModel::getIsDeleted, 0));
        return count == null ? 0L : count;
    }

    @Override
    public long countPrototypeTagsByTagId(Long tagId) {
        if (tagId == null) return 0L;
        Long count = postPrototypeTagMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostPrototypeTag>()
                        .eq(PostPrototypeTag::getTagId, tagId));
        return count == null ? 0L : count;
    }
}
