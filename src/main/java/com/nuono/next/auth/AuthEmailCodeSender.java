package com.nuono.next.auth;

import java.time.LocalDateTime;

public interface AuthEmailCodeSender {

    void sendLoginCode(String email, String code, LocalDateTime expiresAt);
}
