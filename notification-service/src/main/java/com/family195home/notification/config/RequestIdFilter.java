package com.family195home.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 為每個請求指定 requestId：沿用呼叫端帶來的 {@code X-Request-Id}（例如 app-service 之間的呼叫）， 沒有或格式不合法就自行產生。寫入 MDC
 * 讓同一請求的所有日誌可用 {@code %X{requestId}} 串起來，並回傳在回應標頭， 使用者回報問題時可憑此查日誌。最先執行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // 限制字元與長度，避免呼叫端塞入換行等內容造成日誌注入
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String requestId =
                incoming != null && VALID.matcher(incoming).matches()
                        ? incoming
                        : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
