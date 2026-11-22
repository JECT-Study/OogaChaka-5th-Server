package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.family.entity.Family;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FamilyResponse(
    @Schema(description = "가족 ID", example = "5") Long familyId,
    @Schema(description = "가족 초대코드", example = "ABCD2345") String inviteCode,
    @Schema(description = "가입 순서로 정렬한 가족 구성원") List<FamilyMemberResponse> members) {

    public static FamilyResponse of(Family family, List<FamilyMemberResponse> members) {
        return new FamilyResponse(family.getId(), family.getInviteCode(), members);
    }
}
