package com.nuono.next.noon;

@FunctionalInterface
public interface NoonEmailOtpReader {

    String readOtp(String email, String mailAuthCode);
}
