package com.ject.oogachaka.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyInviteResponse;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 가족 생성·조회와 부모 사용자 조회를 검증한다. 가족·초대코드 저장소와 사용자 서비스는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    private static final FamilyCreateRequest REQUEST = new FamilyCreateRequest("차카", "https://example.com/child.png");
    private static final InviteCode INVITE_CODE = new InviteCode("K7MP2Q", OffsetDateTime.parse("2026-11-24T05:05:00Z"));

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private InviteCodeStore inviteCodeStore;

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

        assertThatThrownBy(() -> service.createFamily(7L, REQUEST))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_ALREADY_JOINED));
    }

    // 미가입 사용자가 자녀 프로필로 가족을 만들면 부모만 연결된 두 구성원과 자녀 초대코드를 반환한다.
    @Test
    void 부모와_자녀를_같은_가족에_등록하고_초대코드를_반환한다() {
        LocalDateTime createdAt = LocalDateTime.parse("2026-11-24T05:00:00");
        when(userService.getUserForFamilyJoin(7L)).thenReturn(user(7L, "우가", null));
        User child = User.create("차카", "https://example.com/child.png");
        ReflectionTestUtils.setField(child, "id", 8L);
        when(userService.createUser("차카", "https://example.com/child.png")).thenReturn(child);
        when(inviteCodeStore.issue(5L)).thenReturn(INVITE_CODE);
        when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            ReflectionTestUtils.setField(family, "id", 5L);
            ReflectionTestUtils.setField(family, "createdAt", createdAt);
            return family;
        });
        when(familyMemberRepository.save(any(FamilyMember.class))).thenAnswer(invocation -> {
            FamilyMember member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", member.getFamilyRole() == FamilyRole.PARENT ? 21L : 22L);
            return member;
        });

        FamilyResponse response = service.createFamily(7L, REQUEST);

        ArgumentCaptor<FamilyMember> savedMembers = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberRepository, times(2)).save(savedMembers.capture());
        assertThat(savedMembers.getAllValues()).satisfiesExactly(parent -> {
            assertThat(parent.getFamilyId()).isEqualTo(5L);
            assertThat(parent.getFamilyRole()).isEqualTo(FamilyRole.PARENT);
            assertThat(parent.getConnectedAt()).isEqualTo(createdAt);
        }, childMember -> {
            assertThat(childMember.getFamilyId()).isEqualTo(5L);
            assertThat(childMember.getFamilyRole()).isEqualTo(FamilyRole.CHILD);
            assertThat(childMember.getConnectedAt()).isNull();
        });
        verify(userService).joinFamily(7L, 21L);
        verify(userService).joinFamily(8L, 22L);
        assertThat(response.familyId()).isEqualTo(5L);
        assertThat(response.inviteCode()).isEqualTo(INVITE_CODE.code());
        assertThat(response.inviteCodeExpiresAt()).isEqualTo(INVITE_CODE.expiresAt());
        assertThat(response.members()).containsExactly(
            new FamilyMemberResponse(7L, "우가", null, FamilyRole.PARENT, true),
            new FamilyMemberResponse(8L, "차카", "https://example.com/child.png", FamilyRole.CHILD, false));
    }

    // 구성원 ID가 없는 사용자가 내 가족을 조회하면 FAMILY_NOT_FOUND를 반환한다.
    @Test
    void 미가입_사용자는_내_가족을_조회할_수_없다() {
        when(userService.getUser(7L)).thenReturn(User.create("우가"));

        assertThatThrownBy(() -> service.getMyFamily(7L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_NOT_FOUND));
    }

    // 부모에게 현재 코드와 가입 순서대로 짝지은 사용자를 반환하며, 자녀 연결 여부는 첫 입장 시각 유무를 따른다.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 부모에게_현재_코드와_구성원별_연결_상태를_가입_순서대로_반환한다(boolean connected) {
        Family family = Family.create();
        ReflectionTestUtils.setField(family, "id", 5L);
        User parent = user(8L, "우가", 21L);
        User child = user(7L, "차카", 22L);
        ReflectionTestUtils.setField(child, "profileImageUrl", "https://example.com/child.png");
        FamilyMember parentMember = member(21L, 5L, FamilyRole.PARENT);
        FamilyMember childMember = member(22L, 5L, FamilyRole.CHILD);
        if (connected) {
            childMember.connect(LocalDateTime.parse("2026-11-24T05:01:00"));
        }
        when(userService.getUser(8L)).thenReturn(parent);
        when(familyMemberRepository.findById(21L)).thenReturn(Optional.of(parentMember));
        when(familyRepository.findById(5L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.findAllByFamilyIdOrderByIdAsc(5L)).thenReturn(List.of(parentMember, childMember));
        when(userService.listUsersByFamilyMemberIds(List.of(21L, 22L))).thenReturn(List.of(child, parent));

        when(inviteCodeStore.find(5L)).thenReturn(Optional.of(INVITE_CODE));

        FamilyResponse response = service.getMyFamily(8L);

        assertThat(response.familyId()).isEqualTo(5L);
        assertThat(response.inviteCode()).isEqualTo(INVITE_CODE.code());
        assertThat(response.inviteCodeExpiresAt()).isEqualTo(INVITE_CODE.expiresAt());
        assertThat(response.members()).containsExactly(
            new FamilyMemberResponse(8L, "우가", null, FamilyRole.PARENT, true),
            new FamilyMemberResponse(7L, "차카", "https://example.com/child.png", FamilyRole.CHILD, connected));
    }

    // 자녀가 자기 가족을 조회하면 초대코드 저장소에 접근하지 않고 코드와 만료 시각을 null로 반환한다.
    @Test
    void 자녀에게는_초대코드를_공개하지_않는다() {
        Family family = Family.create();
        ReflectionTestUtils.setField(family, "id", 5L);
        User child = user(7L, "차카", 22L);
        FamilyMember childMember = member(22L, 5L, FamilyRole.CHILD);
        when(userService.getUser(7L)).thenReturn(child);
        when(familyMemberRepository.findById(22L)).thenReturn(Optional.of(childMember));
        when(familyRepository.findById(5L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.findAllByFamilyIdOrderByIdAsc(5L)).thenReturn(List.of(childMember));
        when(userService.listUsersByFamilyMemberIds(List.of(22L))).thenReturn(List.of(child));

        FamilyResponse response = service.getMyFamily(7L);

        assertThat(response.inviteCode()).isNull();
        assertThat(response.inviteCodeExpiresAt()).isNull();
        verifyNoInteractions(inviteCodeStore);
    }

    // 등록되지 않은 코드로 초대를 조회하면 FAMILY_INVITE_CODE_NOT_FOUND로 거절한다.
    @Test
    void 없는_코드로_초대를_조회할_수_없다() {
        assertThatThrownBy(() -> service.getInvite("OLD"))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_INVITE_CODE_NOT_FOUND));
    }

    // 부모 사용자 조회 순서가 뒤바뀌어도 가입 순서로 닉네임을 반환하고, 자녀 구성원이 있을 때만 childJoined가 참이다.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 초대_조회는_가입_순서의_부모_닉네임과_자녀_여부를_반환한다(boolean childJoined) {
        FamilyMember first = member(21L, 5L, FamilyRole.PARENT);
        FamilyMember second = member(23L, 5L, FamilyRole.PARENT);
        when(familyRepository.findByInviteCode("ABCD2345")).thenReturn(Optional.of(family()));
        when(familyMemberRepository.findAllByFamilyIdOrderByIdAsc(5L)).thenReturn(childJoined
            ? List.of(first, member(22L, 5L, FamilyRole.CHILD), second) : List.of(first, second));
        when(userService.listUsersByFamilyMemberIds(List.of(21L, 23L)))
            .thenReturn(List.of(user(7L, "가람", 23L), user(8L, "우가", 21L)));

        FamilyInviteResponse response = service.getInvite("ABCD2345");

        assertThat(response.parentNicknames()).containsExactly("우가", "가람");
        assertThat(response.childJoined()).isEqualTo(childJoined);
    }

    // 소문자와 앞뒤 공백이 섞인 코드로 조회하면 대문자 코드의 가족을 찾고, 입장하면 그 가족의 기존 자녀 ID를 반환한다.
    @Test
    void 초대_조회와_입장은_코드의_대소문자와_앞뒤_공백을_무시한다() {
        Family family = family();
        FamilyMember child = member(22L, 5L, FamilyRole.CHILD);
        when(familyRepository.findByInviteCode("ABCD2345")).thenReturn(Optional.of(family));
        when(familyRepository.findForChildJoinByInviteCode("ABCD2345")).thenReturn(Optional.of(family));
        when(familyMemberRepository.findAllByFamilyIdOrderByIdAsc(5L)).thenReturn(List.of(child));
        when(familyMemberRepository.findAllByFamilyIdAndFamilyRole(5L, FamilyRole.CHILD)).thenReturn(List.of(child));
        when(userService.listUserIdsByFamilyMemberIds(List.of(22L))).thenReturn(List.of(43L));

        assertThat(service.getInvite(" abcd2345 ").childJoined()).isTrue();
        assertThat(service.joinAsChild(" abcd2345 ", null)).isEqualTo(new ChildJoinResult(43L, false));
    }

    // 첫 입장이면 받은 이름으로 사용자를 만들고 초대된 가족의 CHILD 구성원과 연결한 뒤 신규 사용자로 반환한다.
    @Test
    void 첫_입장은_사용자와_자녀_구성원을_연결한다() {
        when(familyRepository.findForChildJoinByInviteCode("ABCD2345")).thenReturn(Optional.of(family()));
        when(userService.createUser("차카")).thenReturn(user(43L, "차카", null));
        when(familyMemberRepository.save(any(FamilyMember.class))).thenAnswer(invocation -> {
            FamilyMember child = invocation.getArgument(0);
            assertThat(child.getFamilyId()).isEqualTo(5L);
            assertThat(child.getFamilyRole()).isEqualTo(FamilyRole.CHILD);
            ReflectionTestUtils.setField(child, "id", 22L);
            return child;
        });

        assertThat(service.joinAsChild("ABCD2345", "차카")).isEqualTo(new ChildJoinResult(43L, true));
        verify(userService).joinFamily(43L, 22L);
    }

    // 이름을 생략하거나 다른 이름을 보내도 이미 가입한 자녀의 ID를 반환하고 사용자·구성원을 추가하지 않는다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "다른 이름")
    void 재입장은_기존_자녀로_로그인한다(String nickname) {
        when(familyRepository.findForChildJoinByInviteCode("ABCD2345")).thenReturn(Optional.of(family()));
        when(familyMemberRepository.findAllByFamilyIdAndFamilyRole(5L, FamilyRole.CHILD))
            .thenReturn(List.of(member(22L, 5L, FamilyRole.CHILD)));
        when(userService.listUserIdsByFamilyMemberIds(List.of(22L))).thenReturn(List.of(43L));

        assertThat(service.joinAsChild("ABCD2345", nickname)).isEqualTo(new ChildJoinResult(43L, false));
        verify(userService, never()).createUser(any());
        verify(familyMemberRepository, never()).save(any());
    }

    // 첫 입장에 이름이 없거나 공백뿐이면 INVALID_REQUEST를 반환하고 사용자·구성원을 생성하지 않는다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "   ")
    void 첫_입장에는_자녀_이름이_필요하다(String nickname) {
        when(familyRepository.findForChildJoinByInviteCode("ABCD2345")).thenReturn(Optional.of(family()));

        assertThatThrownBy(() -> service.joinAsChild("ABCD2345", nickname))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(userService);
        verify(familyMemberRepository, never()).save(any());
    }

    // 등록되지 않은 코드로 입장하면 FAMILY_INVITE_CODE_NOT_FOUND를 반환하고 사용자·구성원을 생성하지 않는다.
    @Test
    void 없는_코드로_입장할_수_없다() {
        assertThatThrownBy(() -> service.joinAsChild("OLD", "차카"))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_INVITE_CODE_NOT_FOUND));
        verifyNoInteractions(userService, familyMemberRepository);
    }

    // 가족에 가입하지 않은 사용자가 재발급을 요청하면 FAMILY_NOT_FOUND를 반환한다.
    @Test
    void 미가입_사용자는_초대코드를_재발급할_수_없다() {
        when(userService.getUser(7L)).thenReturn(user(7L, "우가", null));

        assertThatThrownBy(() -> service.reissueInviteCode(7L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FAMILY_NOT_FOUND));
    }

    // 자녀가 재발급을 요청하면 FORBIDDEN을 반환하고 새 코드를 생성하지 않는다.
    @Test
    void 자녀는_초대코드를_재발급할_수_없다() {
        when(userService.getUser(43L)).thenReturn(user(43L, "차카", 22L));
        when(familyMemberRepository.findById(22L)).thenReturn(Optional.of(member(22L, 5L, FamilyRole.CHILD)));

        assertThatThrownBy(() -> service.reissueInviteCode(43L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(inviteCodeGenerator);
    }

    // 부모가 재발급을 요청하면 해당 가족의 기존 코드를 생성기가 발급한 코드로 교체하고 새 코드를 반환한다.
    @Test
    void 부모는_가족의_초대코드를_교체한다() {
        Family family = family();
        when(userService.getUser(7L)).thenReturn(user(7L, "우가", 21L));
        when(familyMemberRepository.findById(21L)).thenReturn(Optional.of(member(21L, 5L, FamilyRole.PARENT)));
        when(familyRepository.findById(5L)).thenReturn(Optional.of(family));
        when(inviteCodeGenerator.generate()).thenReturn("WXYZ6789");

        FamilyInviteCodeResponse response = service.reissueInviteCode(7L);

        assertThat(family.getInviteCode()).isEqualTo("WXYZ6789");
        assertThat(response.inviteCode()).isEqualTo("WXYZ6789");
    }

    private Family family() {
        Family family = Family.create("ABCD2345");
        ReflectionTestUtils.setField(family, "id", 5L);
        return family;
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
