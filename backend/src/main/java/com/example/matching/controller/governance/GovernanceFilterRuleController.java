package com.example.matching.controller.governance;

import com.example.matching.common.result.R;
import com.example.matching.entity.governance.GovernanceFilterRule;
import com.example.matching.service.governance.GovernanceFilterRuleService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/governance/filter-rules")
@RequiredArgsConstructor
public class GovernanceFilterRuleController {
    private final GovernanceFilterRuleService service;

    @GetMapping
    public R<List<GovernanceFilterRule>> list(@RequestParam(required = false) String scope,
                                               @RequestParam(required = false) String reviewStatus) {
        return R.ok(service.list(scope, reviewStatus));
    }

    @GetMapping("/samples")
    public R<Map<String, Object>> samples(@RequestParam String scope,
                                          @RequestParam(defaultValue = "30") int limit) {
        List<String> samples = service.sampleTexts(scope, limit);
        return R.ok(Map.of("scope", scope, "count", samples.size(), "samples", samples));
    }

    @PostMapping
    public R<GovernanceFilterRule> save(@RequestBody GovernanceFilterRule rule) {
        return R.ok(service.save(rule, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public R<GovernanceFilterRule> update(@PathVariable Long id, @RequestBody GovernanceFilterRule rule) {
        rule.setId(id);
        return R.ok(service.save(rule, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteCustom(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @PostMapping("/suggestions/generate")
    public R<Map<String, Object>> generateSuggestions(@RequestBody Map<String, Object> body) {
        String scope = String.valueOf(body.getOrDefault("scope", GovernanceFilterRuleService.POST_JD));
        @SuppressWarnings("unchecked")
        List<String> samples = (List<String>) body.getOrDefault("samples", List.of());
        service.generateAiSuggestions(scope, samples, SecurityUtils.getCurrentUserId());
        return R.ok(Map.of("accepted", true, "message", "AI规则建议已提交后台生成"));
    }

    @PostMapping("/{id}/review")
    public R<Void> reviewSuggestion(@PathVariable Long id, @RequestParam boolean approve) {
        service.reviewSuggestion(id, approve, SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
