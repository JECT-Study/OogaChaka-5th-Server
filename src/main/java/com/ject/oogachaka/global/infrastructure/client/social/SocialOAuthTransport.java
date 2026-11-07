package com.ject.oogachaka.global.infrastructure.client.social;

import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * 제공자별 클라이언트가 공유하는 HTTP 호출 경계다. 인가 코드를 제공자 토큰으로 교환하고 그 토큰으로 프로필을 조회한다.
 * 제공자 쪽 실패(4xx·5xx·타임아웃·잘못된 응답)는 모두 AUTH_SOCIAL_LOGIN_FAILED로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SocialOAuthTransport {

    private final RestClient socialRestClient;

    public String exchangeToken(URI uri, MultiValueMap<String, String> form) {
        JsonNode response = read(socialRestClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON).body(new SensitiveForm(form)).retrieve());

        // 네이버는 코드 교환 실패도 HTTP 200에 error를 담아 응답한다.
        if (response.hasNonNull("error")) {
            throw failed();
        }

        return requiredText(response, "access_token");
    }

    public JsonNode getUserInfo(URI uri, String token) {
        return read(socialRestClient.get().uri(uri).accept(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token)).retrieve());
    }

    // 원래 예외는 제공자 응답 원문을 담을 수 있어 cause로 넘기지 않는다.
    private JsonNode read(RestClient.ResponseSpec response) {
        try {
            JsonNode body = response.body(JsonNode.class);
            if (body == null) {
                throw failed();
            }
            return body;
        } catch (RestClientException exception) {
            throw failed();
        }
    }

    // 계정 ID·access_token처럼 없으면 로그인을 이어갈 수 없는 필드를 읽는다.
    static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw failed();
        }
        return value;
    }

    // 없음·null·빈 문자열·문자열이 아닌 값을 모두 null로 통일한다.
    static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    static BusinessException failed() {
        return new BusinessException(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
    }

    /** RestClient는 DEBUG에서 body.toString()을 출력하므로 code·secret이 담긴 폼의 문자열 표현만 가린다. */
    private static final class SensitiveForm extends LinkedMultiValueMap<String, String> {
        private SensitiveForm(MultiValueMap<String, String> values) {
            super(values);
        }

        @Override
        public String toString() {
            return "OAuthForm[REDACTED]";
        }
    }
}
