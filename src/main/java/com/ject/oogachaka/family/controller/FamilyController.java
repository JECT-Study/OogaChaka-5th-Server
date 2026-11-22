package com.ject.oogachaka.family.controller;

import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.global.response.ApiResponse;
import com.ject.oogachaka.global.response.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
public class FamilyController implements FamilyControllerDocs {

    private final FamilyService familyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FamilyResponse> createFamily(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(SuccessCode.CREATED, familyService.createFamily(userId));
    }

    @GetMapping("/me")
    public ApiResponse<FamilyResponse> getMyFamily(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getMyFamily(userId));
    }
}
