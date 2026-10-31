package com.family195home.app.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** 執行過濾器，回傳過濾鏈內部看到的 MDC requestId 與回應。 */
    private String run(String incomingHeader, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(RequestIdFilter.HEADER_NAME, incomingHeader);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        seen.set(MDC.get(RequestIdFilter.MDC_KEY));
                    }
                };
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void generatesRequestIdWhenAbsentAndReturnsItInResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(null, response);

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(seen);
    }

    @Test
    void reusesValidIncomingRequestId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run("monthly-2026-10.abc_1", response);

        assertThat(seen).isEqualTo("monthly-2026-10.abc_1");
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME))
                .isEqualTo("monthly-2026-10.abc_1");
    }

    @Test
    void replacesInvalidIncomingRequestIdToPreventLogInjection() throws Exception {
        String evil = "abc\nERROR forged log line";
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(evil, response);

        assertThat(seen).isNotEqualTo(evil).doesNotContain("\n");
        assertThat(run("x".repeat(65), new MockHttpServletResponse())).hasSize(36);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run("keep-me", new MockHttpServletResponse());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
