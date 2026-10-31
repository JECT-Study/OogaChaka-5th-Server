package com.ject.oogachaka.auth.security;

import com.ject.oogachaka.auth.enums.TokenType;
import com.ject.oogachaka.auth.repository.AuthTokenStore;
import com.ject.oogachaka.auth.token.JwtClaims;
import com.ject.oogachaka.auth.token.JwtTokenProvider;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_CLAIMS_ATTRIBUTE = "com.ject.oogachaka.auth.accessClaims";
    private final JwtTokenProvider tokens;
    private final AuthTokenStore tokenStore;
    private final JsonSecurityHandler errorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 공개 인증 API는 access가 만료된 클라이언트의 공통 헤더도 무시한다.
        return AuthRequestMatchers.PUBLIC_ENDPOINTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            try {
                if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                    || java.util.Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() != 1) {
                    throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
                }
                JwtClaims claims = tokens.validate(header.substring(7), TokenType.ACCESS);
                if (tokenStore.isBlacklisted(claims)) {
                    throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
                }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(claims.userId(), null, List.of()));
                SecurityContextHolder.setContext(context);
                request.setAttribute(ACCESS_CLAIMS_ATTRIBUTE, claims);
            } catch (BusinessException exception) {
                SecurityContextHolder.clearContext();
                errorHandler.write(response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
