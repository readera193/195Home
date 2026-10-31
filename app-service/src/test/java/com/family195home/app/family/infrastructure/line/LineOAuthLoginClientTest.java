package com.family195home.app.family.infrastructure.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.family195home.app.family.application.LineProfile;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LineOAuthLoginClientTest {

    private MockRestServiceServer server;
    private LineOAuthLoginClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client =
                new LineOAuthLoginClient(
                        builder, "chan-id", "chan-secret", "https://home.example/login/callback");
    }

    @Test
    void 授權碼換取_access_token_後取得_profile() {
        server.expect(requestTo("https://api.line.me/oauth2/v2.1/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString(
                                                        "grant_type=authorization_code"),
                                                org.hamcrest.Matchers.containsString(
                                                        "code=the-code"),
                                                org.hamcrest.Matchers.containsString(
                                                        "client_id=chan-id"),
                                                org.hamcrest.Matchers.containsString(
                                                        "client_secret=chan-secret"))))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"at-1\",\"token_type\":\"Bearer\"}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.line.me/v2/profile"))
                .andExpect(header("Authorization", "Bearer at-1"))
                .andRespond(
                        withSuccess(
                                "{\"userId\":\"U123\",\"displayName\":\"小明\",\"pictureUrl\":\"x\"}",
                                MediaType.APPLICATION_JSON));

        LineProfile profile = client.fetchProfile("the-code");

        assertThat(profile).isEqualTo(new LineProfile("U123", "小明"));
        server.verify();
    }

    @Test
    void 授權碼無效時回傳_LINE_LOGIN_FAILED() {
        server.expect(requestTo("https://api.line.me/oauth2/v2.1/token"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.fetchProfile("bad"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getKind()).isEqualTo(ErrorKind.UNAUTHORIZED);
                            assertThat(e.getCode()).isEqualTo("LINE_LOGIN_FAILED");
                        });
    }
}
