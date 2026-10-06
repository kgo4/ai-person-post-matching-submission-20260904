package com.example.matching.service.learning.impl;

import com.example.matching.entity.learning.LearningMasteryLog;
import com.example.matching.entity.learning.LearningQuiz;
import com.example.matching.entity.learning.LearningQuizRecord;
import com.example.matching.mapper.learning.LearningMasteryLogMapper;
import com.example.matching.mapper.learning.LearningQuizMapper;
import com.example.matching.mapper.learning.LearningQuizRecordMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link LearningQuizServiceImpl} 单元测试。
 *
 * <p>覆盖题目 CRUD、按各维度查询、答题记录提交（首次/重复/掌握达标）、
 * 三种掌握度计算口径、掌握度总览与薄弱环节等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningQuizServiceImplTest {

    @Mock private LearningQuizMapper quizMapper;
    @Mock private LearningQuizRecordMapper recordMapper;
    @Mock private LearningMasteryLogMapper masteryLogMapper;

    @InjectMocks
    private LearningQuizServiceImpl service;

    /**
     * MyBatis-Plus LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningQuiz.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), LearningQuizRecord.class);
    }

    private LearningQuiz quiz(Long id, Long domainId, Long nodeId, Long tagId) {
        LearningQuiz q = new LearningQuiz();
        q.setId(id);
        q.setQuizCode("QUIZ" + id);
        q.setDomainId(domainId);
        q.setNodeId(nodeId);
        q.setTagId(tagId);
        q.setIsDeleted(0);
        q.setStatus("ACTIVE");
        return q;
    }

    private LearningQuizRecord record(Long quizId, Integer mastered) {
        LearningQuizRecord r = new LearningQuizRecord();
        r.setQuizId(quizId);
        r.setIsMastered(mastered);
        r.setIsDeleted(0);
        return r;
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("getAllQuizzes：返回启用且未删除的全部题目")
    void getAllQuizzes_returnsList() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, 1L, 1L, 1L), quiz(2L, 1L, 1L, 1L)));

        List<LearningQuiz> result = service.getAllQuizzes();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getQuizById：透传 mapper 结果，不存在返回 null")
    void getQuizById_bothPaths() {
        when(quizMapper.selectById(1L)).thenReturn(quiz(1L, 1L, 1L, 1L));
        assertNotNull(service.getQuizById(1L));

        when(quizMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getQuizById(99L));
    }

    @Test
    @DisplayName("getQuizByCode：按编码查询未删除题目")
    void getQuizByCode_ok() {
        when(quizMapper.selectOne(any())).thenReturn(quiz(1L, 1L, 1L, 1L));

        assertNotNull(service.getQuizByCode("QUIZ1"));
    }

    @Test
    @DisplayName("按领域/知识点/标签/难度查询：均透传 selectList 结果")
    void queriesByDimensions_allReturn() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, 1L, 1L, 1L)));

        assertEquals(1, service.getQuizzesByDomainId(1L).size());
        assertEquals(1, service.getQuizzesByNodeId(1L).size());
        assertEquals(1, service.getQuizzesByTagId(1L).size());
        assertEquals(1, service.getQuizzesByDifficultyLevel("EASY").size());
    }

    // ==================== 创建 / 更新 / 删除 ====================

    @Test
    @DisplayName("createQuiz：初始化版本/使用次数/删除标记并回填主键")
    void createQuiz_initializesFields() {
        doAnswer(inv -> {
            LearningQuiz q = inv.getArgument(0);
            q.setId(100L);
            return 1;
        }).when(quizMapper).insert(any(LearningQuiz.class));

        LearningQuiz input = new LearningQuiz();
        input.setQuizCode("QUIZ_NEW");
        input.setQuestionText("问题");

        LearningQuiz saved = service.createQuiz(input);

        assertEquals(100L, saved.getId());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(1, saved.getVersion());
        assertEquals(0, saved.getUsageCount());
        verify(quizMapper).insert(any(LearningQuiz.class));
    }

    @Test
    @DisplayName("updateQuiz：调用 updateById 并原样返回入参")
    void updateQuiz_callsUpdate() {
        LearningQuiz input = quiz(1L, 1L, 1L, 1L);

        LearningQuiz result = service.updateQuiz(input);

        assertSame(input, result);
        verify(quizMapper).updateById(input);
    }

    @Test
    @DisplayName("deleteQuiz：只提交 id + 逻辑删除标记，不做物理删除")
    void deleteQuiz_logicalDelete() {
        service.deleteQuiz(7L);

        ArgumentCaptor<LearningQuiz> cap = ArgumentCaptor.forClass(LearningQuiz.class);
        verify(quizMapper).updateById(cap.capture());
        assertEquals(7L, cap.getValue().getId());
        assertEquals(1, cap.getValue().getIsDeleted());
        verify(quizMapper, never()).deleteById(anyLong());
    }

    // ==================== 答题记录提交 ====================

    @Test
    @DisplayName("submitQuizRecord：首次提交 → 新建记录，答对时 correctCount=1")
    void submitQuizRecord_firstAttempt_correct() {
        when(recordMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            LearningQuizRecord r = inv.getArgument(0);
            r.setId(500L);
            return 1;
        }).when(recordMapper).insert(any(LearningQuizRecord.class));

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);
        input.setIsCorrect(1);
        input.setUserAnswer("A");
        input.setAnswerTime(30);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(500L, saved.getId());
        assertEquals(1, saved.getAttemptCount());
        assertEquals(1, saved.getCorrectCount());
        assertEquals(0, saved.getIsMastered());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(1, saved.getVersion());
        assertNotNull(saved.getFirstAttemptTime());
        assertNotNull(saved.getLastAttemptTime());
        verify(recordMapper).insert(any(LearningQuizRecord.class));
    }

    @Test
    @DisplayName("submitQuizRecord：首次提交答错 → correctCount=0")
    void submitQuizRecord_firstAttempt_wrong() {
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.insert(any(LearningQuizRecord.class))).thenReturn(1);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);
        input.setIsCorrect(0);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(0, saved.getCorrectCount());
        assertNotNull(saved.getFirstAttemptTime());
    }

    @Test
    @DisplayName("submitQuizRecord：首次提交 isCorrect=null → 记为未答对")
    void submitQuizRecord_firstAttempt_nullCorrect() {
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.insert(any(LearningQuizRecord.class))).thenReturn(1);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(0, saved.getCorrectCount());
    }

    @Test
    @DisplayName("submitQuizRecord：已有记录再次答对 → 尝试次数+1、正确次数+1，未达 5 次不置掌握")
    void submitQuizRecord_update_notMasteredYet() {
        LearningQuizRecord existing = new LearningQuizRecord();
        existing.setId(9L);
        existing.setEmpId(100L);
        existing.setQuizId(1L);
        existing.setAttemptCount(2);
        existing.setCorrectCount(1);
        existing.setIsMastered(0);
        when(recordMapper.selectOne(any())).thenReturn(existing);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);
        input.setIsCorrect(1);
        input.setUserAnswer("B");
        input.setAnswerTime(45);
        input.setAnswerScore(new BigDecimal("1.00"));

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(3, saved.getAttemptCount());
        assertEquals(2, saved.getCorrectCount());
        assertEquals(0, saved.getIsMastered());
        assertNull(saved.getMasteredTime());
        assertEquals("B", saved.getUserAnswer());
        assertNotNull(saved.getLastAttemptTime());
        verify(recordMapper).updateById(existing);
        verify(recordMapper, never()).insert(any(LearningQuizRecord.class));
    }

    @Test
    @DisplayName("submitQuizRecord：累计答对达到 5 次 → 置为已掌握并记录掌握时间")
    void submitQuizRecord_update_reachesMastery() {
        LearningQuizRecord existing = new LearningQuizRecord();
        existing.setId(9L);
        existing.setEmpId(100L);
        existing.setQuizId(1L);
        existing.setAttemptCount(4);
        existing.setCorrectCount(4);
        existing.setIsMastered(0);
        when(recordMapper.selectOne(any())).thenReturn(existing);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);
        input.setIsCorrect(1);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(5, saved.getAttemptCount());
        assertEquals(5, saved.getCorrectCount());
        assertEquals(1, saved.getIsMastered());
        assertNotNull(saved.getMasteredTime());
    }

    @Test
    @DisplayName("submitQuizRecord：已有记录答错 → 只累加尝试次数，不动正确次数")
    void submitQuizRecord_update_wrong() {
        LearningQuizRecord existing = new LearningQuizRecord();
        existing.setId(9L);
        existing.setAttemptCount(1);
        existing.setCorrectCount(3);
        existing.setIsMastered(0);
        when(recordMapper.selectOne(any())).thenReturn(existing);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(100L);
        input.setQuizId(1L);
        input.setIsCorrect(0);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        assertEquals(2, saved.getAttemptCount());
        assertEquals(3, saved.getCorrectCount());
        assertEquals(0, saved.getIsMastered());
    }

    // ==================== 答题记录查询 ====================

    @Test
    @DisplayName("getQuizRecordsByEmpId / ByPlanId：透传 selectList 结果")
    void getQuizRecords_queries() {
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 1)));

        assertEquals(1, service.getQuizRecordsByEmpId(100L).size());
        assertEquals(1, service.getQuizRecordsByEmpIdAndPlanId(100L, 10L).size());
    }

    @Test
    @DisplayName("getQuizRecordByEmpIdAndQuizId：无记录返回 null")
    void getQuizRecordByEmpIdAndQuizId_null() {
        when(recordMapper.selectOne(any())).thenReturn(null);
        assertNull(service.getQuizRecordByEmpIdAndQuizId(100L, 1L));
    }

    // ==================== 掌握度计算 ====================

    @Test
    @DisplayName("calculateMasteryScore：领域下无题目 → 返回 0 且不写日志")
    void calculateMasteryScore_noQuizzes() {
        when(quizMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(0.0, service.calculateMasteryScore(100L, 1L));

        verify(masteryLogMapper, never()).insert(any(LearningMasteryLog.class));
    }

    @Test
    @DisplayName("calculateMasteryScore：有题目但无答题记录 → 返回 0 且不写日志")
    void calculateMasteryScore_noRecords() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(0.0, service.calculateMasteryScore(100L, 1L));

        verify(masteryLogMapper, never()).insert(any(LearningMasteryLog.class));
    }

    @Test
    @DisplayName("calculateMasteryScore：4 题中 2 题已掌握 → 50 分并落地掌握度日志")
    void calculateMasteryScore_computesAndLogs() {
        when(quizMapper.selectList(any())).thenReturn(List.of(
                quiz(1L, 1L, null, null), quiz(2L, 1L, null, null),
                quiz(3L, 1L, null, null), quiz(4L, 1L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(1L, 1), record(2L, 1), record(3L, 0)));

        double score = service.calculateMasteryScore(100L, 1L);

        assertEquals(50.0, score);
        ArgumentCaptor<LearningMasteryLog> cap = ArgumentCaptor.forClass(LearningMasteryLog.class);
        verify(masteryLogMapper).insert(cap.capture());
        LearningMasteryLog log = cap.getValue();
        assertEquals(100L, log.getEmpId());
        assertEquals(1L, log.getDomainId());
        assertNull(log.getNodeId());
        assertNull(log.getTagId());
        assertEquals(4, log.getQuizCount());
        assertEquals(2, log.getMasteredCount());
        assertEquals(3, log.getCorrectCount());
        assertEquals("AUTO", log.getCalculationSource());
        assertEquals(0, log.getIsDeleted());
        assertEquals(1, log.getVersion());
        assertNotNull(log.getCalculationTime());
    }

    @Test
    @DisplayName("calculateMasteryScore：isMastered 为 null 的记录不计入已掌握")
    void calculateMasteryScore_nullMasteredIgnored() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, null), record(2L, 1)));

        assertEquals(50.0, service.calculateMasteryScore(100L, 1L));
    }

    @Test
    @DisplayName("calculateMasteryScoreByNodeId：2 题中 1 题掌握 → 50 分，日志写 nodeId")
    void calculateMasteryScoreByNodeId_ok() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, null, 7L, null), quiz(2L, null, 7L, null)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 1)));

        assertEquals(50.0, service.calculateMasteryScoreByNodeId(100L, 7L));

        ArgumentCaptor<LearningMasteryLog> cap = ArgumentCaptor.forClass(LearningMasteryLog.class);
        verify(masteryLogMapper).insert(cap.capture());
        assertEquals(7L, cap.getValue().getNodeId());
        assertNull(cap.getValue().getDomainId());
        assertNull(cap.getValue().getTagId());
    }

    @Test
    @DisplayName("calculateMasteryScoreByNodeId：无题目 → 返回 0")
    void calculateMasteryScoreByNodeId_empty() {
        when(quizMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(0.0, service.calculateMasteryScoreByNodeId(100L, 7L));
        verify(masteryLogMapper, never()).insert(any(LearningMasteryLog.class));
    }

    @Test
    @DisplayName("calculateMasteryScoreByTagId：2 题全掌握 → 100 分，日志写 tagId")
    void calculateMasteryScoreByTagId_ok() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, null, null, 5L), quiz(2L, null, null, 5L)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 1), record(2L, 1)));

        assertEquals(100.0, service.calculateMasteryScoreByTagId(100L, 5L));

        ArgumentCaptor<LearningMasteryLog> cap = ArgumentCaptor.forClass(LearningMasteryLog.class);
        verify(masteryLogMapper).insert(cap.capture());
        assertEquals(5L, cap.getValue().getTagId());
    }

    @Test
    @DisplayName("calculateMasteryScoreByTagId：有题目无记录 → 返回 0 且不写日志")
    void calculateMasteryScoreByTagId_noRecords() {
        when(quizMapper.selectList(any())).thenReturn(List.of(quiz(1L, null, null, 5L)));
        when(recordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(0.0, service.calculateMasteryScoreByTagId(100L, 5L));
        verify(masteryLogMapper, never()).insert(any(LearningMasteryLog.class));
    }

    // ==================== 掌握度总览 / 薄弱环节 ====================

    @Test
    @DisplayName("getMasteryOverview：无任何题目 → 返回空 Map")
    void getMasteryOverview_empty() {
        when(quizMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<Long, Double> overview = service.getMasteryOverview(100L);

        assertTrue(overview.isEmpty());
    }

    @Test
    @DisplayName("getMasteryOverview：按领域聚合，domainId 为空的题目被过滤")
    void getMasteryOverview_grouped() {
        // 第一次 selectList 取全部题目（含 domainId=null 的脏数据）；后续每次按领域查询
        when(quizMapper.selectList(any())).thenReturn(
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null), quiz(3L, null, null, null)),
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 1)));

        Map<Long, Double> overview = service.getMasteryOverview(100L);

        assertEquals(1, overview.size());
        assertEquals(50.0, overview.get(1L));
    }

    @Test
    @DisplayName("getWeakPoints：掌握度低于 60 的领域被列出，达标领域被过滤")
    void getWeakPoints_belowThreshold() {
        when(quizMapper.selectList(any())).thenReturn(
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null), quiz(3L, 2L, null, null)),
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null)),
                List.of(quiz(3L, 2L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(
                List.of(record(1L, 1)),      // 领域1：1/2 = 50% → 薄弱
                List.of(record(3L, 1)));     // 领域2：1/1 = 100% → 达标

        List<Map<String, Object>> weak = service.getWeakPoints(100L, 10);

        assertEquals(1, weak.size());
        assertEquals(1L, weak.get(0).get("domainId"));
        assertEquals(50.0, weak.get(0).get("masteryScore"));
        assertEquals(2, weak.get(0).get("quizCount"));
    }

    @Test
    @DisplayName("getWeakPoints：limit 生效，只返回掌握度最低的前 N 个")
    void getWeakPoints_respectsLimit() {
        when(quizMapper.selectList(any())).thenReturn(
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null),
                        quiz(3L, 2L, null, null), quiz(4L, 2L, null, null),
                        quiz(5L, 3L, null, null), quiz(6L, 3L, null, null)),
                List.of(quiz(1L, 1L, null, null), quiz(2L, 1L, null, null)),
                List.of(quiz(3L, 2L, null, null), quiz(4L, 2L, null, null)),
                List.of(quiz(5L, 3L, null, null), quiz(6L, 3L, null, null)));
        when(recordMapper.selectList(any())).thenReturn(
                List.of(record(1L, 1)),                 // 领域1：50%
                List.of(record(3L, 1), record(4L, 1)),  // 领域2：100%
                List.of(record(5L, 1)));                // 领域3：50%

        List<Map<String, Object>> weak = service.getWeakPoints(100L, 1);

        assertEquals(1, weak.size());
        assertEquals(50.0, weak.get(0).get("masteryScore"));
    }

    // ==================== 默认题库初始化 ====================

    @Test
    @DisplayName("initDefaultQuizzes：已有数据 → 跳过初始化，不插入任何题目")
    void initDefaultQuizzes_skipsWhenExists() {
        when(quizMapper.selectCount(any())).thenReturn(5L);

        service.initDefaultQuizzes();

        verify(quizMapper, never()).insert(any(LearningQuiz.class));
    }

    @Test
    @DisplayName("initDefaultQuizzes：无数据 → 插入 5 道默认题目，字段被正确初始化")
    void initDefaultQuizzes_createsDefaults() {
        when(quizMapper.selectCount(any())).thenReturn(0L);
        when(quizMapper.insert(any(LearningQuiz.class))).thenReturn(1);

        service.initDefaultQuizzes();

        ArgumentCaptor<LearningQuiz> cap = ArgumentCaptor.forClass(LearningQuiz.class);
        verify(quizMapper, times(5)).insert(cap.capture());
        LearningQuiz first = cap.getAllValues().get(0);
        assertEquals("QUIZ001", first.getQuizCode());
        assertEquals("MULTI_CHOICE", first.getQuestionType());
        assertEquals("EASY", first.getDifficultyLevel());
        assertEquals("ACTIVE", first.getStatus());
        assertEquals(60, first.getEstimatedTime());
        assertEquals(BigDecimal.ONE, first.getScore());
        assertEquals(0, first.getUsageCount());
        assertEquals(0, first.getIsDeleted());
        assertEquals(1, first.getVersion());
    }

    @Test
    @DisplayName("initDefaultQuizzes：库存为空 → count 返回 0，仍然插入全部默认题目")
    void initDefaultQuizzes_zeroCountStillInitializes() {
        when(quizMapper.selectCount(any())).thenReturn(0L);
        when(quizMapper.insert(any(LearningQuiz.class))).thenReturn(1);

        service.initDefaultQuizzes();

        verify(quizMapper, times(5)).insert(any(LearningQuiz.class));
    }

    @Test
    @DisplayName("initDefaultQuizzes：count 为 1 视为已有数据 → 跳过初始化")
    void initDefaultQuizzes_oneCountSkips() {
        when(quizMapper.selectCount(any())).thenReturn(1L);

        service.initDefaultQuizzes();

        verify(quizMapper, never()).insert(any(LearningQuiz.class));
    }

    @Test
    @DisplayName("submitQuizRecord：首次提交时 lastAttemptTime 不早于 firstAttemptTime")
    void submitQuizRecord_timestampsConsistent() {
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.insert(any(LearningQuizRecord.class))).thenReturn(1);

        LearningQuizRecord input = new LearningQuizRecord();
        input.setEmpId(1L);
        input.setQuizId(1L);
        input.setIsCorrect(1);

        LearningQuizRecord saved = service.submitQuizRecord(input);

        LocalDateTime first = saved.getFirstAttemptTime();
        LocalDateTime last = saved.getLastAttemptTime();
        assertNotNull(first);
        assertNotNull(last);
        assertFalse(last.isBefore(first));
    }
}
