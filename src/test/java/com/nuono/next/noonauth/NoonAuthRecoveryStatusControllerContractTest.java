package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class NoonAuthRecoveryStatusControllerContractTest {

    @Test
    void accountSessionApiIsReadOnlyAndExposesNoOtpChallengeFields() {
        Method[] methods = NoonAuthRecoveryStatusController.class.getDeclaredMethods();

        assertTrue(Arrays.stream(methods)
                .anyMatch(method -> method.isAnnotationPresent(GetMapping.class)));
        assertFalse(Arrays.stream(methods)
                .anyMatch(method -> method.isAnnotationPresent(PostMapping.class)));
        assertFalse(Arrays.stream(NoonAuthRecoveryStatusView.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .anyMatch(name -> name.contains("otp")
                        || name.contains("challenge")
                        || name.contains("cookie")
                        || name.contains("token")));
    }
}
