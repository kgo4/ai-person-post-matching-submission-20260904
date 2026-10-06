package com.example.matching.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户可读文案闸门的测试。
 *
 * <p>重点不是「能识别 SQL」，而是**不能误伤正常中文提示** ——
 * 后者才是这个工具类会不会被绕开的决定性因素。
 */
class UserFacingMessageTest {

    /** 线上真实出现过的那条重复键报错原文。 */
    private static final String DUPLICATE_KEY = "### Error updating database. Cause: "
            + "java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '1003-0-1' "
            + "for key 'rag_knowledge_chunk.uk_doc_chunk_revision' ### The error may exist in "
            + "com/example/matching/mapper/rag/RagKnowledgeChunkMapper.java (best guess) "
            + "### SQL: INSERT INTO rag_knowledge_chunk ( document_id, chunk_index ) VALUES ( ?, ? )";

    /* ==================== looksTechnical：强特征 ==================== */

    @Test
    @DisplayName("强特征：SQL / 异常类名 / 堆栈行命中即判为技术文案")
    void strongMarkersAreDetected() {
        assertTrue(UserFacingMessage.looksTechnical(DUPLICATE_KEY));
        assertTrue(UserFacingMessage.looksTechnical("Duplicate entry '1003-0-1' for key 'uk_doc_chunk_revision'"));
        assertTrue(UserFacingMessage.looksTechnical("java.lang.NullPointerException: content is null"));
        assertTrue(UserFacingMessage.looksTechnical("at com.example.matching.Foo.bar(Foo.java:42)"));
        assertTrue(UserFacingMessage.looksTechnical("nested exception is org.apache.ibatis.MyBatisException"));
        assertTrue(UserFacingMessage.looksTechnical("CannotAcquireLockException"));
        assertTrue(UserFacingMessage.looksTechnical("SQLState: 23000"));
        assertTrue(UserFacingMessage.looksTechnical("Connection is read-only. Queries leading to data modification"));
    }

    @Test
    @DisplayName("强特征与中文无关：中文前缀 + 英文异常原文同样要被拦下")
    void strongMarkersBeatChinesePrefix() {
        // 这正是历史写法 `"文档索引失败: " + e.getMessage()` 产出形态
        String concatenated = "文档索引失败: " + DUPLICATE_KEY;
        assertTrue(UserFacingMessage.looksTechnical(concatenated));
    }

    /* ==================== looksTechnical：弱特征与长度 ==================== */

    @Test
    @DisplayName("弱特征只在整条没有中文时才生效，避免误伤中文提示")
    void weakMarkersOnlyApplyWithoutChinese() {
        // 无中文 → 命中
        assertTrue(UserFacingMessage.looksTechnical("Connection refused"));
        assertTrue(UserFacingMessage.looksTechnical("read timeout"));
        assertTrue(UserFacingMessage.looksTechnical("unique constraint violated"));
        // 有中文 → 放行，这是写给用户看的
        assertFalse(UserFacingMessage.looksTechnical("AI 调用 timeout，请稍后重试"));
        assertFalse(UserFacingMessage.looksTechnical("索引建立超时（timeout），可稍后重试"));
    }

    @Test
    @DisplayName("无中文且超过长度阈值 → 判为没翻译的英文文案")
    void longEnglishOnlyTextIsTechnical() {
        assertTrue(UserFacingMessage.looksTechnical("This operation is not permitted for the current user role"));
        // 短英文标识符放行：可能是枚举值，不会造成伤害
        assertFalse(UserFacingMessage.looksTechnical("INDEXED"));
        assertFalse(UserFacingMessage.looksTechnical("PDF"));
    }

    @Test
    @DisplayName("空文本判为技术文案：宁可给兜底也不给空白")
    void blankIsTechnical() {
        assertTrue(UserFacingMessage.looksTechnical(null));
        assertTrue(UserFacingMessage.looksTechnical(""));
        assertTrue(UserFacingMessage.looksTechnical("   "));
    }

    /* ==================== 不能误伤：这一组才是关键 ==================== */

    @Test
    @DisplayName("正常中文业务提示绝不能被误判")
    void chineseHintsAreNotTechnical() {
        assertFalse(UserFacingMessage.looksTechnical("材料已保存，但建立检索索引失败，请稍后在材料管理中重试索引"));
        assertFalse(UserFacingMessage.looksTechnical("扫描件或纯图片 PDF 没有文本层，需要先做 OCR 再上传"));
        assertFalse(UserFacingMessage.looksTechnical("这份材料此前已经上传过，已直接复用原有索引，没有重复建索引"));
        assertFalse(UserFacingMessage.looksTechnical("会议链接域名不在允许列表中（当前允许：meeting.iflyrec.com）"));
        assertFalse(UserFacingMessage.looksTechnical("请先上传至少一份权威材料，再开始解析"));
        // 带技术名词但确实是说给人听的
        assertFalse(UserFacingMessage.looksTechnical("Milvus 不可用，已降级为关键词检索，请稍后重试"));
        assertFalse(UserFacingMessage.looksTechnical("材料已保存，但建立检索索引失败（知识源文档不存在），请稍后重试"));
    }

    /* ==================== sanitize ==================== */

    @Test
    @DisplayName("sanitize：技术文案换兜底，友好文案原样返回并去空白")
    void sanitizeReplacesOnlyTechnicalText() {
        assertEquals("材料索引失败，请稍后重试",
                UserFacingMessage.sanitize(DUPLICATE_KEY, "材料索引失败，请稍后重试"));
        assertEquals("材料索引失败，请稍后重试",
                UserFacingMessage.sanitize(null, "材料索引失败，请稍后重试"));
        assertEquals("请先上传至少一份权威材料",
                UserFacingMessage.sanitize("  请先上传至少一份权威材料  ", "兜底"));
        assertEquals("兜底", UserFacingMessage.sanitize("   ", "兜底"));
    }

    /* ==================== withCause ==================== */

    @Test
    @DisplayName("withCause：原因友好时保留，原因技术时只留中文前缀")
    void withCauseKeepsFriendlyCauseOnly() {
        assertEquals("文档索引失败：知识源文档不存在",
                UserFacingMessage.withCause("文档索引失败", "知识源文档不存在"));
        assertEquals("文档索引失败", UserFacingMessage.withCause("文档索引失败", DUPLICATE_KEY));
        assertEquals("文档索引失败", UserFacingMessage.withCause("文档索引失败", null));
        assertEquals("文档索引失败", UserFacingMessage.withCause("文档索引失败", "Connection refused"));
    }
}
