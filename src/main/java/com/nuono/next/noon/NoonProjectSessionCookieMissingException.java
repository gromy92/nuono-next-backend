package com.nuono.next.noon;

/** A successful project-session response did not establish a reusable browser session. */
final class NoonProjectSessionCookieMissingException extends IllegalStateException {

    NoonProjectSessionCookieMissingException() {
        super("Noon session/create 未返回有效 Cookie。");
    }
}
