package com.nuono.next.datapull.cutover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DataPullRuntimeCutoverManifestDatabaseTest {

    @Test
    void usesMysqlReadOnlyConsistentSnapshotSyntax() {
        assertEquals(
                "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY",
                DataPullRuntimeCutoverManifestDatabase.READ_ONLY_SNAPSHOT_SQL
        );
    }
}
