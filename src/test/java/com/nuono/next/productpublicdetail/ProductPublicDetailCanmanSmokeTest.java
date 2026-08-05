package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.productpublicdetail.datapull.Dp05ProductDetailJob;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Keeps the old opt-in Canman writer from returning as a hidden production bypass. */
class ProductPublicDetailCanmanSmokeTest {

    @Test
    void canmanDailyPullIsOwnedOnlyByTheManagedDp05Job() throws Exception {
        assertTrue(Dp05ProductDetailJob.class.getName().endsWith("Dp05ProductDetailJob"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/nuono/next/productpublicdetail/"
                        + "ProductPublicDetailController.java"
        ));
        String service = Files.readString(Path.of(
                "src/main/java/com/nuono/next/productpublicdetail/"
                        + "ProductPublicDetailSyncService.java"
        ));

        assertFalse(controller.contains("/sync-tasks"));
        assertFalse(controller.contains("@PostMapping"));
        assertFalse(service.contains("submitManual("));
        assertFalse(service.contains("submitScheduled("));
        assertFalse(service.contains("operationalTaskService.start("));
    }
}
