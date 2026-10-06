package com.example.matching.service.common;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BusinessCodeGenerator {

    public String nextEmployeeCode() {
        return next("EMP");
    }

    public String nextPostCode() {
        return next("POST");
    }

    public String nextTemplateCode() {
        return next("TPL");
    }

    public String nextAbilityTagCode() {
        return next("TAG");
    }

    private String next(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
