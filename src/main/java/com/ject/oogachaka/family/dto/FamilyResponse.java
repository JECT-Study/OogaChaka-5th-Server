package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.family.entity.Family;
import com.ject.oogachaka.family.repository.InviteCodeStore.InviteCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record FamilyResponse(
    @Schema(description = "가족 ID", example = "5") Long familyId,
    @Schema(description = "가족 초대코드 (부모에게만, 유효한 코드가 없으면 null)", example = "K7MP2Q", nullable = true) String inviteCode,
    @Schema(description = "초대코드 만료 시각 (부모에게만, 유효한 코드가 없으면 null)", example = "2026-11-24T05:05:00Z", nullable = true) OffsetDateTime inviteCodeExpiresAt,
    @Schema(description = "가입 순서로 정렬한 가족 구성원") List<FamilyMemberResponse> members) {

    public static FamilyResponse of(Family family, InviteCode inviteCode, List<FamilyMemberResponse> members) {
        return new FamilyResponse(family.getId(), inviteCode == null ? null : inviteCode.code(),
            inviteCode == null ? null : inviteCode.expiresAt(), members);
    }
}
