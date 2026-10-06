package com.example.matching.service.evolution;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.evolution.KnowledgeSourceMaterialQuery;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.vo.evolution.KnowledgeSourceMaterialVO;

/**
 * 演化资料入口服务接口
 * <p>
 * 负责行业白皮书、内部文件、云知识库资料进入演化知识源。
 *
 * @author system
 */
public interface EvolutionSourceIngestionService {

    /**
     * 上传行业白皮书
     *
     * @param file       文件
     * @param dto        上传信息
     * @param operatorId 操作人ID
     * @return 知识源文档
     */
    KnowledgeSourceDocument uploadIndustryWhitepaper(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId);

    /**
     * 上传公司内部资料（权威材料走这条）。
     *
     * <p>同一份内容、同一资料类别此前已上传过时**直接复用**，不重复建 RAG 文档与向量索引。
     *
     * @param fileName   文件名
     * @param content    文件内容
     * @param dto        上传信息（含 {@code ephemeral}：是否仅试算）
     * @param operatorId 操作人ID
     * @return 上传结果，区分「新建」与「复用」
     */
    SourceUploadOutcome uploadInternalDocument(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId);

    /**
     * 同步云知识库
     *
     * @param request 同步请求
     * @return 同步的文档数量
     */
    int syncCloudKnowledge(CloudSyncRequest request);

    /**
     * 对知识源文档进行索引（切片+向量化）
     *
     * @param documentId 文档ID
     * @return 切片数量
     */
    int indexKnowledgeSource(Long documentId);

    /**
     * 分页查询人工上传的权威材料（材料管理用）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResponse<KnowledgeSourceMaterialVO> pageMaterials(KnowledgeSourceMaterialQuery query);

    /**
     * 删除一份权威材料。
     *
     * <p>级联清理：知识源文档记录、RAG 文档、分块、向量投影与磁盘文件。
     * 已落地候选里保存的证据原文是快照，不受影响。
     *
     * @param documentId 知识源文档ID
     * @return 面向使用者的中文结果说明
     */
    String deleteMaterial(Long documentId);

    /**
     * 清理过期的「仅试算」材料（定时兜底）。
     *
     * <p>正常路径由趋势解析任务进入终态时触发清理；这里是兜底 ——
     * 任务被取消、服务重启、或用户上传后从未发起解析，都会留下无人认领的试算材料。
     *
     * @param olderThanHours 超过多少小时未更新的试算材料才清理
     * @return 实际清理的份数
     */
    int purgeExpiredEphemeralMaterials(int olderThanHours);
}
