package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.port.evolution.MarketJdQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 市场JD查询端口适配器 — infrastructure 持久化层，允许依赖 Mapper。
 */
@Service
@RequiredArgsConstructor
public class MarketJdQueryPortAdapter implements MarketJdQueryPort {

    private final MarketJdDataMapper marketJdDataMapper;

    @Override
    public MarketJdSnapshot getAdmissibleSnapshot(Long jdId) {
        if (jdId == null) {
            return null;
        }
        MarketJdData jd = marketJdDataMapper.selectById(jdId);
        if (jd == null) {
            return null;
        }
        // 重复（isDuplicate != 0）或清洗/治理阻断（analysisStatus=2）的 JD 不得作为证据
        if (jd.getIsDuplicate() != null && jd.getIsDuplicate() != 0) {
            return null;
        }
        if (jd.getAnalysisStatus() != null && jd.getAnalysisStatus() == 2) {
            return null;
        }
        return new MarketJdSnapshot(jd.getId(), jd.getPostName(), jd.getJobDescription(),
                jd.getCompanyDiversityKey());
    }

    @Override
    public String getCompanyDiversityKey(Long jdId) {
        if (jdId == null) {
            return null;
        }
        MarketJdData jd = marketJdDataMapper.selectById(jdId);
        return jd != null ? jd.getCompanyDiversityKey() : null;
    }

    @Override
    public List<String> findFilteredTexts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return marketJdDataMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MarketJdData>()
                        .eq("analysis_status", 2)
                        .or(wrapper -> wrapper.gt("noise_score", 0))
                        .orderByDesc("created_time")
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(jd -> (jd.getJobDescription() == null ? "" : jd.getJobDescription())
                        + "\n" + (jd.getRequirements() == null ? "" : jd.getRequirements()))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .collect(Collectors.toList());
    }
}
