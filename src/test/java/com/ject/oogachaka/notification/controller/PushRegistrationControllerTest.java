package com.ject.oogachaka.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.notification.dto.PushRegistrationRequest;
import com.ject.oogachaka.notification.dto.PushUnregistrationRequest;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.PushRegistrationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 요청 검증과 인증 사용자 ID 전달만 확인한다. 서비스는 목 객체다. */
@ExtendWith(MockitoExtension.class)
class PushRegistrationControllerTest {

    @Mock
    private PushRegistrationService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PushRegistrationController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // 등록 요청은 인증된 사용자 ID와 함께 서비스로 넘기고 공통 성공 응답을 반환한다.
    @Test
    void 등록하면_인증_사용자_ID로_서비스를_호출한다() throws Exception {
        mvc.perform(put("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"dummy-token\",\"devicePlatform\":\"IOS\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(service).register(eq(7L), eq(new PushRegistrationRequest("dummy-token", DevicePlatform.IOS)));
    }

    // 빈 토큰, 플랫폼 누락, 없는 플랫폼은 서비스 호출 전에 400 INVALID_REQUEST로 거절한다.
    @Test
    void 잘못된_등록_요청은_400을_반환한다() throws Exception {
        for (String body : List.of("{\"token\":\" \",\"devicePlatform\":\"WEB\"}", "{\"token\":\"dummy-token\"}",
            "{\"token\":\"dummy-token\",\"devicePlatform\":\"OTHER\"}")) {
            mvc.perform(put("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    // 해제 요청도 인증된 사용자 ID와 함께 서비스로 넘긴다.
    @Test
    void 해제하면_인증_사용자_ID로_서비스를_호출한다() throws Exception {
        mvc.perform(delete("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"dummy-token\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(service).unregister(eq(7L), any(PushUnregistrationRequest.class));
    }
}
