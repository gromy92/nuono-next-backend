package com.nuono.next.noonauth;

import java.util.Optional;

/** The only business-facing entry point for shared Noon Project authorization recovery. */
public interface NoonAuthWaitQueue {
    Optional<Long> enqueue(NoonAuthWaitRequest request);
}
