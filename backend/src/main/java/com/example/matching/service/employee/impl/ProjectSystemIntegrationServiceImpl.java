package com.example.matching.service.employee.impl;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.service.employee.ProjectSystemIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * External project-system integration boundary.
 *
 * This bean is disabled unless explicitly enabled. An enabled deployment must provide a real
 * adapter instead of silently pretending that a synchronization completed.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "project.system.enabled", havingValue = "true", matchIfMissing = false)
public class ProjectSystemIntegrationServiceImpl implements ProjectSystemIntegrationService {

    @Override
    public List<EmpAbility> fetchAbilitiesFromProject(Long empId) {
        throw integrationUnavailable();
    }

    @Override
    public void syncAbilitiesToProject(Long empId, List<EmpAbility> abilities) {
        throw integrationUnavailable();
    }

    @Override
    public boolean isProjectSystemAvailable() {
        return false;
    }

    private UnsupportedOperationException integrationUnavailable() {
        log.error("Project-system integration is enabled but no external adapter is configured");
        return new UnsupportedOperationException("Project-system integration adapter is not configured");
    }
}
