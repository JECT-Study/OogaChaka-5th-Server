package com.ject.oogachaka.family.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.auth.dto.TokenResponse;
import com.ject.oogachaka.auth.service.TokenService;
import com.ject.oogachaka.family.dto.FamilyInviteCodeResponse;
import com.ject.oogachaka.family.dto.FamilyInviteResponse;
import com.ject.oogachaka.family.dto.FamilyMemberResponse;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.service.ChildJoinResult;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 가족 API의 인증 사용자 ID 전달, 요청 검증과 HTTP 응답을 검증한다. 가족 서비스와 토큰 서비스는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class FamilyControllerTest {

    @Mock
    private FamilyService service;

    @Mock
    private TokenService tokens;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FamilyController(service, tokens))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // 인증 사용자가 가족 생성을 요청하면 해당 사용자 ID로 스텁한 가족 ID·초대코드·부모 구성원 정보를 201 CREATED로 반환한다.
    @Test
    void 생성하면_201과_가족_정보를_반환한다() throws Exception {
        FamilyMemberResponse parent = new FamilyMemberResponse(7L, "우가", "https://example.com/me.png", FamilyRole.PARENT);
        when(service.createFamily(7L)).thenReturn(new FamilyResponse(5L, "ABCD2345", List.of(parent)));

        mvc.perform(post("/api/v1/families"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.familyId").value(5))
            .andExpect(jsonPath("$.data.inviteCode").value("ABCD2345"))
            .andExpect(jsonPath("$.data.members[0].userId").value(7))
            .andExpect(jsonPath("$.data.members[0].nickname").value("우가"))
            .andExpect(jsonPath("$.data.members[0].profileImageUrl").value("https://example.com/me.png"))
            .andExpect(jsonPath("$.data.members[0].familyRole").value("PARENT"));
    }

    // 인증 사용자가 내 가족을 조회하면 해당 사용자 ID로 스텁한 가족 ID를 200 SUCCESS로 반환한다.
    @Test
    void 내_가족을_조회하면_200과_가족을_반환한다() throws Exception {
        when(service.getMyFamily(7L)).thenReturn(new FamilyResponse(5L, "ABCD2345", List.of()));

        mvc.perform(get("/api/v1/families/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.familyId").value(5));
    }

    // 서비스가 기존 가족 가입을 이유로 생성을 거절하면 409 FAMILY_ALREADY_JOINED로 응답한다.
    @Test
    void 이미_가입한_사용자의_생성_요청은_409를_반환한다() throws Exception {
        when(service.createFamily(7L)).thenThrow(new BusinessException(ErrorCode.FAMILY_ALREADY_JOINED));

        mvc.perform(post("/api/v1/families"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FAMILY_ALREADY_JOINED"));
    }

    // 공개 초대 조회의 data에는 부모 닉네임 목록과 자녀 가입 여부 두 필드만 담긴다.
    @Test
    void 초대_조회는_공개할_두_필드만_반환한다() throws Exception {
        when(service.getInvite("ABCD2345")).thenReturn(new FamilyInviteResponse(List.of("우가"), true));

        mvc.perform(get("/api/v1/families/invites/ABCD2345"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.parentNicknames[0]").value("우가"))
            .andExpect(jsonPath("$.data.childJoined").value(true));
    }

    // 이름 없이 재입장하면 확인된 자녀 ID로 발급한 토큰과 신규 여부를 소셜 로그인 응답 형식으로 반환한다.
    @Test
    void 입장은_자녀의_토큰과_로그인_결과를_반환한다() throws Exception {
        when(service.joinAsChild("ABCD2345", null)).thenReturn(new ChildJoinResult(43L, false));
        when(tokens.issue(43L)).thenReturn(new TokenResponse("Bearer", "access", "refresh", 1800, 1209600));

        mvc.perform(post("/api/v1/families/join").contentType("application/json")
                .content("{\"inviteCode\":\"ABCD2345\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(43))
            .andExpect(jsonPath("$.data.isNewUser").value(false))
            .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.tokens.accessToken").value("access"))
            .andExpect(jsonPath("$.data.tokens.refreshToken").value("refresh"))
            .andExpect(jsonPath("$.data.tokens.accessTokenExpiresIn").value(1800))
            .andExpect(jsonPath("$.data.tokens.refreshTokenExpiresIn").value(1209600));
    }

    // 초대코드가 공백이면 서비스 호출 전에 400 INVALID_REQUEST로 거절한다.
    @Test
    void 공백_초대코드로_입장할_수_없다() throws Exception {
        mvc.perform(post("/api/v1/families/join").contentType("application/json")
                .content("{\"inviteCode\":\"   \",\"nickname\":\"차카\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service, tokens);
    }

    // 31자 이름을 보내면 서비스 호출 전에 400 INVALID_REQUEST로 거절한다.
    @Test
    void 입장_이름은_30자를_넘을_수_없다() throws Exception {
        mvc.perform(post("/api/v1/families/join").contentType("application/json")
                .content("{\"inviteCode\":\"ABCD2345\",\"nickname\":\"" + "가".repeat(31) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service, tokens);
    }

    // 재발급을 요청하면 인증 사용자의 ID로 발급된 새 코드를 200 SUCCESS로 반환한다.
    @Test
    void 인증_사용자의_재발급_코드를_반환한다() throws Exception {
        when(service.reissueInviteCode(7L)).thenReturn(new FamilyInviteCodeResponse("WXYZ6789"));

        mvc.perform(post("/api/v1/families/me/invite-code"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.inviteCode").value("WXYZ6789"));
    }
}
