package com.example.matching.config;

import com.example.matching.common.trace.TraceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Integer.MIN_VALUE)
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = null;
        if (request instanceof HttpServletRequest httpRequest) {
            traceId = httpRequest.getHeader(HEADER_TRACE_ID);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = "TRC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }
        TraceContext.set(traceId);
        try {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(HEADER_TRACE_ID, traceId);
            }
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    @Override
    public void destroy() {
    }
}
