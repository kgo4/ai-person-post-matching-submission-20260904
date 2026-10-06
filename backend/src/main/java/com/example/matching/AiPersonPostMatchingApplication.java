package com.example.matching;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI人岗自动匹配管理系统 - 启动类
 */ 
@SpringBootApplication
@MapperScan({"com.example.matching.mapper", "com.example.matching.ai.context.mapper"})
@EnableAsync
@EnableScheduling
public class AiPersonPostMatchingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPersonPostMatchingApplication.class, args);
    }
}
