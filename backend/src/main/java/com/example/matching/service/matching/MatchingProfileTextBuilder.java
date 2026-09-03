package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Shared profile text builder for recommendation recall and vector persistence.
 * Keeps employee/post semantic text consistent across Milvus search entrypoints.
 */
@Component
public class MatchingProfileTextBuilder {

    private final ObjectMapper objectMapper;

    public MatchingProfileTextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildEmployeeProfileText(
            EmpEmployee employee,
            List<EmpAbility> abilities,
            Map<Long, String> tagNameMap,
            Map<String, Object> resumeBasicInfo
    ) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> extendFields = parseJsonMap(employee != null ? employee.getExtendFields() : null);

        append(sb, "employee", employee != null ? employee.getRealName() : null);
        append(sb, "level", employee != null ? employee.getLevel() : null);

        Integer yearsOfExperience = resolveYearsOfExperience(employee, resumeBasicInfo, extendFields);
        if (yearsOfExperience != null && yearsOfExperience > 0) {
            append(sb, "experience", yearsOfExperience + " years experience");
        }

        append(sb, "education", firstNonBlank(
                stringValue(resumeBasicInfo, "education"),
                stringValue(extendFields, "education")
        ));
        append(sb, "major", firstNonBlank(
                stringValue(resumeBasicInfo, "major"),
                stringValue(extendFields, "major")
        ));

        appendProjectFragments(sb, resumeBasicInfo, extendFields);

        for (EmpAbility ability : abilities == null ? List.<EmpAbility>of() : abilities) {
            String tagName = firstNonBlank(ability.getAbilityName(), tagNameMap.get(ability.getTagId()), "ability");
            StringBuilder abilitySegment = new StringBuilder();
            abilitySegment.append(tagName).append(" level ").append(defaultInt(ability.getMasteryLevel(), 0));
            if (notBlank(ability.getEvaluationSource())) {
                abilitySegment.append(" source ").append(normalizeSource(ability.getEvaluationSource()));
            }
            if (ability.getSourceWeight() != null) {
                abilitySegment.append(" credibility ").append(ability.getSourceWeight().stripTrailingZeros().toPlainString());
            }
            appendRaw(sb, abilitySegment.toString());
        }

        return sb.toString().trim();
    }

    /**
     * V2 candidate recall text. Only formal employee abilities participate;
     * resume/profile fields are deliberately excluded. Associated tags are
     * optional enrichment and never replace an ability name.
     */
    public String buildFormalEmployeeAbilityRecallText(List<EmpAbility> abilities,
                                                       Map<Long, String> tagNameMap) {
        StringBuilder sb = new StringBuilder();
        for (EmpAbility ability : abilities == null ? List.<EmpAbility>of() : abilities) {
            String name = firstNonBlank(ability.getAbilityName(),
                    tagNameMap == null ? null : tagNameMap.get(ability.getTagId()));
            if (notBlank(name)) appendRaw(sb, "ability " + name.trim());
            if (ability.getTagId() != null && tagNameMap != null) {
                String tagName = tagNameMap.get(ability.getTagId());
                if (notBlank(tagName) && !tagName.equalsIgnoreCase(name)) {
                    appendRaw(sb, "associated tag " + tagName.trim());
                }
            }
        }
        return sb.toString().trim();
    }

    public String buildPostProfileText(
            PostPost post,
            List<PostAbilityModel> requirements,
            Map<Long, String> tagNameMap
    ) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> extendFields = parseJsonMap(post != null ? post.getExtendFields() : null);

        append(sb, "post", post != null ? post.getPostName() : null);
        append(sb, "level", post != null ? post.getPostLevel() : null);
        append(sb, "description", post != null ? post.getJobDescription() : null);
        append(sb, "business domain", stringValue(extendFields, "businessDomain"));
        append(sb, "scenario", firstNonBlank(stringValue(extendFields, "scenario"), stringValue(extendFields, "businessScenario")));

        for (String keyword : extractKeywordList(extendFields)) {
            append(sb, "keyword", keyword);
        }

        for (PostAbilityModel requirement : requirements == null ? List.<PostAbilityModel>of() : requirements) {
            String tagName = firstNonBlank(requirement.getAbilityName(),
                    tagNameMap.get(requirement.getTagId()), "ability");
            StringBuilder requirementSegment = new StringBuilder();
            requirementSegment.append(tagName)
                    .append(" required level ")
                    .append(defaultInt(requirement.getMinRequiredLevel(), 0));
            if (requirement.getIsCore() != null && requirement.getIsCore() == 1) {
                requirementSegment.append(" core ability");
            }
            if (requirement.getIsRequired() != null && requirement.getIsRequired() == 1) {
                requirementSegment.append(" required ability");
            }
            if (requirement.getWeight() != null) {
                requirementSegment.append(" weight ")
                        .append(requirement.getWeight().stripTrailingZeros().toPlainString());
            }
            appendRaw(sb, requirementSegment.toString());
        }

        return sb.toString().trim();
    }

    /** V2 candidate recall text using only the formal post ability model. */
    public String buildFormalPostAbilityRecallText(List<PostAbilityModel> requirements,
                                                    Map<Long, String> tagNameMap) {
        StringBuilder sb = new StringBuilder();
        for (PostAbilityModel requirement : requirements == null ? List.<PostAbilityModel>of() : requirements) {
            String name = firstNonBlank(requirement.getAbilityName(),
                    requirement.getTagId() == null || tagNameMap == null ? null : tagNameMap.get(requirement.getTagId()));
            if (notBlank(name)) appendRaw(sb, "ability " + name.trim());
            if (requirement.getTagId() != null && tagNameMap != null) {
                String tagName = tagNameMap.get(requirement.getTagId());
                if (notBlank(tagName) && !tagName.equalsIgnoreCase(name)) {
                    appendRaw(sb, "associated tag " + tagName.trim());
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * M-12：基于岗位匹配画像构建召回文本（不接触 Entity）
     */
    public String buildPostProfileText(
            MatchingPostProfile post,
            Map<Long, String> tagNameMap
    ) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> extendFields = parseJsonMap(post != null ? post.extendFields() : null);

        append(sb, "post", post != null ? post.postName() : null);
        append(sb, "level", post != null ? post.postLevel() : null);
        append(sb, "description", post != null ? post.jobDescription() : null);
        append(sb, "business domain", stringValue(extendFields, "businessDomain"));
        append(sb, "scenario", firstNonBlank(stringValue(extendFields, "scenario"), stringValue(extendFields, "businessScenario")));

        for (String keyword : extractKeywordList(extendFields)) {
            append(sb, "keyword", keyword);
        }

        List<MatchingRequirementSnapshot> requirements = post != null && post.requirements() != null
                ? post.requirements() : List.of();
        for (MatchingRequirementSnapshot requirement : requirements) {
            String tagName = firstNonBlank(requirement.abilityName(), tagNameMap.get(requirement.tagId()), "ability");
            StringBuilder requirementSegment = new StringBuilder();
            requirementSegment.append(tagName)
                    .append(" required level ")
                    .append(defaultInt(requirement.minRequiredLevel(), 0));
            if (requirement.isCore() != null && requirement.isCore() == 1) {
                requirementSegment.append(" core ability");
            }
            if (requirement.isRequired() != null && requirement.isRequired() == 1) {
                requirementSegment.append(" required ability");
            }
            if (requirement.weight() != null) {
                requirementSegment.append(" weight ")
                        .append(requirement.weight().stripTrailingZeros().toPlainString());
            }
            appendRaw(sb, requirementSegment.toString());
        }

        return sb.toString().trim();
    }

    /** V2 recall text for an already assembled formal post snapshot. */
    public String buildFormalPostAbilityRecallText(MatchingPostProfile post,
                                                    Map<Long, String> tagNameMap) {
        StringBuilder sb = new StringBuilder();
        List<MatchingRequirementSnapshot> requirements = post != null && post.requirements() != null
                ? post.requirements() : List.of();
        for (MatchingRequirementSnapshot requirement : requirements) {
            String name = firstNonBlank(requirement.abilityName(),
                    requirement.tagId() == null || tagNameMap == null ? null : tagNameMap.get(requirement.tagId()));
            if (notBlank(name)) appendRaw(sb, "ability " + name.trim());
            if (requirement.tagId() != null && tagNameMap != null) {
                String tagName = tagNameMap.get(requirement.tagId());
                if (notBlank(tagName) && !tagName.equalsIgnoreCase(name)) {
                    appendRaw(sb, "associated tag " + tagName.trim());
                }
            }
        }
        return sb.toString().trim();
    }

    private void appendProjectFragments(StringBuilder sb, Map<String, Object> resumeBasicInfo, Map<String, Object> extendFields) {
        List<?> projects = firstProjectList(resumeBasicInfo, extendFields);
        if (projects == null || projects.isEmpty()) {
            return;
        }
        int limit = Math.min(projects.size(), 2);
        for (int i = 0; i < limit; i += 1) {
            Object project = projects.get(i);
            if (!(project instanceof Map<?, ?> map)) {
                append(sb, "project", String.valueOf(project));
                continue;
            }
            append(sb, "project", firstNonBlank(
                    stringValue(map, "name"),
                    stringValue(map, "projectName"),
                    stringValue(map, "title")
            ));
            append(sb, "role", firstNonBlank(
                    stringValue(map, "role"),
                    stringValue(map, "responsibility")
            ));
            append(sb, "project detail", firstNonBlank(
                    stringValue(map, "description"),
                    stringValue(map, "summary"),
                    stringValue(map, "techStack")
            ));
        }
    }

    private List<?> firstProjectList(Map<String, Object> resumeBasicInfo, Map<String, Object> extendFields) {
        Object fromResume = firstNonNull(
                objectValue(resumeBasicInfo, "projects"),
                objectValue(resumeBasicInfo, "projectExperiences"),
                objectValue(resumeBasicInfo, "projectList")
        );
        if (fromResume instanceof List<?> list) {
            return list;
        }
        Object fromExtend = firstNonNull(
                objectValue(extendFields, "projects"),
                objectValue(extendFields, "projectExperiences"),
                objectValue(extendFields, "projectList")
        );
        if (fromExtend instanceof List<?> list) {
            return list;
        }
        return Collections.emptyList();
    }

    private List<String> extractKeywordList(Map<String, Object> extendFields) {
        Object raw = firstNonNull(
                objectValue(extendFields, "keywords"),
                objectValue(extendFields, "tags"),
                objectValue(extendFields, "highlights")
        );
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        if (raw instanceof String str && !str.isBlank()) {
            return List.of(str.trim());
        }
        return List.of();
    }

    private Integer resolveYearsOfExperience(
            EmpEmployee employee,
            Map<String, Object> resumeBasicInfo,
            Map<String, Object> extendFields
    ) {
        Integer fromResume = firstPositiveInt(
                objectValue(resumeBasicInfo, "yearsOfExperience"),
                objectValue(resumeBasicInfo, "workYears"),
                objectValue(resumeBasicInfo, "experienceYears")
        );
        if (fromResume != null) {
            return fromResume;
        }
        Integer fromExtend = firstPositiveInt(
                objectValue(extendFields, "yearsOfExperience"),
                objectValue(extendFields, "workYears"),
                objectValue(extendFields, "experienceYears")
        );
        if (fromExtend != null) {
            return fromExtend;
        }
        if (employee != null && employee.getEntryDate() != null) {
            long years = ChronoUnit.YEARS.between(employee.getEntryDate(), LocalDate.now());
            return (int) Math.max(years, 0);
        }
        return null;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private void append(StringBuilder sb, String label, String value) {
        if (notBlank(value)) {
            appendRaw(sb, label + " " + value.trim());
        }
    }

    private void appendRaw(StringBuilder sb, String fragment) {
        if (!notBlank(fragment)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(". ");
        }
        sb.append(fragment.trim());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Object objectValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        return map.get(key);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstPositiveInt(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Integer parsed = toInteger(value);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeSource(String source) {
        return Objects.requireNonNullElse(source, "").trim().toUpperCase(Locale.ROOT);
    }
}
