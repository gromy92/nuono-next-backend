package com.nuono.next.noonpull;

public enum NoonPullRetryAction {
    RETRY,
    DELAY,
    WAIT_FOR_AUTH,
    PAUSE,
    MANUAL_ACTION,
    NONE
}
