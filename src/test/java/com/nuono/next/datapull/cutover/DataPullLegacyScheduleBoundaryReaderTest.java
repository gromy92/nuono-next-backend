package com.nuono.next.datapull.cutover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.cutover.DataPullLegacyScheduleBoundaryReader.BoundaryRow;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataPullLegacyScheduleBoundaryReaderTest {
    private static final DataPullScope SCOPE = new DataPullScope(
            307L,
            108065L,
            "account-307",
            "egress-cn-1",
            "PRJ108065",
            "STR108065-NSA",
            "SA",
            "owner=307|store=STR108065-NSA|site=SA"
    );

    @Test
    void earliestAbandonedWindowBecomesTheExclusiveCatchUpBoundary() {
        Map<OperationCode, Map<String, LocalDateTime>> boundaries =
                new DataPullLegacyScheduleBoundaryReader().resolve(List.of(
                        row("SALES", "2026-07-16"),
                        row("SALES", "2026-08-05"),
                        row("ORDER", "2026-08-05")
                ), List.of(SCOPE));

        assertEquals(
                LocalDateTime.of(2026, 7, 16, 16, 0),
                boundaries.get(OperationCode.DP01).get(SCOPE.getStableScopeKey())
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 5, 16, 0),
                boundaries.get(OperationCode.DP02).get(SCOPE.getStableScopeKey())
        );
    }

    @Test
    void missingWindowOrUnmappedScopeFailsClosed() {
        DataPullLegacyScheduleBoundaryReader reader =
                new DataPullLegacyScheduleBoundaryReader();

        assertThrows(IllegalStateException.class, () -> reader.resolve(List.of(
                new BoundaryRow(307L, "STR108065-NSA", "SA", "ORDER", null, 1L)
        ), List.of(SCOPE)));
        assertThrows(IllegalStateException.class, () -> reader.resolve(List.of(
                new BoundaryRow(999L, "missing", "SA", "ORDER",
                        LocalDate.parse("2026-08-05"), 0L)
        ), List.of(SCOPE)));
    }

    private BoundaryRow row(String domain, String targetDateTo) {
        return new BoundaryRow(
                307L,
                "STR108065-NSA",
                "SA",
                domain,
                LocalDate.parse(targetDateTo),
                0L
        );
    }
}
