package com.example.matching.integration.volcengine.asr;

import org.junit.jupiter.api.Test;
import org.java_websocket.client.WebSocketClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StreamingAsrClientTest {

    @Test
    void fullServerResponseReadsPayloadAfterTheHeaderLengthField() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        AtomicReference<String> transcription = new AtomicReference<>();
        session.setTranscriptCallback((text, definite) -> transcription.set(text));

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage",
                serverResponse("{\"result\":{\"text\":\"hello\"}}"));

        assertThat(transcription).hasValue("hello");
    }

    @Test
    void fullServerResponseWithSequenceReadsPayloadAfterSequenceAndLengthFields() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        AtomicReference<String> transcription = new AtomicReference<>();
        session.setTranscriptCallback((text, definite) -> transcription.set(text));

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage",
                serverResponseWithSequence("{\"result\":{\"text\":\"hello\"}}"));

        assertThat(transcription).hasValue("hello");
    }

    @Test
    void emptyFinalServerResponseDoesNotAttemptJsonParsing() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        AtomicInteger completions = new AtomicInteger();
        session.setCompleteCallback(completions::incrementAndGet);

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage", finalServerResponse(""));

        assertThat(completions).hasValue(0);
    }

    @Test
    void keepAliveSendsANonFinalSilentAudioFrame() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        WebSocketClient webSocketClient = mock(WebSocketClient.class);
        ReflectionTestUtils.setField(session, "webSocketClient", webSocketClient);
        ((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(session, "isConnected")).set(true);
        ((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(session, "isReady")).set(true);

        ReflectionTestUtils.invokeMethod(session, "sendKeepAliveAudio");

        verify(webSocketClient).send(any(byte[].class));
    }

    @Test
    void errorResponseSkipsErrorCodeAndPayloadLength() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        WebSocketClient webSocketClient = mock(WebSocketClient.class);
        ReflectionTestUtils.setField(session, "webSocketClient", webSocketClient);
        ((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(session, "isConnected")).set(true);
        ((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(session, "isReady")).set(true);
        String payload = "{\"error\":\"invalid audio\"}";

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage", errorResponse(payload));

        assertThat(ReflectionTestUtils.getField(session, "serverError")).isEqualTo(payload);
        assertThat(session.isConnected()).isFalse();
        verify(webSocketClient).close();
    }

    @Test
    void completionCallbackRunsOnlyForFinalServerResponse() {
        StreamingAsrClient.AsrSession session = new StreamingAsrClient().createSession();
        AtomicInteger completions = new AtomicInteger();
        session.setCompleteCallback(completions::incrementAndGet);

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage",
                serverResponse("{\"result\":{\"text\":\"partial\"}}"));

        assertThat(completions).hasValue(0);

        ReflectionTestUtils.invokeMethod(session, "handleBinaryMessage",
                finalServerResponse("{\"result\":{\"text\":\"final\"}}"));

        assertThat(completions).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void duplicatedApiKeyFallsBackToLegacyHeaders() {
        StreamingAsrClient client = new StreamingAsrClient();
        ReflectionTestUtils.setField(client, "appId", "app-123");
        ReflectionTestUtils.setField(client, "accessKey", "legacy-key");
        ReflectionTestUtils.setField(client, "apiKey", "legacy-key");
        ReflectionTestUtils.setField(client, "resourceId", "volc.bigasr.sauc.duration");

        StreamingAsrClient.AsrSession session = client.createSession();
        Map<String, String> headers = session.buildHeaders("connect-1");

        assertThat(headers)
                .containsEntry("X-Api-App-Key", "app-123")
                .containsEntry("X-Api-Access-Key", "legacy-key")
                .containsEntry("X-Api-Resource-Id", "volc.bigasr.sauc.duration")
                .containsEntry("X-Api-Connect-Id", "connect-1")
                .doesNotContainKey("X-Api-Key");
    }

    @SuppressWarnings("unchecked")
    @Test
    void distinctApiKeyUsesNewConsoleHeader() {
        StreamingAsrClient client = new StreamingAsrClient();
        ReflectionTestUtils.setField(client, "appId", "app-123");
        ReflectionTestUtils.setField(client, "accessKey", "legacy-key");
        ReflectionTestUtils.setField(client, "apiKey", "new-key");
        ReflectionTestUtils.setField(client, "resourceId", "volc.bigasr.sauc.duration");

        StreamingAsrClient.AsrSession session = client.createSession();
        Map<String, String> headers = session.buildHeaders("connect-2");

        assertThat(headers)
                .containsEntry("X-Api-Key", "new-key")
                .containsEntry("X-Api-Resource-Id", "volc.bigasr.sauc.duration")
                .containsEntry("X-Api-Connect-Id", "connect-2")
                .doesNotContainKey("X-Api-App-Key")
                .doesNotContainKey("X-Api-Access-Key");
    }

    private byte[] serverResponse(String payload) {
        return serverResponse((byte) 0x90, payload);
    }

    private byte[] finalServerResponse(String payload) {
        return serverResponse((byte) 0x92, payload);
    }

    private byte[] serverResponseWithSequence(String payload) {
        byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(12 + body.length);
        frame.put((byte) 0x11).put((byte) 0x91).put((byte) 0x10).put((byte) 0x00);
        frame.putInt(1).putInt(body.length).put(body);
        return frame.array();
    }

    private byte[] serverResponse(byte flags, String payload) {
        byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length);
        frame.put((byte) 0x11).put(flags).put((byte) 0x10).put((byte) 0x00);
        frame.putInt(body.length).put(body);
        return frame.array();
    }

    private byte[] errorResponse(String payload) {
        byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(12 + body.length);
        frame.put((byte) 0x11).put((byte) 0xF0).put((byte) 0x10).put((byte) 0x00);
        frame.putInt(1000).putInt(body.length).put(body);
        return frame.array();
    }
}
