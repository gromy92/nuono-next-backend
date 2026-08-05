package com.nuono.next.datapull.orchestration;

import java.time.Instant;

/** Bounded technical maintenance invoked by the single DP runtime scheduler. */
public interface DataPullRuntimeMaintenance {
    void run(Instant nowUtc);
}
