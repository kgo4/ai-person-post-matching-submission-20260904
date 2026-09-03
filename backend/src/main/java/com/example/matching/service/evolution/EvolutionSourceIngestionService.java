package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;

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
     * @param file     文件
     * @param dto      上传信息
     * @param operatorId 操作人ID
     * @return 知识源文档
     */
    KnowledgeSourceDocument uploadIndustryWhitepaper(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId);

    /**
     * 上传公司内部资料
     *
     * @param file     文件
     * @param dto      上传信息
     * @param operatorId 操作人ID
     * @return 知识源文档
     */
    KnowledgeSourceDocument uploadInternalDocument(
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
}
