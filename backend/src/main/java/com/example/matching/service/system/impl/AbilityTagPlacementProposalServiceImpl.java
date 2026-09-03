package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagCandidatePlacementProposalMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.service.system.AbilityTagPlacementProposalService;
import com.example.matching.service.system.PlacementApplyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Applies reviewed placement proposals through the existing candidate approval paths. */
@Service
@RequiredArgsConstructor
public class AbilityTagPlacementProposalServiceImpl implements AbilityTagPlacementProposalService {

    private static final String PENDING = "PENDING";
    private static final String APPLIED = "APPLIED";
    private static final String STALE = "STALE";
    private static final String MERGE_EXISTING = "MERGE_EXISTING";
    private static final String CREATE_L2 = "CREATE_L2";
    private static final String APPLY_COMMENT = "采纳标签挂载建议";

    private final AbilityTagCandidatePlacementProposalMapper proposalMapper;
    private final AbilityTagCandidateMapper candidateMapper;
    private final AbilityTagMapper tagMapper;
    private final AbilityTagCandidateService candidateService;

    @Override
    @Transactional
    public AbilityTagCandidatePlacementProposal createPending(AbilityTagCandidatePlacementProposal proposal) {
        requireAction(proposal.getAction());
        AbilityTagCandidate candidate = candidateMapper.selectById(proposal.getCandidateId());
        if (candidate == null || !PENDING.equals(candidate.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT, "仅待治理候选可以生成挂载建议");
        }
        proposal.setId(null);
        proposal.setStatus(PENDING);
        proposal.setProposalVersion(1);
        proposalMapper.insert(proposal);
        return proposal;
    }

    @Override
    public List<AbilityTagCandidatePlacementProposal> listByCandidateId(Long candidateId) {
        return proposalMapper.selectList(Wrappers.<AbilityTagCandidatePlacementProposal>lambdaQuery()
                .eq(AbilityTagCandidatePlacementProposal::getCandidateId, candidateId)
                .orderByDesc(AbilityTagCandidatePlacementProposal::getProposalVersion));
    }

    @Override
    @Transactional
    public AbilityTagCandidatePlacementProposal updatePending(Long candidateId, Long proposalId,
                                                               String action, Long parentDomainId,
                                                               Long targetTagId, String rationale) {
        AbilityTagCandidatePlacementProposal proposal = requireProposal(candidateId, proposalId);
        if (!PENDING.equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT, "已处理的挂载建议不能修改");
        }
        requireAction(action);
        proposal.setAction(action);
        proposal.setTargetParentDomainId(parentDomainId);
        proposal.setTargetTagId(targetTagId);
        proposal.setRationale(rationale);
        proposal.setProposalVersion((proposal.getProposalVersion() == null ? 0 : proposal.getProposalVersion()) + 1);
        proposalMapper.updateById(proposal);
        return proposal;
    }

    @Override
    @Transactional
    public PlacementApplyResult apply(Long candidateId, Long proposalId, Integer proposalVersion, Long operatorId) {
        AbilityTagCandidatePlacementProposal proposal = requireProposal(candidateId, proposalId);
        if (APPLIED.equals(proposal.getStatus())) {
            return new PlacementApplyResult(APPLIED, proposal.getFinalTagId());
        }
        if (!PENDING.equals(proposal.getStatus()) || !Objects.equals(proposal.getProposalVersion(), proposalVersion)) {
            return markStale(proposal);
        }

        AbilityTagCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null || !PENDING.equals(candidate.getStatus())) {
            return markStale(proposal);
        }

        requireAction(proposal.getAction());
        AbilityTag parent = requireEnabledDomain(proposal.getTargetParentDomainId());
        Long finalTagId;
        if (MERGE_EXISTING.equals(proposal.getAction())) {
            AbilityTag target = requireTargetLeaf(proposal.getTargetTagId(), parent, candidate);
            candidateService.merge(candidateId, target.getId(), operatorId, APPLY_COMMENT);
            finalTagId = target.getId();
        } else {
            if (proposal.getTargetTagId() != null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "创建 L2 建议不能指定已有目标标签");
            }
            if (!Objects.equals(candidate.getTagCategory(), parent.getTagCategory())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "候选标签分类与目标 L1 能力域不一致");
            }
            finalTagId = candidateService.approve(candidateId, parent.getId(), operatorId, APPLY_COMMENT);
        }

        proposal.setStatus(APPLIED);
        proposal.setFinalTagId(finalTagId);
        proposal.setAppliedBy(operatorId);
        proposal.setAppliedTime(LocalDateTime.now());
        proposalMapper.updateById(proposal);
        return new PlacementApplyResult(APPLIED, finalTagId);
    }

    private AbilityTagCandidatePlacementProposal requireProposal(Long candidateId, Long proposalId) {
        AbilityTagCandidatePlacementProposal proposal = proposalMapper.selectById(proposalId);
        if (proposal == null || !Objects.equals(candidateId, proposal.getCandidateId())) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "标签挂载建议不存在");
        }
        return proposal;
    }

    private PlacementApplyResult markStale(AbilityTagCandidatePlacementProposal proposal) {
        proposal.setStatus(STALE);
        proposalMapper.updateById(proposal);
        return new PlacementApplyResult(STALE, null);
    }

    private void requireAction(String action) {
        if (!MERGE_EXISTING.equals(action) && !CREATE_L2.equals(action)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的标签挂载动作");
        }
    }

    private AbilityTag requireEnabledDomain(Long parentDomainId) {
        AbilityTag parent = tagMapper.selectById(parentDomainId);
        if (!AbilityTagHierarchy.isEnabledDomain(parent)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "目标必须是启用的 L1 能力域");
        }
        return parent;
    }

    private AbilityTag requireTargetLeaf(Long targetTagId, AbilityTag parent, AbilityTagCandidate candidate) {
        AbilityTag target = tagMapper.selectById(targetTagId);
        if (!AbilityTagHierarchy.isAssessable(target)
                || !Objects.equals(target.getParentId(), parent.getId())
                || !Objects.equals(target.getTagCategory(), candidate.getTagCategory())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "目标标签必须是该 L1 能力域下启用的 L2 标签");
        }
        return target;
    }
}
