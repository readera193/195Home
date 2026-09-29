package com.family195home.app.common;

import org.springframework.http.HttpStatus;

/**
 * 跨 family/expense/statistics 模組共用的業務例外：帶有 HTTP 狀態碼與穩定的錯誤代碼
 * （對應 contracts/app-service.md 列出的各端點 Errors，例如 GROUP_NAME_TAKEN）。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
