package com.example.matching.service.matching.algorithm;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.entity.employee.EmpEmployee;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HardConditionChecker {

    private static final Pattern FORMATTED_NUMBER = Pattern.compile(
            "^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([^\\d\\s].*)?\\s*$");

    private final ObjectMapper objectMapper;

    @Autowired
    public HardConditionChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HardConditionResult checkHardConditions(EmpEmployee emp, List<HardCondition> conditions) {
        return checkHardConditions(emp, conditions, null);
    }

    public HardConditionResult checkHardConditions(EmpEmployee emp, List<HardCondition> conditions,
                                                     Map<String, Object> resumeBasicInfo) {
        HardConditionResult result = new HardConditionResult();
        if (conditions == null || conditions.isEmpty()) {
            result.setPassed(true);
            result.setDetails(List.of());
            return result;
        }

        Map<String, Object> extendFields = parseExtendFields(emp.getExtendFields());

        List<ConditionDetail> details = new ArrayList<>();
        boolean allPassed = true;

        for (HardCondition cond : conditions) {
            ConditionDetail detail = new ConditionDetail();
            detail.setField(cond.getField());
            detail.setLabel(cond.getLabel() != null ? cond.getLabel() : cond.getField());
            detail.setOperator(cond.getOperator());
            detail.setExpectedValue(cond.getValue());

            String actualValue = getFieldValue(emp, extendFields, cond.getField(), resumeBasicInfo);
            detail.setActualValue(actualValue);

            boolean passed = evaluateCondition(actualValue, cond);
            detail.setPassed(passed);
            detail.setSource(getFieldValueSource(emp, extendFields, cond.getField(), resumeBasicInfo));
            details.add(detail);

            if (!passed) {
                allPassed = false;
            }
        }

        result.setPassed(allPassed);
        result.setDetails(details);
        return result;
    }

    private String getFieldValue(EmpEmployee emp, Map<String, Object> extendFields,
                                  String field, Map<String, Object> resumeBasicInfo) {
        if (field == null) return null;

        String value = switch (field) {
            case "education" -> getExtendField(extendFields, "education");
            case "gender" -> emp.getGender() != null ? String.valueOf(emp.getGender()) : null;
            default -> getExtendField(extendFields, field);
        };

        if ((value == null || value.isBlank()) && resumeBasicInfo != null) {
            value = getResumeField(resumeBasicInfo, field);
        }

        return value;
    }

    private String getFieldValueSource(EmpEmployee emp, Map<String, Object> extendFields,
                                        String field, Map<String, Object> resumeBasicInfo) {
        if (field == null) return "未知";
        String staticValue = switch (field) {
            case "education" -> getExtendField(extendFields, "education");
            case "gender" -> emp.getGender() != null ? String.valueOf(emp.getGender()) : null;
            default -> getExtendField(extendFields, field);
        };
        if (staticValue != null && !staticValue.isBlank()) return "员工档案";
        if (resumeBasicInfo != null && resumeBasicInfo.get(field) != null) return "简历解析";
        return "未填写";
    }

    private String getResumeField(Map<String, Object> resumeBasicInfo, String field) {
        if (resumeBasicInfo == null) return null;
        Object val = resumeBasicInfo.get(field);
        if (val == null) return null;
        return val.toString();
    }

    private String getExtendField(Map<String, Object> extendFields, String key) {
        if (extendFields == null) return null;
        Object val = extendFields.get(key);
        return val != null ? val.toString() : null;
    }

    private Map<String, Object> parseExtendFields(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    boolean evaluateCondition(String actual, HardCondition condition) {
        String operator = condition != null ? condition.getOperator() : null;
        String expected = condition != null ? condition.getValue() : null;
        if (expected == null || expected.isBlank()) return true;

        String normalizedOperator = (operator == null || operator.isBlank() ? "eq" : operator)
                .trim().toLowerCase(Locale.ROOT);
        String normalizedExpected = expected.trim();

        if (condition != null && "rank".equalsIgnoreCase(condition.getFieldType())) {
            return evaluateRankCondition(actual, normalizedOperator, normalizedExpected, condition.getValueRankJson());
        }

        if ("exists".equals(normalizedOperator)) {
            return actual != null && !actual.isBlank();
        }
        if ("notexists".equals(normalizedOperator)) {
            return actual == null || actual.isBlank();
        }
        if ("in".equals(normalizedOperator)) {
            if (actual == null) return false;
            return splitExpectedValues(normalizedExpected).contains(actual.trim().toLowerCase(Locale.ROOT));
        }
        if ("notin".equals(normalizedOperator)) {
            if (actual == null) return true;
            return !splitExpectedValues(normalizedExpected).contains(actual.trim().toLowerCase(Locale.ROOT));
        }
        if ("contains".equals(normalizedOperator)) {
            return actual != null && actual.toLowerCase(Locale.ROOT).contains(normalizedExpected.toLowerCase(Locale.ROOT));
        }
        if ("notcontains".equals(normalizedOperator)) {
            return actual == null || !actual.toLowerCase(Locale.ROOT).contains(normalizedExpected.toLowerCase(Locale.ROOT));
        }

        try {
            double actNum = Double.parseDouble(actual);
            double expNum = Double.parseDouble(normalizedExpected);
            return switch (normalizedOperator) {
                case "gte" -> actNum >= expNum;
                case "lte" -> actNum <= expNum;
                case "gt" -> actNum > expNum;
                case "lt" -> actNum < expNum;
                case "eq" -> Math.abs(actNum - expNum) < 0.001;
                case "neq" -> Math.abs(actNum - expNum) >= 0.001;
                default -> true;
            };
        } catch (NumberFormatException | NullPointerException e) {
            if (actual == null) return "neq".equals(normalizedOperator);
            Integer formattedNumberComparison = compareFormattedNumbers(actual, normalizedExpected);
            if (formattedNumberComparison != null) {
                return switch (normalizedOperator) {
                    case "gte" -> formattedNumberComparison >= 0;
                    case "lte" -> formattedNumberComparison <= 0;
                    case "gt" -> formattedNumberComparison > 0;
                    case "lt" -> formattedNumberComparison < 0;
                    case "eq" -> formattedNumberComparison == 0;
                    case "neq" -> formattedNumberComparison != 0;
                    default -> true;
                };
            }
            return switch (normalizedOperator) {
                case "eq" -> actual.trim().equalsIgnoreCase(normalizedExpected);
                case "neq" -> !actual.trim().equalsIgnoreCase(normalizedExpected);
                case "gte" -> actual.compareTo(normalizedExpected) >= 0;
                case "lte" -> actual.compareTo(normalizedExpected) <= 0;
                case "gt" -> actual.compareTo(normalizedExpected) > 0;
                case "lt" -> actual.compareTo(normalizedExpected) < 0;
                default -> true;
            };
        }
    }

    private Integer compareFormattedNumbers(String actual, String expected) {
        Matcher actualMatcher = FORMATTED_NUMBER.matcher(actual);
        Matcher expectedMatcher = FORMATTED_NUMBER.matcher(expected);
        if (!actualMatcher.matches() || !expectedMatcher.matches()) {
            return null;
        }
        if (!normalizeUnit(actualMatcher.group(2)).equals(normalizeUnit(expectedMatcher.group(2)))) {
            return null;
        }
        return new BigDecimal(actualMatcher.group(1)).compareTo(new BigDecimal(expectedMatcher.group(1)));
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
    }

    private boolean evaluateRankCondition(String actual, String operator, String expected, String valueRankJson) {
        Map<String, Integer> ranks = parseRankMap(valueRankJson);
        if (ranks.isEmpty()) {
            return evaluateTextCondition(actual, operator, expected);
        }
        Integer actualRank = actual != null ? ranks.get(actual.trim()) : null;
        Integer expectedRank = ranks.get(expected.trim());
        if (expectedRank == null) return true;
        if (actualRank == null) {
            return "neq".equals(operator) || "notin".equals(operator);
        }
        return switch (operator) {
            case "gte" -> actualRank >= expectedRank;
            case "lte" -> actualRank <= expectedRank;
            case "gt" -> actualRank > expectedRank;
            case "lt" -> actualRank < expectedRank;
            case "neq" -> !actualRank.equals(expectedRank);
            case "in" -> splitExpectedValues(expected).stream()
                    .map(ranks::get)
                    .filter(Objects::nonNull)
                    .anyMatch(rank -> rank.equals(actualRank));
            case "notin" -> splitExpectedValues(expected).stream()
                    .map(ranks::get)
                    .filter(Objects::nonNull)
                    .noneMatch(rank -> rank.equals(actualRank));
            default -> actualRank.equals(expectedRank);
        };
    }

    private Map<String, Integer> parseRankMap(String valueRankJson) {
        if (valueRankJson == null || valueRankJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(valueRankJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private boolean evaluateTextCondition(String actual, String operator, String expected) {
        if (actual == null) return "neq".equals(operator);
        return switch (operator) {
            case "eq" -> actual.trim().equalsIgnoreCase(expected);
            case "neq" -> !actual.trim().equalsIgnoreCase(expected);
            case "gte" -> actual.compareTo(expected) >= 0;
            case "lte" -> actual.compareTo(expected) <= 0;
            case "gt" -> actual.compareTo(expected) > 0;
            case "lt" -> actual.compareTo(expected) < 0;
            default -> true;
        };
    }

    private Set<String> splitExpectedValues(String expected) {
        Set<String> values = new HashSet<>();
        for (String item : expected.split(",")) {
            if (item != null && !item.isBlank()) {
                values.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }
}
