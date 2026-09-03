package com.example.matching.service.matching;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingProfileTextBuilderTest {

    private MatchingProfileTextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MatchingProfileTextBuilder(new ObjectMapper());
    }

    @Test
    void buildEmployeeProfileText_includesYearsProjectsAndEvidence() {
        EmpEmployee employee = new EmpEmployee();
        employee.setRealName("Alice");
        employee.setLevel("P6");
        employee.setEntryDate(LocalDate.now().minusYears(4));
        employee.setExtendFields("{\"education\":\"本科\",\"major\":\"计算机科学\"}");

        EmpAbility javaAbility = new EmpAbility();
        javaAbility.setTagId(10L);
        javaAbility.setMasteryLevel(5);
        javaAbility.setEvaluationSource("MANUAL");
        javaAbility.setSourceWeight(new BigDecimal("0.90"));

        EmpAbility springAbility = new EmpAbility();
        springAbility.setTagId(11L);
        springAbility.setMasteryLevel(4);
        springAbility.setEvaluationSource("AI_ASSESSMENT");
        springAbility.setSourceWeight(new BigDecimal("0.75"));

        Map<String, Object> resumeBasicInfo = Map.of(
                "yearsOfExperience", 6,
                "projects", List.of(
                        Map.of("name", "Payment Platform", "role", "Tech Lead", "description", "Built payment settlement services"),
                        Map.of("name", "Search Center", "techStack", "Java, Spring Cloud, Elasticsearch")
                )
        );

        String text = builder.buildEmployeeProfileText(
                employee,
                List.of(javaAbility, springAbility),
                Map.of(10L, "Java", 11L, "Spring Cloud"),
                resumeBasicInfo
        );

        assertThat(text).contains("Alice");
        assertThat(text).contains("P6");
        assertThat(text).contains("6 years experience");
        assertThat(text).contains("education 本科");
        assertThat(text).contains("major 计算机科学");
        assertThat(text).contains("project Payment Platform");
        assertThat(text).contains("role Tech Lead");
        assertThat(text).contains("Java level 5");
        assertThat(text).contains("source MANUAL");
        assertThat(text).contains("Spring Cloud level 4");
    }

    @Test
    void buildPostProfileText_includesCoreRequiredWeightAndKeywords() {
        PostPost post = new PostPost();
        post.setPostName("Senior Java Engineer");
        post.setPostLevel("P6-P7");
        post.setJobDescription("Responsible for architecture design and platform delivery");
        post.setExtendFields("{\"businessDomain\":\"Payment\",\"keywords\":[\"distributed\",\"high concurrency\"]}");

        PostAbilityModel javaRequirement = new PostAbilityModel();
        javaRequirement.setTagId(10L);
        javaRequirement.setMinRequiredLevel(5);
        javaRequirement.setWeight(new BigDecimal("35"));
        javaRequirement.setIsCore(1);
        javaRequirement.setIsRequired(1);

        PostAbilityModel designRequirement = new PostAbilityModel();
        designRequirement.setTagId(11L);
        designRequirement.setMinRequiredLevel(4);
        designRequirement.setWeight(new BigDecimal("25"));
        designRequirement.setIsCore(0);
        designRequirement.setIsRequired(1);

        String text = builder.buildPostProfileText(
                post,
                List.of(javaRequirement, designRequirement),
                Map.of(10L, "Java", 11L, "System Design")
        );

        assertThat(text).contains("Senior Java Engineer");
        assertThat(text).contains("P6-P7");
        assertThat(text).contains("business domain Payment");
        assertThat(text).contains("keyword distributed");
        assertThat(text).contains("keyword high concurrency");
        assertThat(text).contains("Java required level 5");
        assertThat(text).contains("core ability");
        assertThat(text).contains("required ability");
        assertThat(text).contains("weight 35");
        assertThat(text).contains("System Design required level 4");
    }

    @Test
    void formalRecallTextUsesOnlyFormalAbilitiesAndOptionalAssociatedTags() {
        EmpAbility ability = new EmpAbility();
        ability.setAbilityName("Redis");
        ability.setTagId(10L);

        String employeeText = builder.buildFormalEmployeeAbilityRecallText(
                List.of(ability), Map.of(10L, "缓存中间件"));

        assertThat(employeeText).contains("ability Redis");
        assertThat(employeeText).contains("associated tag 缓存中间件");
        assertThat(employeeText).doesNotContain("employee");
        assertThat(employeeText).doesNotContain("project");

        PostAbilityModel requirement = new PostAbilityModel();
        requirement.setAbilityName("缓存中间件设计");
        requirement.setTagId(null);
        String postText = builder.buildFormalPostAbilityRecallText(
                List.of(requirement), Map.of());

        assertThat(postText).contains("ability 缓存中间件设计");
        assertThat(postText).doesNotContain("description");
    }
}
