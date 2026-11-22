package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.family.entity.Family;
import io.swagger.v3.oas.annotations.media.Schema;

public record FamilyInviteCodeResponse(
    @Schema(description = "새 가족 초대코드", example = "WXYZ6789") String inviteCode) {

    public static FamilyInviteCodeResponse from(Family family) {
        return new FamilyInviteCodeResponse(family.getInviteCode());
    }
}
