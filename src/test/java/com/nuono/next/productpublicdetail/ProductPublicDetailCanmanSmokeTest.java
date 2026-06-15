package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nuono.next.NuonoNextApplication;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = NuonoNextApplication.class,
        properties = {
                "nuono.product-public-detail.scheduler.enabled=false",
                "nuono.product-public-detail.scheduler.max-products-per-task=1"
        }
)
@ActiveProfiles("local-db")
class ProductPublicDetailCanmanSmokeTest {
    private static final Long OWNER_USER_ID = 307L;
    private static final String STORE_CODE = "STR108065-NAE";
    private static final String SITE_CODE = "AE";
    private static final Long PRODUCT_MASTER_ID = 52654L;
    private static final Long PRODUCT_VARIANT_ID = 53600L;
    private static final String NOON_PRODUCT_CODE = "ZE77E911445B6633FC201Z";

    @Autowired
    private ProductPublicDetailSyncService syncService;

    @Autowired
    private OperationalTaskService operationalTaskService;

    @Autowired
    private ProductPublicDetailMapper mapper;

    @Test
    void smokeOneCanmanProductIntoDailySnapshot() throws Exception {
        assumeTrue(Boolean.getBoolean("nuono.product-public-detail.canman-smoke"));

        ProductPublicDetailTaskView submitted = syncService.submitManual(context(), STORE_CODE, SITE_CODE);
        OperationalTask task = waitForTask(submitted.getId());
        assertTrue(task.getStatus().isTerminal());

        ProductPublicDetailSnapshot snapshot = mapper.selectDailySnapshot(
                PRODUCT_MASTER_ID,
                PRODUCT_VARIANT_ID,
                SITE_CODE,
                "NOON",
                LocalDate.now(ZoneId.of("Asia/Shanghai"))
        );
        assertNotNull(snapshot);
        assertNotNull(snapshot.getSyncStatus());

        writeEvidence(task, snapshot);
    }

    private OperationalTask waitForTask(Long taskId) throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            Optional<OperationalTask> task = operationalTaskService.find(taskId);
            if (task.isPresent() && task.get().getStatus() != null && task.get().getStatus().isTerminal()) {
                return task.get();
            }
            Thread.sleep(500L);
        }
        return operationalTaskService.find(taskId).orElseThrow();
    }

    private void writeEvidence(OperationalTask task, ProductPublicDetailSnapshot snapshot) throws Exception {
        Path evidenceDir = Path.of(".scratch/product-public-detail-sync");
        Files.createDirectories(evidenceDir);
        Path evidence = evidenceDir.resolve("canman-smoke-2026-06-15.md");
        String body = "# canman 商品前台详情同步 smoke\n\n"
                + "- ownerUserId: " + OWNER_USER_ID + "\n"
                + "- storeCode: " + STORE_CODE + "\n"
                + "- siteCode: " + SITE_CODE + "\n"
                + "- productMasterId: " + PRODUCT_MASTER_ID + "\n"
                + "- productVariantId: " + PRODUCT_VARIANT_ID + "\n"
                + "- noonProductCode: " + NOON_PRODUCT_CODE + "\n"
                + "- taskId: " + task.getId() + "\n"
                + "- taskStatus: " + task.getStatus() + "\n"
                + "- taskMessage: " + nullSafe(task.getMessage()) + "\n"
                + "- snapshotId: " + snapshot.getId() + "\n"
                + "- snapshotStatus: " + snapshot.getSyncStatus() + "\n"
                + "- failureCode: " + nullSafe(snapshot.getFailureCode()) + "\n"
                + "- failureMessage: " + nullSafe(snapshot.getFailureMessage()) + "\n"
                + "- factDate: " + snapshot.getFactDate() + "\n"
                + "- isLatest: " + snapshot.getLatest() + "\n";
        Files.writeString(evidence, body);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.replace('\n', ' ');
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(OWNER_USER_ID)
                .storeCodes(java.util.Set.of(STORE_CODE))
                .storeOwnerUserIds(Map.of(STORE_CODE, OWNER_USER_ID))
                .build();
    }
}
