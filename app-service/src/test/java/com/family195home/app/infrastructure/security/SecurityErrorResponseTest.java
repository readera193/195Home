package com.family195home.app.infrastructure.security;

import com.family195home.app.presentation.exception.GlobalExceptionHandler;
import com.family195home.app.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 驗證 filter 層的 401 與 MVC 層的 404 都回傳同一格式的 Problem Details JSON。 */
@WebMvcTest(controllers = SecurityErrorResponseTest.ProbeController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthFilter.class, InternalTokenFilter.class, JwtTokenProvider.class,
        ProblemDetailAuthHandlers.class, GlobalExceptionHandler.class, SecurityErrorResponseTest.ProbeController.class})
class SecurityErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void noToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/expenses"));
    }

    @Test
    void invalidToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/expenses").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void internalEndpointWithoutInternalToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/internal/line-bindings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validToken_passesSecurityFilterChain() throws Exception {
        String token = jwtTokenProvider.issue(1L, "a@example.com").token();

        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedRequestToUnknownPath_returns404ProblemDetail() throws Exception {
        String token = jwtTokenProvider.issue(1L, "a@example.com").token();

        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe")
        String probe() {
            return "ok";
        }
    }
}
