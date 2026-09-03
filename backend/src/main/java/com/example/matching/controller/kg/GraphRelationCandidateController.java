package com.example.matching.controller.kg;

import com.example.matching.application.kg.GraphRelationCandidateApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.kg.GraphRelationCandidateCreateDTO;
import com.example.matching.dto.kg.GraphRelationCandidateReviewDTO;
import com.example.matching.dto.kg.GraphRelationCandidateRevokeDTO;
import com.example.matching.dto.kg.api.GraphRelationCandidateResponse;
import com.example.matching.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kg/relations/candidates")
@RequiredArgsConstructor
public class GraphRelationCandidateController {

    private final GraphRelationCandidateApiFacade facade;

    @PostMapping
    public R<GraphRelationCandidateResponse> create(@Valid @RequestBody GraphRelationCandidateCreateDTO request) {
        return R.ok(facade.create(request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{candidateId}/review")
    public R<GraphRelationCandidateResponse> review(@PathVariable Long candidateId,
                                                     @Valid @RequestBody GraphRelationCandidateReviewDTO request) {
        return R.ok(facade.review(candidateId, request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{candidateId}/revoke")
    public R<GraphRelationCandidateResponse> revoke(@PathVariable Long candidateId,
                                                     @Valid @RequestBody GraphRelationCandidateRevokeDTO request) {
        return R.ok(facade.revoke(candidateId, request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping
    public R<List<GraphRelationCandidateResponse>> list(@RequestParam(required = false) String reviewStatus) {
        return R.ok(facade.list(reviewStatus));
    }

    @GetMapping("/governance/policies")
    public R<Map<String, Object>> policies() {
        return R.ok(facade.policies());
    }

    @GetMapping("/governance/check")
    public R<Map<String, Object>> check() {
        return R.ok(facade.check());
    }
}
