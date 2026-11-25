package com.ject.oogachaka.notification.controller;

import com.ject.oogachaka.global.config.OpenApiConfig;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.notification.dto.PushRegistrationDeleteRequest;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** PushRegistrationController의 Swagger 문서. 컨트롤러가 이 인터페이스를 구현하면 springdoc이 여기 어노테이션을 읽는다. */
@Tag(name = "푸시", description = "FCM 기기 등록·해제. 앱 연동 방법은 docs/push-notifications.md를 참고한다.")
public interface PushRegistrationControllerDocs {

    @Operation(summary = "푸시 기기 등록",
        description = "로그인 직후, 앱 시작, FCM 토큰 갱신 때마다 호출한다. 같은 토큰을 다시 보내도 행이 늘지 않고, "
            + "다른 계정으로 로그인하면 소유자만 바뀐다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<Void> register(@Parameter(hidden = true) Long userId, PushRegistrationRequest request);

    @Operation(summary = "푸시 기기 해제",
        description = "로그아웃할 때 access 토큰을 지우기 전에 호출한다. 본인 소유 토큰만 지우며, 없는 토큰이어도 성공한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<Void> delete(@Parameter(hidden = true) Long userId, PushRegistrationDeleteRequest request);
}
