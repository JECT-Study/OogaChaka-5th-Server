package com.ject.oogachaka.auth.security;

import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// [검토] 공용 처리 객체를 Spring 빈으로 등록해 생성자 주입으로 공유한다.
@Component
// [검토] final 의존성을 받는 생성자를 Lombok으로 생성해 Spring이 필요한 객체를 주입하게 한다.
@RequiredArgsConstructor
// [검토] 인증 없음(401)과 권한 거부(403)를 Security 기본 HTML 대신 프로젝트 JSON 계약으로 응답한다.
public class JsonSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // [검토] HTTP API와 같은 Jackson 3 직렬화 설정으로 오류 응답을 출력한다.
    private final ObjectMapper objectMapper;

    // [검토] 상위 타입의 메서드를 구현·재정의함을 표시해 이름이나 매개변수가 잘못되면 컴파일 때 확인한다.
    @Override
    // [검토] 인증 주체가 없어 보호 API에 접근할 수 없는 경우 Spring Security가 호출하는 진입점이다.
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
        // [검토] 응답 스트림 쓰기가 실패할 수 있으므로 처리 결과를 숨기지 않고 IOException을 전달한다.
        throws IOException {
        // [검토] 인증 자체가 없으면 일반 UNAUTHORIZED를 반환한다. 만료·위조 상세 코드는 JWT 필터가 직접 지정한다.
        write(response, ErrorCode.UNAUTHORIZED);
    }

    // [검토] 상위 타입의 메서드를 구현·재정의함을 표시해 이름이나 매개변수가 잘못되면 컴파일 때 확인한다.
    @Override
    // [검토] 인증은 되었지만 해당 동작을 허용하지 않은 경우의 Security 오류 처리다.
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
        // [검토] 응답 스트림 쓰기가 실패할 수 있으므로 처리 결과를 숨기지 않고 IOException을 전달한다.
        throws IOException {
        // [검토] 권한 거부는 재인증 요청인 401과 구분해 403을 사용한다.
        write(response, ErrorCode.FORBIDDEN);
    }

    // [검토] 필터·인가 실패가 공통 응답 상태·헤더·본문을 공유하게 하는 내부 출력 경계다.
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        // [검토] 본문 코드뿐 아니라 실제 HTTP 상태를 ErrorCode에 맞춘다.
        response.setStatus(errorCode.getHttpStatus().value());
        // [검토] 한국어 오류 메시지가 기본 서버 인코딩에 따라 깨지지 않도록 UTF-8을 지정한다.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // [검토] 클라이언트가 오류 본문도 JSON으로 해석할 수 있게 미디어 타입을 명시한다.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // [검토] 인증 자격이 필요한 응답에만 사용할 인증 방식 안내 헤더를 추가한다.
        if (errorCode.getHttpStatus().value() == 401) {
            // [검토] 클라이언트가 이 API의 인증 방식이 Bearer임을 알 수 있게 한다. 실제 토큰 값을 헤더에 넣지 않는다.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        // [검토] 공통 code·message·data=null 형식을 출력한다. 원래 Security 예외의 원문을 직렬화하지 않는다.
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(errorCode));
    }
}
