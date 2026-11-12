package com.ject.oogachaka.auth.service;

import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.auth.token.TokenPair;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * JWT 생성·검증과 Redis 상태 변경을 조정한다.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    // 서명·토큰 종류·필수 클레임을 검증하고 문자열 JWT를 생성하는 구성 요소다.
    private final JwtTokenProvider tokens;
    private final AuthTokenStore tokenStore;

    // 가입 또는 기존 소셜 계정 확인이 완료된 사용자에게 독립적인 access·refresh 묶음을 발급한다.
    public TokenResponse issue(Long userId) {
        TokenPair pair = tokens.issuePair(userId);
        tokenStore.saveRefresh(pair.refresh().claims());
        return TokenResponse.from(pair);
    }

    // 제출된 refresh를 검증하고 이전 토큰을 한 번만 새 토큰으로 바꾼다.
    public TokenResponse refresh(String refreshToken) {
        // access를 갱신용으로 쓰지 못하게 refresh 종류까지 확인한 후 사용자 ID와 jti를 신뢰한다.
        JwtClaims previous = tokens.validate(refreshToken, TokenType.REFRESH);
        TokenPair next = tokens.issuePair(previous.userId());

        // 이미 쓰였거나 로그아웃된 refresh는 Redis에 없어서 교체가 거부된다.
        if (!tokenStore.rotate(previous, next.refresh().claims())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        return TokenResponse.from(next);
    }

    // 로그아웃은 이 기기의 refresh만 지운다. 이미 발급된 access는 만료 시각까지 유효하다.
    public void logout(Long userId, String refreshToken) {
        JwtClaims refresh = tokens.validate(refreshToken, TokenType.REFRESH);
        if (!userId.equals(refresh.userId())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        tokenStore.deleteRefresh(refresh);
    }
}
