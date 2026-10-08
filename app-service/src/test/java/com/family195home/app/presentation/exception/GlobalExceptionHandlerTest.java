package com.family195home.app.presentation.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;

/** 驗證所有錯誤都以 application/problem+json 回應，且一律帶有穩定的 code 欄位。 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void apiException_isMappedToProblemDetailWithKindStatusAndCode() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.code").value("GROUP_NAME_TAKEN"))
                .andExpect(jsonPath("$.detail").value("群組名稱已被使用"))
                .andExpect(jsonPath("$.instance").value("/probe/conflict"));
    }

    @Test
    void everyErrorKind_mapsToExpectedHttpStatus() throws Exception {
        mockMvc.perform(get("/probe/kind").param("kind", "BAD_REQUEST")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/probe/kind").param("kind", "UNAUTHORIZED")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/probe/kind").param("kind", "FORBIDDEN")).andExpect(status().isForbidden());
        mockMvc.perform(get("/probe/kind").param("kind", "NOT_FOUND")).andExpect(status().isNotFound());
        mockMvc.perform(get("/probe/kind").param("kind", "CONFLICT")).andExpect(status().isConflict());
        mockMvc.perform(get("/probe/kind").param("kind", "GONE")).andExpect(status().isGone());
        mockMvc.perform(get("/probe/kind").param("kind", "INTERNAL")).andExpect(status().isInternalServerError());
    }

    @Test
    void beanValidationFailure_returnsValidationErrorWithFieldList() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void springMvcException_stillGetsProblemDetailWithDefaultCode() throws Exception {
        // 缺少必填 query 參數：由 ResponseEntityExceptionHandler 處理，補上依狀態碼推導的 code
        mockMvc.perform(get("/probe/kind"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void dateTimeParseFailure_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/probe/date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FORMAT"));
    }

    @Test
    void unexpectedException_returns500WithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("系統發生未預期的錯誤"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ApiException(ErrorKind.CONFLICT, "GROUP_NAME_TAKEN", "群組名稱已被使用");
        }

        @GetMapping("/probe/kind")
        void kind(@RequestParam ErrorKind kind) {
            throw new ApiException(kind, "ANY", "any");
        }

        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        @GetMapping("/probe/date")
        void date() {
            java.time.LocalDateTime.parse("not-a-date");
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    record Body(@NotBlank String name) {
    }
}
