package com.ject.oogachaka.auth.security;

import com.ject.oogachaka.auth.enums.TokenType;
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

    private final JwtTokenProvider tokens;
    private final JsonSecurityHandler errorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return AuthRequestMatchers.PUBLIC_ENDPOINTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            try {
                if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
                }
                JwtClaims claims = tokens.validate(header.substring(7), TokenType.ACCESS);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(claims.userId(), null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (BusinessException exception) {
                errorHandler.write(response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
