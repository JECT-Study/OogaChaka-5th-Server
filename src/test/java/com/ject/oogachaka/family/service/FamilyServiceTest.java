package com.ject.oogachaka.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.family.entity.FamilyMember;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.repository.FamilyMemberRepository;
import com.ject.oogachaka.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 부모 조회 분기만 확인한다. DB는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private FamilyService service;

    // 구성원과 같은 가족의 PARENT 구성원을 찾아 그 사용자 ID를 돌려준다.
    @Test
    void 같은_가족의_부모_사용자_ID를_찾는다() {
        when(familyMemberRepository.findById(20L)).thenReturn(Optional.of(member(20L, 5L, FamilyRole.CHILD)));
        when(familyMemberRepository.findAllByFamilyIdAndFamilyRole(5L, FamilyRole.PARENT))
            .thenReturn(List.of(member(21L, 5L, FamilyRole.PARENT), member(22L, 5L, FamilyRole.PARENT)));
        when(userService.listUserIdsByFamilyMemberIds(List.of(21L, 22L))).thenReturn(List.of(1L, 2L));

        assertThat(service.listParentUserIds(20L)).containsExactly(1L, 2L);
    }

    // 가족에 속하지 않은 사용자(familyMemberId 없음)는 조회 없이 빈 목록이다.
    @Test
    void 가족이_없으면_빈_목록이다() {
        assertThat(service.listParentUserIds(null)).isEmpty();
        verifyNoInteractions(familyMemberRepository, userService);
    }

    private FamilyMember member(Long id, Long familyId, FamilyRole role) {
        FamilyMember member = FamilyMember.create(familyId, role);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
