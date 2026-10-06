package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.MarketJdDataVersionMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.evolution.RecruitmentDataGovernanceService;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link MarketJdImportServiceImpl} 单元测试。
 *
 * <p>覆盖文本 / Excel / 已确认岗位导入、批次与单条删除、去重与重算、分页查询、
 * 批次分析（准入开启/关闭、清洗阻断、异常降级）与单条解析等分支。
 * 所有 Mapper、AI 能力服务、准入服务与治理服务均 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketJdImportServiceImplTest {

    @Mock private MarketJdDataMapper marketJdDataMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostCapabilityGenerationService postCapabilityGenerationService;
    @Mock private PostDataCleaningService postDataCleaningService;
    @Mock private MarketJdCapabilityAdmissionService admissionService;
    @Mock private CrawlerBatchLogService batchLogService;
    @Mock private MarketJdDataVersionMapper marketJdDataVersionMapper;
    @Mock private RecruitmentDataGovernanceService recruitmentDataGovernanceService;

    private MarketJdCapabilityAdmissionProperties admissionProperties;
    private MarketJdImportServiceImpl service;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，否则抛
     * "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, MarketJdData.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, com.example.matching.entity.evolution.MarketJdDataVersion.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, com.example.matching.entity.post.PostPost.class);
    }

    @BeforeEach
    void setUp() {
        admissionProperties = new MarketJdCapabilityAdmissionProperties();
        service = new MarketJdImportServiceImpl(
                marketJdDataMapper, postPostMapper, postCapabilityGenerationService,
                postDataCleaningService, new ObjectMapper(), admissionService, admissionProperties,
                recruitmentDataGovernanceService, batchLogService, marketJdDataVersionMapper);
        // insert 回填主键
        doAnswer(inv -> {
            MarketJdData d = inv.getArgument(0);
            if (d.getId() == null) d.setId(1L);
            return 1;
        }).when(marketJdDataMapper).insert(any(MarketJdData.class));
        // 默认：治理无噪声
        when(recruitmentDataGovernanceService.governBatch(any()))
                .thenReturn(new RecruitmentDataGovernanceService.GovernanceResult(0, 0, 0, 0, List.of()));
    }

    private PostCleaningResult cleanOk(String cleanedText) {
        PostCleaningResult r = new PostCleaningResult();
        r.setBlocked(false);
        r.setQualityScore(new BigDecimal("0.8"));
        r.setCleanedPostName("Java工程师");
        r.setCleanedText(cleanedText);
        r.setCleaningRecordId(1L);
        return r;
    }

    private MarketJdData jd(Long id, String postName, String desc) {
        MarketJdData d = new MarketJdData();
        d.setId(id);
        d.setBatchNo("B1");
        d.setPostName(postName);
        d.setCompanyName("某公司");
        d.setJobDescription(desc);
        d.setAnalysisStatus(0);
        d.setIsDuplicate(0);
        d.setTextHash("hash-" + id);
        return d;
    }

    // ==================== importFromTextList / WithBatch ====================

    @Test
    @DisplayName("importFromTextListWithBatch：空列表 → 不导入且不登记批次")
    void importFromTextListWithBatch_empty() {
        var result = service.importFromTextListWithBatch(null, "BOSS");
        assertNull(result.batchNo());
        assertEquals(0, result.imported());
        assertTrue(service.importFromTextListWithBatch(List.of(), "BOSS").batchNo() == null);
        verify(batchLogService, never()).record(any());
    }

    @Test
    @DisplayName("importFromTextListWithBatch：有效文本导入成功，空白行被跳过")
    void importFromTextListWithBatch_importsAndSkips() {
        var result = service.importFromTextListWithBatch(
                List.of("Java开发工程师\n负责系统开发", "  ", ""), "BOSS");

        assertNotNull(result.batchNo());
        assertTrue(result.batchNo().startsWith("BATCH_"));
        assertEquals(1, result.imported());
        verify(marketJdDataMapper, times(1)).insert(any(MarketJdData.class));
        verify(batchLogService).record(any());
    }

    @Test
    @DisplayName("importFromTextList：返回导入条数（委托 WithBatch）")
    void importFromTextList_returnsCount() {
        assertEquals(2, service.importFromTextList(
                List.of("岗位一 JD", "岗位二 JD"), "ZHILIN"));
    }

    @Test
    @DisplayName("importFromTextListWithBatch：岗位名命中系统岗位（精确/模糊/null）")
    void importFromTextListWithBatch_matchSystemPost() {
        com.example.matching.entity.post.PostPost exact = new com.example.matching.entity.post.PostPost();
        exact.setId(42L);
        when(postPostMapper.selectOne(any())).thenReturn(exact);

        service.importFromTextListWithBatch(List.of("Java开发工程师\n岗位描述足够长"), "BOSS");

        verify(marketJdDataMapper).insert(any(MarketJdData.class));
    }

    // ==================== importFromExcelData ====================

    @Test
    @DisplayName("importFromExcelData：null / 空列表 → 返回 0")
    void importFromExcelData_empty() {
        assertEquals(0, service.importFromExcelData(null));
        assertEquals(0, service.importFromExcelData(List.of()));
    }

    @Test
    @DisplayName("importFromExcelData：正文为空的行跳过，有效行导入并派生来源平台")
    void importFromExcelData_mixed() {
        MarketJdData valid = jd(null, "Java工程师", "负责核心系统开发与维护");
        valid.setSourcePlatform("BOSS");
        MarketJdData blankDesc = jd(null, "无描述", "   ");

        int imported = service.importFromExcelData(List.of(valid, blankDesc));

        assertEquals(1, imported);
        verify(marketJdDataMapper, times(1)).insert(any(MarketJdData.class));
    }

    @Test
    @DisplayName("importFromExcelData：多个不同来源平台 → 批次平台标记为 EXCEL_MULTI")
    void importFromExcelData_multiPlatform() {
        MarketJdData a = jd(null, "岗位A", "描述A足够长用于导入");
        a.setSourcePlatform("BOSS");
        MarketJdData b = jd(null, "岗位B", "描述B足够长用于导入");
        b.setSourcePlatform("ZHILIN");

        assertEquals(2, service.importFromExcelData(List.of(a, b)));
    }

    // ==================== importVerifiedPostBatch ====================

    @Test
    @DisplayName("importVerifiedPostBatch：参数非法或空列表 → 返回 0")
    void importVerifiedPostBatch_invalidArgs() {
        assertEquals(0, service.importVerifiedPostBatch(null, List.of()));
        assertEquals(0, service.importVerifiedPostBatch(1L, null));
        assertEquals(0, service.importVerifiedPostBatch(1L, List.of()));
    }

    @Test
    @DisplayName("importVerifiedPostBatch：批次已存在 → 直接返回既有条数，不重复导入")
    void importVerifiedPostBatch_alreadyExists() {
        when(marketJdDataMapper.selectCount(any())).thenReturn(5L);

        int result = service.importVerifiedPostBatch(1L,
                List.of(new MarketJdImportService.VerifiedPostImportJd("岗位", "描述", 1L, List.of())));

        assertEquals(5, result);
        verify(marketJdDataMapper, never()).insert(any(MarketJdData.class));
    }

    @Test
    @DisplayName("importVerifiedPostBatch：正常导入含有效与空白 JD，标签去重排序")
    void importVerifiedPostBatch_imports() {
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        int result = service.importVerifiedPostBatch(9L, Arrays.asList(
                new MarketJdImportService.VerifiedPostImportJd("岗位A", "描述A",
                        1L, Arrays.asList(3L, 1L, 1L, null)),
                new MarketJdImportService.VerifiedPostImportJd("岗位B", "  ", 2L, null),
                null));

        assertEquals(1, result);
        verify(batchLogService).record(any());
    }

    // ==================== deleteBatch / deleteJd / deleteJds ====================

    @Test
    @DisplayName("deleteBatch：批次号为空 → 抛参数异常")
    void deleteBatch_blank() {
        assertThrows(BusinessException.class, () -> service.deleteBatch(null));
        assertThrows(BusinessException.class, () -> service.deleteBatch("  "));
    }

    @Test
    @DisplayName("deleteBatch：批次不存在（无数据且无日志）→ 抛未找到")
    void deleteBatch_notFound() {
        when(marketJdDataMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(batchLogService.countByBatchNo(any())).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.deleteBatch("B1"));
    }

    @Test
    @DisplayName("deleteBatch：存在数据 → 级联清理版本快照并删除行与日志")
    void deleteBatch_deletesCascade() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jd(1L, "岗位", "描述")));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(2);
        when(marketJdDataMapper.delete(any())).thenReturn(1);
        when(batchLogService.deleteByBatchNo(any())).thenReturn(1);

        var result = service.deleteBatch("B1");

        assertEquals("B1", result.batchNo());
        assertEquals(1, result.marketJdRows());
        assertEquals(2, result.versionSnapshots());
        assertEquals(1, result.batchLogs());
    }

    @Test
    @DisplayName("deleteJd：id 为 null → 参数异常；记录不存在 → 未找到")
    void deleteJd_notFound() {
        assertThrows(BusinessException.class, () -> service.deleteJd(null));
        when(marketJdDataMapper.selectById(7L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteJd(7L));
    }

    @Test
    @DisplayName("deleteJd：正常删除 → 清理版本快照并返回行数")
    void deleteJd_success() {
        when(marketJdDataMapper.selectById(7L)).thenReturn(jd(7L, "岗位", "描述"));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(3);
        when(marketJdDataMapper.deleteById(7L)).thenReturn(1);

        var result = service.deleteJd(7L);

        assertEquals(7L, result.id());
        assertEquals(1, result.marketJdRows());
        assertEquals(3, result.versionSnapshots());
        verify(marketJdDataMapper).update(any(), any());
    }

    @Test
    @DisplayName("deleteJds：空列表 / 全 null → 参数异常")
    void deleteJds_invalid() {
        assertThrows(BusinessException.class, () -> service.deleteJds(null));
        assertThrows(BusinessException.class, () -> service.deleteJds(List.of()));
        assertThrows(BusinessException.class,
                () -> service.deleteJds(new ArrayList<>(Arrays.asList((Long) null))));
    }

    @Test
    @DisplayName("deleteJds：部分 id 不存在 → 返回 missingIds，仅删存在项")
    void deleteJds_partialMissing() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jd(1L, "岗位", "描述")));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(1);
        when(marketJdDataMapper.delete(any())).thenReturn(1);

        var result = service.deleteJds(List.of(1L, 1L, 2L));

        assertEquals(2, result.requested());
        assertEquals(List.of(2L), result.missingIds());
    }

    @Test
    @DisplayName("deleteJds：所有 id 均不存在 → 直接返回 0 删除")
    void deleteJds_noneExist() {
        when(marketJdDataMapper.selectList(any())).thenReturn(new ArrayList<>());

        var result = service.deleteJds(List.of(5L, 6L));

        assertEquals(2, result.requested());
        assertEquals(0, result.marketJdRows());
        assertEquals(List.of(5L, 6L), result.missingIds());
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("pageMarketJds：带岗位名与批次过滤 → 委托 selectPage")
    void pageMarketJds_withFilters() {
        Page<MarketJdData> page = new Page<>(1, 10);
        Page<MarketJdData> result = new Page<>(1, 10);
        result.setRecords(List.of(jd(1L, "岗位", "描述")));
        when(marketJdDataMapper.selectPage(any(), any())).thenReturn(result);

        IPage<MarketJdData> out = service.pageMarketJds(page, "Java", "B1");

        assertEquals(1, out.getRecords().size());
    }

    @Test
    @DisplayName("pageMarketJds：无过滤条件（null）→ 正常返回")
    void pageMarketJds_noFilters() {
        Page<MarketJdData> page = new Page<>(1, 10);
        when(marketJdDataMapper.selectPage(any(), any())).thenReturn(page);

        assertNotNull(service.pageMarketJds(page, null, null));
    }

    @Test
    @DisplayName("getMarketJdsByPostId：返回匹配列表")
    void getMarketJdsByPostId_returns() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jd(1L, "岗位", "描述")));

        assertEquals(1, service.getMarketJdsByPostId(100L, 5).size());
    }

    // ==================== deduplicateByBatch / resetDedupeByBatch ====================

    @Test
    @DisplayName("deduplicateByBatch：本批内同哈希 → 标记重复并回填规范文档")
    void deduplicateByBatch_exactDuplicateInBatch() {
        MarketJdData first = jd(1L, "岗位", "描述");
        first.setTextHash("H1");
        MarketJdData second = jd(2L, "岗位", "描述");
        second.setTextHash("H1");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(first, second));

        int dup = service.deduplicateByBatch("B1");

        assertEquals(1, dup);
        assertEquals(1, second.getIsDuplicate());
        verify(marketJdDataMapper).updateById(second);
    }

    @Test
    @DisplayName("deduplicateByBatch：命中其他批次同哈希 → 标记重复")
    void deduplicateByBatch_crossBatchDuplicate() {
        MarketJdData row = jd(1L, "岗位", "描述");
        row.setTextHash("H2");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);

        assertEquals(1, service.deduplicateByBatch("B1"));
    }

    @Test
    @DisplayName("deduplicateByBatch：空批次 → 返回 0")
    void deduplicateByBatch_empty() {
        when(marketJdDataMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertEquals(0, service.deduplicateByBatch("B1"));
    }

    @Test
    @DisplayName("resetDedupeByBatch：批次号为空 → 参数异常；批次无数据 → 未找到")
    void resetDedupeByBatch_invalid() {
        assertThrows(BusinessException.class, () -> service.resetDedupeByBatch(null));
        when(marketJdDataMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.resetDedupeByBatch("B1"));
    }

    @Test
    @DisplayName("resetDedupeByBatch：爬虫行重算哈希，人工行仅清判定")
    void resetDedupeByBatch_recomputesCrawlerHash() {
        MarketJdData crawler = jd(1L, "岗位A", "描述A");
        crawler.setIngestChannel(MarketJdData.CHANNEL_CRAWLER);
        MarketJdData manual = jd(2L, "岗位B", "描述B");
        manual.setIngestChannel(MarketJdData.CHANNEL_MANUAL_UPLOAD);
        manual.setIsDuplicate(1);

        // 第一次 selectList 取批次行；deduplicateByBatch 内部再取一次（返回空）
        when(marketJdDataMapper.selectList(any()))
                .thenReturn(List.of(crawler, manual), new ArrayList<>());
        when(marketJdDataMapper.update(any(), any())).thenReturn(1);

        var result = service.resetDedupeByBatch("B1");

        assertEquals("B1", result.batchNo());
        assertEquals(2, result.scannedRows());
        assertEquals(1, result.hashRecomputed());
        assertEquals(0, result.duplicateHits());
    }

    // ==================== getBatchStatistics ====================

    @Test
    @DisplayName("getBatchStatistics：统计总数/重复/已分析/已匹配")
    void getBatchStatistics_aggregates() {
        MarketJdData a = jd(1L, "岗位A", "描述");
        a.setIsDuplicate(1);
        a.setAnalysisStatus(1);
        a.setMatchedPostId(10L);
        MarketJdData b = jd(2L, "岗位B", "描述");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(a, b));

        var stats = service.getBatchStatistics("B1");

        assertEquals(2, stats.getTotalCount());
        assertEquals(1, stats.getDuplicateCount());
        assertEquals(1, stats.getAnalyzedCount());
        assertEquals(1, stats.getMatchedCount());
    }

    // ==================== analyzeBatch ====================

    @Test
    @DisplayName("analyzeBatch：特性开关关闭 → 走传统路径并逐条写标签")
    void analyzeBatch_legacyPath() {
        admissionProperties.setEnabled(false);
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));

        JdAbilityItemDTO matched = new JdAbilityItemDTO();
        matched.setMatchStatus("MATCHED");
        matched.setMatchedTagId(5L);
        when(postCapabilityGenerationService.analyzePostText(any(), any(), any(), any(), any()))
                .thenReturn(List.of(matched));

        var result = service.analyzeBatch("B1");

        assertEquals("B1", result.getBatchNo());
        assertEquals(1, result.getExtractedSuccess());
        verify(marketJdDataMapper, atLeastOnce()).updateById(row);
    }

    @Test
    @DisplayName("analyzeBatch：准入开启，清洗阻断 → 计入噪声并置状态2")
    void analyzeBatch_blockedByCleaning() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);
        PostCleaningResult blocked = new PostCleaningResult();
        blocked.setBlocked(true);
        blocked.setQualityScore(new BigDecimal("0.1"));
        blocked.setBlockReason("低质量");
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(blocked);

        var result = service.analyzeBatch("B1");

        assertEquals(1, result.getSkippedNoise());
        assertEquals(2, row.getAnalysisStatus());
    }

    @Test
    @DisplayName("analyzeBatch：准入开启，正常提取并准入 → 合并直通标签")
    void analyzeBatch_admissionPath() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(new JdAbilityItemDTO()));

        MarketJdCapabilityAdmissionService.AdmissionPlan plan =
                new MarketJdCapabilityAdmissionService.AdmissionPlan(
                        Map.of(1L, new LinkedHashSet<>(List.of(5L, 6L))),
                        Map.of(1L, new LinkedHashSet<>(List.of(7L))),
                        List.of(), 2, 1, 0, 0, 0, 0, 0, Set.of());
        when(admissionService.admitBatch(any())).thenReturn(plan);

        var result = service.analyzeBatch("B1");

        assertEquals(1, result.getExtractedSuccess());
        assertEquals(2, result.getAutoAdmittedCount());
        assertEquals(1, result.getHarnessPassCount());
        assertEquals(1, row.getAnalysisStatus());
    }

    @Test
    @DisplayName("analyzeBatch：准入基础设施异常 → 计入失败且 JD 状态保持 0")
    void analyzeBatch_admissionInfraFailure() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(new JdAbilityItemDTO()));
        when(admissionService.admitBatch(any())).thenThrow(new RuntimeException("harness down"));

        var result = service.analyzeBatch("B1");

        assertEquals(1, result.getExtractedFailed());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("analyzeBatch：无待分析 JD → 提前返回，不调提取")
    void analyzeBatch_noCandidates() {
        when(marketJdDataMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        var result = service.analyzeBatch("B1");

        assertEquals(0, result.getExtractedSuccess());
        verify(postCapabilityGenerationService, never()).analyzeMarketJdText(any(), any(), any(), any());
    }

    @Test
    @DisplayName("analyzeBatch：单条提取抛业务异常 → 计入阻断")
    void analyzeBatch_businessExceptionBlocked() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenThrow(new com.example.matching.common.exception.BusinessException(
                        com.example.matching.common.exception.ErrorCodeEnum.PARAM_ERROR, "不适用"));

        var result = service.analyzeBatch("B1");

        assertEquals(1, result.getSkippedNoise());
        assertEquals(2, row.getAnalysisStatus());
    }

    // ==================== analyzeOne ====================

    @Test
    @DisplayName("analyzeOne：id 为 null → 参数异常；不存在 → 未找到；重复项 → 状态冲突")
    void analyzeOne_guardBranches() {
        assertThrows(BusinessException.class, () -> service.analyzeOne(null));

        when(marketJdDataMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.analyzeOne(1L));

        MarketJdData dup = jd(2L, "岗位", "描述");
        dup.setIsDuplicate(1);
        when(marketJdDataMapper.selectById(2L)).thenReturn(dup);
        assertThrows(BusinessException.class, () -> service.analyzeOne(2L));
    }

    @Test
    @DisplayName("analyzeOne：清洗阻断 → 返回状态2并写质量分")
    void analyzeOne_blockedByCleaning() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        PostCleaningResult blocked = new PostCleaningResult();
        blocked.setBlocked(true);
        blocked.setQualityScore(new BigDecimal("0.2"));
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(blocked);

        var result = service.analyzeOne(1L);

        assertEquals(2, result.analysisStatus());
        assertEquals(2, row.getAnalysisStatus());
    }

    @Test
    @DisplayName("analyzeOne：准入开启且成功 → 状态1，返回准入/推荐计数")
    void analyzeOne_admissionSuccess() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(new JdAbilityItemDTO()));

        MarketJdCapabilityAdmissionService.AdmissionPlan plan =
                new MarketJdCapabilityAdmissionService.AdmissionPlan(
                        Map.of(1L, new LinkedHashSet<>(List.of(5L))),
                        Map.of(1L, new LinkedHashSet<>(List.of(9L))),
                        List.of(), 1, 1, 0, 0, 0, 0, 2, Set.of());
        when(admissionService.admitBatch(any())).thenReturn(plan);

        var result = service.analyzeOne(1L);

        assertEquals(1, result.analysisStatus());
        assertEquals(1, result.acceptedTagCount());
        assertEquals(1, result.recommendedTagCount());
        assertFalse(result.infraFailed());
        assertTrue(result.message().contains("新能力需不少于"));
    }

    @Test
    @DisplayName("analyzeOne：能力提取抛业务异常 → 状态2可跳过，不可重试")
    void analyzeOne_extractionBusinessException() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenThrow(new com.example.matching.common.exception.BusinessException(
                        com.example.matching.common.exception.ErrorCodeEnum.PARAM_ERROR, "不适用"));

        var result = service.analyzeOne(1L);

        assertEquals(2, result.analysisStatus());
        assertFalse(result.infraFailed());
    }

    @Test
    @DisplayName("analyzeOne：能力提取抛基础设施异常 → 状态保持0可重试")
    void analyzeOne_extractionInfraException() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenThrow(new RuntimeException("llm down"));

        var result = service.analyzeOne(1L);

        assertEquals(0, result.analysisStatus());
        assertTrue(result.infraFailed());
    }

    @Test
    @DisplayName("analyzeOne：特性开关关闭 → 走传统路径，只写 MATCHED 标签")
    void analyzeOne_legacyPath() {
        admissionProperties.setEnabled(false);
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        JdAbilityItemDTO matched = new JdAbilityItemDTO();
        matched.setMatchStatus("MATCHED");
        matched.setMatchedTagId(5L);
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(matched));

        var result = service.analyzeOne(1L);

        assertEquals(1, result.analysisStatus());
        assertEquals(1, result.acceptedTagCount());
        assertTrue(result.message().contains("传统路径"));
    }

    @Test
    @DisplayName("analyzeOne：准入抛基础设施异常 → 状态0可重试")
    void analyzeOne_admissionInfraException() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(new JdAbilityItemDTO()));
        when(admissionService.admitBatch(any())).thenThrow(new RuntimeException("harness down"));

        var result = service.analyzeOne(1L);

        assertEquals(0, result.analysisStatus());
        assertTrue(result.infraFailed());
    }

    @Test
    @DisplayName("analyzeOne：准入计划标记该 JD 基础设施失败 → 状态0可重试")
    void analyzeOne_infraFailedJd() {
        MarketJdData row = jd(1L, "岗位A", "描述A");
        when(marketJdDataMapper.selectById(1L)).thenReturn(row);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleanOk("清洗后文本"));
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), anyLong(), any()))
                .thenReturn(List.of(new JdAbilityItemDTO()));

        MarketJdCapabilityAdmissionService.AdmissionPlan plan =
                new MarketJdCapabilityAdmissionService.AdmissionPlan(
                        Map.of(), Map.of(), List.of(), 0, 0, 1, 0, 0, 0, 3, Set.of(1L));
        when(admissionService.admitBatch(any())).thenReturn(plan);

        var result = service.analyzeOne(1L);

        assertEquals(0, result.analysisStatus());
        assertTrue(result.infraFailed());
    }
}
