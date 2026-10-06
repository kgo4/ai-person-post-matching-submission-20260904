package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频面试通用问题构建：问题数量归一化、通用问题列表生成。
 * <p>
 * 从 VideoInterviewServiceImpl（690 行）中拆分的通用问题组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoInterviewQuestionBuilder {

    private final EmpVideoInterviewQuestionMapper questionMapper;

    private static final int DEFAULT_QUESTION_COUNT = 6;
    private static final int MIN_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 10;
    private static final String Q_TYPE_GENERAL = "GENERAL";
    private static final String Q_TYPE_TECHNICAL = "TECHNICAL";
    private static final String Q_TYPE_BEHAVIORAL = "BEHAVIORAL";
    public int normalizeQuestionCount(Integer questionCount) {
        int count = questionCount != null ? questionCount : DEFAULT_QUESTION_COUNT;
        if (count < MIN_QUESTION_COUNT || count > MAX_QUESTION_COUNT) {
            throw new BusinessException(400, "题目数量必须在3-10之间");
        }
        return count;
    }

    /**
     * 构建通用面试问题（agent 异常时的无AI fallback）
     */
    public List<EmpVideoInterviewQuestion> buildGeneralQuestions(Long sessionId, int startOrder, int maxCount) {
        String[][] generalQuestions = {
                {Q_TYPE_GENERAL, "请简单介绍一下您自己，包括您的专业背景和职业经历。"},
                {Q_TYPE_BEHAVIORAL, "请描述一个您在团队合作中遇到挑战并成功解决的案例。"},
                {Q_TYPE_GENERAL, "您如何看待持续学习？请分享一个您最近学习新技能的经历。"},
                {Q_TYPE_BEHAVIORAL, "请举例说明您如何在压力下保持高效工作。"},
                {Q_TYPE_GENERAL, "您对未来3-5年的职业规划是什么？"},
                {Q_TYPE_BEHAVIORAL, "请描述一次您主动承担额外责任的经历。"},
                {Q_TYPE_GENERAL, "您认为自己最大的优势和需要改进的地方分别是什么？"},
                {Q_TYPE_BEHAVIORAL, "请分享一个您在项目中提出创新方案的例子。"}
        };

        List<EmpVideoInterviewQuestion> questions = new ArrayList<>();
        for (int i = 0; i < generalQuestions.length && (startOrder + i) <= maxCount; i++) {
            EmpVideoInterviewQuestion q = new EmpVideoInterviewQuestion();
            q.setSessionId(sessionId);
            q.setQuestionOrder(startOrder + i);
            q.setQuestionType(generalQuestions[i][0]);
            q.setQuestionText(generalQuestions[i][1]);
            q.setExpectedTagsJson("[]");
            questions.add(q);
        }
        return questions;
    }

    public Long findQuestionId(Long sessionId, Integer questionOrder) {
        if (questionOrder == null || questionOrder <= 0) {
            return null;
        }
        EmpVideoInterviewQuestion question = questionMapper.selectOne(
                Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                        .eq(EmpVideoInterviewQuestion::getQuestionOrder, questionOrder)
        );
        return question != null ? question.getId() : null;
    }

}