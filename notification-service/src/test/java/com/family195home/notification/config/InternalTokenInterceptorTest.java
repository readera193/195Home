package com.family195home.notification.config;

import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.controller.NotificationLogController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalTokenInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationLogRepository repository = mock(NotificationLogRepository.class);
        when(repository.findByGroupAndMonth(1L, "2026-09")).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationLogController(repository))
                .addMappedInterceptors(new String[]{"/api/notifications/**"}, new InternalTokenInterceptor("secret-token", Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    void missingToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/notifications/logs").param("familyGroupId", "1").param("month", "2026-09"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongToken_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/logs").param("familyGroupId", "1").param("month", "2026-09")
                        .header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctToken_isAllowed() throws Exception {
        mockMvc.perform(get("/api/notifications/logs").param("familyGroupId", "1").param("month", "2026-09")
                        .header("X-Internal-Token", "secret-token"))
                .andExpect(status().isOk());
    }
}
