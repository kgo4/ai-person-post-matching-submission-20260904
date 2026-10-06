package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.PostCleaningRecordPageQuery;
import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.dto.post.PostRawInput;

/**
 * 岗位数据清洗服务接口
 * <p>
 * 系统内部自动处理：清洗去噪、质量评估、去重检测。
 * 用户不需要手动触发清洗，但可以通过前端查看清洗记录。
 * <p>
 * 阻断策略：
 * - 相似度 >= 0.92：阻断，提示重复岗位
 * - 0.80 - 0.92：不中断，继续解析，但标记为疑似重复
 * - < 0.80：正常解析
 * <p>
 * 质量策略：
 * - qualityScore < 0.4：阻断
 * - 0.4 - 0.7：继续解析，但标记低质量
 * - >= 0.7：正常解析
 */
public interface PostDataCleaningService {

    /**
     * 清洗并检测岗位数据（核心方法）
     * <p>
     * 流程：
     * 1. 文本清洗去噪（去除广告、无关内容、格式噪声等）
     * 2. 结构化提取（职责、要求分离）
     * 3. 质量评分
     * 4. 去重检测（与已有岗位比对）
     * 5. 持久化清洗记录
     *
     * @param input 岗位原始输入
     * @return 清洗结果（含阻断判定）
     */
    PostCleaningResult cleanAndDetect(PostRawInput input);

    /**
     * 标记清洗记录已进入Agent
     *
     * @param cleaningRecordId 清洗记录ID
     * @param agentInputSnapshot Agent输入快照（JSON）
     */
    void markEnteredAgent(Long cleaningRecordId, String agentInputSnapshot);

    /**
     * 分页查询清洗记录
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<PostCleaningRecordVO> pageRecords(PostCleaningRecordPageQuery query);

    /**
     * 查询清洗记录详情
     *
     * @param id 记录ID
     * @return 记录详情
     */
    PostCleaningRecordVO getRecordDetail(Long id);

    /**
     * 重新解析清洗记录（仍走自动清洗流程）
     *
     * @param id 记录ID
     * @return 新的清洗结果
     */
    PostCleaningResult reparse(Long id);
}
