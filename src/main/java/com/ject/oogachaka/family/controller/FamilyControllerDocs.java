package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyJoinRequest;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.global.config.OpenApiConfig;
import com.ject.oogachaka.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** FamilyController의 Swagger 문서. 컨트롤러가 이 인터페이스를 구현하면 springdoc이 여기 어노테이션을 읽는다. */
@Tag(name = "가족", description = "가족 생성·조회, 자녀 입장과 초대코드 재발급")
public interface FamilyControllerDocs {

    @Operation(summary = "가족 생성",
        description = "요청자를 부모로 등록하고 자녀 프로필로 자녀 계정을 만든 뒤 5분간 유효한 1회용 초대코드를 발급한다. "
            + "이미 가족에 속하면 409 FAMILY_ALREADY_JOINED, 입력 검증에 실패하면 400 INVALID_REQUEST를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> createFamily(@Parameter(hidden = true) Long userId, FamilyCreateRequest request);

    @Operation(summary = "내 가족 조회",
        description = "가입 순서대로 구성원을 반환한다. connected는 자녀가 초대코드로 입장했는지를 나타내며 부모는 항상 true다. "
            + "초대코드와 만료 시각은 부모에게만 제공하며 유효한 코드가 없으면 null이다. 속한 가족이 없으면 404 FAMILY_NOT_FOUND를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> getMyFamily(@Parameter(hidden = true) Long userId);

    @Operation(summary = "자녀 입장",
        description = "1회용 초대코드로 미리 만든 자녀 계정에 연결하고 로그인한다. 코드의 대소문자와 앞뒤 공백은 무시한다. "
            + "응답은 소셜 로그인과 같은 형식이며 isNewUser는 이번 입장이 첫 연결인지 나타낸다. "
            + "입력 오류는 400 INVALID_REQUEST, 없거나 만료됐거나 이미 사용한 코드는 404 FAMILY_INVITE_CODE_NOT_FOUND를 반환한다.")
    ApiResponse<SocialLoginResponse> joinAsChild(FamilyJoinRequest request);

    @Operation(summary = "초대코드 재발급",
        description = "5분간 유효한 새 코드와 만료 시각을 반환하고 이전 코드를 즉시 무효화한다. "
            + "부모가 아니면 403 FORBIDDEN, 속한 가족이 없으면 404 FAMILY_NOT_FOUND를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyInviteCodeResponse> reissueInviteCode(@Parameter(hidden = true) Long userId);
}
