package com.example.matching.application.capability;

import com.example.matching.dto.capability.CapabilityBrainSummaryDTO;
import com.example.matching.service.capability.CapabilityBrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapabilityBrainApiFacade {

    private final CapabilityBrainService capabilityBrainService;

    public CapabilityBrainSummaryDTO getSummary() {
        return capabilityBrainService.getSummary();
    }
}
