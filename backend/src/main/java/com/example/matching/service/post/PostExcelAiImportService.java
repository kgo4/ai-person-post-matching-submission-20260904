package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.dto.post.PostImportPreviewDTO;
import com.example.matching.dto.post.PostImportConfirmDTO;

import java.io.InputStream;

/**
 * Excel AI导入服务接口
 * <p>
 * 负责Excel岗位批量导入的完整流程：
 * 1. 上传Excel文件
 * 2. 程序化读取原始单元格数据
 * 3. AI识别Excel结构（表头、字段映射）
 * 4. 组装统一岗位对象
 * 5. 对每条岗位调用能力提取服务
 * 6. 返回预览结果供用户确认
 * 7. 用户确认后批量创建岗位和能力模型
 */
public interface PostExcelAiImportService {

    /**
     * 上传并解析Excel文件（快速返回）
     * <p>
     * 流程：读取Excel原始数据 -> AI识别结构 -> 组装岗位对象 -> 保存到数据库
     * 不做能力分析，能力分析由 analyzeBatch 异步触发。
     *
     * @param file 上传的Excel文件（xls/xlsx）
     * @return 预览结果（岗位名称已解析，能力分析待触发）
     */
    PostImportPreviewDTO uploadAndAnalyze(String fileName, InputStream inputStream);

    /**
     * 触发批量AI能力分析
     * <p>
     * 对批次中所有待分析的岗位逐条调用AI提取能力要求。
     * 前端可轮询 getPreview 获取分析进度。
     *
     * @param batchId 批次ID
     */
    void analyzeBatch(Long batchId);

    /**
     * 获取导入预览（已上传的批次）
     *
     * @param batchId 批次ID
     * @return 预览结果
     */
    PostImportPreviewDTO getPreview(Long batchId);

    /**
     * 确认并批量导入
     * <p>
     * 将用户确认后的岗位数据批量写入系统。
     *
     * @param confirmDTO 确认请求（含批次ID和用户编辑后的数据）
     */
    void confirmAndImport(PostImportConfirmDTO confirmDTO);

    /** 由确认导入消息消费者执行批次导入。 */
    void processConfirmedImport(Long batchId);

    /** 将已完成的岗位导入批次复用为市场发现样本，不重新分析 JD。 */
    int includeBatchInMarketDiscovery(Long batchId);

    /**
     * 取消正在进行的分析任务
     * <p>
     * 设置批次的取消标志，Listener 会在下一条处理前检查并停止。
     *
     * @param batchId 批次ID
     */
    void cancelBatch(Long batchId);

    /**
     * 分页查询导入批次列表
     *
     * @param current      当前页码
     * @param size         每页大小
     * @param importStatus 可选，按状态筛选
     * @return 分页结果
     */
    Page<PostImportBatchVO> pageBatches(long current, long size, Integer importStatus);

    /**
     * 重新触发失败批次的AI分析
     * <p>
     * 仅对待解析(0)、分析失败(5)或已取消的批次有效。
     * 会重置批次状态和失败项的分析状态，重新发送到MQ。
     *
     * @param batchId 批次ID
     */
    void retryBatch(Long batchId);

    /** 删除导入批次及临时明细，不删除已确认创建的岗位。 */
    void deleteBatch(Long batchId);
}
