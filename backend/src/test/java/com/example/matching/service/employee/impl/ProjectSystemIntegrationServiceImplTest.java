package com.example.matching.service.employee.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSystemIntegrationServiceImplTest {

    private final ProjectSystemIntegrationServiceImpl service = new ProjectSystemIntegrationServiceImpl();

    @Test
    void failsExplicitlyUntilAnExternalProjectSystemAdapterIsConfigured() {
        assertThatThrownBy(() -> service.fetchAbilitiesFromProject(42L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.syncAbilitiesToProject(42L, java.util.List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
