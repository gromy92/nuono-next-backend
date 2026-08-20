package com.nuono.next.noon;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Compatibility boundary retained only for DP paths excluded from automatic replay. */
@Component
@Profile("local-db")
final class NoonAccountSessionAttention implements NoonAccountSessionAttentionPort {
    @Override
    public void requireManualLogin() {
        // Compatibility boundary for DP paths that are deliberately excluded from automatic replay.
    }

    @Override
    public boolean blocksProviderCalls() {
        return false;
    }
}
