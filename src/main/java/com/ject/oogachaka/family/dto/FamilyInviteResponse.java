package com.ject.oogachaka.family.dto;

import com.ject.oogachaka.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

public record FamilyInviteResponse(
    @Schema(description = "가입 순서로 정렬한 부모 닉네임", example = "[\"우가\"]") List<String> parentNicknames,
    @Schema(description = "자녀 가입 여부", example = "false") boolean childJoined) {

    public static FamilyInviteResponse of(List<User> parents, boolean childJoined) {
        List<String> nicknames = parents.stream()
            .sorted(Comparator.comparing(User::getFamilyMemberId))
            .map(User::getNickname)
            .toList();
        return new FamilyInviteResponse(nicknames, childJoined);
    }
}
