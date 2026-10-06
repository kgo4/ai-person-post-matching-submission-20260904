package com.example.matching.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptMetadataResolverTest {

    private PromptMetadataResolver resolver;

    /** 内容非空即 exists()=true（ByteArrayResource 的默认行为） */
    private static org.springframework.core.io.Resource resource(String content) {
        return new ByteArrayResource(content.getBytes());
    }

    /**
     * 表示"该候选不存在"。
     *
     * ⚠️ **不能用 `new ByteArrayResource(new byte[0])`**：`ByteArrayResource#exists()`
     * **恒返回 true**（它只看内容是否为 null，空数组也算存在）。用它当哨兵会让
     * 第一个候选永远"命中"，于是解析到空内容 → 报 `has no version header`，
     * 真正想测的场景根本走不到。这里显式覆写 `exists()`。
     */
    private static final org.springframework.core.io.Resource NOT_FOUND =
            new ByteArrayResource(new byte[0]) {
                @Override
                public boolean exists() {
                    return false;
                }
            };

    @BeforeEach
    void setUp() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        resolver = new PromptMetadataResolver(resourceLoader);

        // ⚠️ 这里是**按候选顺序探测**的，不能再用"兜底返回默认内容"：
        // 解析器会依次探测 `-system.txt` → `-prompt.ftl` → `.ftl` → `.txt`，
        // 若兜底对任何路径都返回非空，第一次探测就会命中兜底内容，
        // 永远走不到真正想测的那个候选（表现为版本恒为兜底值，测试假红）。
        // 所以：**不在白名单里的路径一律视为不存在**（null + exists()=false）。
        when(resourceLoader.getResource(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.contains("/ftl-only-prompt.ftl")) {
                return resource("# prompt-version: v1.5\ncontent");
            } else if (path.contains("/txt-only-system.txt")) {
                return resource("# prompt-version: v2.1\ncontent");
            } else if (path.contains("/both-system.txt")) {
                return resource("# prompt-version: v2.1\ncontent");
            } else if (path.contains("/both-prompt.ftl")) {
                return resource("# prompt-version: v1.5\ncontent");
            } else if (path.contains("/no-version-prompt.ftl")) {
                return resource("no version header");
            } else if (path.contains("/empty-system.txt")) {
                return resource("");
            } else if (path.contains("/old-format-prompt.ftl")) {
                return resource("<#-- prompt-version: v3.0 -->\ncontent");
            }
            return NOT_FOUND;
        });
    }

    @Test
    @DisplayName("只有 -prompt.ftl 时，从 ftl 解析（FreeMarker 渲染那条路）")
    void resolvesVersionFromFtlFile() {
        PromptMetadataResolver.PromptMetadata meta = resolver.resolve("ftl-only");
        assertThat(meta.name()).isEqualTo("ftl-only");
        assertThat(meta.version()).isEqualTo("v1.5");
    }

    @Test
    @DisplayName("只有 -system.txt 时，从 txt 解析（chat 埋点那条路）")
    void resolvesVersionFromTxtFile() {
        PromptMetadataResolver.PromptMetadata meta = resolver.resolve("txt-only");
        assertThat(meta.name()).isEqualTo("txt-only");
        assertThat(meta.version()).isEqualTo("v2.1");
    }

    @Test
    @DisplayName("两者都存在时优先 -system.txt（与 chat() 埋点同源，且顺序是契约）")
    void prefersSystemTxtWhenBothExist() {
        assertThat(resolver.resolve("both").version()).isEqualTo("v2.1");
    }

    @Test
    void rejectsLegacyFreemarkerCommentFormat() {
        assertThatThrownBy(() -> resolver.resolve("old-format"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing valid version header");
    }

    @Test
    void throwsOnMissingHeader() {
        assertThatThrownBy(() -> resolver.resolve("no-version"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing valid version header");
    }

    @Test
    void throwsOnEmptyFile() {
        assertThatThrownBy(() -> resolver.resolve("empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no version header");
    }

    /**
     * 真实 classpath 上的解析（不经 mock）。
     *
     * <p>回归背景：运行审计页的「提示词版本」整列 `unknown`。
     * `LangChain4jChatService.resolveVersion` 把调用方的**裸名**拼成 `name + ".txt"`，
     * 而实际资源叫 `name-system.txt` / `name-prompt.ftl` —— 找不到就 catch 返回 unknown（静默）。
     */
    @Nested
    @DisplayName("真实资源：裸名要能解析出版本（而不是 unknown）")
    class RealClasspath {

        private final PromptMetadataResolver realResolver =
                new PromptMetadataResolver(new org.springframework.core.io.DefaultResourceLoader());

        @Test
        @DisplayName("经 -system.txt 解析（chat 埋点传的就是裸名）")
        void viaSystemTxt() {
            assertThat(realResolver.resolve("gap-diagnosis").version())
                    .isNotEqualTo("unknown")
                    .startsWith("v");
            assertThat(realResolver.resolve("ai-test-evaluate").version()).startsWith("v");
        }

        @Test
        @DisplayName("经 -prompt.ftl 解析（只有 ftl、没有 system.txt 的那几个）")
        void viaPromptFtl() {
            for (String name : new String[] {
                    "excel-structure-recognize", "extend-field-parse",
                    "gap-diagnosis", "learning-suggestion"}) {
                assertThat(realResolver.resolve(name).version())
                        .as("%s 必须解析出版本而不是 unknown", name)
                        .isNotEqualTo("unknown")
                        .startsWith("v");
            }
        }

        @Test
        @DisplayName("经裸 .ftl 解析（matching-overview-report）")
        void viaBareFtl() {
            assertThat(realResolver.resolve("matching-overview-report").version()).startsWith("v");
        }

        @Test
        @DisplayName("带扩展名也幂等（.txt / -prompt.ftl 后缀会被剥掉）")
        void withExtensionIsIdempotent() {
            String bare = realResolver.resolve("gap-diagnosis").version();
            assertThat(realResolver.resolve("gap-diagnosis.txt").version()).isEqualTo(bare);
            assertThat(realResolver.resolve("gap-diagnosis-prompt.ftl").version()).isEqualTo(bare);
        }

        @Test
        @DisplayName("确实不存在时抛错（而不是静默 unknown —— 静默正是当初没人发现的原因）")
        void missingResourceThrows() {
            assertThatThrownBy(() -> realResolver.resolve("this-prompt-does-not-exist"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Prompt resource not found");
        }
    }
}
