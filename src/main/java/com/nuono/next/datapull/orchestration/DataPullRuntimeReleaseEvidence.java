package com.nuono.next.datapull.orchestration;

/**
 * Code-level provider for one release prerequisite.
 *
 * <p>Implementations must verify an authoritative source. They must not mirror a property or
 * environment flag.
 */
public interface DataPullRuntimeReleaseEvidence {

    DataPullRuntimeReleaseRequirement requirement();

    boolean verified();
}
