package com.family195home.notification.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** app-service 以 Problem Details 回應錯誤，確認 client 能取出 code / detail 並容忍其餘欄位。 */
class AppServiceClientErrorParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesCodeAndDetailFromProblemDetailAndIgnoresOtherFields() {
        String body = """
                {"type":"about:blank","title":"Gone","status":410,
                 "detail":"綁定碼已過期或已使用","instance":"/api/internal/line-bindings","code":"CODE_EXPIRED_OR_USED"}
                """;

        AppServiceClientException ex = AppServiceClient.parseError(objectMapper, 410, body, "fallback");

        assertThat(ex.getStatusCode()).isEqualTo(410);
        assertThat(ex.getErrorCode()).isEqualTo("CODE_EXPIRED_OR_USED");
        assertThat(ex.getMessage()).isEqualTo("綁定碼已過期或已使用");
    }

    @Test
    void fallsBackToUnknownWhenBodyIsNotJson() {
        AppServiceClientException ex = AppServiceClient.parseError(objectMapper, 502, "<html>Bad Gateway</html>", "502 Bad Gateway");

        assertThat(ex.getStatusCode()).isEqualTo(502);
        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("502 Bad Gateway");
    }

    @Test
    void usesUnknownCodeWhenProblemDetailHasNoCode() {
        AppServiceClientException ex = AppServiceClient.parseError(objectMapper, 500, "{\"status\":500}", "boom");

        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("boom");
    }
}
