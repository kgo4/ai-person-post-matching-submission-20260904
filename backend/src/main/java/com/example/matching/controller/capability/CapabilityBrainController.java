package com.example.matching.controller.capability;

import com.example.matching.application.capability.CapabilityBrainApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.capability.CapabilityBrainSummaryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "岗位能力大脑", description = "统一展示多源采集、RAG、图谱、岗位演化、匹配诊断和学习路径闭环。")
@RestController
@RequestMapping("/api/capability-brain")
@RequiredArgsConstructor
public class CapabilityBrainController {

    private final CapabilityBrainApiFacade capabilityBrainApiFacade;

    @Operation(summary = "获取能力闭环总览")
    @GetMapping("/summary")
    public R<CapabilityBrainSummaryDTO> getSummary() {
        return R.ok(capabilityBrainApiFacade.getSummary());
    }
}
