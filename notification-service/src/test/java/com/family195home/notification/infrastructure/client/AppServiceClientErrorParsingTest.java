package com.family195home.notification.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** app-service 以 Problem Details 回應錯誤，確認 client 能取出 code / detail 並容忍其餘欄位。 */
class AppServiceClientErrorParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesCodeAndDetailFromProblemDetailAndIgnoresOtherFields() {
        String body =
                """
                {"type":"about:blank","title":"Not Found","status":404,
                 "detail":"找不到成員","instance":"/api/internal/line-bindings","code":"MEMBER_NOT_FOUND"}
                """;

        AppServiceClientException ex =
                AppServiceClient.parseError(objectMapper, 404, body, "fallback");

        assertThat(ex.getStatusCode()).isEqualTo(404);
        assertThat(ex.getErrorCode()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("找不到成員");
    }

    @Test
    void fallsBackToUnknownWhenBodyIsNotJson() {
        AppServiceClientException ex =
                AppServiceClient.parseError(
                        objectMapper, 502, "<html>Bad Gateway</html>", "502 Bad Gateway");

        assertThat(ex.getStatusCode()).isEqualTo(502);
        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("502 Bad Gateway");
    }

    @Test
    void usesUnknownCodeWhenProblemDetailHasNoCode() {
        AppServiceClientException ex =
                AppServiceClient.parseError(objectMapper, 500, "{\"status\":500}", "boom");

        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("boom");
    }

    @Test
    void getMonthlySummary_mapsProblemDetailErrorToClientException() {
        AppServiceClient client =
                clientReturning(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "{\"status\":500,\"detail\":\"系統異常\",\"code\":\"INTERNAL_ERROR\"}");

        assertThatThrownBy(() -> client.getMonthlySummary(10L, "2026-07"))
                .isInstanceOfSatisfying(
                        AppServiceClientException.class,
                        e -> {
                            assertThat(e.getStatusCode()).isEqualTo(500);
                            assertThat(e.getErrorCode()).isEqualTo("INTERNAL_ERROR");
                        });
    }

    @Test
    void findAllBindings_mapsProblemDetailErrorToClientException() {
        AppServiceClient client =
                clientReturning(
                        HttpStatus.UNAUTHORIZED,
                        "{\"status\":401,\"detail\":\"內部驗證失敗\",\"code\":\"UNAUTHORIZED\"}");

        assertThatThrownBy(client::findAllBindings)
                .isInstanceOfSatisfying(
                        AppServiceClientException.class,
                        e -> {
                            assertThat(e.getStatusCode()).isEqualTo(401);
                            assertThat(e.getErrorCode()).isEqualTo("UNAUTHORIZED");
                        });
    }

    @Test
    void findByLineUserId_returnsEmptyOn404ButThrowsOtherErrors() {
        AppServiceClient notBound =
                clientReturning(
                        HttpStatus.NOT_FOUND,
                        "{\"status\":404,\"detail\":\"此 LINE 帳號尚未加入任何家庭\",\"code\":\"NOT_BOUND\"}");
        assertThat(notBound.findByLineUserId("line-user-1")).isEmpty();

        AppServiceClient broken =
                clientReturning(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "{\"status\":500,\"code\":\"INTERNAL_ERROR\"}");
        assertThatThrownBy(() -> broken.findByLineUserId("line-user-1"))
                .isInstanceOfSatisfying(
                        AppServiceClientException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("INTERNAL_ERROR"));
    }

    private static AppServiceClient clientReturning(HttpStatus status, String body) {
        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://app-service")
                        .exchangeFunction(
                                request ->
                                        Mono.just(
                                                ClientResponse.create(status)
                                                        .header(
                                                                "Content-Type",
                                                                MediaType
                                                                        .APPLICATION_PROBLEM_JSON_VALUE)
                                                        .body(body)
                                                        .build()))
                        .build();
        return new AppServiceClient(webClient);
    }
}
