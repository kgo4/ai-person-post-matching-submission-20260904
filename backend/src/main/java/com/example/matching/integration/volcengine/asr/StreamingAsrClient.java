package com.example.matching.integration.volcengine.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.zip.GZIPOutputStream;

/**
 * 大模型流式语音识别客户端
 * <p>
 * 使用火山引擎大模型ASR WebSocket协议，支持实时流式语音转录。
 * 协议地址：wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async
 */
@Slf4j
@Component
public class StreamingAsrClient {

    @Value("${volcengine.asr.ws-url:wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async}")
    private String wsUrl;

    @Value("${volcengine.asr.app-id}")
    private String appId;

    @Value("${volcengine.asr.access-key}")
    private String accessKey;

    @Value("${volcengine.asr.resource-id:volc.bigasr.sauc.duration}")
    private String resourceId;

    @Value("${volcengine.asr.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建ASR会话
     */
    public AsrSession createSession() {
        return new AsrSession();
    }

    /**
     * ASR会话
     */
    public class AsrSession {
        private static final long KEEP_ALIVE_INTERVAL_MILLIS = 2_000;
        private static final byte[] SILENT_AUDIO_FRAME = new byte[3_200];

        private WebSocketClient webSocketClient;
        private final AtomicBoolean isConnected = new AtomicBoolean(false);
        private final AtomicBoolean isReady = new AtomicBoolean(false);
        private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "asr-keep-alive");
            thread.setDaemon(true);
            return thread;
        });
        private volatile String serverError;
        private BiConsumer<String, Boolean> transcriptCallback;
        private Runnable completeCallback;
        private volatile CountDownLatch finalResponseLatch = new CountDownLatch(0);

        /**
         * 连接到ASR服务
         */
        public void connect() throws Exception {
            String connectId = UUID.randomUUID().toString();
            URI uri = new URI(wsUrl);

            Map<String, String> headers = buildHeaders(connectId);

            webSocketClient = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("ASR WebSocket连接已建立，connectId: {}", connectId);
                    isConnected.set(true);
                }

                @Override
                public void onMessage(String message) {
                    log.debug("ASR收到文本消息: {}", message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    try {
                        byte[] message = new byte[bytes.remaining()];
                        bytes.get(message);
                        handleBinaryMessage(message);
                    } catch (Exception e) {
                        log.error("ASR解析二进制消息失败: {}", e.getMessage(), e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("ASR WebSocket已关闭，code: {}, reason: {}", code, reason);
                    isConnected.set(false);
                    isReady.set(false);
                    stopKeepAlive();
                    finalResponseLatch.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("ASR WebSocket错误: {}", ex.getMessage(), ex);
                    serverError = ex.getMessage();
                }
            };

            webSocketClient.connectBlocking(10, TimeUnit.SECONDS);
            if (!isConnected.get()) {
                throw new Exception("ASR WebSocket连接失败");
            }

            // 发送 full client request（配置参数）
            sendFullClientRequest();
            isReady.set(true);
            startKeepAlive();
        }

        /**
         * 发送 full client request（事件1：配置参数）
         */
        private void sendFullClientRequest() throws Exception {
            Map<String, Object> request = new HashMap<>();

            Map<String, Object> user = new HashMap<>();
            user.put("uid", "video-interview-system");
            request.put("user", user);

            Map<String, Object> audio = new HashMap<>();
            audio.put("format", "pcm");
            audio.put("rate", 16000);
            audio.put("bits", 16);
            audio.put("channel", 1);
            audio.put("language", "zh-CN");
            request.put("audio", audio);

            Map<String, Object> req = new HashMap<>();
            req.put("model_name", "bigmodel");
            req.put("enable_itn", true);
            req.put("enable_punc", true);
            req.put("show_utterances", true);
            request.put("request", req);

            String jsonPayload = objectMapper.writeValueAsString(request);
            byte[] payload = gzipCompress(jsonPayload.getBytes(StandardCharsets.UTF_8));

            // 协议头：version=1, header_size=1, type=1(完整客户端请求), flags=0, serialization=1(JSON), compression=1(Gzip)
            byte[] header = new byte[]{0x11, 0x10, 0x11, 0x00};
            sendBinaryFrame(header, payload);
            log.info("ASR full client request已发送");
        }

        /**
         * 发送音频数据
         *
         * @param audioData PCM音频数据（16kHz, 16bit, mono）
         * @param isLast    是否为最后一包
         */
        public void sendAudio(byte[] audioData, boolean isLast) throws Exception {
            if (!isConnected.get()) {
                throw new IllegalStateException("ASR WebSocket未连接");
            }

            byte[] compressed = gzipCompress(audioData);

            // 协议头：version=1, header_size=1, type=2(纯音频), flags=0/2, serialization=0(无), compression=1(Gzip)
            byte flags = isLast ? (byte) 0x22 : (byte) 0x20;
            byte[] header = new byte[]{0x11, flags, 0x01, 0x00};
            sendBinaryFrame(header, compressed);
        }

        private void startKeepAlive() {
            keepAliveExecutor.scheduleAtFixedRate(() -> {
                try {
                    sendKeepAliveAudio();
                } catch (Exception e) {
                    log.debug("ASR keep-alive frame was not sent: {}", e.getMessage());
                }
            }, KEEP_ALIVE_INTERVAL_MILLIS, KEEP_ALIVE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void sendKeepAliveAudio() throws Exception {
            if (isConnected() && webSocketClient != null) {
                sendAudio(SILENT_AUDIO_FRAME, false);
            }
        }

        private void stopKeepAlive() {
            keepAliveExecutor.shutdownNow();
        }

        /**
         * 发送最后一包（空负包，表示音频结束）
         */
        public void sendFinish() throws Exception {
            finalResponseLatch = new CountDownLatch(1);
            sendAudio(new byte[0], true);
        }

        /**
         * 处理服务端二进制消息
         */
        private void handleBinaryMessage(byte[] data) throws Exception {
            if (data.length < 4) return;

            int headerSize = (data[0] & 0x0F) * 4;
            if (headerSize < 4 || data.length < headerSize + Integer.BYTES) {
                log.warn("ASR binary frame header is incomplete: length={}", data.length);
                return;
            }

            int messageType = (data[1] >> 4) & 0x0F;
            int messageFlags = data[1] & 0x0F;
            int compression = data[2] & 0x0F;
            boolean finalResponse = (messageFlags & 0x02) != 0;
            boolean hasSequenceNumber = (messageFlags & 0x01) != 0;

            if (messageType == 0x0F) {
                // 错误响应
                byte[] payloadBytes = extractPayload(data, headerSize + Integer.BYTES);
                if (compression == 0x01) {
                    payloadBytes = gzipDecompress(payloadBytes);
                }
                String errorMsg = new String(payloadBytes, StandardCharsets.UTF_8);
                log.error("ASR服务错误: {}", errorMsg);
                serverError = errorMsg;
                isConnected.set(false);
                isReady.set(false);
                stopKeepAlive();
                if (webSocketClient != null) {
                    webSocketClient.close();
                }
                finalResponseLatch.countDown();
                return;
            }

            if (messageType != 0x09) {
                log.debug("ASR忽略消息类型: {}", messageType);
                return;
            }

            // Full server responses with flag 1 or 3 include a sequence number before payload length.
            int payloadLengthOffset = headerSize + (hasSequenceNumber ? Integer.BYTES : 0);
            byte[] payloadBytes = extractPayload(data, payloadLengthOffset);

            if (compression == 0x01) {
                payloadBytes = gzipDecompress(payloadBytes);
            }

            if (payloadBytes.length == 0) {
                log.debug("ASR server response contains an empty payload");
                if (finalResponse) {
                    finalResponseLatch.countDown();
                }
                return;
            }

            String jsonStr = new String(payloadBytes, StandardCharsets.UTF_8);
            parseTranscriptionResult(jsonStr, finalResponse);
        }

        private byte[] extractPayload(byte[] data, int lengthOffset) {
            if (lengthOffset < 0 || lengthOffset + Integer.BYTES > data.length) {
                throw new IllegalArgumentException("ASR binary frame has no payload length");
            }

            int payloadLength = ByteBuffer.wrap(data, lengthOffset, Integer.BYTES).getInt();
            int payloadOffset = lengthOffset + Integer.BYTES;
            if (payloadLength < 0 || payloadOffset + payloadLength > data.length) {
                throw new IllegalArgumentException("ASR binary frame payload length is invalid: " + payloadLength);
            }

            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, payloadOffset, payload, 0, payloadLength);
            return payload;
        }

        /**
         * 解析转录结果
         */
        private void parseTranscriptionResult(String jsonStr, boolean finalResponse) {
            try {
                JsonNode root = objectMapper.readTree(jsonStr);
                JsonNode result = root.get("result");
                if (result == null) return;

                JsonNode utterances = result.get("utterances");
                if (utterances != null && utterances.isArray()) {
                    for (JsonNode utt : utterances) {
                        String text = utt.has("text") ? utt.get("text").asText() : "";
                        // 结束响应必须接收，以保留 ASR 尚未单独标记 definite 的尾部片段。
                        // 历史分段的重复由转写缓冲按会话/题目指纹幂等拦截，不能在这里
                        // 直接丢弃，否则最后一句回答可能无法落库。
                        boolean definite = (utt.has("definite") && utt.get("definite").asBoolean())
                                || finalResponse;
                        if (!text.isBlank() && transcriptCallback != null) {
                            transcriptCallback.accept(text, definite);
                        }
                    }
                } else {
                    // 无utterances时使用全文
                    String text = result.has("text") ? result.get("text").asText() : "";
                    if (!text.isBlank() && transcriptCallback != null) {
                        transcriptCallback.accept(text, finalResponse);
                    }
                }

                if (finalResponse && completeCallback != null) {
                    completeCallback.run();
                }
                if (finalResponse) {
                    finalResponseLatch.countDown();
                }
            } catch (Exception e) {
                log.error("解析ASR转录结果失败: {}", e.getMessage(), e);
            }
        }

        /**
         * 发送二进制帧
         */
        private void sendBinaryFrame(byte[] header, byte[] payload) throws Exception {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + payload.length);
            buffer.put(header);
            buffer.putInt(payload.length);
            buffer.put(payload);
            buffer.flip();
            webSocketClient.send(buffer.array());
        }

        /**
         * 设置转录回调
         */
        public void setTranscriptCallback(BiConsumer<String, Boolean> callback) {
            this.transcriptCallback = callback;
        }

        /**
         * 设置完成回调
         */
        public void setCompleteCallback(Runnable callback) {
            this.completeCallback = callback;
        }

        /**
         * 关闭会话
         */
        public void close() {
            try {
                if (webSocketClient != null && isConnected.get()) {
                    sendFinish();
                    if (!finalResponseLatch.await(3, TimeUnit.SECONDS)) {
                        log.warn("ASR最终转写响应超时，将关闭会话");
                    }
                    webSocketClient.close();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待ASR最终转写响应时被中断");
            } catch (Exception e) {
                log.error("关闭ASR会话失败: {}", e.getMessage(), e);
            }
            isConnected.set(false);
            isReady.set(false);
            stopKeepAlive();
        }

        public boolean isConnected() {
            return isConnected.get() && isReady.get();
        }

        Map<String, String> buildHeaders(String connectId) {
            Map<String, String> headers = new HashMap<>();
            if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(accessKey)) {
                headers.put("X-Api-Key", apiKey);
            } else {
                headers.put("X-Api-App-Key", appId);
                headers.put("X-Api-Access-Key", accessKey);
            }
            headers.put("X-Api-Resource-Id", resourceId);
            headers.put("X-Api-Connect-Id", connectId);
            return headers;
        }
    }

    // ==================== Gzip 工具方法 ====================

    private static byte[] gzipCompress(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] gzipDecompress(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(
                new java.io.ByteArrayInputStream(data))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        return out.toByteArray();
    }
}
