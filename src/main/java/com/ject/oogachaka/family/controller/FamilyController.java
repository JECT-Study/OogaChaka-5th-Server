package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.auth.dto.SocialLoginResponse;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyInviteResponse;
import com.ject.oogachaka.family.dto.FamilyJoinRequest;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.service.ChildJoinResult;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
public class FamilyController implements FamilyControllerDocs {

    private final FamilyService familyService;
    private final TokenService tokenService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FamilyResponse> createFamily(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(SuccessCode.CREATED, familyService.createFamily(userId));
    }

    @GetMapping("/me")
    public ApiResponse<FamilyResponse> getMyFamily(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getMyFamily(userId));
    }

    @GetMapping("/invites/{inviteCode}")
    public ApiResponse<FamilyInviteResponse> getInvite(@PathVariable String inviteCode) {
        return ApiResponse.success(familyService.getInvite(inviteCode));
    }

    @PostMapping("/join")
    public ApiResponse<SocialLoginResponse> joinAsChild(@Valid @RequestBody FamilyJoinRequest request) {
        ChildJoinResult result = familyService.joinAsChild(request.inviteCode(), request.nickname());
        // 소셜 로그인처럼 DB 트랜잭션을 끝낸 뒤 발급해 Redis 호출 중에는 DB 잠금과 커넥션을 점유하지 않는다.
        return ApiResponse.success(new SocialLoginResponse(result.userId(), result.isNewUser(), tokenService.issue(result.userId())));
    }

    @PostMapping("/me/invite-code")
    public ApiResponse<FamilyInviteCodeResponse> reissueInviteCode(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.reissueInviteCode(userId));
    }
}
