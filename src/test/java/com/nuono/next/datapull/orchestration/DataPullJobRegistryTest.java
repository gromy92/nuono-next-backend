package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataPullJobRegistryTest {

    @Test
    void rejectsDuplicateImplementationsForOneOperation() {
        DataPullJob first = job(OperationCode.DP04);
        DataPullJob duplicate = job(OperationCode.DP04);

        assertThrows(
                IllegalArgumentException.class,
                () -> new DataPullJobRegistry(List.of(first, duplicate))
        );
    }

    @Test
    void missingImplementationFailsClosed() {
        DataPullJobRegistry registry = new DataPullJobRegistry(List.of(job(OperationCode.DP04)));

        assertThrows(IllegalStateException.class, () -> registry.require(OperationCode.DP05));
    }

    private DataPullJob job(OperationCode operationCode) {
        return new TestDataPullJob(
                operationCode,
                "noon-partner",
                List.of(),
                ignored -> AdvanceResult.succeeded()
        );
    }
}
