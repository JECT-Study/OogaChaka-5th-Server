package com.ject.oogachaka.family.controller;

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
        description = "새 가족을 만들고 요청자를 부모로 등록한다. 이미 가족에 속하면 409 FAMILY_ALREADY_JOINED를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> createFamily(@Parameter(hidden = true) Long userId);

    @Operation(summary = "내 가족 조회",
        description = "초대코드와 가입 순서대로 정렬한 구성원을 반환한다. 속한 가족이 없으면 404 FAMILY_NOT_FOUND를 반환한다.",
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    ApiResponse<FamilyResponse> getMyFamily(@Parameter(hidden = true) Long userId);
}
