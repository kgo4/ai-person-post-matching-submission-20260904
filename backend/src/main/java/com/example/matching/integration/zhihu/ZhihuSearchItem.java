package com.example.matching.integration.zhihu;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZhihuSearchItem(
        @JsonProperty("Title") String title,
        @JsonProperty("ContentType") String contentType,
        @JsonProperty("ContentID") String contentId,
        @JsonProperty("ContentText") String contentText,
        @JsonProperty("Url") String url,
        @JsonProperty("CommentCount") Integer commentCount,
        @JsonProperty("VoteUpCount") Integer voteUpCount
) {}
