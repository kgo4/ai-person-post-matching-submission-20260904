package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingOutboxApiFacade;
import com.example.matching.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/matching/outbox")
@RequiredArgsConstructor
public class MatchingOutboxController {

    private final MatchingOutboxApiFacade matchingOutboxApiFacade;

    @GetMapping("/summary")
    public R<Map<String, Long>> summary() {
        return R.ok(matchingOutboxApiFacade.statusSummary());
    }

    @PostMapping("/{id}/replay")
    public R<Boolean> replay(@PathVariable Long id) {
        return R.ok(matchingOutboxApiFacade.replay(id));
    }
}
