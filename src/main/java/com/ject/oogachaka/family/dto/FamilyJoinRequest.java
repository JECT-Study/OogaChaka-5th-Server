package com.ject.oogachaka.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FamilyJoinRequest(
    @Schema(description = "가족 초대코드", example = "ABCD2345") @NotBlank String inviteCode,
    @Schema(description = "새 자녀를 생성할 때 필요한 이름", example = "차카") @Size(max = 30) String nickname) {
}
