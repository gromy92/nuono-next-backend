package com.nuono.next.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.MobileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LocalDbMobileAuthServiceTest {

    private LocalMobileSessionStore sessionStore;
    private LocalDbMobileAuthService service;

    @BeforeEach
    void setUp() {
        sessionStore = new LocalMobileSessionStore();
        ReflectionTestUtils.setField(sessionStore, "fixedSmsCode", "246810");
        ReflectionTestUtils.setField(sessionStore, "smsCodeExpireSeconds", 300L);
        service = new LocalDbMobileAuthService(mock(MobileMapper.class), sessionStore, new ObjectMapper());
        ReflectionTestUtils.setField(service, "smsCooldownSeconds", 60);
        ReflectionTestUtils.setField(service, "debugCodeEnabled", false);
    }

    @Test
    void shouldNotExposeDebugCodeByDefault() {
        MobileSendCodeCommand command = new MobileSendCodeCommand();
        command.setPhone("13800000000");

        MobileSendCodeResponse response = service.sendCode(command);

        assertEquals(Boolean.TRUE, response.getSuccess());
        assertNull(response.getDebugCode());
    }

    @Test
    void shouldRejectSmsCodeRequestsInsideCooldownWindow() {
        MobileSendCodeCommand command = new MobileSendCodeCommand();
        command.setPhone("13800000000");

        service.sendCode(command);
        MobileApiException error = assertThrows(MobileApiException.class, () -> service.sendCode(command));

        assertEquals(429, error.getCode());
    }
}
