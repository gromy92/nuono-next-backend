package com.nuono.next.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LocalMobileSessionStoreTest {

    @Test
    void shouldTrackSmsCooldownSeparatelyFromVerification() {
        LocalMobileSessionStore store = new LocalMobileSessionStore();
        ReflectionTestUtils.setField(store, "fixedSmsCode", "246810");
        ReflectionTestUtils.setField(store, "smsCodeExpireSeconds", 300L);

        String code = store.issueSmsCode("13800000000", 60);

        assertEquals("246810", code);
        assertTrue(store.secondsUntilSmsAllowed("13800000000") > 0);
        assertTrue(store.verifySmsCode("13800000000", "246810"));
        assertTrue(store.secondsUntilSmsAllowed("13800000000") > 0);
    }
}
