package com.family195home.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * app-service 錯誤回應（RFC 9457 Problem Details）中本服務需要的欄位：
 * {@code code} 為穩定的錯誤代碼，{@code detail} 為錯誤說明；其餘欄位（type、title、status、instance…）忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorBody(String code, String detail) {
}
