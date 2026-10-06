package com.example.matching.infrastructure.persistence;

import com.example.matching.port.vectorsearch.VectorSearchPort;
import com.example.matching.vector.MilvusVectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VectorSearchPortAdapter implements VectorSearchPort {

    private final MilvusVectorService milvusVectorService;

    @Override
    public List<Map<String, Object>> searchEmployeesForPost(String postText, int topK) {
        return milvusVectorService.searchEmployeesForPost(postText, topK);
    }

    @Override
    public List<Map<String, Object>> searchPostsForEmployee(String empText, int topK) {
        return milvusVectorService.searchPostsForEmployee(empText, topK);
    }
}
