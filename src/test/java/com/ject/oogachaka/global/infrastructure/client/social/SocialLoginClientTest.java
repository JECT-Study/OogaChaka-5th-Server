package com.ject.oogachaka.global.infrastructure.client.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 요청·응답을 MockRestServiceServer로 대체해 제공자별 변환과 오류·로그 처리를 검증한다. 실제 소셜 제공자는 호출하지 않는다. */
class SocialLoginClientTest {

    private static final String TOKEN_URI = "https://provider.example/token";
    private static final String USER_URI = "https://provider.example/userinfo";
    private static final String VERIFIER = "v".repeat(43);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SocialOAuthTransport transport = new SocialOAuthTransport(builder.build(), JsonMapper.builder().build());

    // 구글 토큰 요청에 인증 코드·secret·콜백 주소·PKCE verifier를 폼으로 보내고 받은 토큰으로 사용자 정보를 조회한다.
    // 프로필의 sub를 계정 식별자로, name을 닉네임으로 추출하는지 확인한다.
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

    // 카카오에 PKCE verifier를 보내고 숫자 id를 계정 식별 문자열로 변환한다.
    // 동의하지 않은 닉네임·이메일이 없어도 사용자 정보 조회가 실패하지 않아야 한다.
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

    // 네이버 토큰 요청에는 state를 포함하고 PKCE verifier는 보내지 않는다.
    // resultcode가 00인 프로필의 response 내부에서 계정 ID와 닉네임을 추출하는지 확인한다.
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

    // 코드 교환의 400 응답은 로그인 거절로, 통신 타임아웃은 제공자 일시 장애로 구분한다.
    // 공급자 응답 원문이나 내부 예외가 BusinessException의 메시지·원인에 남지 않아야 한다.
    @Test
    void providerErrorsAndTimeoutsNeverExposeRawBodyOrCause() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withBadRequest().body("TEST_ONLY_SENSITIVE_BODY"));
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_REJECTED);
        server.verify();
        server.reset();
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("TEST_ONLY_INTERNAL_DETAILS")));
        assertFailure(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        server.verify();
    }

    // 제공자의 500 오류와 429 요청 제한은 사용자 코드 오류로 취급하지 않고 모두 AUTH_PROVIDER_UNAVAILABLE로 처리한다.
    @Test
    void providerServerFailureAndRateLimitRemainServiceUnavailable() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());
        assertFailure(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        server.verify();
        server.reset();
        server.expect(requestTo(TOKEN_URI)).andRespond(withTooManyRequests());
        assertFailure(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        server.verify();
    }

    // HTTP 200이어도 토큰 응답 본문에 OAuth error가 있으면 로그인 성공으로 처리하지 않고 AUTH_SOCIAL_LOGIN_REJECTED(400)로 거부한다.
    @Test
    void oauthErrorInSuccessfulHttpResponseIsAlsoRejected() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
            "{\"error\":\"invalid_grant\",\"error_description\":\"TEST_ONLY_SENSITIVE_BODY\"}", MediaType.APPLICATION_JSON));
        assertThat(ErrorCode.AUTH_SOCIAL_LOGIN_REJECTED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_REJECTED);
        server.verify();
    }

    // 코드 교환에 성공한 뒤 서버가 보유한 제공자 토큰으로 프로필을 조회하다 401을 받으면 AUTH_SOCIAL_LOGIN_FAILED로 처리한다.
    @Test
    void userInfoRejectionIsProviderFailureBecauseServerOwnsProviderToken() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{\"access_token\":\"TEST_ONLY_PROVIDER_ACCESS\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI)).andRespond(withUnauthorizedRequest());
        assertFailure(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
        server.verify();
    }

    // 제공자 응답이 JSON이 아니거나 프로필에 필수 계정 ID가 없으면 AUTH_SOCIAL_LOGIN_FAILED로 중단한다.
    // 이메일 등 다른 값으로 임시 계정 식별자를 만들어 로그인을 이어가면 안 된다.
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

    // RestClient의 DEBUG 로그를 켜도 인증 코드·secret·제공자 토큰·원본 소셜 ID·verifier가 로그에 나타나지 않아야 한다.
    // 폼 본문이 실제로 가려진 문자열로 출력되었는지도 확인해 단순 로그 미수집을 성공으로 오인하지 않는다.
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
