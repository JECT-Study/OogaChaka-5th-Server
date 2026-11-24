package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.family.entity.FamilyMember;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record FamilyMemberResponse(
    @Schema(description = "사용자 ID", example = "42") Long userId,
    @Schema(description = "사용자 닉네임", example = "우가") String nickname,
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png") String profileImageUrl,
    @Schema(description = "가족 내 역할", example = "PARENT") FamilyRole familyRole,
    @Schema(description = "자녀가 초대코드로 입장해 연결됐는지 여부. 부모는 항상 true") boolean connected) {

    public static FamilyMemberResponse of(FamilyMember member, User user) {
        // V5 이전에 가입한 부모는 연결 일시가 없어도 연결된 구성원이다.
        boolean connected = member.getFamilyRole() == FamilyRole.PARENT || member.getConnectedAt() != null;
        return new FamilyMemberResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(),
            member.getFamilyRole(), connected);
    }
}
