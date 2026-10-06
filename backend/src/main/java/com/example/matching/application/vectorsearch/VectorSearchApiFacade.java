package com.example.matching.application.vectorsearch;

import com.example.matching.port.vectorsearch.VectorSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VectorSearchApiFacade {

    private final VectorSearchPort vectorSearchPort;

    public List<Map<String, Object>> searchEmployeesForPost(String postText, int topK) {
        return vectorSearchPort.searchEmployeesForPost(postText, topK);
    }

    public List<Map<String, Object>> searchPostsForEmployee(String empText, int topK) {
        return vectorSearchPort.searchPostsForEmployee(empText, topK);
    }
}
