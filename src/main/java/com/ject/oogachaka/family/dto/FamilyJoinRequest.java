package com.ject.oogachaka.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FamilyJoinRequest(
    @Schema(description = "가족 초대코드", example = "K7MP2Q") @NotBlank String inviteCode) {
}
