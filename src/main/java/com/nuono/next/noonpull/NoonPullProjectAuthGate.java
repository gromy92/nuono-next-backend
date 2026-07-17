package com.nuono.next.noonpull;

/** Stops provider calls for one blocked Project without blocking healthy Projects on the same email identity. */
public interface NoonPullProjectAuthGate {
    boolean isBlocked(Long ownerUserId, String projectCode);
}
