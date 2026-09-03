package com.example.matching.vector;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.config.MilvusConfig;
import com.example.matching.config.ResilientMilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务（统一集合）
 * <p>
 * 使用统一集合 person_post_vector 管理员工和岗位向量。
 * 通过 type 字段区分 EMPLOYEE / POST，ref_id + type 唯一约束。
 */
@Slf4j
@Service
public class MilvusVectorService {

    @Autowired(required = false)
    private ResilientMilvusClient resilientMilvusClient;

    /**
     * 获取 Milvus 客户端（支持自动重连），不可用时返回 null
     */
    private MilvusServiceClient getMilvusClient() {
        return resilientMilvusClient != null ? resilientMilvusClient.getClient() : null;
    }

    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Autowired
    private AbilityTagMapper abilityTagMapper;

    @Autowired(required = false)
    private MilvusConfig milvusConfig;

    public static final String TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String TYPE_POST = "POST";
    public static final String TYPE_TAG_CANDIDATE = "TAG_CANDIDATE";
    private String getCollection() {
        return milvusConfig != null && milvusConfig.getProfileCollectionName() != null
                ? milvusConfig.getProfileCollectionName() : "person_post_vector";
    }

    private int getVectorDim() {
        return milvusConfig != null && milvusConfig.getDimension() > 0
                ? milvusConfig.getDimension() : 1536;
    }
    private static final String INDEX_NAME = "vector_idx";

    /**
     * 是否允许破坏性重建集合（drop + recreate）。
     * <p>
     * 默认 false：只有 schema 明确不兼容（INCOMPATIBLE）且此开关为 true 时才执行 drop；
     * Milvus 不可用（UNAVAILABLE）时绝不删除集合。
     */
    @org.springframework.beans.factory.annotation.Value("${milvus.allow-destructive-recreate:false}")
    private boolean allowDestructiveRecreate = false;

    /** 集合 schema 兼容性三态结果 */
    enum SchemaStatus {
        /** schema 与当前 SDK 兼容，可直接使用 */
        COMPATIBLE,
        /** schema 明确不兼容，需要重建 */
        INCOMPATIBLE,
        /** 无法确认（Milvus 不可用/超时/网络错误），绝不能执行破坏性操作 */
        UNAVAILABLE
    }

    // ==================== 初始化 ====================

    public void initCollections() {
        try {
            initCollectionsInternal();
        } catch (Exception e) {
            log.warn("Milvus 集合初始化未完成，向量检索降级运行（Milvus 恢复后自动重连）: {}", e.getMessage());
        }
    }

    private void initCollectionsInternal() {
        if (getMilvusClient() == null) {
            log.warn("MilvusServiceClient 未初始化，跳过集合初始化");
            return;
        }
        R<Boolean> hasColl = getMilvusClient().hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(getCollection()).build());
        if (hasColl.getData() != null && hasColl.getData()) {
            SchemaStatus status = checkCollectionSchema();
            switch (status) {
                case COMPATIBLE -> log.info("Collection {} already exists", getCollection());
                case INCOMPATIBLE -> {
                    if (allowDestructiveRecreate) {
                        log.warn("Collection {} schema is not compatible with current SDK, recreating it", getCollection());
                        recreateCollection();
                    } else {
                        log.error("Collection {} schema is INCOMPATIBLE with current SDK but destructive recreate is disabled "
                                + "(milvus.allow-destructive-recreate=false). Vector operations will not be available. "
                                + "Enable the flag explicitly only after confirming the schema mismatch is intentional.", getCollection());
                    }
                }
                case UNAVAILABLE -> log.error("Collection {} schema could not be verified (Milvus unavailable). "
                        + "Skipping destructive actions; vector operations will degrade.", getCollection());
            }
            return;
        }


        // id: Long, 主键，雪花算法
        FieldType idField = FieldType.newBuilder()
                .withName("id").withDataType(DataType.Int64).withPrimaryKey(true).build();
        // type: EMPLOYEE/POST
        FieldType typeField = FieldType.newBuilder()
                .withName("type").withDataType(DataType.VarChar).withMaxLength(20).build();
        // ref_id: 关联员工ID/岗位ID
        FieldType refIdField = FieldType.newBuilder()
                .withName("ref_id").withDataType(DataType.Int64).build();
        // text: 向量化原文
        FieldType textField = FieldType.newBuilder()
                .withName("text").withDataType(DataType.VarChar).withMaxLength(65535).build();
        // vector: 1536维浮点向量
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector").withDataType(DataType.FloatVector).withDimension(getVectorDim()).build();
        // create_time: 创建时间
        FieldType createTimeField = FieldType.newBuilder()
                .withName("create_time").withDataType(DataType.Int64).build();

        @SuppressWarnings("deprecation")
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(getCollection())
                .withDescription("人岗匹配统一向量集合")
                .addFieldType(idField)
                .addFieldType(typeField)
                .addFieldType(refIdField)
                .addFieldType(textField)
                .addFieldType(vectorField)
                .addFieldType(createTimeField)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        getMilvusClient().createCollection(createParam);

        // HNSW 向量索引
        getMilvusClient().createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(getCollection())
                .withFieldName("vector")
                .withIndexName(INDEX_NAME)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\": 16, \"efConstruction\": 200}")
                .build());

        getMilvusClient().loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(getCollection()).build());

        log.info("Collection {} created", getCollection());
    }

    private void recreateCollection() {
        if (!allowDestructiveRecreate) {
            log.error("Destructive recreate of collection {} skipped: milvus.allow-destructive-recreate is disabled", getCollection());
            return;
        }
        try {
            getMilvusClient().dropCollection(
                    DropCollectionParam.newBuilder().withCollectionName(getCollection()).build());
        } catch (Exception e) {
            log.error("Drop collection {} failed before recreate: {}", getCollection(), e.getMessage(), e);
        }
        initCollectionsAfterDrop();
        log.warn("Collection {} was recreated due to schema mismatch; a full vector rebuild is required to restore data", getCollection());
    }

    private void initCollectionsAfterDrop() {
        R<Boolean> hasColl = getMilvusClient().hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(getCollection()).build());
        if (hasColl.getData() != null && hasColl.getData()) {
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName("id").withDataType(DataType.Int64).withPrimaryKey(true).build();
        FieldType typeField = FieldType.newBuilder()
                .withName("type").withDataType(DataType.VarChar).withMaxLength(20).build();
        FieldType refIdField = FieldType.newBuilder()
                .withName("ref_id").withDataType(DataType.Int64).build();
        FieldType textField = FieldType.newBuilder()
                .withName("text").withDataType(DataType.VarChar).withMaxLength(65535).build();
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector").withDataType(DataType.FloatVector).withDimension(getVectorDim()).build();
        FieldType createTimeField = FieldType.newBuilder()
                .withName("create_time").withDataType(DataType.Int64).build();

        @SuppressWarnings("deprecation")
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(getCollection())
                .withDescription("person post matching unified vector collection")
                .addFieldType(idField)
                .addFieldType(typeField)
                .addFieldType(refIdField)
                .addFieldType(textField)
                .addFieldType(vectorField)
                .addFieldType(createTimeField)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();

        getMilvusClient().createCollection(createParam);
        getMilvusClient().createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(getCollection())
                .withFieldName("vector")
                .withIndexName(INDEX_NAME)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\": 16, \"efConstruction\": 200}")
                .build());
        getMilvusClient().loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(getCollection()).build());
        log.info("Collection {} recreated", getCollection());
    }

    private SchemaStatus checkCollectionSchema() {
        try {
            R<io.milvus.grpc.DescribeCollectionResponse> response = getMilvusClient().describeCollection(
                    DescribeCollectionParam.newBuilder().withCollectionName(getCollection()).build());
            if (response.getData() == null) {
                log.error("Describe collection {} returned no data, schema unknown", getCollection());
                return SchemaStatus.UNAVAILABLE;
            }
            io.milvus.response.DescCollResponseWrapper schema =
                    new io.milvus.response.DescCollResponseWrapper(response.getData());
            io.milvus.param.collection.FieldType vectorField = schema.getFieldByName("vector");
            if (vectorField == null || vectorField.getDataType() != DataType.FloatVector
                    || vectorField.getDimension() != getVectorDim()) {
                log.error("Collection {} vector schema mismatch: expected dim={}, actual={}",
                        getCollection(), getVectorDim(), vectorField == null ? "missing" : vectorField.getDimension());
                return SchemaStatus.INCOMPATIBLE;
            }
            return SchemaStatus.COMPATIBLE;
        } catch (io.milvus.exception.MilvusException e) {
            log.error("Describe collection {} failed with Milvus error: {}", getCollection(), e.getMessage(), e);
            return SchemaStatus.UNAVAILABLE;
        } catch (Exception e) {
            log.error("Describe collection {} failed: {}", getCollection(), e.getMessage(), e);
            return SchemaStatus.UNAVAILABLE;
        }
    }

    // ==================== 向量插入（upsert: ref_id + type 唯一） ====================

    /**
     * 插入/更新员工能力向量
     */
    public boolean insertEmployeeVector(Long empId, EmpEmployee emp, List<EmpAbility> abilities) {
        if (getMilvusClient() == null) {
            log.debug("MilvusServiceClient 不可用，跳过员工向量插入：empId={}", empId);
            return false;
        }
        Map<Long, String> tagNameMap = resolveTagNames(abilities == null ? List.of() : abilities.stream()
                .map(EmpAbility::getTagId).toList());
        String text = buildFormalEmployeeRecallText(abilities, tagNameMap);
        List<Float> vector = vectorEmbeddingService.embed(text);
        if (!isValidVector(vector)) {
            log.debug("向量无效（embedding可能失败），跳过Milvus插入：empId={}", empId);
            return false;
        }
        return upsertVector(empId, TYPE_EMPLOYEE, text, vector);
    }

    /**
     * 插入/更新岗位要求向量
     */
    public boolean insertPostVector(Long postId, PostPost post, List<PostAbilityModel> requirements) {
        if (getMilvusClient() == null) {
            log.debug("MilvusServiceClient 不可用，跳过岗位向量插入：postId={}", postId);
            return false;
        }
        Map<Long, String> tagNameMap = resolveTagNames(requirements == null ? List.of() : requirements.stream()
                .map(PostAbilityModel::getTagId).filter(Objects::nonNull).toList());
        String text = buildFormalPostRecallText(requirements, tagNameMap);
        List<Float> vector = vectorEmbeddingService.embed(text);
        if (!isValidVector(vector)) {
            log.debug("向量无效（embedding可能失败），跳过Milvus插入：postId={}", postId);
            return false;
        }
        return upsertVector(postId, TYPE_POST, text, vector);
    }

    /** Stores a semantic candidate-cluster centroid for taxonomy governance recall. */
    public boolean upsertTagCandidateVector(Long candidateId, String text, List<Float> vector) {
        if (getMilvusClient() == null || !isValidVector(vector)) return false;
        return upsertVector(candidateId, TYPE_TAG_CANDIDATE, text, vector);
    }

    /** Searches semantic candidate clusters. Callers must still validate the returned cluster state. */
    public List<Map<String, Object>> searchTagCandidateVectors(List<Float> vector, int topK) {
        if (!isValidVector(vector)) return Collections.emptyList();
        return searchByType(vector, TYPE_TAG_CANDIDATE, topK);
    }

    /**
     * 检查向量是否有效（非空且非全零占位向量）
     */
    private boolean isValidVector(List<Float> vector) {
        if (vector == null || vector.isEmpty()) return false;
        for (Float f : vector) {
            if (f != null && f != 0f) return true;
        }
        return false; // 全零向量 = 占位向量 = 无效
    }

    private String buildFormalEmployeeRecallText(List<EmpAbility> abilities,
                                                 Map<Long, String> tagNameMap) {
        StringBuilder text = new StringBuilder();
        for (EmpAbility ability : abilities == null ? List.<EmpAbility>of() : abilities) {
            String name = ability.getAbilityName();
            if ((name == null || name.isBlank()) && ability.getTagId() != null && tagNameMap != null) {
                name = tagNameMap.get(ability.getTagId());
            }
            appendRecallTerm(text, "ability", name);
            if (ability.getTagId() != null && tagNameMap != null) {
                String tagName = tagNameMap.get(ability.getTagId());
                if (tagName != null && !tagName.isBlank() && !tagName.equalsIgnoreCase(name)) {
                    appendRecallTerm(text, "associated tag", tagName);
                }
            }
        }
        return text.toString().trim();
    }

    private String buildFormalPostRecallText(List<PostAbilityModel> requirements,
                                             Map<Long, String> tagNameMap) {
        StringBuilder text = new StringBuilder();
        for (PostAbilityModel requirement : requirements == null ? List.<PostAbilityModel>of() : requirements) {
            String name = requirement.getAbilityName();
            if ((name == null || name.isBlank()) && requirement.getTagId() != null && tagNameMap != null) {
                name = tagNameMap.get(requirement.getTagId());
            }
            appendRecallTerm(text, "ability", name);
            if (requirement.getTagId() != null && tagNameMap != null) {
                String tagName = tagNameMap.get(requirement.getTagId());
                if (tagName != null && !tagName.isBlank() && !tagName.equalsIgnoreCase(name)) {
                    appendRecallTerm(text, "associated tag", tagName);
                }
            }
        }
        return text.toString().trim();
    }

    private void appendRecallTerm(StringBuilder text, String type, String value) {
        if (value == null || value.isBlank()) return;
        if (!text.isEmpty()) text.append(". ");
        text.append(type).append(' ').append(value.trim());
    }

    /**
     * Atomically replaces the current vector using a stable primary key. Old records from the
     * previous random-ID implementation are removed only after the upsert succeeds.
     */
    private boolean upsertVector(Long refId, String type, String text, List<Float> vector) {
        try {
            long stableId = stableVectorId(refId, type);
            long nowMs = System.currentTimeMillis();

            List<UpsertParam.Field> fields = new ArrayList<>();
            fields.add(new UpsertParam.Field("id", Collections.singletonList(stableId)));
            fields.add(new UpsertParam.Field("type", Collections.singletonList(type)));
            fields.add(new UpsertParam.Field("ref_id", Collections.singletonList(refId)));
            fields.add(new UpsertParam.Field("text", Collections.singletonList(text)));
            fields.add(new UpsertParam.Field("vector", Collections.singletonList(vector)));
            fields.add(new UpsertParam.Field("create_time", Collections.singletonList(nowMs)));

            R<io.milvus.grpc.MutationResult> result = getMilvusClient().upsert(
                    UpsertParam.newBuilder().withCollectionName(getCollection()).withFields(fields).build());
            boolean succeeded = result.getStatus() == R.Status.Success.getCode() || result.getData() != null;
            if (succeeded) {
                getMilvusClient().delete(io.milvus.param.dml.DeleteParam.newBuilder()
                        .withCollectionName(getCollection())
                        .withExpr(vectorFilterExpression(refId, type, stableId))
                        .build());
            }
            return succeeded;
        } catch (Exception e) {
            log.error("Upsert vector failed: refId={}, type={}", refId, type, e);
            return false;
        }
    }

    private long stableVectorId(Long refId, String type) {
        long hash = 0xcbf29ce484222325L;
        String key = type + ':' + refId;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }

    private String vectorFilterExpression(Long refId, String type, long excludedId) {
        if (refId == null || (TYPE_EMPLOYEE.equals(type) == false && TYPE_POST.equals(type) == false
                && TYPE_TAG_CANDIDATE.equals(type) == false)) {
            throw new IllegalArgumentException("Unsupported vector filter");
        }
        return String.format(Locale.ROOT, "ref_id == %d && type == \"%s\" && id != %d", refId, type, excludedId);
    }

    /**
     * 判断异常是否为 collection 不存在
     */
    private boolean isCollectionNotFoundException(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("collection not found") || msg.contains("can't find collection"));
    }

    // ==================== 向量检索 ====================

    /**
     * 为岗位搜索最匹配的员工（type=EMPLOYEE）
     */
    public List<Map<String, Object>> searchEmployeesForPost(String postText, int topK) {
        List<Float> vector = vectorEmbeddingService.embed(postText);
        if (!isValidVector(vector)) {
            log.warn("岗位查询向量无效，跳过员工向量搜索。postText={}", postText);
            return Collections.emptyList();
        }
        return searchByType(vector, TYPE_EMPLOYEE, topK);
    }

    /**
     * 为员工搜索最匹配的岗位（type=POST）
     */
    public List<Map<String, Object>> searchPostsForEmployee(String empText, int topK) {
        List<Float> vector = vectorEmbeddingService.embed(empText);
        if (!isValidVector(vector)) {
            log.warn("员工查询向量无效，跳过岗位向量搜索。empText={}", empText);
            return Collections.emptyList();
        }
        return searchByType(vector, TYPE_POST, topK);
    }

    private static final int MAX_SEARCH_RETRIES = 3;

    /**
     * 按类型过滤搜索（带自动重试）
     */
    private List<Map<String, Object>> searchByType(List<Float> queryVector, String type, int topK) {
        if (getMilvusClient() == null) {
            log.debug("MilvusServiceClient 不可用，跳过向量搜索：type={}", type);
            return Collections.emptyList();
        }

        for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES; attempt++) {
            try {
                log.info("Milvus 向量搜索开始：type={}, topK={}, 向量维度={}, 第{}次尝试", type, topK,
                        queryVector != null ? queryVector.size() : "null", attempt);

                List<String> outFields = Arrays.asList("ref_id", "type", "text");
                String expr = "type == \"" + type + "\"";

                SearchParam param = SearchParam.newBuilder()
                        .withCollectionName(getCollection())
                        .withVectorFieldName("vector")
                        .withMetricType(MetricType.COSINE)
                        .withOutFields(outFields)
                        .withExpr(expr)
                        .withTopK(topK)
                        .withFloatVectors(Collections.singletonList(queryVector))
                        .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                        .build();

                R<SearchResults> result = getMilvusClient().search(param);
                if (result.getData() == null) {
                    log.warn("Milvus 搜索返回 null：type={}", type);
                    return Collections.emptyList();
                }

                SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
                List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

                log.info("Milvus 搜索返回 {} 条结果：type={}", scores.size(), type);

                return scores.stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    Object refId = s.get("ref_id");
                    double rawScore = s.getScore();
                    double finalScore = Math.round((1.0 - rawScore) * 10000.0) / 100.0;
                    m.put("refId", refId != null ? Long.parseLong(String.valueOf(refId)) : s.getLongID());
                    m.put("score", finalScore);
                    log.debug("Milvus 搜索结果：refId={}, rawDistance={}, finalScore={}", refId, rawScore, finalScore);
                    return m;
                }).collect(Collectors.toList());
            } catch (Exception e) {
                if (attempt < MAX_SEARCH_RETRIES && isTransientMilvusError(e)) {
                    log.warn("Milvus 搜索瞬时错误，第{}次重试：type={}, error={}", attempt, type, e.getMessage());
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("Milvus search failed after {} attempts: type={}", attempt, type, e);
                    return Collections.emptyList();
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * 判断是否为 Milvus 瞬时网络错误
     */
    private boolean isTransientMilvusError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("UNAVAILABLE") || msg.contains("Connection reset")
                || msg.contains("DEADLINE_EXCEEDED") || msg.contains("io exception");
    }

    // ==================== 删除 ====================

    /**
     * 删除指定实体的向量
     */
    public boolean deleteVector(Long refId, String type) {
        if (getMilvusClient() == null) {
            log.debug("MilvusServiceClient 不可用，跳过向量删除：refId={}, type={}", refId, type);
            return false;
        }
        try {
            getMilvusClient().delete(io.milvus.param.dml.DeleteParam.newBuilder()
                    .withCollectionName(getCollection())
                    .withExpr("ref_id == " + refId + " && type == \"" + type + "\"")
                    .build());
            return true;
        } catch (Exception e) {
            log.error("Delete vector failed: refId={}, type={}", refId, type, e);
            return false;
        }
    }

    // ==================== 文本构建 ====================

    private String buildEmployeeText(EmpEmployee emp, List<EmpAbility> abilities) {
        StringBuilder sb = new StringBuilder();
        sb.append(emp.getRealName()).append(" ");
        sb.append(emp.getLevel() != null ? emp.getLevel() : "").append(" ");
        Map<Long, String> tagNameMap = resolveTagNames(abilities == null ? List.of() : abilities.stream()
                .map(EmpAbility::getTagId).toList());
        for (EmpAbility a : abilities) {
            sb.append(tagNameMap.getOrDefault(a.getTagId(), "ability" + a.getTagId())).append(":");
            sb.append(a.getMasteryLevel()).append("级; ");
        }
        return sb.toString();
    }

    private String buildPostText(PostPost post, List<PostAbilityModel> requirements) {
        StringBuilder sb = new StringBuilder();
        sb.append(post.getPostName()).append(" ");
        sb.append(post.getJobDescription() != null ? post.getJobDescription() : "").append(" ");
        Map<Long, String> tagNameMap = resolveTagNames(requirements == null ? List.of() : requirements.stream()
                .map(PostAbilityModel::getTagId).filter(Objects::nonNull).toList());
        for (PostAbilityModel r : requirements) {
            sb.append(r.getAbilityName() != null ? r.getAbilityName()
                    : tagNameMap.getOrDefault(r.getTagId(), "ability" + r.getTagId())).append(":");
            sb.append("要求").append(r.getMinRequiredLevel()).append("级; ");
        }
        return sb.toString();
    }

    private Map<Long, String> resolveTagNames(Collection<Long> tagIds) {
        List<Long> distinctTagIds = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctTagIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (AbilityTag tag : abilityTagMapper.selectBatchIds(distinctTagIds)) {
            names.put(tag.getId(), tag.getTagName());
        }
        for (Long tagId : distinctTagIds) {
            names.putIfAbsent(tagId, "ability" + tagId);
        }
        return names;
    }
}
