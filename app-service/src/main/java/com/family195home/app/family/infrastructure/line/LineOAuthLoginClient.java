package com.family195home.app.family.infrastructure.line;

import com.family195home.app.family.application.LineLoginClient;
import com.family195home.app.family.application.LineProfile;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** LINE Login（OAuth 2.1）：以授權碼換 access token，再取得使用者 profile。 */
@Component
public class LineOAuthLoginClient implements LineLoginClient {

    private static final String TOKEN_URL = "https://api.line.me/oauth2/v2.1/token";
    private static final String PROFILE_URL = "https://api.line.me/v2/profile";

    private final RestClient restClient;
    private final String channelId;
    private final String channelSecret;
    private final String redirectUri;

    public LineOAuthLoginClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.line.login.channel-id}") String channelId,
            @Value("${app.line.login.channel-secret}") String channelSecret,
            @Value("${app.line.login.redirect-uri}") String redirectUri) {
        this.restClient = restClientBuilder.build();
        this.channelId = channelId;
        this.channelSecret = channelSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public LineProfile fetchProfile(String code) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            form.add("client_id", channelId);
            form.add("client_secret", channelSecret);

            TokenResponse token = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.accessToken() == null) {
                throw loginFailed(null);
            }

            ProfileResponse profile = restClient.get()
                    .uri(PROFILE_URL)
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(ProfileResponse.class);
            if (profile == null || profile.userId() == null) {
                throw loginFailed(null);
            }
            String name = profile.displayName() != null && !profile.displayName().isBlank()
                    ? profile.displayName()
                    : "LINE 使用者";
            return new LineProfile(profile.userId(), name);
        } catch (RestClientException e) {
            // 授權碼無效/過期（4xx）或 LINE 服務異常：一律視為登入失敗，不外洩細節
            throw loginFailed(e);
        }
    }

    private static ApiException loginFailed(Exception cause) {
        ApiException ex = new ApiException(ErrorKind.UNAUTHORIZED, "LINE_LOGIN_FAILED", "LINE 登入失敗，請重新登入");
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(String userId, String displayName) {
    }
}
