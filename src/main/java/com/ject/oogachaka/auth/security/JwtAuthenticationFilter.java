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

// [검토] final 의존성을 받는 생성자를 Lombok으로 생성해 Spring이 필요한 객체를 주입하게 한다.
@RequiredArgsConstructor
// [검토] Security 체인에서 요청의 Bearer access를 인증 주체로 바꾼다. 기본 ERROR·ASYNC 재디스패치 생략 규칙은 상위 필터를 따른다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokens;
    // [검토] MVC Advice 밖인 필터 실패도 공통 JSON과 상태 코드로 응답한다.
    private final JsonSecurityHandler errorHandler;

    // [검토] 상위 타입의 메서드를 구현·재정의함을 표시해 이름이나 매개변수가 잘못되면 컴파일 때 확인한다.
    @Override
    // [검토] 로그인·갱신에 access 검증을 요구하면 만료된 헤더 때문에 복구할 수 없으므로 공개 인증 API를 생략한다.
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 공개 인증 API는 access가 만료된 클라이언트의 공통 헤더도 무시한다.
        // [검토] Security 인가와 같은 경로·메서드 규칙을 사용한다. /auth/** 전체를 생략하는 넓은 규칙은 아니다.
        return AuthRequestMatchers.PUBLIC_ENDPOINTS.matches(request);
    }

    // [검토] 상위 타입의 메서드를 구현·재정의함을 표시해 이름이나 매개변수가 잘못되면 컴파일 때 확인한다.
    @Override
    // [검토] 보호 요청에서 인증 헤더를 검사하고 성공한 요청만 인증 정보를 담아 다음 필터로 넘긴다.
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        // [검토] 서블릿 처리와 응답 스트림 쓰기 실패는 서블릿 API의 예외 계약을 그대로 전달한다.
        throws ServletException, IOException {
        // [검토] 인증 토큰은 URL·쿠키 대신 Authorization 헤더에서 읽는 API 계약이다.
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // [검토] 헤더가 없으면 여기서 인증하지 않고 Security 인가 단계가 보호 경로에 UNAUTHORIZED를 반환하도록 둔다.
        if (header != null) {
            try {
                // [검토] Bearer 접두사는 대소문자를 구분하지 않지만 뒤에 토큰 문자가 최소 하나는 있어야 한다.
                if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
                }
                // [검토] 접두사를 뺀 JWT를 access로 검증한다. refresh가 같은 키로 서명되어 있어도 보호 API에 사용할 수 없다.
                JwtClaims claims = tokens.validate(header.substring(7), TokenType.ACCESS);
                // [검토] 이 요청에서 검증한 정보로 새 인증 컨텍스트를 만든다. 다른 요청의 상태를 수정해 재사용하지 않는다.
                var context = SecurityContextHolder.createEmptyContext();
                // [검토] 비밀번호를 저장하지 않고 Long 사용자 ID만 principal에 둔다. 역할 기능이 없어 권한 목록은 현재 비워두었다.
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(claims.userId(), null, List.of()));
                // [검토] 이후 Security 인가와 @AuthenticationPrincipal이 같은 인증 주체를 읽게 한다. 요청 종료 정리는 Security 체인이 담당한다.
                SecurityContextHolder.setContext(context);
            } catch (BusinessException exception) {
                // [검토] 원문 JWT나 파서 원인 대신 공통 오류 코드·메시지만 JSON으로 출력한다.
                errorHandler.write(response, exception.getErrorCode());
                // [검토] 오류 응답을 쓴 뒤 컨트롤러가 실행되거나 응답이 두 번 쓰이지 않도록 필터를 끝낸다.
                return;
            }
        }
        // [검토] 인증 성공 또는 헤더 부재 요청을 다음 필터로 전달한다. 헤더 부재인 보호 요청의 허용 여부는 SecurityConfig가 결정한다.
        chain.doFilter(request, response);
    }
}
