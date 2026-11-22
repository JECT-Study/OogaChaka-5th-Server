package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyInviteResponse;
import com.ject.oogachaka.family.dto.FamilyJoinRequest;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.global.config.OpenApiConfig;
import com.ject.oogachaka.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** FamilyController의 Swagger 문서. 컨트롤러가 이 인터페이스를 구현하면 springdoc이 여기 어노테이션을 읽는다. */
@Tag(name = "가족", description = "가족 생성·조회, 초대 조회, 자녀 입장과 초대코드 재발급")
public interface FamilyControllerDocs {

    @Operation(summary = "가족 생성",
        description = "새 가족을 만들고 요청자를 부모로 등록한다. 이미 가족에 속하면 409 FAMILY_ALREADY_JOINED를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> createFamily(@Parameter(hidden = true) Long userId);

    @Operation(summary = "내 가족 조회",
        description = "초대코드와 가입 순서대로 정렬한 구성원을 반환한다. 속한 가족이 없으면 404 FAMILY_NOT_FOUND를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> getMyFamily(@Parameter(hidden = true) Long userId);

    @Operation(summary = "초대 정보 조회",
        description = "부모 닉네임을 가입 순서로 반환하고 자녀 가입 여부를 알려준다. 유효하지 않은 코드는 404 FAMILY_INVITE_CODE_NOT_FOUND를 반환한다.")
    ApiResponse<FamilyInviteResponse> getInvite(String inviteCode);

    @Operation(summary = "자녀 입장",
        description = "자녀가 있으면 nickname을 무시하고 기존 자녀로 로그인한다. 응답은 소셜 로그인과 같은 형식이다. "
            + "새 자녀의 이름 누락 등 입력 오류는 400 INVALID_REQUEST, 유효하지 않은 코드는 404 FAMILY_INVITE_CODE_NOT_FOUND를 반환한다.")
    ApiResponse<SocialLoginResponse> joinAsChild(FamilyJoinRequest request);

    @Operation(summary = "초대코드 재발급",
        description = "새 코드를 발급하고 이전 코드를 즉시 무효화한다. 부모가 아니면 403 FORBIDDEN, 속한 가족이 없으면 404 FAMILY_NOT_FOUND를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyInviteCodeResponse> reissueInviteCode(@Parameter(hidden = true) Long userId);
}
