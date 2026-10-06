package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.evolution.KnowledgeMaterialCleaner;
import com.example.matching.service.evolution.KnowledgeSourceIndexStatusUpdater;
import com.example.matching.service.evolution.SourceUploadOutcome;
import com.example.matching.service.rag.KnowledgeDocumentDeduplicator;
import com.example.matching.service.rag.KnowledgeDocumentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权威材料入口的测试。
 *
 * <p>三组重点：
 * <ul>
 *   <li><b>重复材料复用</b>：同一内容 + 同一类别不该重复建 RAG 索引；</li>
 *   <li><b>仅试算隔离</b>：试算材料会被自动清理，绝不能被正式上传复用；</li>
 *   <li><b>兜底清理的故障隔离</b>：一份坏数据不能拖死整批。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvolutionSourceIngestionServiceImplTest {

    private static final Path UPLOAD_DIR = Paths.get("uploads/evolution-sources");

    @Mock private KnowledgeSourceDocumentMapper sourceDocumentMapper;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private KnowledgeSourceIndexStatusUpdater indexStatusUpdater;
    @Mock private KnowledgeDocumentDeduplicator deduplicator;
    @Mock private KnowledgeMaterialCleaner materialCleaner;

    private Path uploadedFile;

    /**
     * LambdaQueryWrapper 把 `SFunction` 解析成列名依赖 MP 的 TableInfo 缓存。
     * 单测里没有 MyBatis 启动流程，不手动初始化就会在断言 SQL 片段时抛
     * 「can not find lambda cache for this entity」。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KnowledgeSourceDocument.class);
    }

    private EvolutionSourceIngestionServiceImpl service() {
        return new EvolutionSourceIngestionServiceImpl(sourceDocumentMapper, knowledgeDocumentService,
                indexStatusUpdater, deduplicator, materialCleaner);
    }

    @AfterEach
    void cleanUpUploadedFile() throws Exception {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    private static final byte[] CONTENT =
            "2026 年市场职业报告：新增岗位与能力要求变化。".getBytes(StandardCharsets.UTF_8);

    @Test
    void uploadIndustryWhitepaper_createsAndLinksRagDocument() {
        EvolutionSourceIngestionServiceImpl service =
                new EvolutionSourceIngestionServiceImpl(sourceDocumentMapper, knowledgeDocumentService,
                        indexStatusUpdater, deduplicator, materialCleaner);
        EvolutionSourceUploadDTO request = new EvolutionSourceUploadDTO();
        request.setTitle("2026 AI 行业白皮书");
        request.setEvolutionEnabled(true);

        doAnswer(invocation -> {
            KnowledgeSourceDocument document = invocation.getArgument(0);
            document.setId(91L);
            return 1;
        }).when(sourceDocumentMapper).insert(any(KnowledgeSourceDocument.class));
        RagKnowledgeDocument ragDocument = new RagKnowledgeDocument();
        ragDocument.setId(301L);
        when(knowledgeDocumentService.saveDocument(any(KnowledgeDocumentSaveDTO.class))).thenReturn(ragDocument);

        KnowledgeSourceDocument result = service.uploadIndustryWhitepaper(
                "whitepaper.txt", "AI 人才需求增长".getBytes(StandardCharsets.UTF_8), request, 7L);

        ArgumentCaptor<KnowledgeDocumentSaveDTO> ragCaptor = ArgumentCaptor.forClass(KnowledgeDocumentSaveDTO.class);
        verify(knowledgeDocumentService).saveDocument(ragCaptor.capture());
        assertThat(ragCaptor.getValue().getSourceType()).isEqualTo("INDUSTRY_WHITEPAPER");
        assertThat(ragCaptor.getValue().getSourceRefId()).isEqualTo(91L);
        assertThat(ragCaptor.getValue().getContent()).isEqualTo("AI 人才需求增长");
        assertThat(result.getRagDocumentId()).isEqualTo(301L);
        assertThat(result.getStoragePath()).isNotBlank();
        uploadedFile = Path.of(result.getStoragePath());
        verify(sourceDocumentMapper).updateById(result);
    }

    /* ==================== 重复材料复用 ==================== */

    private EvolutionSourceUploadDTO dto(String category, Boolean ephemeral) {
        EvolutionSourceUploadDTO dto = new EvolutionSourceUploadDTO();
        dto.setTitle("2026市场职业报告");
        dto.setSourceCategory(category);
        dto.setEphemeral(ephemeral);
        return dto;
    }

    private KnowledgeSourceDocument material(long id, int chunkCount, String indexStatus) {
        KnowledgeSourceDocument document = new KnowledgeSourceDocument();
        document.setId(id);
        document.setTitle("2026市场职业报告");
        document.setSourceType("CLOUD_KNOWLEDGE_INTERNAL");
        document.setSourceCategory("MARKET_REPORT");
        document.setChunkCount(chunkCount);
        document.setIndexStatus(indexStatus);
        return document;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<KnowledgeSourceDocument> capturedDedupWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<KnowledgeSourceDocument>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sourceDocumentMapper).selectOne(captor.capture());
        return captor.getValue();
    }

    /** 判重条件的 SQL 片段（列名是字面量，参数值走 paramNameValuePairs）。 */
    private String capturedDedupWhere() {
        return capturedDedupWrapper().getSqlSegment();
    }

    @Test
    @DisplayName("同一内容同一类别重复上传：直接复用，不新建也不重复建索引")
    void uploadInternalDocument_reusesExistingMaterial() {
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(material(700L, 18, "INDEXED"));

        SourceUploadOutcome outcome = service().uploadInternalDocument(
                "2026市场职业报告.txt", CONTENT, dto("MARKET_REPORT", false), 9L);

        assertThat(outcome.reused()).isTrue();
        assertThat(outcome.needsIndexing()).isFalse();
        assertThat(outcome.document().getId()).isEqualTo(700L);
        verify(sourceDocumentMapper, never()).insert(any(KnowledgeSourceDocument.class));
    }

    @Test
    @DisplayName("复用到的材料此前没索引成功：仍要补建一次索引")
    void uploadInternalDocument_reusedMaterialNeedsIndexingWhenUnindexed() {
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(material(701L, 0, "FAILED"));

        SourceUploadOutcome outcome = service().uploadInternalDocument(
                "2026市场职业报告.txt", CONTENT, dto("MARKET_REPORT", false), 9L);

        assertThat(outcome.reused()).isTrue();
        assertThat(outcome.needsIndexing()).isTrue();
        verify(sourceDocumentMapper, never()).insert(any(KnowledgeSourceDocument.class));
    }

    @Test
    @DisplayName("判重键含来源类型 + 资料类别：不同类别必须各算一份")
    void uploadInternalDocument_dedupKeyIncludesCategory() {
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(material(700L, 18, "INDEXED"));

        service().uploadInternalDocument("2026市场职业报告.txt", CONTENT, dto("POLICY_DOCUMENT", false), 9L);

        String where = capturedDedupWhere();
        assertThat(where).contains("dedup_key");
        // 已删除/归档的材料不能复用，否则等于把用户删掉的东西又挂回来
        assertThat(where).contains("status");

        // 类别是嵌在 dedup_key 的**值**里的：同一内容以「政策文件」和「市场报告」上传必须各算一份
        // （类别决定跨来源印证信号，合并掉会让岗位永远无法被判为多来源印证）
        assertThat(capturedDedupWrapper().getParamNameValuePairs().values())
                .map(String::valueOf)
                .contains("CLOUD_KNOWLEDGE_INTERNAL|POLICY_DOCUMENT|hash-abc");
    }

    /* ==================== 仅试算隔离 ==================== */

    @Test
    @DisplayName("正式上传的判重条件限定 ephemeral = 0，不会复用仅试算材料")
    void uploadInternalDocument_persistentUploadExcludesEphemeralMaterials() {
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(material(700L, 18, "INDEXED"));

        service().uploadInternalDocument("2026市场职业报告.txt", CONTENT, dto("MARKET_REPORT", false), 9L);

        assertThat(capturedDedupWhere())
                .as("试算材料会被自动清理，正式材料挂在它上面会凭空消失")
                .contains("ephemeral");
    }

    @Test
    @DisplayName("仅试算上传不复用正式材料")
    void uploadInternalDocument_ephemeralDoesNotReusePersistedMaterial() {
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");

        // 判重条件放宽后不会命中正式材料，于是往下走「新建」分支（这里让它落库失败以便快速结束）
        assertThatThrownBy(() -> service().uploadInternalDocument(
                "2026市场职业报告.txt", CONTENT, dto("MARKET_REPORT", true), 9L))
                .isInstanceOf(RuntimeException.class);

        assertThat(capturedDedupWhere())
                .as("仅试算的判重条件里不该有 ephemeral = 0")
                .doesNotContain("ephemeral");
    }

    @Test
    @DisplayName("新建材料插入失败时清掉已落盘的孤儿文件")
    void uploadInternalDocument_cleansUpOrphanFileWhenInsertFails() throws IOException {
        long before = countFiles(UPLOAD_DIR);
        when(deduplicator.canonicalHash(anyString())).thenReturn("hash-abc");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(null);
        when(sourceDocumentMapper.insert(any(KnowledgeSourceDocument.class)))
                .thenThrow(new IllegalStateException("模拟落库失败"));

        assertThatThrownBy(() -> service().uploadInternalDocument(
                "2026市场职业报告.txt", CONTENT, dto("MARKET_REPORT", false), 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟落库失败");

        // 事务会回滚记录，但文件不会自己消失 —— 必须由代码删掉，否则磁盘里堆孤儿文件
        assertThat(countFiles(UPLOAD_DIR)).isEqualTo(before);
    }

    private long countFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (var stream = Files.list(dir)) {
            return stream.count();
        }
    }

    /* ==================== 单份清理与兜底清理 ==================== */

    @Test
    @DisplayName("删除材料：委托独立事务的清理器并组装面向用户的文案")
    void deleteMaterial_delegatesToCleaner() {
        when(materialCleaner.deleteOne(700L))
                .thenReturn(new KnowledgeMaterialCleaner.CleanupResult("2026市场职业报告", null, 18));

        String message = service().deleteMaterial(700L);

        assertThat(message).contains("2026市场职业报告").contains("18");
        verify(materialCleaner).deleteOne(700L);
    }

    @Test
    @DisplayName("兜底清理：一份材料清理失败不影响其余材料")
    void purgeExpiredEphemeralMaterials_isolatesFailures() {
        KnowledgeSourceDocument broken = new KnowledgeSourceDocument();
        broken.setId(801L);
        KnowledgeSourceDocument healthy = new KnowledgeSourceDocument();
        healthy.setId(802L);
        when(sourceDocumentMapper.selectList(any())).thenReturn(List.of(broken, healthy));
        when(materialCleaner.deleteOne(801L))
                .thenThrow(new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "模拟清理失败"));
        when(materialCleaner.deleteOne(802L))
                .thenReturn(new KnowledgeMaterialCleaner.CleanupResult("可清理材料", null, 3));

        int purged = service().purgeExpiredEphemeralMaterials(24);

        assertThat(purged).as("坏数据只该让它自己失败，不能拖累整批").isEqualTo(1);
        verify(materialCleaner).deleteOne(801L);
        verify(materialCleaner).deleteOne(802L);
    }

    @Test
    @DisplayName("兜底清理只扫仅试算材料，且保留时长非法时按 24 小时处理")
    void purgeExpiredEphemeralMaterials_onlyScansEphemeral() {
        when(sourceDocumentMapper.selectList(any())).thenReturn(List.of());

        assertThat(service().purgeExpiredEphemeralMaterials(0)).isZero();

        ArgumentCaptor<LambdaQueryWrapper<KnowledgeSourceDocument>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sourceDocumentMapper).selectList(captor.capture());
        String where = captor.getValue().getSqlSegment();
        assertThat(where).contains("ephemeral");
        assertThat(where).contains("collected_time");
        assertThat(where).contains("source_type");
    }
}
