package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLifecycleCalculationMapperSqlContractTest {

    @Test
    void currentStockSnapshotIsNotTreatedAsEarliestInventoryDate() throws IOException {
        String mapper = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "infrastructure",
                "mapper",
                "ProductLifecycleCalculationMapper.java"
        ));

        assertTrue(mapper.contains("\"  NULL AS earliestInventoryDate,\""));
    }
}
