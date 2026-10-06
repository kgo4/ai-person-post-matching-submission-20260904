package com.example.matching.controller.system;

import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.AbilityTagPlacementProposalUpdateRequest;
import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;
import com.example.matching.service.system.AbilityTagPlacementProposalService;
import com.example.matching.service.system.PlacementApplyResult;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Operator-facing review and controlled application of Agent placement recommendations. */
@Tag(name = "候选标签挂载建议")
@RestController
@RequestMapping("/api/system/tag-candidate/{candidateId}/placement-proposals")
@RequiredArgsConstructor
public class AbilityTagPlacementProposalController {

    private final AbilityTagPlacementProposalService proposalService;

    @Operation(summary = "查询候选标签的挂载建议")
    @GetMapping
    public R<List<AbilityTagCandidatePlacementProposal>> list(@PathVariable Long candidateId) {
        return R.ok(proposalService.listByCandidateId(candidateId));
    }

    @Operation(summary = "修改待采纳的挂载建议")
    @PutMapping("/{proposalId}")
    public R<AbilityTagCandidatePlacementProposal> update(
            @PathVariable Long candidateId,
            @PathVariable Long proposalId,
            @RequestBody AbilityTagPlacementProposalUpdateRequest request) {
        return R.ok(proposalService.updatePending(candidateId, proposalId, request.action(),
                request.targetParentDomainId(), request.targetTagId(), request.rationale()));
    }

    @Operation(summary = "采纳建议并一键挂载到能力标签树")
    @PostMapping("/{proposalId}/apply")
    public R<PlacementApplyResult> apply(
            @PathVariable Long candidateId,
            @PathVariable Long proposalId,
            @RequestParam Integer proposalVersion) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return R.ok(proposalService.apply(candidateId, proposalId, proposalVersion,
                operatorId == null ? 0L : operatorId));
    }
}
