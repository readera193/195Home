package com.family195home.notification.client;

import com.family195home.notification.dto.BindingResult;
import com.family195home.notification.dto.ErrorBody;
import com.family195home.notification.dto.LineBindingView;
import com.family195home.notification.dto.MonthlySummaryView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 呼叫 app-service 內部 API（/api/internal/**）的唯一元件，帶入 X-Internal-Token 共用密鑰
 * （見 contracts/app-service.md、research.md 決策 6、7）。這是系統中唯一保留的跨進程呼叫。
 */
@Component
public class AppServiceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppServiceClient(
            @Value("${app.app-service.base-url}") String baseUrl,
            @Value("${app.internal.token}") String internalToken) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    public BindingResult consumeBindingCode(String code, String lineUserId) {
        return webClient.post()
                .uri("/api/internal/line-bindings")
                .bodyValue(Map.of("code", code, "lineUserId", lineUserId))
                .retrieve()
                .bodyToMono(BindingResult.class)
                .onErrorMap(WebClientResponseException.class, this::toClientException)
                .block();
    }

    public Optional<BindingResult> findByLineUserId(String lineUserId) {
        try {
            BindingResult result = webClient.get()
                    .uri("/api/internal/line-bindings/by-line-user/{lineUserId}", lineUserId)
                    .retrieve()
                    .bodyToMono(BindingResult.class)
                    .block();
            return Optional.ofNullable(result);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<LineBindingView> findAllBindings() {
        return webClient.get()
                .uri("/api/internal/line-bindings")
                .retrieve()
                .bodyToFlux(LineBindingView.class)
                .collectList()
                .block();
    }

    public MonthlySummaryView getMonthlySummary(Long familyGroupId, String month) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/statistics/monthly")
                        .queryParam("familyGroupId", familyGroupId)
                        .queryParam("month", month)
                        .build())
                .retrieve()
                .bodyToMono(MonthlySummaryView.class)
                .block();
    }

    private AppServiceClientException toClientException(WebClientResponseException e) {
        try {
            ErrorBody body = objectMapper.readValue(e.getResponseBodyAsString(), ErrorBody.class);
            return new AppServiceClientException(e.getStatusCode().value(), body.code(), body.message());
        } catch (Exception parseError) {
            return new AppServiceClientException(e.getStatusCode().value(), "UNKNOWN", e.getMessage());
        }
    }
}
