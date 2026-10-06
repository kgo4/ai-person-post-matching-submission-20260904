package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.contest.ContestQueryPort.ContestEvidenceDTO;
import com.example.matching.port.contest.ContestQueryPort.EvidenceWriteCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestQueryPortAdapter implements ContestQueryPort {

    private final ContestEvidenceItemMapper evidenceItemMapper;

    @Override
    public List<ContestEvidenceDTO> listAllEvidence(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestEvidenceItem>();
        if (limit > 0) w.last("LIMIT " + limit);
        return evidenceItemMapper.selectList(w).stream().map(ContestEvidenceDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<ContestEvidenceDTO> listEvidencePaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ContestEvidenceItem>(page, size);
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestEvidenceItem>()
                .orderByAsc(ContestEvidenceItem::getId);
        return evidenceItemMapper.selectPage(p, w).getRecords().stream()
                .map(ContestEvidenceDTO::from).collect(Collectors.toList());
    }

    @Override
    public boolean evidenceExists(String sourceType, Long sourceRefId, String targetType, Long targetRefId) {
        return evidenceItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestEvidenceItem>()
                        .eq(ContestEvidenceItem::getSourceType, sourceType)
                        .eq(ContestEvidenceItem::getSourceRefId, sourceRefId)
                        .eq(ContestEvidenceItem::getTargetType, targetType)
                        .eq(ContestEvidenceItem::getTargetRefId, targetRefId)) > 0;
    }

    @Override
    public void saveEvidence(EvidenceWriteCommand command) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setEvidenceCode(command.evidenceCode());
        item.setSourceType(command.sourceType());
        item.setSourceRefId(command.sourceRefId());
        item.setSourceTitle(command.sourceTitle());
        item.setSourceText(command.sourceText());
        item.setTargetType(command.targetType());
        item.setTargetRefId(command.targetRefId());
        item.setAbilityName(command.abilityName());
        item.setTagId(command.tagId());
        item.setConfidenceScore(command.confidenceScore());
        item.setCredibilityScore(command.credibilityScore());
        item.setEvidenceStatus("VERIFIED");
        item.setRagChunkIds(command.ragChunkIds());
        item.setRagDocumentIds(command.ragDocumentIds());
        evidenceItemMapper.insert(item);
    }
}
