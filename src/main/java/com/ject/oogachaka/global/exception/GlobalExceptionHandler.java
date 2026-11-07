package com.ject.oogachaka.global.exception;

import com.ject.oogachaka.global.crypto.EncryptionException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// [검토] 원문 예외를 언제 로그에 남기는지 이 처리기에서 통제하기 위한 로거를 생성한다.
@Slf4j
// [검토] MVC 컨트롤러에서 발생한 예외를 공통 JSON 응답으로 바꾸는 전역 처리기다. 필터 내부 실패는 별도 핸들러가 처리한다.
@RestControllerAdvice
// [검토] 프레임워크의 잘못된 본문·요청 메서드 등 표준 MVC 오류 처리에 프로젝트 응답 형식을 결합한다.
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // [검토] 데이터 암복호화 실패는 일반 내부 예외와 구분해 정제한 로그·응답으로 처리한다.
    @ExceptionHandler(EncryptionException.class)
    // [검토] 복호화 실패가 있어도 평문·암호문을 대체 응답으로 내보내지 않는 오류 경계다.
    public ResponseEntity<ApiResponse<Void>> handleEncryptionException(EncryptionException exception) {
        // [검토] 실패 작업 종류만 기록한다. 키·원문·암호문을 포함할 수 있는 원래 예외 체인은 출력하지 않는다.
        log.error("암호화 처리 오류가 발생했습니다. operation={}", exception.getOperation());
        // [검토] 암호화 실패를 현재 공통 500 업무 코드로 표현한다.
        ErrorCode errorCode = ErrorCode.CRYPTO_OPERATION_FAILED;
        // [검토] 프로젝트 오류 코드의 상태를 실제 HTTP 응답에 반영한다. 본문에 오류 코드만 넣고 200을 반환하지 않는다.
        return ResponseEntity.status(errorCode.getHttpStatus())
            // [검토] 외부에 노출해도 되는 공통 코드·메시지와 null data만 반환한다.
            .body(ApiResponse.error(errorCode));
    }

    // [검토] 컨트롤러·서비스가 의도적으로 던진 입력·인증·업무 실패를 처리한다.
    @ExceptionHandler(BusinessException.class)
    // [검토] 예를 들어 provider 변환 실패의 AUTH_PROVIDER_UNSUPPORTED를 여기서 HTTP 400으로 바꿔 응답한다.
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        // [검토] 업무 실패마다 미리 정한 HTTP 상태와 공개 메시지를 사용한다. 예외 메시지를 그대로 응답하지 않는다.
        ErrorCode errorCode = exception.getErrorCode();
        // [검토] 프로젝트 오류 코드의 상태를 실제 HTTP 응답에 반영한다. 본문에 오류 코드만 넣고 200을 반환하지 않는다.
        return ResponseEntity.status(errorCode.getHttpStatus())
            // [검토] 외부에 노출해도 되는 공통 코드·메시지와 null data만 반환한다.
            .body(ApiResponse.error(errorCode));
    }

    // Spring MVC의 요청 오류를 공통 응답으로 변환하며 원래 상태 코드와 헤더를 유지한다.
    // [검토] 상위 타입의 메서드를 구현·재정의함을 표시해 이름이나 매개변수가 잘못되면 컴파일 때 확인한다.
    @Override
    // [검토] 이 메서드 범위의 null 허용 기본값을 정적 분석에 명시한 현재 로컬 설정이다. 런타임 null 검사를 자동 생성하지 않는다.
    @NullMarked
    // [검토] 상위 MVC 예외 처리의 마지막 응답 생성 지점을 재정의해 요청 오류도 ApiResponse로 통일한다.
    protected ResponseEntity<Object> createResponseEntity(
        // [검토] 상위 처리기가 본문 없이 호출할 수도 있다는 null 허용 계약이다. 전달된 기본 본문 대신 공통 오류 본문을 만든다.
        @Nullable Object body,
        // [검토] 405의 Allow 등 프레임워크가 만든 의미 있는 응답 헤더를 유지한다.
        HttpHeaders headers,
        // [검토] 본문을 바꾸더라도 프레임워크가 판단한 실제 HTTP 상태는 유지한다.
        HttpStatusCode statusCode,
        // [검토] 상위 처리 메서드와 같은 시그니처를 유지하기 위한 요청 정보다. 현재 본문 변환에서는 직접 사용하지 않는다.
        WebRequest request
    ) {
        // [검토] 400·405 등 요청 오류의 상태·헤더를 보존하면서 JSON 본문만 프로젝트 계약으로 변환한다.
        return new ResponseEntity<>(ApiResponse.error(ErrorCode.from(statusCode)), headers, statusCode);
    }

    // [검토] 앞선 구체 처리기에 해당하지 않는 MVC 예외의 마지막 처리 경계다.
    @ExceptionHandler(Exception.class)
    // [검토] 예상 밖 오류는 500으로 처리하되 Security 예외는 원래 책임 경계로 보낸다.
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) throws Exception {
        // 보안 예외는 Spring Security 필터에서 처리하도록 전달한다.
        // [검토] 인증·권한 오류를 일반 500으로 삼키면 Security의 401·403 계약이 깨지므로 구분한다.
        if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
            // [검토] 보안 예외는 바깥 Security 필터가 적절한 인증·권한 실패 응답으로 처리하게 다시 던진다.
            throw exception;
        }

        // [검토] 원인을 추적해야 하는 예상 밖 오류의 스택을 기록한다. 외부 인증 원문은 SocialOAuthTransport 등 앞선 경계에서 정제한다는 설계에 의존한다.
        log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);
        // [검토] 원인이 분류되지 않은 서버 실패를 사용자 입력 오류로 추측하지 않고 500으로 처리한다.
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        // [검토] 프로젝트 오류 코드의 상태를 실제 HTTP 응답에 반영한다. 본문에 오류 코드만 넣고 200을 반환하지 않는다.
        return ResponseEntity.status(errorCode.getHttpStatus())
            // [검토] 외부에 노출해도 되는 공통 코드·메시지와 null data만 반환한다.
            .body(ApiResponse.error(errorCode));
    }
}
