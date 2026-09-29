package com.family195home.notification.client;

/** 呼叫 app-service 內部 API 收到非 2xx 回應時拋出，攜帶 HTTP 狀態碼與錯誤代碼供上層決定回覆內容。 */
public class AppServiceClientException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public AppServiceClientException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
