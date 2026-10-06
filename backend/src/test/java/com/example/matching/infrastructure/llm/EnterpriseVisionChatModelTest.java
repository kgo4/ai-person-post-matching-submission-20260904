package com.example.matching.infrastructure.llm;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 企业视觉模型门面的行为约束测试。
 * <p>
 * 重点覆盖三件事：① 未配置时安全降级（不发起任何外部调用）；② 多模态消息结构符合 OpenAI 兼容约定
 * （system 纯文本 + user 图文块）；③ 热切换（原子替换）后新实例立即生效。
 */
class EnterpriseVisionChatModelTest {

    private static final String IMAGE_DATA_URL = "data:image/jpeg;base64,AAAA";

    private EnterpriseVisionChatModel newModel() {
        AiProviderConcurrencyGate gate = new AiProviderConcurrencyGate(2, 1, 0, 1_000, 1, 0);
        return new EnterpriseVisionChatModel(gate);
    }

    private SystemAiModelConfig usableConfig() {
        SystemAiModelConfig config = new SystemAiModelConfig();
        config.setId(1L);
        config.setEnabled(true);
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-chat");
        config.setTimeoutSeconds(30);
        return config;
    }

    @Test
    void degradesToNullWhenVisionModelNameMissing() {
        EnterpriseVisionChatModel model = newModel();

        // 文本模型可用，但没有下发视觉模型名 —— 不能复用文本模型名去发图
        model.refreshFromConfig(usableConfig(), "key", null);

        assertThat(model.isEnabled()).isFalse();
        assertThat(model.getCurrentModelName()).isNull();
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void degradesToNullWhenModelDisabled() {
        EnterpriseVisionChatModel model = newModel();
        SystemAiModelConfig disabled = usableConfig();
        disabled.setEnabled(false);

        model.refreshFromConfig(disabled, "key", "deepseek-v4.1-flash");

        assertThat(model.isEnabled()).isFalse();
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void degradesToNullWhenApiKeyMissing() {
        EnterpriseVisionChatModel model = newModel();

        model.refreshFromConfig(usableConfig(), "  ", "deepseek-v4.1-flash");

        assertThat(model.isEnabled()).isFalse();
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void activatesWhenConfigAndVisionNameProvided() {
        EnterpriseVisionChatModel model = newModel();

        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");

        assertThat(model.isEnabled()).isTrue();
        assertThat(model.getCurrentModelName()).isEqualTo("deepseek-v4.1-flash");
    }

    @Test
    void refreshFromPropertiesDisablesWhenPropertiesAbsent() {
        EnterpriseVisionChatModel model = newModel();

        model.refreshFromProperties(null, "deepseek-v4.1-flash");

        assertThat(model.isEnabled()).isFalse();
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void refreshFromPropertiesActivatesWhenEnvFullyConfigured() {
        EnterpriseVisionChatModel model = newModel();
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("dev-key");
        properties.setModelName("deepseek-chat");
        properties.setVisionModelName("deepseek-v4.1-flash");

        model.refreshFromProperties(properties, properties.getVisionModelName());

        assertThat(model.isEnabled()).isTrue();
        assertThat(model.getCurrentModelName()).isEqualTo("deepseek-v4.1-flash");
    }

    @Test
    void sendsSystemAsPlainTextAndImagesInsideUserMessage() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");

        RecordingChatModel recorder = new RecordingChatModel();
        injectDelegate(model, recorder);

        String reply = model.analyzeVision("system-prompt", "user-prompt", List.of(IMAGE_DATA_URL));

        assertThat(reply).isEqualTo("vision-ok");
        List<ChatMessage> messages = recorder.captured.get();
        assertThat(messages).hasSize(2);
        // 1) system 消息必须是纯文本，绝不含图片（DeepSeek 等厂商会 400 拒绝）
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo("system-prompt");
        // 2) user 消息承载图片与用户文本
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        List<Content> contents = ((UserMessage) messages.get(1)).contents();
        assertThat(contents).hasSize(2);
        assertThat(contents.get(0)).isInstanceOf(ImageContent.class);
        assertThat(((ImageContent) contents.get(0)).image().url().toString()).isEqualTo(IMAGE_DATA_URL);
        assertThat(contents.get(1)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) contents.get(1)).text()).isEqualTo("user-prompt");
    }

    @Test
    void skipsSystemMessageWhenBlankAndIgnoresBlankImageUrls() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");
        RecordingChatModel recorder = new RecordingChatModel();
        injectDelegate(model, recorder);

        model.analyzeVision("  ", "only-text", List.of("", "   "));

        List<ChatMessage> messages = recorder.captured.get();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        List<Content> contents = ((UserMessage) messages.get(0)).contents();
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).isInstanceOf(TextContent.class);
    }

    @Test
    void returnsNullWhenNoUsableContent() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");
        RecordingChatModel recorder = new RecordingChatModel();
        injectDelegate(model, recorder);

        assertThat(model.analyzeVision(null, null, List.of())).isNull();
        assertThat(recorder.captured.get()).isNull();
    }

    @Test
    void returnsNullWhenDelegateThrows() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");
        injectDelegate(model, new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("upstream 400");
            }
        });

        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void returnsNullWhenResponseTextBlank() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");
        injectDelegate(model, new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("   ")).build();
            }
        });

        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    @Test
    void hotSwitchReplacesDelegateImmediately() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "old-vision-model");
        assertThat(model.getCurrentModelName()).isEqualTo("old-vision-model");

        // 模拟后台保存配置触发热切换：模型实例被原子替换，调用方无需重新注入
        SystemAiModelConfig switched = usableConfig();
        switched.setBaseUrl("https://another.example/v1");
        model.refreshFromConfig(switched, "new-key", "deepseek-v4.1-flash");

        assertThat(model.getCurrentModelName()).isEqualTo("deepseek-v4.1-flash");
        assertThat(model.isEnabled()).isTrue();

        // 替换后新 delegate 立即生效：这里再注入一个可控 delegate，验证既有方法签名零改动
        RecordingChatModel recorder = new RecordingChatModel();
        injectDelegate(model, recorder);
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isEqualTo("vision-ok");
        assertThat(recorder.captured.get()).hasSize(2);
    }

    @Test
    void hotSwitchDisablesWhenVisionNameRemoved() {
        EnterpriseVisionChatModel model = newModel();
        model.refreshFromConfig(usableConfig(), "key", "deepseek-v4.1-flash");
        assertThat(model.isEnabled()).isTrue();

        // 后台把视觉模型名清空 —— 立即降级，不再发任何视觉请求
        model.refreshFromConfig(usableConfig(), "key", "   ");

        assertThat(model.isEnabled()).isFalse();
        assertThat(model.getCurrentModelName()).isNull();
        assertThat(model.analyzeVision("sys", "user", List.of(IMAGE_DATA_URL))).isNull();
    }

    /** 直接向门面注入一个可控 delegate，绕开真实 HTTP —— 门面的装配与编解码逻辑由本测试自行验证。 */
    private void injectDelegate(EnterpriseVisionChatModel model, ChatModel delegate) {
        ReflectionTestUtils.setField(model, "delegate", new AtomicReference<>(delegate));
    }

    /** 记录收到的消息并返回固定回复，用于断言请求结构。 */
    private static final class RecordingChatModel implements ChatModel {
        private final AtomicReference<List<ChatMessage>> captured = new AtomicReference<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            captured.set(request.messages());
            return ChatResponse.builder().aiMessage(AiMessage.from("vision-ok")).build();
        }
    }
}
