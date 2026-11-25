package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyJoinRequest;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.service.ChildJoinResult;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
public class FamilyController implements FamilyControllerDocs {

    private final FamilyService familyService;
    private final TokenService tokenService;

    @PostMapping
    public ApiResponse<FamilyResponse> createFamily(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody FamilyCreateRequest request) {
        return ApiResponse.success(SuccessCode.CREATED, familyService.createFamily(userId, request));
    }

    @GetMapping("/me")
    public ApiResponse<FamilyResponse> getMyFamily(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getMyFamily(userId));
    }

    @PostMapping("/join")
    public ApiResponse<SocialLoginResponse> joinAsChild(@Valid @RequestBody FamilyJoinRequest request) {
        ChildJoinResult result = familyService.joinAsChild(request.inviteCode());
        // 소셜 로그인처럼 자녀 연결 기록이 커밋된 뒤에 토큰을 발급한다.
        return ApiResponse.success(new SocialLoginResponse(result.userId(), result.isNewUser(), tokenService.issue(result.userId())));
    }

    @PostMapping("/me/invite-code")
    public ApiResponse<FamilyInviteCodeResponse> reissueInviteCode(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.reissueInviteCode(userId));
    }
}
