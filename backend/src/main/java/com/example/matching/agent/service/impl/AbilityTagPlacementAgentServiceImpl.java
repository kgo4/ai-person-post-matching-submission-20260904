package com.example.matching.agent.service.impl;

import com.example.matching.agent.service.AbilityTagPlacementAgentService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.service.system.AbilityTagPlacementProposalService;
import com.example.matching.service.system.TaxonomyClassifyResult;
import com.example.matching.service.system.impl.AbilityTagTaxonomyClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 候选标签挂载建议生成。
 * <p>
 * 仅使用本地规则/向量分类器，<strong>不调用任何 LLM/AI 服务</strong>。
 * 分类器未命中时不生成建议，由候选审核走人工批准/合并流程。
 */
@Slf4j
@Service
public class AbilityTagPlacementAgentServiceImpl implements AbilityTagPlacementAgentService {

    private final AbilityTagCandidateMapper candidateMapper;
    private final AbilityTagTaxonomyClassifier taxonomyClassifier;
    private final AbilityTagPlacementProposalService proposalService;

    public AbilityTagPlacementAgentServiceImpl(AbilityTagCandidateMapper candidateMapper,
                                               AbilityTagTaxonomyClassifier taxonomyClassifier,
                                               AbilityTagPlacementProposalService proposalService) {
        this.candidateMapper = candidateMapper;
        this.taxonomyClassifier = taxonomyClassifier;
        this.proposalService = proposalService;
    }

    @Override
    public Optional<AbilityTagCandidatePlacementProposal> generateProposal(Long candidateId) {
        AbilityTagCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null || !"PENDING".equals(candidate.getStatus())) {
            return Optional.empty();
        }

        TaxonomyClassifyResult classified = taxonomyClassifier.classify(candidate.getCandidateName());
        if (classified != null && classified.abilityTag() != null) {
            AbilityTag target = classified.abilityTag();
            return Optional.of(saveMergeProposal(candidateId, target, classified.confidence(),
                    "现有" + classified.source() + "分类器匹配到二级能力标签「" + target.getTagName() + "」"));
        }
        return Optional.empty();
    }

    private AbilityTagCandidatePlacementProposal saveMergeProposal(Long candidateId, AbilityTag target,
                                                                     java.math.BigDecimal confidence, String rationale) {
        AbilityTagCandidatePlacementProposal proposal = new AbilityTagCandidatePlacementProposal();
        proposal.setCandidateId(candidateId);
        proposal.setAction("MERGE_EXISTING");
        proposal.setTargetParentDomainId(target.getParentId());
        proposal.setTargetTagId(target.getId());
        proposal.setConfidence(confidence);
        proposal.setRationale(rationale);
        return proposalService.createPending(proposal);
    }
}
