package com.ject.oogachaka.family.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.family.dto.FamilyCreateRequest;
import com.ject.oogachaka.family.dto.FamilyMemberResponse;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.family.service.FamilyService;
import com.ject.oogachaka.global.exception.BusinessException;
import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.global.response.code.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 가족 API의 입력 검증, 인증 사용자 ID·본문 전달과 HTTP 응답을 검증한다. 가족 서비스는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class FamilyControllerTest {

    @Mock
    private FamilyService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FamilyController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // 인증 사용자가 자녀 프로필을 보내면 해당 ID·본문으로 생성한 코드, UTC 만료 시각과 구성원 연결 여부를 201 CREATED로 반환한다.
    @Test
    void 생성하면_201과_가족_정보를_반환한다() throws Exception {
        FamilyCreateRequest request = new FamilyCreateRequest("차카", "https://example.com/child.png");
        FamilyMemberResponse parent = new FamilyMemberResponse(7L, "우가", "https://example.com/me.png", FamilyRole.PARENT, true);
        FamilyMemberResponse child = new FamilyMemberResponse(8L, "차카", request.childProfileImageUrl(), FamilyRole.CHILD, false);
        when(service.createFamily(7L, request)).thenReturn(new FamilyResponse(5L, "K7MP2Q",
            OffsetDateTime.parse("2026-11-24T05:05:00Z"), List.of(parent, child)));

        mvc.perform(post("/api/v1/families").contentType(MediaType.APPLICATION_JSON).content("""
                {"childNickname":"차카","childProfileImageUrl":"https://example.com/child.png"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.familyId").value(5))
            .andExpect(jsonPath("$.data.inviteCode").value("K7MP2Q"))
            .andExpect(jsonPath("$.data.inviteCodeExpiresAt").value("2026-11-24T05:05:00Z"))
            .andExpect(jsonPath("$.data.members[0].userId").value(7))
            .andExpect(jsonPath("$.data.members[0].nickname").value("우가"))
            .andExpect(jsonPath("$.data.members[0].profileImageUrl").value("https://example.com/me.png"))
            .andExpect(jsonPath("$.data.members[0].familyRole").value("PARENT"))
            .andExpect(jsonPath("$.data.members[0].connected").value(true))
            .andExpect(jsonPath("$.data.members[1].connected").value(false));
    }

    // 닉네임이 누락·공백·31자이거나 이미지 URL이 공백이면 서비스 호출 전에 400 INVALID_REQUEST를 반환한다.
    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"childNickname\":\" \"}",
        "{\"childNickname\":\"1234567890123456789012345678901\"}",
        "{\"childNickname\":\"차카\",\"childProfileImageUrl\":\" \"}"
    })
    void 잘못된_자녀_프로필은_400을_반환한다(String body) throws Exception {
        mvc.perform(post("/api/v1/families").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    // 인증 사용자가 내 가족을 조회하면 해당 사용자 ID로 스텁한 가족 ID를 200 SUCCESS로 반환한다.
    @Test
    void 내_가족을_조회하면_200과_가족을_반환한다() throws Exception {
        when(service.getMyFamily(7L)).thenReturn(new FamilyResponse(5L, null, null, List.of()));

        mvc.perform(get("/api/v1/families/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.familyId").value(5));
    }

    // 서비스가 기존 가족 가입을 이유로 생성을 거절하면 409 FAMILY_ALREADY_JOINED로 응답한다.
    @Test
    void 이미_가입한_사용자의_생성_요청은_409를_반환한다() throws Exception {
        when(service.createFamily(7L, new FamilyCreateRequest("차카", null)))
            .thenThrow(new BusinessException(ErrorCode.FAMILY_ALREADY_JOINED));

        mvc.perform(post("/api/v1/families").contentType(MediaType.APPLICATION_JSON)
                .content("{\"childNickname\":\"차카\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FAMILY_ALREADY_JOINED"));
    }
}
