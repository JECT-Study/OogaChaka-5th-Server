package com.ject.oogachaka.global.infrastructure.client.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ject.oogachaka.auth.enums.AuthProvider;
import com.ject.oogachaka.global.config.OAuthProperties;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SocialLoginClientTest {

    private static final String TOKEN_URI = "https://provider.example/token";
    private static final String USER_URI = "https://provider.example/userinfo";
    private static final String VERIFIER = "v".repeat(43);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SocialOAuthTransport transport = new SocialOAuthTransport(builder.build(), JsonMapper.builder().build());

    @Test
    void googleSendsFormPkceAndReadsSubject() {
        var form = form();
        form.add("code_verifier", VERIFIER);
        expectExchange(form, """
            {"sub":"TEST_ONLY_GOOGLE_ID","name":"구글별명","email":"google@example.invalid"}
            """);
        SocialUserInfo user = new GoogleSocialLoginClient(properties(AuthProvider.GOOGLE), transport)
            .getUserInfo("TEST_ONLY_CODE", "TEST_ONLY_STATE", VERIFIER);
        assertThat(user.getProviderUserId()).isEqualTo("TEST_ONLY_GOOGLE_ID");
        assertThat(user.getNickname()).isEqualTo("구글별명");
        server.verify();
    }

    @Test
    void kakaoReadsNumericIdAndAllowsMissingEmailOrNickname() {
        var form = form();
        form.add("code_verifier", VERIFIER);
        expectExchange(form, "{\"id\":1234567890,\"kakao_account\":{}}");
        SocialUserInfo user = new KakaoSocialLoginClient(properties(AuthProvider.KAKAO), transport)
            .getUserInfo("TEST_ONLY_CODE", "TEST_ONLY_STATE", VERIFIER);
        assertThat(user.getProviderUserId()).isEqualTo("1234567890");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getNickname()).isNull();
        server.verify();
    }

    @Test
    void naverPassesStateAndUnwrapsResponse() {
        var form = form();
        form.add("state", "TEST_ONLY_STATE");
        expectExchange(form, """
            {"resultcode":"00","response":{"id":"TEST_ONLY_NAVER_ID","nickname":"네이버별명"}}
            """);
        SocialUserInfo user = new NaverSocialLoginClient(properties(AuthProvider.NAVER), transport)
            .getUserInfo("TEST_ONLY_CODE", "TEST_ONLY_STATE", "");
        assertThat(user.getProviderUserId()).isEqualTo("TEST_ONLY_NAVER_ID");
        assertThat(user.getNickname()).isEqualTo("네이버별명");
        server.verify();
    }

    @Test
    void providerErrorsAndTimeoutsNeverExposeRawBodyOrCause() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withBadRequest().body("TEST_ONLY_SENSITIVE_BODY"));
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
        server.verify();
        server.reset();
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("TEST_ONLY_INTERNAL_DETAILS")));
        assertFailure(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        server.verify();
    }

    @Test
    void malformedJsonAndMissingUserIdFailWithoutFallbackIdentity() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("TEST_ONLY_INVALID_JSON", MediaType.APPLICATION_JSON));
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
        server.verify();
        server.reset();
        var form = form();
        form.add("code_verifier", VERIFIER);
        expectExchange(form, "{\"email\":\"google@example.invalid\"}");
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void restClientDebugLogDoesNotExposeCodeSecretOrProviderToken() {
        Logger logger = (Logger) LoggerFactory.getLogger("org.springframework.web.client.DefaultRestClient");
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.DEBUG);
        try {
            var form = form();
            form.add("code_verifier", VERIFIER);
            expectExchange(form, "{\"sub\":\"TEST_ONLY_GOOGLE_ID\"}");
            new GoogleSocialLoginClient(properties(AuthProvider.GOOGLE), transport)
                .getUserInfo("TEST_ONLY_CODE", "TEST_ONLY_STATE", VERIFIER);
            assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("OAuthForm[REDACTED]"))
                .allSatisfy(message -> assertThat(message).doesNotContain(
                    "TEST_ONLY_CODE", "TEST_ONLY_SECRET", "TEST_ONLY_PROVIDER_ACCESS", "TEST_ONLY_GOOGLE_ID", VERIFIER));
            server.verify();
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(events);
            events.stop();
        }
    }

    private void assertFailure(ErrorCode expected) {
        assertThatThrownBy(() -> new GoogleSocialLoginClient(properties(AuthProvider.GOOGLE), transport)
            .getUserInfo("TEST_ONLY_CODE", "TEST_ONLY_STATE", VERIFIER))
            .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(expected))
            .hasNoCause().hasMessageNotContaining("TEST_ONLY");
    }

    private void expectExchange(LinkedMultiValueMap<String, String> form, String userInfo) {
        server.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST)).andExpect(content().formData(form))
            .andRespond(withSuccess("{\"access_token\":\"TEST_ONLY_PROVIDER_ACCESS\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI)).andExpect(header("Authorization", "Bearer TEST_ONLY_PROVIDER_ACCESS"))
            .andRespond(withSuccess(userInfo, MediaType.APPLICATION_JSON));
    }

    private LinkedMultiValueMap<String, String> form() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "TEST_ONLY_CLIENT");
        form.add("client_secret", "TEST_ONLY_SECRET");
        form.add("code", "TEST_ONLY_CODE");
        form.add("redirect_uri", "https://app.example/callback");
        return form;
    }

    private OAuthProperties properties(AuthProvider provider) {
        return new OAuthProperties(Duration.ofMinutes(5), Duration.ofSeconds(3), Duration.ofSeconds(5), Map.of(provider,
            new OAuthProperties.Provider(true, "TEST_ONLY_CLIENT", "TEST_ONLY_SECRET", URI.create("https://app.example/callback"),
                URI.create("https://provider.example/authorize"), URI.create(TOKEN_URI), URI.create(USER_URI), List.of())));
    }
}
