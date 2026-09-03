package com.example.matching.integration.zhihu;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ZhihuSearchResponse(
        @JsonProperty("HasMore") Boolean hasMore,
        @JsonProperty("SearchHashId") String searchHashId,
        @JsonProperty("Items") List<ZhihuSearchItem> items,
        @JsonProperty("EmptyReason") String emptyReason
) {}
