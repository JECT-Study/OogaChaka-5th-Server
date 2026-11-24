package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.family.repository.InviteCodeStore.InviteCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record FamilyInviteCodeResponse(
    @Schema(description = "가족 초대코드", example = "K7MP2Q") String inviteCode,
    @Schema(description = "초대코드 만료 시각", example = "2026-11-24T05:05:00Z") OffsetDateTime inviteCodeExpiresAt) {

    public static FamilyInviteCodeResponse from(InviteCode inviteCode) {
        return new FamilyInviteCodeResponse(inviteCode.code(), inviteCode.expiresAt());
    }
}
