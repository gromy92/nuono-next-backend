package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchDiagnosticMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseShippingBatchDiagnosticServiceTest {

    private static final String STORE = "STR69486-NSA";

    @Mock
    private OfficialWarehouseMapper warehouseMapper;
    @Mock
    private OfficialWarehouseShippingBatchDiagnosticMapper diagnosticMapper;

    private OfficialWarehouseShippingBatchDiagnosticService service;

    @BeforeEach
    void setUp() {
        service = new OfficialWarehouseShippingBatchDiagnosticService(warehouseMapper, diagnosticMapper);
        StoreSiteRecord site = new StoreSiteRecord();
        site.ownerUserId = 307L;
        site.storeCode = STORE;
        site.siteCode = "SA";
        when(warehouseMapper.selectStoreSite(307L, STORE, "SA")).thenReturn(site);
    }

    @Test
    void explainsSyncedBoxesWithoutProductDetails() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.packageCount = 3;
        row.sourceCandidateCount = 0;
        row.goodsLineCount = 0;
        when(diagnosticMapper.selectExactBatchDiagnostic(307L, STORE, "SA", "ZDAIR8111341"))
                .thenReturn(row);

        OfficialWarehouseShippingBatchDiagnosticView result = service.diagnose(
                access(), STORE, "sa", " ZDAIR8111341 "
        );

        assertThat(result.code).isEqualTo("NO_PRODUCT_DETAILS");
        assertThat(result.title).isEqualTo("物流批次缺少商品明细");
        assertThat(result.message)
                .contains("ZDAIR8111341")
                .contains("已同步 3 个箱子")
                .contains("重新同步或导入装箱单");
        assertThat(result.action).isEqualTo("补充装箱单商品明细后重新查询");
    }

    @Test
    void keepsDiagnosticReasonsOrderedAndActionable() {
        assertReason(siteMismatch(), "SITE_MISMATCH", "属于 AE 站点");
        assertReason(ineligibleStatus(), "STATUS_NOT_ELIGIBLE", "当前状态为已取消");
        assertReason(sourceScopeMismatch(), "SOURCE_SCOPE_MISMATCH", "不属于当前店铺/站点");
        assertReason(pendingMatch(), "PRODUCT_MATCH_PENDING", "2 条商品仍待条码匹配");
        assertReason(allExcluded(), "ALL_PRODUCTS_EXCLUDED", "商品均已标记为不参与 ASN");
        assertReason(productScopeMismatch(), "PRODUCT_SCOPE_MISMATCH", "当前店铺/站点");
        assertReason(noShippedQuantity(), "NO_SHIPPED_QUANTITY", "发货数量为 0");
        assertReason(noAvailableQuantity(), "NO_AVAILABLE_QUANTITY", "可约仓数量为 0");
    }

    @Test
    void doesNotRevealBatchesOutsideTheResolvedOwnerScope() {
        when(diagnosticMapper.selectExactBatchDiagnostic(307L, STORE, "SA", "OTHER-OWNER-BATCH"))
                .thenReturn(null);

        OfficialWarehouseShippingBatchDiagnosticView result = service.diagnose(
                access(), STORE, "SA", "OTHER-OWNER-BATCH"
        );

        assertThat(result.code).isEqualTo("BATCH_NOT_FOUND");
        assertThat(result.message).contains("当前账号下未找到");
        assertThat(result.message).doesNotContain("其他账号");
    }

    private void assertReason(
            OfficialWarehouseShippingBatchDiagnosticRecord row,
            String code,
            String messagePart
    ) {
        when(diagnosticMapper.selectExactBatchDiagnostic(307L, STORE, "SA", row.batchNo)).thenReturn(row);
        OfficialWarehouseShippingBatchDiagnosticView result = service.diagnose(access(), STORE, "SA", row.batchNo);
        assertThat(result.code).isEqualTo(code);
        assertThat(result.message).contains(messagePart);
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord eligibleBatch() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = new OfficialWarehouseShippingBatchDiagnosticRecord();
        row.id = 901440L;
        row.batchNo = "ZDAIR8111341";
        row.targetStoreCode = "RUH";
        row.targetSiteCode = "SA";
        row.status = "warehouse_received";
        row.latestNodeStatus = "warehouse_received";
        row.goodsLineCount = 1;
        row.resolvedLineCount = 1;
        row.shippedQuantity = 10;
        row.remainingQuantity = 10;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord siteMismatch() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "SITE-MISMATCH";
        row.targetStoreCode = "DXB";
        row.targetSiteCode = "AE";
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord ineligibleStatus() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "CANCELLED";
        row.status = "cancelled";
        row.latestNodeStatus = "cancelled";
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord pendingMatch() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "PENDING-MATCH";
        row.goodsLineCount = 0;
        row.resolvedLineCount = 0;
        row.sourceCandidateCount = 2;
        row.currentScopeCandidateCount = 2;
        row.unmatchedCandidateCount = 2;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord allExcluded() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "ALL-EXCLUDED";
        row.goodsLineCount = 0;
        row.resolvedLineCount = 0;
        row.sourceCandidateCount = 2;
        row.currentScopeCandidateCount = 2;
        row.excludedCandidateCount = 2;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord sourceScopeMismatch() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "SOURCE-SCOPE";
        row.goodsLineCount = 0;
        row.resolvedLineCount = 0;
        row.sourceCandidateCount = 2;
        row.currentScopeCandidateCount = 0;
        row.unmatchedCandidateCount = 2;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord productScopeMismatch() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "PRODUCT-SCOPE";
        row.resolvedLineCount = 0;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord noShippedQuantity() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "NO-SHIPPED";
        row.shippedQuantity = 0;
        row.remainingQuantity = 0;
        return row;
    }

    private OfficialWarehouseShippingBatchDiagnosticRecord noAvailableQuantity() {
        OfficialWarehouseShippingBatchDiagnosticRecord row = eligibleBatch();
        row.batchNo = "NO-AVAILABLE";
        row.remainingQuantity = 0;
        return row;
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(Map.of(STORE, 307L))
                .build();
    }
}
