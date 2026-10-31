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

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider tokens;
    private final AuthTokenStore tokenStore;

    public TokenResponse issue(Long userId) {
        TokenPair pair = tokens.issuePair(userId);
        tokenStore.saveRefresh(pair.refresh().claims());
        return TokenResponse.from(pair);
    }

    public TokenResponse refresh(String refreshToken) {
        JwtClaims previous = tokens.validate(refreshToken, TokenType.REFRESH);
        TokenPair next = tokens.issuePair(previous.userId());
        if (!tokenStore.rotate(previous, next.refresh().claims())) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_REUSED);
        }
        return TokenResponse.from(next);
    }

    public void logout(Long userId, JwtClaims access, String refreshToken) {
        JwtClaims refresh = tokens.validate(refreshToken, TokenType.REFRESH);
        if (!userId.equals(access.userId()) || !userId.equals(refresh.userId()) || access.type() != TokenType.ACCESS) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        tokenStore.logout(refresh, access);
    }
}
