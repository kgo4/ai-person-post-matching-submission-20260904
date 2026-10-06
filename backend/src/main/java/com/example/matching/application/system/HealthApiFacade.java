package com.example.matching.application.system;

import com.example.matching.service.system.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HealthApiFacade {

    private final HealthService healthService;

    public Map<String, Object> checkMilvus() {
        return healthService.checkMilvus();
    }
}
