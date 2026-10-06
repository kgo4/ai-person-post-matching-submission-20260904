package com.example.matching.service.ability;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;

/** Creates reusable JD extraction guidance only when a post-model editor explicitly requests it. */
public interface PostAbilityMemoryGovernanceService {

    void createFutureJdExtractionRule(PostAbilityModelConfigDTO dto);
}
