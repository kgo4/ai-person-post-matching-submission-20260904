package com.example.matching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：/uploads/** -> 本地 uploads 目录。
 * <p>
 * 用于通过 URL 访问本地上传的文件（简历、资源封面、面试录像等）。
 * 目录相对应用运行目录（默认项目根），上传文件统一落盘在 uploads/ 下。
 */
@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get("uploads").toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
