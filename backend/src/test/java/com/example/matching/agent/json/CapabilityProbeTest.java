package com.example.matching.agent.json;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityProbeTest {

    @Test
    void detectsJsonSchemaCapability() {
        ChatModel model = modelWith(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        CapabilityProbe probe = new CapabilityProbe();
        assertEquals(CapabilityProbe.Level.JSON_SCHEMA, probe.probe(model));
    }

    @Test
    void fallsBackToJsonObjectWhenUnsupported() {
        ChatModel model = modelWith(Set.of());
        CapabilityProbe probe = new CapabilityProbe();
        assertEquals(CapabilityProbe.Level.JSON_OBJECT, probe.probe(model));
    }

    private static ChatModel modelWith(Set<Capability> caps) {
        return new ChatModel() {
            @Override
            public Set<Capability> supportedCapabilities() {
                return caps;
            }
        };
    }
}
