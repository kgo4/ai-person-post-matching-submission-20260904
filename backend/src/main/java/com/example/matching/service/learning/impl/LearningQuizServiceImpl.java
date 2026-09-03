package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.learning.LearningMasteryLog;
import com.example.matching.entity.learning.LearningQuiz;
import com.example.matching.entity.learning.LearningQuizRecord;
import com.example.matching.mapper.learning.LearningMasteryLogMapper;
import com.example.matching.mapper.learning.LearningQuizMapper;
import com.example.matching.mapper.learning.LearningQuizRecordMapper;
import com.example.matching.service.learning.LearningQuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测验题目服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningQuizServiceImpl implements LearningQuizService {

    private final LearningQuizMapper quizMapper;
    private final LearningQuizRecordMapper recordMapper;
    private final LearningMasteryLogMapper masteryLogMapper;

    @Override
    public List<LearningQuiz> getAllQuizzes() {
        return quizMapper.selectList(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getIsDeleted, 0)
                        .eq(LearningQuiz::getStatus, "ACTIVE"));
    }

    @Override
    public LearningQuiz getQuizById(Long quizId) {
        return quizMapper.selectById(quizId);
    }

    @Override
    public LearningQuiz getQuizByCode(String quizCode) {
        return quizMapper.selectOne(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getQuizCode, quizCode)
                        .eq(LearningQuiz::getIsDeleted, 0));
    }

    @Override
    public List<LearningQuiz> getQuizzesByDomainId(Long domainId) {
        return quizMapper.selectList(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getDomainId, domainId)
                        .eq(LearningQuiz::getIsDeleted, 0)
                        .eq(LearningQuiz::getStatus, "ACTIVE"));
    }

    @Override
    public List<LearningQuiz> getQuizzesByNodeId(Long nodeId) {
        return quizMapper.selectList(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getNodeId, nodeId)
                        .eq(LearningQuiz::getIsDeleted, 0)
                        .eq(LearningQuiz::getStatus, "ACTIVE"));
    }

    @Override
    public List<LearningQuiz> getQuizzesByTagId(Long tagId) {
        return quizMapper.selectList(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getTagId, tagId)
                        .eq(LearningQuiz::getIsDeleted, 0)
                        .eq(LearningQuiz::getStatus, "ACTIVE"));
    }

    @Override
    public List<LearningQuiz> getQuizzesByDifficultyLevel(String difficultyLevel) {
        return quizMapper.selectList(
                Wrappers.<LearningQuiz>lambdaQuery()
                        .eq(LearningQuiz::getDifficultyLevel, difficultyLevel)
                        .eq(LearningQuiz::getIsDeleted, 0)
                        .eq(LearningQuiz::getStatus, "ACTIVE"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningQuiz createQuiz(LearningQuiz quiz) {
        quiz.setIsDeleted(0);
        quiz.setVersion(1);
        quiz.setUsageCount(0);
        quizMapper.insert(quiz);
        return quiz;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningQuiz updateQuiz(LearningQuiz quiz) {
        quizMapper.updateById(quiz);
        return quiz;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuiz(Long quizId) {
        LearningQuiz quiz = new LearningQuiz();
        quiz.setId(quizId);
        quiz.setIsDeleted(1);
        quizMapper.updateById(quiz);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningQuizRecord submitQuizRecord(LearningQuizRecord record) {
        // 查找现有记录
        LearningQuizRecord existingRecord = getQuizRecordByEmpIdAndQuizId(record.getEmpId(), record.getQuizId());

        if (existingRecord != null) {
            // 更新现有记录
            existingRecord.setUserAnswer(record.getUserAnswer());
            existingRecord.setIsCorrect(record.getIsCorrect());
            existingRecord.setAnswerTime(record.getAnswerTime());
            existingRecord.setAnswerScore(record.getAnswerScore());
            existingRecord.setAttemptCount(existingRecord.getAttemptCount() + 1);
            existingRecord.setLastAttemptTime(LocalDateTime.now());

            if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                existingRecord.setCorrectCount(existingRecord.getCorrectCount() + 1);

                // 检查是否达到掌握标准（5次答对）
                if (existingRecord.getCorrectCount() >= 5) {
                    existingRecord.setIsMastered(1);
                    existingRecord.setMasteredTime(LocalDateTime.now());
                }
            }

            recordMapper.updateById(existingRecord);
            return existingRecord;
        } else {
            // 创建新记录
            record.setAttemptCount(1);
            record.setFirstAttemptTime(LocalDateTime.now());
            record.setLastAttemptTime(LocalDateTime.now());
            record.setCorrectCount(record.getIsCorrect() != null && record.getIsCorrect() == 1 ? 1 : 0);
            record.setIsMastered(0);
            record.setIsDeleted(0);
            record.setVersion(1);
            recordMapper.insert(record);
            return record;
        }
    }

    @Override
    public List<LearningQuizRecord> getQuizRecordsByEmpId(Long empId) {
        return recordMapper.selectList(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .eq(LearningQuizRecord::getIsDeleted, 0));
    }

    @Override
    public LearningQuizRecord getQuizRecordByEmpIdAndQuizId(Long empId, Long quizId) {
        return recordMapper.selectOne(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .eq(LearningQuizRecord::getQuizId, quizId)
                        .eq(LearningQuizRecord::getIsDeleted, 0));
    }

    @Override
    public List<LearningQuizRecord> getQuizRecordsByEmpIdAndPlanId(Long empId, Long planId) {
        return recordMapper.selectList(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .eq(LearningQuizRecord::getPlanId, planId)
                        .eq(LearningQuizRecord::getIsDeleted, 0));
    }

    @Override
    public double calculateMasteryScore(Long empId, Long domainId) {
        // 获取该领域的所有题目
        List<LearningQuiz> quizzes = getQuizzesByDomainId(domainId);
        if (quizzes.isEmpty()) {
            return 0.0;
        }

        // 获取员工在该领域的答题记录
        List<Long> quizIds = quizzes.stream().map(LearningQuiz::getId).collect(Collectors.toList());
        List<LearningQuizRecord> records = recordMapper.selectList(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .in(LearningQuizRecord::getQuizId, quizIds)
                        .eq(LearningQuizRecord::getIsDeleted, 0));

        if (records.isEmpty()) {
            return 0.0;
        }

        // 计算掌握度
        long totalQuizzes = quizzes.size();
        long masteredCount = records.stream().filter(r -> r.getIsMastered() != null && r.getIsMastered() == 1).count();
        long attemptedCount = records.size();

        // 掌握度 = (已掌握题目数 / 总题目数) * 100
        double masteryScore = (double) masteredCount / totalQuizzes * 100;

        // 记录掌握度日志
        saveMasteryLog(empId, domainId, null, null, masteryScore, (int) totalQuizzes, (int) masteredCount, (int) attemptedCount);

        return masteryScore;
    }

    @Override
    public double calculateMasteryScoreByNodeId(Long empId, Long nodeId) {
        // 获取该知识点的所有题目
        List<LearningQuiz> quizzes = getQuizzesByNodeId(nodeId);
        if (quizzes.isEmpty()) {
            return 0.0;
        }

        // 获取员工在该知识点的答题记录
        List<Long> quizIds = quizzes.stream().map(LearningQuiz::getId).collect(Collectors.toList());
        List<LearningQuizRecord> records = recordMapper.selectList(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .in(LearningQuizRecord::getQuizId, quizIds)
                        .eq(LearningQuizRecord::getIsDeleted, 0));

        if (records.isEmpty()) {
            return 0.0;
        }

        // 计算掌握度
        long totalQuizzes = quizzes.size();
        long masteredCount = records.stream().filter(r -> r.getIsMastered() != null && r.getIsMastered() == 1).count();
        long attemptedCount = records.size();

        // 掌握度 = (已掌握题目数 / 总题目数) * 100
        double masteryScore = (double) masteredCount / totalQuizzes * 100;

        // 记录掌握度日志
        saveMasteryLog(empId, null, nodeId, null, masteryScore, (int) totalQuizzes, (int) masteredCount, (int) attemptedCount);

        return masteryScore;
    }

    @Override
    public double calculateMasteryScoreByTagId(Long empId, Long tagId) {
        // 获取该能力标签的所有题目
        List<LearningQuiz> quizzes = getQuizzesByTagId(tagId);
        if (quizzes.isEmpty()) {
            return 0.0;
        }

        // 获取员工在该能力标签的答题记录
        List<Long> quizIds = quizzes.stream().map(LearningQuiz::getId).collect(Collectors.toList());
        List<LearningQuizRecord> records = recordMapper.selectList(
                Wrappers.<LearningQuizRecord>lambdaQuery()
                        .eq(LearningQuizRecord::getEmpId, empId)
                        .in(LearningQuizRecord::getQuizId, quizIds)
                        .eq(LearningQuizRecord::getIsDeleted, 0));

        if (records.isEmpty()) {
            return 0.0;
        }

        // 计算掌握度
        long totalQuizzes = quizzes.size();
        long masteredCount = records.stream().filter(r -> r.getIsMastered() != null && r.getIsMastered() == 1).count();
        long attemptedCount = records.size();

        // 掌握度 = (已掌握题目数 / 总题目数) * 100
        double masteryScore = (double) masteredCount / totalQuizzes * 100;

        // 记录掌握度日志
        saveMasteryLog(empId, null, null, tagId, masteryScore, (int) totalQuizzes, (int) masteredCount, (int) attemptedCount);

        return masteryScore;
    }

    @Override
    public Map<Long, Double> getMasteryOverview(Long empId) {
        Map<Long, Double> overview = new HashMap<>();

        // 获取所有领域
        List<LearningQuiz> allQuizzes = getAllQuizzes();
        Map<Long, List<LearningQuiz>> quizzesByDomain = allQuizzes.stream()
                .filter(q -> q.getDomainId() != null)
                .collect(Collectors.groupingBy(LearningQuiz::getDomainId));

        // 计算每个领域的掌握度
        for (Map.Entry<Long, List<LearningQuiz>> entry : quizzesByDomain.entrySet()) {
            Long domainId = entry.getKey();
            double masteryScore = calculateMasteryScore(empId, domainId);
            overview.put(domainId, masteryScore);
        }

        return overview;
    }

    @Override
    public List<Map<String, Object>> getWeakPoints(Long empId, int limit) {
        List<Map<String, Object>> weakPoints = new ArrayList<>();

        // 获取所有领域
        List<LearningQuiz> allQuizzes = getAllQuizzes();
        Map<Long, List<LearningQuiz>> quizzesByDomain = allQuizzes.stream()
                .filter(q -> q.getDomainId() != null)
                .collect(Collectors.groupingBy(LearningQuiz::getDomainId));

        // 计算每个领域的掌握度
        for (Map.Entry<Long, List<LearningQuiz>> entry : quizzesByDomain.entrySet()) {
            Long domainId = entry.getKey();
            double masteryScore = calculateMasteryScore(empId, domainId);

            if (masteryScore < 60) { // 掌握度低于60%视为薄弱环节
                Map<String, Object> weakPoint = new HashMap<>();
                weakPoint.put("domainId", domainId);
                weakPoint.put("masteryScore", masteryScore);
                weakPoint.put("quizCount", entry.getValue().size());
                weakPoints.add(weakPoint);
            }
        }

        // 按掌握度排序
        weakPoints.sort((a, b) -> Double.compare((double) a.get("masteryScore"), (double) b.get("masteryScore")));

        // 限制返回数量
        return weakPoints.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultQuizzes() {
        log.info("初始化默认测验题目");

        // 检查是否已有数据
        Long count = quizMapper.selectCount(
                Wrappers.<LearningQuiz>lambdaQuery().eq(LearningQuiz::getIsDeleted, 0));
        if (count > 0) {
            log.info("测验题目数据已存在，跳过初始化");
            return;
        }

        // 创建一些默认的测验题目
        createDefaultQuiz("QUIZ001", "Python中哪些是可变类型？", "MULTI_CHOICE",
                "[{\"key\":\"A\",\"value\":\"list\"},{\"key\":\"B\",\"value\":\"dict\"},{\"key\":\"C\",\"value\":\"set\"},{\"key\":\"D\",\"value\":\"tuple\"}]",
                "A,B,C", "list、dict、set是可变类型，tuple是不可变类型", "EASY", 1L, 1L, 1L);

        createDefaultQuiz("QUIZ002", "Python中*args和**kwargs的区别是什么？", "SHORT_ANSWER",
                null, "*args接收位置参数，**kwargs接收关键字参数", "*args接收位置参数，**kwargs接收关键字参数", "MEDIUM", 1L, 1L, 1L);

        createDefaultQuiz("QUIZ003", "FastAPI中如何定义路由？", "SHORT_ANSWER",
                null, "使用@app.get()、@app.post()等装饰器", "使用@app.get()、@app.post()等装饰器", "EASY", 2L, 2L, 2L);

        createDefaultQuiz("QUIZ004", "什么是Transformer的Self-Attention机制？", "SHORT_ANSWER",
                null, "Self-Attention让每个token关注序列中所有其他token，计算Q、K、V矩阵", "Self-Attention让每个token关注序列中所有其他token，计算Q、K、V矩阵", "HARD", 3L, 3L, 3L);

        createDefaultQuiz("QUIZ005", "LoRA微调的原理是什么？", "SHORT_ANSWER",
                null, "LoRA通过低秩分解矩阵来减少可训练参数数量", "LoRA通过低秩分解矩阵来减少可训练参数数量", "MEDIUM", 4L, 4L, 4L);

        log.info("默认测验题目初始化完成");
    }

    private void createDefaultQuiz(String code, String questionText, String questionType,
                                   String optionsJson, String referenceAnswer, String answerExplanation,
                                   String difficultyLevel, Long domainId, Long nodeId, Long tagId) {
        LearningQuiz quiz = new LearningQuiz();
        quiz.setQuizCode(code);
        quiz.setQuestionText(questionText);
        quiz.setQuestionType(questionType);
        quiz.setOptionsJson(optionsJson);
        quiz.setReferenceAnswer(referenceAnswer);
        quiz.setAnswerExplanation(answerExplanation);
        quiz.setDifficultyLevel(difficultyLevel);
        quiz.setDomainId(domainId);
        quiz.setNodeId(nodeId);
        quiz.setTagId(tagId);
        quiz.setEstimatedTime(60); // 默认60秒
        quiz.setScore(BigDecimal.ONE);
        quiz.setUsageCount(0);
        quiz.setStatus("ACTIVE");
        quiz.setIsDeleted(0);
        quiz.setVersion(1);
        quizMapper.insert(quiz);
    }

    private void saveMasteryLog(Long empId, Long domainId, Long nodeId, Long tagId,
                                double masteryScore, int quizCount, int masteredCount, int attemptedCount) {
        LearningMasteryLog log = new LearningMasteryLog();
        log.setEmpId(empId);
        log.setDomainId(domainId);
        log.setNodeId(nodeId);
        log.setTagId(tagId);
        log.setMasteryScore(BigDecimal.valueOf(masteryScore));
        log.setQuizCount(quizCount);
        log.setCorrectCount(attemptedCount);
        log.setMasteredCount(masteredCount);
        log.setCalculationTime(LocalDateTime.now());
        log.setCalculationSource("AUTO");
        log.setIsDeleted(0);
        log.setVersion(1);
        masteryLogMapper.insert(log);
    }
}
