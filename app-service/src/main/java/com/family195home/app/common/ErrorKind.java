package com.family195home.app.common;

/**
 * 業務錯誤分類：與傳輸層（HTTP）無關，由 {@link GlobalExceptionHandler} 對應成狀態碼。
 */
public enum ErrorKind {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    GONE,
    INTERNAL
}
