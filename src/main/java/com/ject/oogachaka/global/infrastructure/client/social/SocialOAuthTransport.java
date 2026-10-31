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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 제공자 응답/예외 원문을 요청 로그와 예외 원인 체인에 전달하지 않는 외부 통신 경계 */
@Component
@RequiredArgsConstructor
public class SocialOAuthTransport {

    private static final int MAX_RESPONSE_BYTES = 65_536;
    private final RestClient socialRestClient;
    private final ObjectMapper objectMapper;

    public String exchangeToken(URI uri, MultiValueMap<String, String> form) {
        JsonNode response = read(socialRestClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON).body(new SensitiveForm(form)), true);
        if (response.hasNonNull("error")) {
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_LOGIN_REJECTED);
        }
        String token = requiredText(response, "access_token");
        if (token.chars().anyMatch(Character::isISOControl)) {
            throw failed();
        }
        return token;
    }

    public JsonNode getUserInfo(URI uri, String token) {
        return read(socialRestClient.get().uri(uri).accept(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(token)), false);
    }

    private JsonNode read(RestClient.RequestHeadersSpec<?> request, boolean tokenExchange) {
        try {
            return request.exchange((outgoing, incoming) -> {
                if (!incoming.getStatusCode().is2xxSuccessful()) {
                    if (incoming.getStatusCode().is5xxServerError() || incoming.getStatusCode().value() == 429) {
                        throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
                    }
                    // 서버가 관리하는 제공자 토큰의 userinfo 실패와 인가 코드 교환 거절을 구분한다.
                    throw new BusinessException(tokenExchange && incoming.getStatusCode().is4xxClientError()
                        ? ErrorCode.AUTH_SOCIAL_LOGIN_REJECTED : ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
                }
                byte[] body = incoming.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw failed();
                }
                JsonNode node = objectMapper.readTree(body);
                if (node == null || !node.isObject()) {
                    throw failed();
                }
                return node;
            });
        } catch (JacksonException exception) {
            throw failed();
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        }
    }

    static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw failed();
        }
        return value;
    }

    static BusinessException failed() {
        return new BusinessException(ErrorCode.AUTH_SOCIAL_LOGIN_FAILED);
    }

    /** RestClient는 DEBUG에서 body.toString()을 출력하므로 폼의 문자열 표현도 가린다. */
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
