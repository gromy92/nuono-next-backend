package com.nuono.next.datapull.orchestration;

import java.time.Instant;

/** Kernel Interface for one bounded schedule reconciliation phase. */
@FunctionalInterface
public interface DataPullRuntimeReconciler {

    int reconcileAt(Instant observedAt);
}
