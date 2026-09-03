package com.example.matching.ai.context.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.JdImportTask;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.JdImportTaskMapper;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.evolution.MarketJdQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI上下文来源引用服务实现
 *
 * @author system
 */
@Slf4j
@Service
public class AiContextSourceRefServiceImpl implements AiContextSourceRefService {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final EmpAiTestMapper empAiTestMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final EmpVideoInterviewSessionMapper interviewSessionMapper;
    private final EmpVideoInterviewQuestionMapper interviewQuestionMapper;
    private final InterviewFollowUpQuestionMapper interviewFollowUpQuestionMapper;
    private final RagKnowledgeChunkMapper ragKnowledgeChunkMapper;
    private final JdImportTaskMapper jdImportTaskMapper;
    private final EmpResumeParseMapper empResumeParseMapper;
    private final KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper;
    private final MarketJdQueryPort marketJdQueryPort;

    public AiContextSourceRefServiceImpl(
            ContestEvidenceItemMapper evidenceItemMapper,
            EmpAbilityMapper empAbilityMapper,
            EmpAiTestMapper empAiTestMapper,
            PostAbilityModelMapper postAbilityModelMapper,
            MatchingRecordMapper matchingRecordMapper,
            AbilityTagMapper abilityTagMapper,
            EmpVideoInterviewSessionMapper interviewSessionMapper,
            EmpVideoInterviewQuestionMapper interviewQuestionMapper,
            InterviewFollowUpQuestionMapper interviewFollowUpQuestionMapper,
            RagKnowledgeChunkMapper ragKnowledgeChunkMapper,
            JdImportTaskMapper jdImportTaskMapper,
            EmpResumeParseMapper empResumeParseMapper,
            KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper,
            MarketJdQueryPort marketJdQueryPort) {
        this.evidenceItemMapper = evidenceItemMapper;
        this.empAbilityMapper = empAbilityMapper;
        this.empAiTestMapper = empAiTestMapper;
        this.postAbilityModelMapper = postAbilityModelMapper;
        this.matchingRecordMapper = matchingRecordMapper;
        this.abilityTagMapper = abilityTagMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.interviewFollowUpQuestionMapper = interviewFollowUpQuestionMapper;
        this.ragKnowledgeChunkMapper = ragKnowledgeChunkMapper;
        this.jdImportTaskMapper = jdImportTaskMapper;
        this.empResumeParseMapper = empResumeParseMapper;
        this.knowledgeSourceDocumentMapper = knowledgeSourceDocumentMapper;
        this.marketJdQueryPort = marketJdQueryPort;
    }

    @Override
    public AiContextSourceRefDTO fromEmpAbility(EmpAbility ability, AbilityTag tag) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.empAbilityFactRef(ability.getId()));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(ability.getId()));
        ref.setTitle(tag != null ? tag.getTagName() : "员工能力");
        ref.setSnippet(buildEmpAbilitySnippet(ability, tag));
        ref.setSourceType(AbilitySourceType.canonicalize(ability.getEvaluationSource()));
        ref.setConfidenceScore(ability.getSourceWeight() != null ?
                ability.getSourceWeight().multiply(new java.math.BigDecimal("100")) : null);
        return ref;
    }

    @Override
    public AiContextSourceRefDTO fromPostAbilityModel(PostAbilityModel model, AbilityTag tag) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.postAbilityModelFactRef(model.getId()));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(model.getId()));
        ref.setTitle(tag != null ? tag.getTagName() : "岗位要求");
        ref.setSnippet(buildPostAbilitySnippet(model, tag));
        ref.setSourceType(SourceRefConstants.SOURCE_POST_DESCRIPTION);
        return ref;
    }

    @Override
    public AiContextSourceRefDTO fromEvidence(ContestEvidenceItem evidence) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.contestEvidenceRef(evidence.getId()));
        ref.setRefType(SourceRefConstants.PREFIX_EVIDENCE.replace(":", ""));
        ref.setRefId(String.valueOf(evidence.getId()));
        ref.setTitle(evidence.getSourceTitle());
        ref.setSnippet(truncate(evidence.getSourceText(), 200));
        ref.setSourceType(evidence.getSourceType());
        ref.setConfidenceScore(evidence.getConfidenceScore());
        ref.setCredibilityScore(evidence.getCredibilityScore());
        ref.setReviewStatus(evidence.getEvidenceStatus());
        return ref;
    }

    @Override
    public AiContextSourceRefDTO fromMatchingRecord(MatchingRecord record) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.matchingRecordRef(record.getId()));
        ref.setRefType(SourceRefConstants.PREFIX_MATCHING.replace(":", ""));
        ref.setRefId(String.valueOf(record.getId()));
        ref.setTitle("匹配记录");
        ref.setSnippet("匹配分: " + record.getAiMatchScore());
        ref.setSourceType("MATCHING_SYSTEM");
        return ref;
    }

    @Override
    public boolean isAllowedSourceRef(String ref, AiContextPackageDTO context) {
        // 如果context为null，只做格式校验
        if (context == null) {
            return isValidSourceRefFormat(ref);
        }

        if (context.getSourceRefs() == null) {
            return false;
        }
        return context.getSourceRefs().stream()
                .anyMatch(sr -> sr.getRef() != null && sr.getRef().equals(ref));
    }

    @Override
    public AiContextSourceRefDTO resolve(String ref) {
        ResolveOutcome result = resolveWithStatus(ref);
        return result.resolved();
    }

    @Override
    public ResolveOutcome resolveWithStatus(String ref) {
        if (ref == null || !SourceRefConstants.isValidFormat(ref)) {
            return new ResolveOutcome(SourceRefValidationResult.UNSUPPORTED, null);
        }

        String entityType = SourceRefConstants.parseEntityType(ref);
        Long id = SourceRefConstants.parseEntityId(ref);

        if (entityType == null || id == null) {
            return new ResolveOutcome(SourceRefValidationResult.UNSUPPORTED, null);
        }

        try {
            AiContextSourceRefDTO resolved;
            switch (entityType) {
                case SourceRefConstants.ENTITY_CONTEST_EVIDENCE:
                    resolved = resolveEvidence(id);
                    break;
                case SourceRefConstants.ENTITY_EMP_ABILITY:
                    resolved = resolveEmpAbility(id);
                    break;
                case SourceRefConstants.ENTITY_POST_ABILITY_MODEL:
                    resolved = resolvePostAbilityModel(id);
                    break;
                case SourceRefConstants.ENTITY_MATCHING_RECORD:
                    resolved = resolveMatchingRecord(id);
                    break;
                case SourceRefConstants.ENTITY_INTERVIEW_SESSION:
                    resolved = resolveInterviewSession(id);
                    break;
                case SourceRefConstants.ENTITY_INTERVIEW_QUESTION:
                    resolved = resolveInterviewQuestion(id);
                    break;
                case SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP:
                    resolved = resolveInterviewFollowUp(id);
                    break;
                case SourceRefConstants.ENTITY_CHUNK:
                    resolved = resolveRagChunk(id);
                    break;
                case SourceRefConstants.SOURCE_MARKET_JD:
                    resolved = resolveMarketJd(id);
                    break;
                case SourceRefConstants.SOURCE_JD_IMPORT:
                    resolved = resolveJdImport(id);
                    break;
                case SourceRefConstants.SOURCE_RESUME_PARSE:
                    resolved = resolveResumeParse(id);
                    break;
                case SourceRefConstants.SOURCE_AI_TEST:
                    resolved = resolveAiTest(id, ref);
                    break;
                case SourceRefConstants.SOURCE_AI_INTERVIEW:
                    resolved = resolveAiInterview(id, ref);
                    break;
                case SourceRefConstants.SOURCE_INDUSTRY_WHITEPAPER,
                        SourceRefConstants.SOURCE_POLICY_DOCUMENT,
                        SourceRefConstants.SOURCE_OCCUPATION_STANDARD,
                        SourceRefConstants.SOURCE_CLOUD_KNOWLEDGE_INTERNAL,
                        SourceRefConstants.SOURCE_INTERNAL_BUSINESS_REQUIREMENT,
                        SourceRefConstants.SOURCE_INTERNAL_POST_REQUIREMENT,
                        SourceRefConstants.SOURCE_MARKET_REPORT,
                        SourceRefConstants.SOURCE_RECRUITMENT_JD:
                    resolved = resolveKnowledgeSource(entityType, id);
                    break;
                case SourceRefConstants.SOURCE_ZHIHU_TREND:
                    resolved = resolveZhihuTrend(id);
                    break;
                default:
                    return new ResolveOutcome(SourceRefValidationResult.UNSUPPORTED, null);
            }
            if (resolved == null) {
                return new ResolveOutcome(SourceRefValidationResult.NOT_FOUND, null);
            }
            return new ResolveOutcome(SourceRefValidationResult.VALID, resolved);
        } catch (Exception e) {
            log.warn("来源引用解析失败: {}", ref, e);
            return new ResolveOutcome(SourceRefValidationResult.DEPENDENCY_ERROR, null);
        }
    }

    private AiContextSourceRefDTO resolveEvidence(Long id) {
        ContestEvidenceItem evidence = evidenceItemMapper.selectById(id);
        if (evidence != null && evidence.getIsDeleted() != 1) {
            return fromEvidence(evidence);
        }
        return null;
    }

    private AiContextSourceRefDTO resolveEmpAbility(Long id) {
        EmpAbility ability = empAbilityMapper.selectById(id);
        if (ability != null && ability.getIsDeleted() != 1) {
            AbilityTag tag = abilityTagMapper.selectById(ability.getTagId());
            return fromEmpAbility(ability, tag);
        }
        return null;
    }

    private AiContextSourceRefDTO resolvePostAbilityModel(Long id) {
        PostAbilityModel model = postAbilityModelMapper.selectById(id);
        if (model != null && model.getIsDeleted() != 1) {
            AbilityTag tag = abilityTagMapper.selectById(model.getTagId());
            return fromPostAbilityModel(model, tag);
        }
        return null;
    }

    private AiContextSourceRefDTO resolveMatchingRecord(Long id) {
        MatchingRecord record = matchingRecordMapper.selectById(id);
        if (record != null && record.getIsDeleted() != 1) {
            return fromMatchingRecord(record);
        }
        return null;
    }

    private AiContextSourceRefDTO resolveInterviewSession(Long id) {
        EmpVideoInterviewSession session = interviewSessionMapper.selectById(id);
        if (session == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, id));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(session.getSessionName() != null ? session.getSessionName() : "面试会话");
        ref.setSnippet(truncate(session.getSessionName(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_AI_INTERVIEW);
        return ref;
    }

    private AiContextSourceRefDTO resolveInterviewQuestion(Long id) {
        EmpVideoInterviewQuestion question = interviewQuestionMapper.selectById(id);
        if (question == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, id));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle("面试问题");
        ref.setSnippet(truncate(question.getQuestionText(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_AI_INTERVIEW);
        return ref;
    }

    private AiContextSourceRefDTO resolveInterviewFollowUp(Long id) {
        InterviewFollowUpQuestion followUp = interviewFollowUpQuestionMapper.selectById(id);
        if (followUp == null || followUp.getIsDeleted() != 0) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, id));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle("面试追问");
        ref.setSnippet(truncate(followUp.getQuestionText(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_AI_INTERVIEW);
        return ref;
    }

    private AiContextSourceRefDTO resolveRagChunk(Long id) {
        RagKnowledgeChunk chunk = ragKnowledgeChunkMapper.selectById(id);
        if (chunk == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.factRef(SourceRefConstants.ENTITY_CHUNK, id));
        ref.setRefType(SourceRefConstants.PREFIX_FACT.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle("RAG知识分块");
        ref.setSnippet(truncate(chunk.getChunkText(), 200));
        ref.setSourceType("RAG_CHUNK");
        return ref;
    }

    /**
     * Compatibility bridge for existing assessment evidence such as
     * source:AI_TEST:80 and source:AI_TEST:80:Q1. The test row remains the
     * verified primary record; detailed question-level proof is carried by
     * fact:INTERVIEW_QUESTION or other fact references in the same claim.
     */
    private AiContextSourceRefDTO resolveAiTest(Long id, String originalRef) {
        EmpAiTest test = empAiTestMapper.selectById(id);
        if (test == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(originalRef);
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(test.getTestTitle() != null ? test.getTestTitle() : "AI测试");
        ref.setSnippet(truncate(test.getAnalysisReport() != null ? test.getAnalysisReport()
                : test.getAiEvaluation(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_AI_TEST);
        ref.setConfidenceScore(test.getScore());
        return ref;
    }

    /**
     * Compatibility bridge for the persisted source:AI_INTERVIEW:{sessionId}
     * source reference. The session row is verified here; question and follow-up
     * fact references are still required by the interview Harness policy.
     */
    private AiContextSourceRefDTO resolveAiInterview(Long id, String originalRef) {
        EmpVideoInterviewSession session = interviewSessionMapper.selectById(id);
        if (session == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(originalRef);
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(session.getSessionName() != null ? session.getSessionName() : "AI面试会话");
        ref.setSnippet(truncate(session.getSessionName(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_AI_INTERVIEW);
        return ref;
    }

    /**
     * 知乎趋势引用由服务端采集流水线生成，内容正文仍以演化证据表中的 URL 为准。
     * 这里返回可验证的外部来源上下文，避免把知乎引用误判为非法格式。
     */
    private AiContextSourceRefDTO resolveZhihuTrend(Long id) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_ZHIHU_TREND, id));
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle("知乎趋势外部资料");
        ref.setSnippet("由知乎开放平台采集的岗位演化趋势参考");
        ref.setSourceType(SourceRefConstants.SOURCE_ZHIHU_TREND);
        return ref;
    }

    private AiContextSourceRefDTO resolveKnowledgeSource(String sourceType, Long ragDocumentId) {
        KnowledgeSourceDocument document = knowledgeSourceDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeSourceDocument>()
                        .eq(KnowledgeSourceDocument::getSourceType, sourceType)
                        .eq(KnowledgeSourceDocument::getRagDocumentId, ragDocumentId));
        if (document == null) {
            // Legacy source refs used knowledge_source_document.id before the RAG bridge existed.
            document = knowledgeSourceDocumentMapper.selectById(ragDocumentId);
            if (document == null || !sourceType.equals(document.getSourceType())) {
                return null;
            }
        }
        if (!"ACTIVE".equals(document.getStatus())) {
            return null;
        }

        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.sourceRef(sourceType, ragDocumentId));
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(ragDocumentId));
        ref.setTitle(document.getTitle() != null ? document.getTitle() : sourceType);
        ref.setSnippet(truncate(document.getTitle(), 200));
        ref.setSourceType(sourceType);
        ref.setConfidenceScore(document.getAuthorityScore());
        return ref;
    }

    private AiContextSourceRefDTO resolveMarketJd(Long id) {
        // 缺失/重复/噪声阻断/删除的 JD 一律返回 null（不得作为证据）
        MarketJdQueryPort.MarketJdSnapshot jd = marketJdQueryPort.getAdmissibleSnapshot(id);
        if (jd == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_MARKET_JD, id));
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(jd.postName() != null ? jd.postName() : "市场JD");
        ref.setSnippet(truncate(jd.jobDescription(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_MARKET_JD);
        return ref;
    }

    private AiContextSourceRefDTO resolveJdImport(Long id) {
        JdImportTask task = jdImportTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() != 0) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_JD_IMPORT, id));
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(task.getJdSummary() != null ? task.getJdSummary() : "JD导入任务");
        ref.setSnippet(truncate(task.getJdRawText(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_JD_IMPORT);
        return ref;
    }

    private AiContextSourceRefDTO resolveResumeParse(Long id) {
        EmpResumeParse parse = empResumeParseMapper.selectById(id);
        if (parse == null) {
            return null;
        }
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        ref.setRef(SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_RESUME_PARSE, id));
        ref.setRefType(SourceRefConstants.PREFIX_SOURCE.replace(":", ""));
        ref.setRefId(String.valueOf(id));
        ref.setTitle(parse.getFileName() != null ? parse.getFileName() : "简历解析记录");
        ref.setSnippet(truncate(parse.getParsedContent(), 200));
        ref.setSourceType(SourceRefConstants.SOURCE_RESUME_PARSE);
        return ref;
    }

    private boolean isValidSourceRefFormat(String ref) {
        return SourceRefConstants.isValidFormat(ref);
    }

    private String buildEmpAbilitySnippet(EmpAbility ability, AbilityTag tag) {
        StringBuilder sb = new StringBuilder();
        if (tag != null) {
            sb.append(tag.getTagName());
        }
        sb.append(" 等级: L").append(ability.getMasteryLevel() != null ? ability.getMasteryLevel() : 0);
        if (ability.getEvaluationSource() != null) {
            sb.append(" 来源: ").append(AbilitySourceType.canonicalize(ability.getEvaluationSource()));
        }
        return sb.toString();
    }

    private String buildPostAbilitySnippet(PostAbilityModel model, AbilityTag tag) {
        StringBuilder sb = new StringBuilder();
        if (tag != null) {
            sb.append(tag.getTagName());
        }
        sb.append(" 要求等级: L").append(model.getMinRequiredLevel() != null ? model.getMinRequiredLevel() : 3);
        if (model.getIsCore() != null && model.getIsCore() == 1) {
            sb.append(" [核心]");
        }
        if (model.getIsRequired() != null && model.getIsRequired() == 1) {
            sb.append(" [必填]");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
