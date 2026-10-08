package com.family195home.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 維運用的 /api/notifications/** 端點以與 app-service 相同的共用密鑰 Header {@code X-Internal-Token} 保護；
 * LINE Webhook 不經過本攔截器（由 line-bot-sdk 驗證 X-Line-Signature）。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    static final String HEADER_NAME = "X-Internal-Token";

    private final byte[] expectedToken;
    // 使用 Spring 設定的 ObjectMapper，ProblemDetail 的自訂屬性（code）才會攤平在 JSON 最外層
    private final ObjectMapper objectMapper;

    public InternalTokenInterceptor(@Value("${app.internal.token}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader(HEADER_NAME);
        // 常數時間比較，避免以回應時間差推測密鑰
        if (header != null && MessageDigest.isEqual(expectedToken, header.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "缺少或錯誤的 X-Internal-Token");
        problem.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problem.setProperty("code", "UNAUTHORIZED");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
        return false;
    }
}
