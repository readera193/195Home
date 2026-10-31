package com.family195home.notification.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.family195home.notification.config.RequestIdFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** 呼叫 app-service 時要帶上目前的 requestId，兩個服務的日誌才串得起來。 */
class AppServiceClientRequestIdTest {

    private final AtomicReference<String> sentHeader = new AtomicReference<>();

    private WebClient webClient() {
        return WebClient.builder()
                .baseUrl("http://app-service")
                .filter(AppServiceClient.requestIdPropagation())
                .exchangeFunction(
                        request -> {
                            sentHeader.set(request.headers().getFirst(RequestIdFilter.HEADER_NAME));
                            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                        })
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsCurrentRequestIdAsHeader() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-123");

        webClient().get().uri("/x").retrieve().toBodilessEntity().block();

        assertThat(sentHeader.get()).isEqualTo("req-123");
    }

    @Test
    void omitsHeaderWhenThereIsNoRequestId() {
        webClient().get().uri("/x").retrieve().toBodilessEntity().block();

        assertThat(sentHeader.get()).isNull();
    }
}
