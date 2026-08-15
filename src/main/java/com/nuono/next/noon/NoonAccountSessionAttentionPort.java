package com.nuono.next.noon;

/** Reports that the one shared Noon account needs an explicit human login. */
public interface NoonAccountSessionAttentionPort {
    void requireManualLogin();

    boolean blocksProviderCalls();
}
