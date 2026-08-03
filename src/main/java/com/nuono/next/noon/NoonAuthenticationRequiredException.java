package com.nuono.next.noon;

/**
 * Structured signal that a Noon operation cannot start or continue until the
 * owning Project session is restored by the shared authorization worker.
 */
public final class NoonAuthenticationRequiredException extends IllegalStateException {

    public NoonAuthenticationRequiredException(String message) {
        super(message);
    }

    public NoonAuthenticationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
