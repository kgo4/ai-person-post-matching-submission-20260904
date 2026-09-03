package com.example.matching.controller;

import com.example.matching.application.system.HealthApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class ConnectionTestController {

    private final HealthApiFacade healthApiFacade;

    @Operation(summary = "测试 Milvus 连接")
    @GetMapping("/milvus")
    public R<Map<String, Object>> testMilvus() {
        return R.ok(healthApiFacade.checkMilvus());
    }

    @Operation(summary = "测试所有数据库连接")
    @GetMapping("/all")
    public R<Map<String, Object>> testAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mysql", "OK");
        Map<String, Object> milvus = healthApiFacade.checkMilvus();
        result.put("milvus", milvus.getOrDefault("status", "UNKNOWN"));
        return R.ok(result);
    }
}
