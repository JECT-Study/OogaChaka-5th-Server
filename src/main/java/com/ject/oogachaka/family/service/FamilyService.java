package com.ject.oogachaka.family.service;

import com.ject.oogachaka.family.entity.FamilyMember;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.repository.FamilyMemberRepository;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyService {

    private final FamilyMemberRepository familyMemberRepository;
    private final UserService userService;

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
}
