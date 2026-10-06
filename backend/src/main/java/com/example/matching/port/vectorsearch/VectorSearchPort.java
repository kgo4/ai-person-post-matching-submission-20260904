package com.example.matching.port.vectorsearch;

import java.util.List;
import java.util.Map;

public interface VectorSearchPort {

    List<Map<String, Object>> searchEmployeesForPost(String postText, int topK);

    List<Map<String, Object>> searchPostsForEmployee(String empText, int topK);
}
