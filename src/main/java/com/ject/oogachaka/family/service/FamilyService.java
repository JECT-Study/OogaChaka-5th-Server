package com.ject.oogachaka.family.service;

import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyMemberResponse;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.entity.Family;
import com.ject.oogachaka.family.entity.FamilyMember;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.repository.FamilyMemberRepository;
import com.ject.oogachaka.family.repository.FamilyRepository;
import com.ject.oogachaka.family.repository.InviteCodeStore;
import com.ject.oogachaka.family.repository.InviteCodeStore.InviteCode;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.response.code.ErrorCode;
import com.ject.oogachaka.user.entity.User;
import com.ject.oogachaka.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private final InviteCodeStore inviteCodeStore;

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
    public FamilyResponse createFamily(Long userId, FamilyCreateRequest request) {
        User user = userService.getUser(userId);
        if (user.getFamilyMemberId() != null) {
            throw new BusinessException(ErrorCode.FAMILY_ALREADY_JOINED);
        }

        Family family = familyRepository.save(Family.create());
        FamilyMember parent = FamilyMember.create(family.getId(), FamilyRole.PARENT);
        parent.connect(family.getCreatedAt());
        parent = familyMemberRepository.save(parent);
        userService.joinFamily(userId, parent.getId());

        User childUser = userService.createChildUser(request.childNickname(), request.childProfileImageUrl());
        FamilyMember child = familyMemberRepository.save(FamilyMember.create(family.getId(), FamilyRole.CHILD));
        userService.joinFamily(childUser.getId(), child.getId());

        InviteCode inviteCode = inviteCodeStore.issue(family.getId());
        return FamilyResponse.of(family, inviteCode,
            List.of(FamilyMemberResponse.of(parent, user), FamilyMemberResponse.of(child, childUser)));
    }

    public FamilyResponse getMyFamily(Long userId) {
        Long memberId = userService.getUser(userId).getFamilyMemberId();
        if (memberId == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyMember familyMember = familyMemberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));
        Family family = familyRepository.findById(familyMember.getFamilyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        List<FamilyMember> members = familyMemberRepository.findAllByFamilyIdOrderByIdAsc(family.getId());
        List<Long> memberIds = members.stream().map(FamilyMember::getId).toList();
        Map<Long, User> usersByMemberId = userService.listUsersByFamilyMemberIds(memberIds).stream()
            .collect(Collectors.toMap(User::getFamilyMemberId, Function.identity()));

        // 초대코드는 자녀를 초대하는 부모에게만 보여 준다.
        InviteCode inviteCode = familyMember.getFamilyRole() == FamilyRole.PARENT
            ? inviteCodeStore.find(family.getId()).orElse(null) : null;
        return FamilyResponse.of(family, inviteCode, members.stream()
            .map(member -> FamilyMemberResponse.of(member, usersByMemberId.get(member.getId())))
            .toList());
    }

    @Transactional
    public ChildJoinResult joinAsChild(String inviteCode) {
        Long familyId = inviteCodeStore.consume(normalizeInviteCode(inviteCode))
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_INVITE_CODE_NOT_FOUND));
        // 가족 생성이 롤백되면 자녀 구성원 없이 초대코드만 Redis에 최대 5분간 남을 수 있다.
        FamilyMember child = familyMemberRepository.findAllByFamilyIdAndFamilyRole(familyId, FamilyRole.CHILD)
            .stream().findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_INVITE_CODE_NOT_FOUND));
        Long userId = userService.listUserIdsByFamilyMemberIds(List.of(child.getId())).stream().findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean firstConnection = child.getConnectedAt() == null;
        if (firstConnection) {
            child.connect(LocalDateTime.now());
        }
        return new ChildJoinResult(userId, firstConnection);
    }

    public FamilyInviteCodeResponse reissueInviteCode(Long userId) {
        Long memberId = userService.getUser(userId).getFamilyMemberId();
        if (memberId == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        FamilyMember member = familyMemberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));
        if (member.getFamilyRole() != FamilyRole.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return FamilyInviteCodeResponse.from(inviteCodeStore.issue(member.getFamilyId()));
    }

    // 초대코드는 영문 대문자와 숫자로만 발급한다.
    private String normalizeInviteCode(String inviteCode) {
        return inviteCode.strip().toUpperCase(Locale.ROOT);
    }
}
