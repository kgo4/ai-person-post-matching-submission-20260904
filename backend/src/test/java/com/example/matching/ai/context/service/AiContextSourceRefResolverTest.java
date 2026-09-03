package com.example.matching.ai.context.service;

import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.service.impl.AiContextSourceRefServiceImpl;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.entity.post.JdImportTask;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.post.JdImportTaskMapper;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.port.evolution.MarketJdQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("SourceRef Resolver Extension")
class AiContextSourceRefResolverTest {

    private AiContextSourceRefServiceImpl service;
    private EmpVideoInterviewSessionMapper sessionMapper;
    private EmpAiTestMapper aiTestMapper;
    private EmpVideoInterviewQuestionMapper questionMapper;
    private InterviewFollowUpQuestionMapper followUpMapper;
    private RagKnowledgeChunkMapper chunkMapper;
    private JdImportTaskMapper jdMapper;
    private EmpResumeParseMapper resumeParseMapper;
    private KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper;
    private MarketJdQueryPort marketJdQueryPort;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(EmpVideoInterviewSessionMapper.class);
        aiTestMapper = mock(EmpAiTestMapper.class);
        questionMapper = mock(EmpVideoInterviewQuestionMapper.class);
        followUpMapper = mock(InterviewFollowUpQuestionMapper.class);
        chunkMapper = mock(RagKnowledgeChunkMapper.class);
        jdMapper = mock(JdImportTaskMapper.class);
        resumeParseMapper = mock(EmpResumeParseMapper.class);
        knowledgeSourceDocumentMapper = mock(KnowledgeSourceDocumentMapper.class);
        marketJdQueryPort = mock(MarketJdQueryPort.class);
        service = new AiContextSourceRefServiceImpl(
                mock(com.example.matching.mapper.contest.ContestEvidenceItemMapper.class),
                mock(com.example.matching.mapper.employee.EmpAbilityMapper.class),
                aiTestMapper,
                mock(com.example.matching.mapper.post.PostAbilityModelMapper.class),
                mock(com.example.matching.mapper.matching.MatchingRecordMapper.class),
                mock(com.example.matching.mapper.system.AbilityTagMapper.class),
                sessionMapper, questionMapper, followUpMapper, chunkMapper, jdMapper, resumeParseMapper,
                knowledgeSourceDocumentMapper, marketJdQueryPort);
    }

    @Test
    @DisplayName("Interview session ref resolves VALID with snippet")
    void interviewSessionResolves() {
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(7L);
        session.setSessionName("Java 深度面试");
        when(sessionMapper.selectById(7L)).thenReturn(session);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("fact:INTERVIEW_SESSION:7");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getTitle()).isEqualTo("Java 深度面试");
    }

    @Test
    @DisplayName("Persisted AI interview source ref resolves through its session")
    void aiInterviewSourceResolves() {
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(118L);
        session.setSessionName("Java AI应用开发面试");
        when(sessionMapper.selectById(118L)).thenReturn(session);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:AI_INTERVIEW:118");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getRef()).isEqualTo("source:AI_INTERVIEW:118");
    }

    @Test
    @DisplayName("Persisted AI test question source ref resolves through its test")
    void aiTestQuestionSourceResolves() {
        EmpAiTest test = new EmpAiTest();
        test.setId(80L);
        test.setTestTitle("Java能力核验");
        test.setAnalysisReport("核心能力已完成核验");
        when(aiTestMapper.selectById(80L)).thenReturn(test);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:AI_TEST:80:Q1");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getRef()).isEqualTo("source:AI_TEST:80:Q1");
        assertThat(outcome.resolved().getSourceType()).isEqualTo("AI_TEST");
    }

    @Test
    @DisplayName("Interview question ref resolves VALID with answer transcript snippet")
    void interviewQuestionResolves() {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setId(9L);
        question.setQuestionText("请解释Spring事务传播机制");
        when(questionMapper.selectById(9L)).thenReturn(question);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("fact:INTERVIEW_QUESTION:9");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getRef()).isEqualTo("fact:INTERVIEW_QUESTION:9");
    }

    @Test
    @DisplayName("Interview follow-up ref resolves VALID")
    void interviewFollowUpResolves() {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setId(5L);
        followUp.setQuestionText("追问：事务回滚场景？");
        followUp.setIsDeleted(0);
        when(followUpMapper.selectById(5L)).thenReturn(followUp);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("fact:INTERVIEW_FOLLOW_UP:5");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getTitle()).isEqualTo("面试追问");
    }

    @Test
    @DisplayName("RAG chunk ref resolves VALID with chunk text")
    void ragChunkResolves() {
        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(88L);
        chunk.setChunkText("Kubernetes 安全最佳实践：容器运行时防护...");
        when(chunkMapper.selectById(88L)).thenReturn(chunk);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("fact:CHUNK:88");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getSnippet()).startsWith("Kubernetes");
    }

    @Test
    @DisplayName("JD import ref resolves VALID with raw JD text snippet")
    void jdImportResolves() {
        JdImportTask task = new JdImportTask();
        task.setId(101L);
        task.setJdSummary("Java 后端开发岗位");
        task.setJdRawText("负责Java后端系统开发，熟悉Spring生态。");
        task.setIsDeleted(0);
        when(jdMapper.selectById(101L)).thenReturn(task);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:JD_IMPORT:101");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getTitle()).isEqualTo("Java 后端开发岗位");
    }

    @Test
    @DisplayName("Resume parse ref resolves VALID with parsed content snippet")
    void resumeParseResolves() {
        com.example.matching.entity.employee.EmpResumeParse parse =
                new com.example.matching.entity.employee.EmpResumeParse();
        parse.setId(55L);
        parse.setFileName("张三-后端开发-简历.pdf");
        parse.setParsedContent("负责微服务架构设计，精通Java并发编程。");
        when(resumeParseMapper.selectById(55L)).thenReturn(parse);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:RESUME_PARSE:55");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getTitle()).isEqualTo("张三-后端开发-简历.pdf");
        assertThat(outcome.resolved().getSourceType()).isEqualTo("RESUME_PARSE");
    }

    @Test
    @DisplayName("Industry whitepaper source ref resolves through its RAG document bridge")
    void industryWhitepaperResolves() {
        KnowledgeSourceDocument document = new KnowledgeSourceDocument();
        document.setId(17L);
        document.setRagDocumentId(301L);
        document.setSourceType("INDUSTRY_WHITEPAPER");
        document.setStatus("ACTIVE");
        document.setTitle("2026 AI 人才趋势白皮书");
        when(knowledgeSourceDocumentMapper.selectOne(any())).thenReturn(document);

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:INDUSTRY_WHITEPAPER:301:chunk-8");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getTitle()).isEqualTo("2026 AI 人才趋势白皮书");
    }

    @Test
    @DisplayName("Deleted/absent entities -> NOT_FOUND")
    void absentEntityIsNotFound() {
        when(sessionMapper.selectById(999L)).thenReturn(null);
        when(jdMapper.selectById(999L)).thenReturn(null);
        when(resumeParseMapper.selectById(999L)).thenReturn(null);

        assertThat(service.resolveWithStatus("fact:INTERVIEW_SESSION:999").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
        assertThat(service.resolveWithStatus("source:JD_IMPORT:999").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
        assertThat(service.resolveWithStatus("source:RESUME_PARSE:999").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("Unsupported entity type -> UNSUPPORTED")
    void unsupportedType() {
        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("fact:UNKNOWN_ENTITY:1");
        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.UNSUPPORTED);
    }

    @Test
    @DisplayName("Market JD ref resolves VALID with JD snippet")
    void marketJdResolves() {
        when(marketJdQueryPort.getAdmissibleSnapshot(42L)).thenReturn(new MarketJdQueryPort.MarketJdSnapshot(
                42L, "高级Java工程师", "负责订单系统开发，精通Java并发编程", "A公司"));

        AiContextSourceRefService.ResolveOutcome outcome =
                service.resolveWithStatus("source:MARKET_JD:42");

        assertThat(outcome.status()).isEqualTo(SourceRefValidationResult.VALID);
        assertThat(outcome.resolved().getRef()).isEqualTo("source:MARKET_JD:42");
        assertThat(outcome.resolved().getTitle()).isEqualTo("高级Java工程师");
        assertThat(outcome.resolved().getSourceType()).isEqualTo("MARKET_JD");
        assertThat(outcome.resolved().getSnippet()).startsWith("负责订单系统开发");
    }

    @Test
    @DisplayName("Missing market JD -> NOT_FOUND")
    void missingMarketJd() {
        when(marketJdQueryPort.getAdmissibleSnapshot(999L)).thenReturn(null);

        assertThat(service.resolveWithStatus("source:MARKET_JD:999").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("Duplicate market JD is not accepted evidence")
    void duplicateMarketJdNotAccepted() {
        when(marketJdQueryPort.getAdmissibleSnapshot(43L)).thenReturn(null);

        assertThat(service.resolveWithStatus("source:MARKET_JD:43").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("Noise-blocked market JD (analysisStatus=2) is not accepted evidence")
    void noiseBlockedMarketJdNotAccepted() {
        when(marketJdQueryPort.getAdmissibleSnapshot(44L)).thenReturn(null);

        assertThat(service.resolveWithStatus("source:MARKET_JD:44").status())
                .isEqualTo(SourceRefValidationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("platform:/cleaning: refs are never Harness-resolvable")
    void platformAndCleaningRefsUnsupported() {
        assertThat(service.resolveWithStatus("platform:BOSS直聘").status())
                .isEqualTo(SourceRefValidationResult.UNSUPPORTED);
        assertThat(service.resolveWithStatus("cleaning:12").status())
                .isEqualTo(SourceRefValidationResult.UNSUPPORTED);
    }
}
