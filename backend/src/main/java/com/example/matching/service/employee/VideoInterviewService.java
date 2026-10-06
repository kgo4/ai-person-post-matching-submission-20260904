package com.example.matching.service.employee;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.employee.video.VideoInterviewCreateDTO;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.dto.employee.video.VideoInterviewImportDTO;
import com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.vo.employee.video.VideoInterviewDetailVO;
import com.example.matching.vo.employee.video.VideoInterviewWsTicketVO;

import java.util.List;

/**
 * AI视频面试 服务接口
 * <p>
 * 提供视频面试会话管理、问题生成、视频上传、转录、多模态分析、能力导入等全流程功能。
 */
public interface VideoInterviewService extends IService<EmpVideoInterviewSession> {

    /**
     * 创建视频面试会话
     *
     * @param dto 创建请求
     * @param userId 当前操作用户ID
     * @return 创建的会话
     */
    EmpVideoInterviewSession createSession(VideoInterviewCreateDTO dto, Long userId);

    /**
     * 生成面试问题
     *
     * @param sessionId 会话ID
     * @param dto 问题生成配置
     */
    void generateQuestions(Long sessionId, VideoInterviewQuestionGenerateDTO dto);

    /**
     * 签发用于WebSocket握手的短期票据.
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @return WebSocket票据
     */
    VideoInterviewWsTicketVO issueWebSocketTicket(Long sessionId, Long userId);

    /**
     * 保存实时视频抽帧摘要证据.
     *
     * @param sessionId 会话ID
     * @param dto 抽帧数据
     */
    void uploadFrame(Long sessionId, VideoInterviewFrameDTO dto);

    /**
     * 开始面试
     * <p>
     * 开始实时面试，推送第一题并启动计时器
     *
     * @param sessionId 会话ID
     */
    void startInterview(Long sessionId);

    /**
     * 下一题
     * <p>
     * 手动切换到下一题
     *
     * @param sessionId 会话ID
     */
    void nextQuestion(Long sessionId);

    /**
     * 结束面试
     * <p>
     * 结束整场面试
     *
     * @param sessionId 会话ID
     */
    void finishInterview(Long sessionId);

    /**
     * 执行多模态分析
     *
     * @param sessionId 会话ID
     */
    void analyze(Long sessionId);

    /**
     * 异步执行多模态分析（面试结束后自动调用）
     *
     * @param sessionId 会话ID
     */
    void analyzeAsync(Long sessionId);

    /**
     * 查询员工的视频面试列表
     *
     * @param empId 员工ID
     * @return 会话列表
     */
    List<EmpVideoInterviewSession> listByEmpId(Long empId);

    List<EmpVideoInterviewSession> listAll();

    /**
     * 获取视频面试详情（含问题、证据、能力）
     *
     * @param sessionId 会话ID
     * @return 详情VO
     */
    VideoInterviewDetailVO getDetail(Long sessionId);

    /**
     * 将审核通过的能力导入到员工能力档案
     *
     * @param sessionId 会话ID
     * @param dto 导入配置
     * @param userId 当前操作用户ID
     */
    void importToAbilityProfile(Long sessionId, VideoInterviewImportDTO dto, Long userId);
}
