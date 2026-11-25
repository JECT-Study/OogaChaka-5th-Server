package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.global.config.OpenApiConfig;
import com.ject.oogachaka.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** FamilyController의 Swagger 문서. 컨트롤러가 이 인터페이스를 구현하면 springdoc이 여기 어노테이션을 읽는다. */
@Tag(name = "가족", description = "가족 생성과 내 가족 조회")
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
}
