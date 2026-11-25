package com.ject.oogachaka.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FamilyCreateRequest(
    @Schema(description = "자녀 닉네임", example = "차카")
    @NotBlank @Size(max = 30) String childNickname,
    @Schema(description = "자녀 프로필 이미지 URL", example = "https://example.com/child.png", nullable = true)
    @Pattern(regexp = ".*\\S.*") @Size(max = 2048) String childProfileImageUrl
) {
}
