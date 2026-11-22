package com.ject.oogachaka.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 가족 생성·조회와 부모 사용자 조회를 검증한다. 저장소, 사용자 서비스와 초대코드 생성기는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

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

    // 이미 구성원 ID가 있는 사용자가 가족 생성을 요청하면 FAMILY_ALREADY_JOINED로 거절한다.
    @Test
    void 이미_가족에_속하면_생성하지_않는다() {
        when(userService.getUserForFamilyJoin(7L)).thenReturn(user(7L, "우가", 21L));

        assertThatThrownBy(() -> service.createFamily(7L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_ALREADY_JOINED));
    }

    // 미가입 사용자가 생성하면 발급 코드로 가족을 만들고 그 가족의 부모 구성원에 요청자를 연결한다.
    @Test
    void 가족을_만들고_요청자를_첫_부모로_반환한다() {
        when(userService.getUserForFamilyJoin(7L)).thenReturn(user(7L, "우가", null));
        when(inviteCodeGenerator.generate()).thenReturn("ABCD2345");
        when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            ReflectionTestUtils.setField(family, "id", 5L);
            return family;
        });
        when(familyMemberRepository.save(any(FamilyMember.class))).thenAnswer(invocation -> {
            FamilyMember parent = invocation.getArgument(0);
            assertThat(parent.getFamilyId()).isEqualTo(5L);
            ReflectionTestUtils.setField(parent, "id", 21L);
            return parent;
        });

        FamilyResponse response = service.createFamily(7L);

        verify(userService).joinFamily(7L, 21L);
        assertThat(response.familyId()).isEqualTo(5L);
        assertThat(response.inviteCode()).isEqualTo("ABCD2345");
        assertThat(response.members()).containsExactly(new FamilyMemberResponse(7L, "우가", null, FamilyRole.PARENT));
    }

    // 구성원 ID가 없는 사용자가 내 가족을 조회하면 FAMILY_NOT_FOUND를 반환한다.
    @Test
    void 미가입_사용자는_내_가족을_조회할_수_없다() {
        when(userService.getUser(7L)).thenReturn(User.create("우가"));

        assertThatThrownBy(() -> service.getMyFamily(7L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_NOT_FOUND));
    }

    // 사용자가 구성원과 반대 순서로 조회돼도 각 사용자의 역할을 맞춰 가입 순서대로 반환한다.
    @Test
    void 구성원_ID로_사용자를_짝지어_가입_순서대로_반환한다() {
        Family family = Family.create("ABCD2345");
        ReflectionTestUtils.setField(family, "id", 5L);
        User parent = user(8L, "우가", 21L);
        User child = user(7L, "차카", 22L);
        ReflectionTestUtils.setField(child, "profileImageUrl", "https://example.com/child.png");
        FamilyMember parentMember = member(21L, 5L, FamilyRole.PARENT);
        FamilyMember childMember = member(22L, 5L, FamilyRole.CHILD);
        when(userService.getUser(7L)).thenReturn(child);
        when(familyMemberRepository.findById(22L)).thenReturn(Optional.of(childMember));
        when(familyRepository.findById(5L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.findAllByFamilyIdOrderByIdAsc(5L)).thenReturn(List.of(parentMember, childMember));
        when(userService.listUsersByFamilyMemberIds(List.of(21L, 22L))).thenReturn(List.of(child, parent));

        FamilyResponse response = service.getMyFamily(7L);

        assertThat(response.familyId()).isEqualTo(5L);
        assertThat(response.inviteCode()).isEqualTo("ABCD2345");
        assertThat(response.members()).containsExactly(
            new FamilyMemberResponse(8L, "우가", null, FamilyRole.PARENT),
            new FamilyMemberResponse(7L, "차카", "https://example.com/child.png", FamilyRole.CHILD));
    }

    private User user(Long id, String nickname, Long memberId) {
        User user = User.create(nickname);
        ReflectionTestUtils.setField(user, "id", id);
        user.joinFamily(memberId);
        return user;
    }

    private FamilyMember member(Long id, Long familyId, FamilyRole role) {
        FamilyMember member = FamilyMember.create(familyId, role);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
