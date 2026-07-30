package com.nuono.next.auth;

public class AuthSessionChangedException extends RuntimeException {

    public AuthSessionChangedException() {
        super("登录状态已变化，请重新登录后再修改密码。");
    }
}
