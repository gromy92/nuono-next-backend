package com.nuono.next.datapull.cutover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataPullRuntimeCutoverManifestDatabaseTest {

    @Test
    void usesMysqlReadOnlyConsistentSnapshotSyntax() {
        assertEquals(
                "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY",
                DataPullRuntimeCutoverManifestDatabase.READ_ONLY_SNAPSHOT_SQL
        );
    }

    @Test
    void retainsThePreviousDp08bBusinessDateBetweenMidnightAndTwoAmShanghai() {
        assertEquals(
                LocalDate.of(2026, 8, 20),
                DataPullRuntimeCutoverManifestDatabase.latestDueDp08bFactDate(
                        LocalDateTime.of(2026, 8, 20, 16, 12)
                )
        );
        assertEquals(
                LocalDate.of(2026, 8, 20),
                DataPullRuntimeCutoverManifestDatabase.latestDueDp08bFactDate(
                        LocalDateTime.of(2026, 8, 20, 17, 59, 59)
                )
        );
    }

    @Test
    void advancesTheDp08bBusinessDateAtTwoAmShanghai() {
        assertEquals(
                LocalDate.of(2026, 8, 21),
                DataPullRuntimeCutoverManifestDatabase.latestDueDp08bFactDate(
                        LocalDateTime.of(2026, 8, 20, 18, 0)
                )
        );
    }
}
