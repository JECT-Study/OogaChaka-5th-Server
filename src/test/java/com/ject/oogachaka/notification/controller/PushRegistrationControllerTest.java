package com.ject.oogachaka.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ject.oogachaka.global.crypto.HmacPurpose;
import com.ject.oogachaka.global.crypto.HmacService;
import com.ject.oogachaka.global.exception.GlobalExceptionHandler;
import com.ject.oogachaka.notification.enums.DevicePlatform;
import com.ject.oogachaka.notification.service.DeviceTokenService;
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

@ExtendWith(MockitoExtension.class)
class PushRegistrationControllerTest {

    @Mock
    private HmacService hmacService;

    @Mock
    private DeviceTokenService deviceTokenService;

    private MockMvc mvc;

    @BeforeEach
    void configureController() {
        var service = new PushRegistrationService(hmacService, deviceTokenService);
        mvc = MockMvcBuilders.standaloneSetup(new PushRegistrationController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putUsesTheLongPrincipalAndReturnsTheCommonResponseWithoutTheToken() throws Exception {
        authenticate();
        when(hmacService.hash("dummy-token", HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");

        mvc.perform(put("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"dummy-token","devicePlatform":"IOS"}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isEmpty());

        verify(deviceTokenService).upsert(7L, "dummy-token", "hash", DevicePlatform.IOS);
    }

    @Test
    void unauthenticatedPutAndDeleteReturnUnauthorized() throws Exception {
        mvc.perform(put("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"dummy-token","devicePlatform":"ANDROID"}
                    """))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(delete("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"dummy-token\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(hmacService, deviceTokenService);
    }

    @Test
    void invalidTokenAndMissingPlatformAreRejectedBeforeHashing() throws Exception {
        authenticate();
        for (String body : List.of("{\"token\":\" \" ,\"devicePlatform\":\"WEB\"}",
            "{\"token\":\"dummy-token\"}", "{\"token\":\"dummy-token\",\"devicePlatform\":\"OTHER\"}",
            "{\"token\":\"" + "a".repeat(4097) + "\",\"devicePlatform\":\"IOS\"}")) {
            mvc.perform(put("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(hmacService, deviceTokenService);
    }

    @Test
    void deleteReleasesOnlyTheCurrentUsersRegistration() throws Exception {
        authenticate();
        when(hmacService.hash("dummy-token", HmacPurpose.DEVICE_TOKEN)).thenReturn("hash");

        mvc.perform(delete("/api/v1/push/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"dummy-token\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(deviceTokenService).unregister(7L, "hash");
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
    }
}
