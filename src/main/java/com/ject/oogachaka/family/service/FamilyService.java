package com.ject.oogachaka.family.service;

import com.ject.oogachaka.family.dto.FamilyMemberResponse;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.entity.Family;
import com.ject.oogachaka.family.entity.FamilyMember;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.repository.FamilyMemberRepository;
import com.ject.oogachaka.family.repository.FamilyRepository;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyService {

    private final FamilyMemberRepository familyMemberRepository;
    private final UserService userService;
    private final FamilyRepository familyRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    // 같은 가족의 부모 사용자 ID. 가족에 속하지 않은 구성원이면 빈 목록이다.
    public List<Long> listParentUserIds(Long familyMemberId) {
        if (familyMemberId == null) {
            return List.of();
        }
        return familyMemberRepository.findById(familyMemberId)
            .map(member -> familyMemberRepository.findAllByFamilyIdAndFamilyRole(member.getFamilyId(), FamilyRole.PARENT))
            .map(parents -> userService.listUserIdsByFamilyMemberIds(parents.stream().map(FamilyMember::getId).toList()))
            .orElse(List.of());
    }

    @Transactional
    public FamilyResponse createFamily(Long userId) {
        User user = userService.getUserForFamilyJoin(userId);
        if (user.getFamilyMemberId() != null) {
            throw new BusinessException(ErrorCode.FAMILY_ALREADY_JOINED);
        }

        Family family = familyRepository.save(Family.create(inviteCodeGenerator.generate()));
        FamilyMember parent = familyMemberRepository.save(FamilyMember.create(family.getId(), FamilyRole.PARENT));
        userService.joinFamily(userId, parent.getId());
        return FamilyResponse.of(family, List.of(FamilyMemberResponse.of(parent, user)));
    }

    public FamilyResponse getMyFamily(Long userId) {
        Long memberId = userService.getUser(userId).getFamilyMemberId();
        if (memberId == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        Family family = familyMemberRepository.findById(memberId)
            .flatMap(member -> familyRepository.findById(member.getFamilyId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        List<FamilyMember> members = familyMemberRepository.findAllByFamilyIdOrderByIdAsc(family.getId());
        List<Long> memberIds = members.stream().map(FamilyMember::getId).toList();
        Map<Long, User> usersByMemberId = userService.listUsersByFamilyMemberIds(memberIds).stream()
            .collect(Collectors.toMap(User::getFamilyMemberId, Function.identity()));
        return FamilyResponse.of(family, members.stream()
            .map(member -> FamilyMemberResponse.of(member, usersByMemberId.get(member.getId())))
            .toList());
    }
}
