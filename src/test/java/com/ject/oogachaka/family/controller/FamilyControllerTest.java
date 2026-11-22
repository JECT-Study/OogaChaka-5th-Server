package com.ject.oogachaka.family.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.family.dto.FamilyMemberResponse;
import com.ject.oogachaka.family.dto.FamilyResponse;
import com.ject.oogachaka.family.enums.FamilyRole;
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

/** 가족 API의 인증 사용자 ID 전달과 HTTP 응답을 검증한다. 가족 서비스는 목 객체다. */
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
}
