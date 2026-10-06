package com.example.matching.service.evolution;

import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 知识源文档索引状态的独立落库器。
 *
 * <p>为什么单独开一个 Bean：索引失败时，调用方 {@code indexKnowledgeSource}
 * 会先写「失败」状态、再抛出让前端知道失败。但那条写操作和抛出发生在同一个事务里，
 * 抛出会触发回滚，**失败状态等于没写** —— 文档永远停在 {@code PENDING}，
 * 材料管理列表上显示成「待索引」，用户重试也不知道上次为什么失败。
 *
 * <p>用 {@code REQUIRES_NEW} 把这次写放到独立事务里先提交，调用方随后回滚也带不走它。
 * 必须是外部 Bean 调用：同类内部自调用不走代理，{@code REQUIRES_NEW} 不会生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSourceIndexStatusUpdater {

    private final KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper;

    /**
     * 以独立事务记录索引失败。
     *
     * @param documentId 知识源文档 id
     * @param reason     面向使用者的中文失败原因（技术细节由调用方写日志）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId, String reason) {
        KnowledgeSourceDocument document = knowledgeSourceDocumentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        document.setStatus("FAILED");
        document.setIndexStatus("FAILED");
        document.setLastIndexedTime(LocalDateTime.now());
        knowledgeSourceDocumentMapper.updateById(document);
        log.warn("知识源文档索引失败已登记: documentId={}, reason={}", documentId, reason);
    }
}


