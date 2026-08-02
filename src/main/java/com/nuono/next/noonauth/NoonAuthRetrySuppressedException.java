package com.nuono.next.noonauth;

/** Raised when the same task fails again under the cookie version already recovered for it. */
public class NoonAuthRetrySuppressedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public NoonAuthRetrySuppressedException(String message) {
        super(message);
    }
}
