package com.family195home.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文件設定：Swagger UI 在 /swagger-ui.html，規格在 /v3/api-docs。 所有 /api/** 預設需要 JWT（Authorize 按鈕貼上
 * Demo 登入取得的 token）；內部 API（/api/internal/**）不列入文件。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("195Home app-service API")
                                .version("0.1.0")
                                .description(
                                        "家庭共享支出平台：家庭群組、支付帳戶、支出紀錄（含編輯鎖）與月結統計。"
                                                + "先呼叫 POST /api/auth/demo 取得 token，再按右上角 Authorize 貼上即可試用。"
                                                + "錯誤一律為 RFC 9457 Problem Details（application/problem+json），code 欄位為穩定的錯誤代碼。"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
