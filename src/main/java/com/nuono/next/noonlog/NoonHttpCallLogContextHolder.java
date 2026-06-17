package com.nuono.next.noonlog;

import java.util.function.Supplier;

public final class NoonHttpCallLogContextHolder {

    private static final ThreadLocal<NoonHttpCallLogContext> CURRENT = new ThreadLocal<>();

    private NoonHttpCallLogContextHolder() {
    }

    public static NoonHttpCallLogContext current() {
        return CURRENT.get();
    }

    public static <T> T with(NoonHttpCallLogContext context, Supplier<T> supplier) {
        NoonHttpCallLogContext previous = CURRENT.get();
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
