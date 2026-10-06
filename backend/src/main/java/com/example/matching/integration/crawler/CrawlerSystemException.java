package com.example.matching.integration.crawler;

/**
 * 爬虫系统调用异常。
 * <p>
 * 由接口层统一翻译为 503，表示「爬虫系统未接入或不可用」，与业务失败区分开。
 */
public class CrawlerSystemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CrawlerSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
