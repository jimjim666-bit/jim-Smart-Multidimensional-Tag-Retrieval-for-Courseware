package com.taglib.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求生成 requestId：写入 request attribute、响应头 X-Request-Id 和 MDC，便于全链路排查。
 * 客户端传入合法 X-Request-Id 时沿用，否则重新生成。
 */
@Slf4j
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = "requestId";
    public static final String HEADER = "X-Request-Id";

    private static final String VALID_REQUEST_ID = "^[A-Za-z0-9-]{1,64}$";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !requestId.matches(VALID_REQUEST_ID)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(ATTRIBUTE, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(ATTRIBUTE);
        }
    }
}
