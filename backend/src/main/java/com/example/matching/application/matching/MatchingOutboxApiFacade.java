package com.example.matching.application.matching;

import com.example.matching.service.matching.MatchingTaskOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingOutboxApiFacade {

    private final MatchingTaskOutboxDispatcher dispatcher;

    public Map<String, Long> statusSummary() {
        return dispatcher.statusSummary();
    }

    public Boolean replay(Long id) {
        return dispatcher.replay(id);
    }
}
