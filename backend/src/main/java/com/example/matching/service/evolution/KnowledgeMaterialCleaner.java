package com.example.matching.service.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 单份权威材料的级联清理器（独立事务）。
 *
 * <p>为什么单独开一个 Bean，而不是把删除逻辑写在 {@code EvolutionSourceIngestionServiceImpl} 里：
 * <ul>
 *   <li><b>事务隔离</b>：兜底清理要「一份失败不影响其余」。若清理循环和单份删除在同一个事务里，
 *       任意一份抛数据库异常都会把整个事务标记为 rollback-only，循环里 catch 住也没用 ——
 *       提交时会整体回滚，且每次调度都卡在同一份脏数据上，永远清不掉。</li>
 *   <li><b>代理生效</b>：{@code REQUIRES_NEW} 必须由外部 Bean 调用才生效，同类内部自调用不走代理。</li>
 * </ul>
 *
 * <p>校验也放在这里，与删除同源：这些判断（是否存在、是否系统自动来源）本来就是「能不能删」的前提，
 * 拆到调用方会多一次读库，且两个入口（人工删除、兜底清理）容易各写一份而出现口径分叉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeMaterialCleaner {

    /** 由系统同步产生的来源类型：这类材料下一轮同步会复活，不允许在材料管理里删掉。 */
    private static final String MANAGED_SOURCE_TYPE = "CLOUD_KNOWLEDGE_INTERNAL";

    private static final List<String> TERMINAL_STATUSES = List.of("DELETED", "ARCHIVED");

    private final KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper;
    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 清理结果。
     *
     * @param title     材料标题（用于组织面向用户的文案；材料已被删除，调用方无法再查到标题）
     * @param storagePath 磁盘文件路径 —— 由调用方在**事务提交后**删除，
     *                    否则事务回滚会留下「记录还在、文件没了」的半残状态
     * @param removedChunks 实际清理掉的检索片段数
     */
    public record CleanupResult(String title, String storagePath, int removedChunks) {
    }

    /**
     * 删除一份人工上传的权威材料及其检索索引。
     *
     * @param documentId 知识源文档 id
     * @return 清理结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CleanupResult deleteOne(Long documentId) {
        if (documentId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请指定要删除的材料");
        }
        KnowledgeSourceDocument document = knowledgeSourceDocumentMapper.selectById(documentId);
        if (document == null || TERMINAL_STATUSES.contains(document.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "材料不存在或已被删除");
        }
        if (!MANAGED_SOURCE_TYPE.equals(document.getSourceType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该材料由系统同步产生，不支持在材料管理中删除");
        }

        int removedChunks = document.getRagDocumentId() == null
                ? 0
                : knowledgeDocumentService.deleteDocument(document.getRagDocumentId());

        // 物理删除源文档记录：knowledge_source_document 没有软删标记，
        // 留下一条 status=DELETED 的记录会被「重复材料复用」再捡起来，等于删了又复活
        knowledgeSourceDocumentMapper.deleteById(documentId);

        log.info("材料已清理: documentId={}, title={}, 清理分块={}",
                documentId, document.getTitle(), removedChunks);
        return new CleanupResult(document.getTitle(), document.getStoragePath(), removedChunks);
    }
}
