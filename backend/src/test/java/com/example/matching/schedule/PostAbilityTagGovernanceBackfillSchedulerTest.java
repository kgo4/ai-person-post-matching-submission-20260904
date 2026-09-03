package com.example.matching.schedule;

import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.port.post.PostQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostAbilityTagGovernanceBackfillSchedulerTest {

    @Mock PostQueryPort postQueryPort;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void publishesGovernanceEventsOnlyForUntaggedNamedAbilities() throws Exception {
        when(postQueryPort.listUntaggedPostAbilityModels(500)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 10L, null, 3, null, 1, 1, "v1", "证据", "Redis"),
                new PostQueryPort.PostAbilityDTO(2L, 10L, null, 3, null, 1, 1, "v1", "证据", "   ")
        ));
        PostAbilityTagGovernanceBackfillScheduler scheduler = new PostAbilityTagGovernanceBackfillScheduler(
                postQueryPort, eventPublisher);
        setField(scheduler, "enabled", true);
        setField(scheduler, "batchSize", 500);

        scheduler.scan();

        ArgumentCaptor<PostAbilityTagGovernanceRequestedEvent> captor =
                ArgumentCaptor.forClass(PostAbilityTagGovernanceRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        verify(postQueryPort).listUntaggedPostAbilityModels(500);
        assertThat(captor.getValue().abilityName()).isEqualTo("Redis");
        verifyNoMoreInteractions(eventPublisher);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
