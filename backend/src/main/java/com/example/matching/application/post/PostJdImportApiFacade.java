package com.example.matching.application.post;

import com.example.matching.dto.post.JdAnalyzeRequestDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.service.post.JdAbilityExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostJdImportApiFacade {

    private final JdAbilityExtractService jdAbilityExtractService;

    public JdAnalyzeResponseDTO analyzeJd(Long postId, String jdText) {
        return jdAbilityExtractService.analyzeJd(postId, jdText);
    }

    public void applyAnalysisResult(Long postId, List<JdAbilityItemDTO> items) {
        jdAbilityExtractService.applyAnalysisResult(postId, items);
    }
}
