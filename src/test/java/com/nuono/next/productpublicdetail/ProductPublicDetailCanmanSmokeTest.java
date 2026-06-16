package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nuono.next.NuonoNextApplication;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private ProductPublicDetailSyncService syncService;

    @Autowired
    private OperationalTaskService operationalTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void smokeOneCanmanProductIntoDailySnapshot() throws Exception {
        assumeTrue(Boolean.getBoolean("nuono.product-public-detail.canman-smoke"));

        LocalDateTime submittedAfter = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(5);
        ProductPublicDetailTaskView submitted = syncService.submitManual(context(), STORE_CODE, SITE_CODE);
        OperationalTask task = waitForTask(submitted.getId());
        assertTrue(task.getStatus().isTerminal());

        ProductPublicDetailSnapshot snapshot = selectLatestTodaySnapshotSince(submittedAfter);
        assertNotNull(snapshot);
        assertNotNull(snapshot.getSyncStatus());

        writeEvidence(task, snapshot);
    }

    private ProductPublicDetailSnapshot selectLatestTodaySnapshotSince(LocalDateTime submittedAfter) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String sql = "SELECT id, owner_user_id, logical_store_id, store_code, site_code, "
                + "product_master_id, product_variant_id, product_site_offer_id, partner_sku, sku_parent, "
                + "noon_product_code, code_type, source_platform, sync_status, failure_code, failure_message, "
                + "fact_date, is_latest, provider_source_url "
                + "FROM product_public_detail_snapshot "
                + "WHERE owner_user_id = ? "
                + "  AND UPPER(store_code) = ? "
                + "  AND UPPER(site_code) = ? "
                + "  AND source_platform = 'NOON' "
                + "  AND fact_date = ? "
                + "  AND is_deleted = b'0' "
                + "  AND gmt_updated >= ? "
                + "ORDER BY gmt_updated DESC, id DESC "
                + "LIMIT 1";
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, OWNER_USER_ID);
            ps.setString(2, STORE_CODE);
            ps.setString(3, SITE_CODE);
            ps.setDate(4, java.sql.Date.valueOf(today));
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(submittedAfter));
        }, rs -> rs.next() ? mapSnapshot(rs) : null);
    }

    private ProductPublicDetailSnapshot mapSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setId(rs.getLong("id"));
        snapshot.setOwnerUserId(rs.getLong("owner_user_id"));
        snapshot.setLogicalStoreId(rs.getLong("logical_store_id"));
        snapshot.setStoreCode(rs.getString("store_code"));
        snapshot.setSiteCode(rs.getString("site_code"));
        snapshot.setProductMasterId(rs.getLong("product_master_id"));
        snapshot.setProductVariantId(rs.getLong("product_variant_id"));
        snapshot.setProductSiteOfferId(rs.getLong("product_site_offer_id"));
        snapshot.setPartnerSku(rs.getString("partner_sku"));
        snapshot.setSkuParent(rs.getString("sku_parent"));
        snapshot.setNoonProductCode(rs.getString("noon_product_code"));
        snapshot.setCodeType(rs.getString("code_type"));
        snapshot.setSourcePlatform(rs.getString("source_platform"));
        snapshot.setSyncStatus(ProductPublicDetailSyncStatus.valueOf(rs.getString("sync_status")));
        snapshot.setFailureCode(rs.getString("failure_code"));
        snapshot.setFailureMessage(rs.getString("failure_message"));
        snapshot.setFactDate(rs.getDate("fact_date").toLocalDate());
        snapshot.setLatest(rs.getBoolean("is_latest"));
        snapshot.setProviderSourceUrl(rs.getString("provider_source_url"));
        return snapshot;
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
        Path evidence = evidenceDir.resolve("canman-smoke-" + LocalDate.now(ZoneId.of("Asia/Shanghai")) + ".md");
        String body = "# canman 商品前台详情同步 smoke\n\n"
                + "- ownerUserId: " + OWNER_USER_ID + "\n"
                + "- storeCode: " + STORE_CODE + "\n"
                + "- siteCode: " + SITE_CODE + "\n"
                + "- productMasterId: " + snapshot.getProductMasterId() + "\n"
                + "- productVariantId: " + snapshot.getProductVariantId() + "\n"
                + "- productSiteOfferId: " + snapshot.getProductSiteOfferId() + "\n"
                + "- noonProductCode: " + snapshot.getNoonProductCode() + "\n"
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
