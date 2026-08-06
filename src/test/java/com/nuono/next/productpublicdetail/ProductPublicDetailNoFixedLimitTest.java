package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProductPublicDetailNoFixedLimitTest {

    @Test
    void legacyOperationalTaskWriterIsAbsent() throws Exception {
        assertFalse(Arrays.stream(ProductPublicDetailSyncService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch((name) -> name.equals("submitManual")
                        || name.equals("submitScheduled")
                        || name.equals("submitTask")
                        || name.equals("runTask")));

        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/productpublicdetail/"
                        + "ProductPublicDetailSyncService.java"
        ));
        assertFalse(source.contains("operationalTaskService.start("));
        assertFalse(source.contains("OperationalTaskPayload"));
    }
}
