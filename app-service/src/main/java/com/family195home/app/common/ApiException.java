package com.family195home.app.common;

/**
 * 跨 family/expense/statistics 模組共用的業務例外：帶有錯誤分類與穩定的錯誤代碼
 * （對應 contracts/app-service.md 列出的各端點 Errors，例如 GROUP_NAME_TAKEN）。
 */
public class ApiException extends RuntimeException {

    private final ErrorKind kind;
    private final String code;

    public ApiException(ErrorKind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public ErrorKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }
}
