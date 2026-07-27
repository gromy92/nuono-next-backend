package com.nuono.next.noonauth;

public interface NoonProjectAuthStateSynchronizer {

    void recordVerifiedProjectSession(Long ownerUserId, String projectCode);

    static NoonProjectAuthStateSynchronizer noop() {
        return (ownerUserId, projectCode) -> {
        };
    }
}
