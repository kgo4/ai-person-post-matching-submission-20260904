package com.example.matching.service.post;

import com.example.matching.entity.post.PostPost;

import java.util.List;

public interface PostPostWriteService {

    PostPost save(PostPost entity);

    PostPost update(PostPost entity);

    List<PostPost> batchSave(List<PostPost> entities);
}
