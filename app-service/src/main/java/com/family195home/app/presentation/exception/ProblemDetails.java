package com.family195home.app.presentation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * 統一建立 RFC 9457（原 RFC 7807）Problem Details 回應本體。
 * {@code code} 為穩定的機器可讀錯誤代碼（對應 contracts/app-service.md 各端點 Errors），
 * {@code detail} 為給使用者看的訊息。
 */
public final class ProblemDetails {

    public static final String CODE_PROPERTY = "code";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }
}
