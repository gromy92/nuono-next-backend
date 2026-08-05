package com.nuono.next.officialwarehouse.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp07InventorySnapshotRowContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deterministicSingleItemBusinessDefectSkipsOnlyThatItem() {
        InventoryItem unidentified = item("", 5);
        InventoryItem valid = item("GOOD", 3);

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = fetch(
                List.of(unidentified, valid)
        );

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getItems()).singleElement()
                .extracting(Dp07InventorySnapshotItem::getPartnerSku).isEqualTo("GOOD");
        assertThat(result.getValue().getSourceItemCount()).isEqualTo(2);
        assertThat(result.getValue().getBusinessSkippedItemCount()).isEqualTo(1);
        assertThat(result.getValue().getBusinessSkippedComparisonFingerprints()).isEmpty();
    }

    @Test
    void twoPassBusinessSkipsUseStableRawRowFingerprints() {
        var firstOrder = objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 5)
                .put("inventory_type", "saleable").put("raw_marker", "A");
        var reordered = objectMapper.createObjectNode()
                .put("raw_marker", "A").put("inventory_type", "saleable")
                .put("qty", 5).put("warehouse_code", "RUH01");
        var different = reordered.deepCopy().put("raw_marker", "B");

        String first = fetchTwoPass(List.of(InventoryItem.from(firstOrder)))
                .getValue().getBusinessSkippedComparisonFingerprints().get(0);
        String same = fetchTwoPass(List.of(InventoryItem.from(reordered)))
                .getValue().getBusinessSkippedComparisonFingerprints().get(0);
        String changed = fetchTwoPass(List.of(InventoryItem.from(different)))
                .getValue().getBusinessSkippedComparisonFingerprints().get(0);

        assertThat(first).matches("[0-9a-f]{64}").isEqualTo(same).isNotEqualTo(changed);
    }

    @Test
    void unnormalizableSkippedRawRowIsAProviderRowContractFailure() {
        assertThatThrownBy(() -> Dp07BusinessSkippedRowFingerprint.from(null, objectMapper))
                .isInstanceOf(Dp07InventorySnapshotItem.ProviderRowContractException.class);
    }

    @Test
    void targetColumnAndTimestampDefectsSkipOnlyThoseRows() {
        InventoryItem oversizedSku = item("X".repeat(101), 5);
        InventoryItem invalidTimestamp = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 4)
                .put("inventory_type", "saleable").put("partner_sku", "BAD-DATE")
                .put("inventory_snapshot_at", "2026-02-30 10:00:00"));
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 3)
                .put("inventory_type", "saleable").put("partner_sku", "GOOD")
                .put("inventory_snapshot_at", "2026-08-02 23:00:00"));

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = fetch(
                List.of(oversizedSku, invalidTimestamp, valid)
        );

        assertThat(result.getValue().getItems()).singleElement()
                .extracting(Dp07InventorySnapshotItem::getPartnerSku).isEqualTo("GOOD");
        assertThat(result.getValue().getSourceItemCount()).isEqualTo(3);
        assertThat(result.getValue().getBusinessSkippedItemCount()).isEqualTo(2);
    }

    @Test
    void oversizedRawRowFailsTheWholeProviderContainerWithoutReturningFacts() {
        InventoryItem oversized = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 5)
                .put("inventory_type", "saleable").put("partner_sku", "TOO-LARGE")
                .put("unexpected", "X".repeat(Dp07InventoryColumnContract.MAX_RAW_PAYLOAD_BYTES)));

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = fetch(
                List.of(oversized, item("GOOD", 3))
        );

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(result.getSanitizedCode())
                .isEqualTo("DP07A_INVENTORY_CONTAINER_CAPACITY_ERROR");
        assertThat(result.getValue()).isNull();
    }

    @Test
    void acceptedMaximumNaturalFieldsUseABoundedOpaqueStableIdentity() {
        InventoryItem accepted = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "W".repeat(100)).put("qty", 1)
                .put("inventory_type", "I".repeat(100)).put("reason_code", "R".repeat(100))
                .put("partner_sku", "P".repeat(100)).put("sku", "N".repeat(100))
                .put("pbarcode", "Q".repeat(100)).put("barcode", "B".repeat(100))
                .put("country_code", "C".repeat(20))
                .put("classification_code", "L".repeat(100)));

        Dp07InventorySnapshotItem item = Dp07InventorySnapshotItem
                .fromProvider(accepted, objectMapper).orElseThrow();

        assertThat(item.getStableIdentity()).startsWith("inventory:v1:").hasSize(77);
        assertThat(new Dp07InventorySnapshotCodec(objectMapper).encode(item)
                .getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(Dp07InventoryColumnContract.MAX_STAGE_PAYLOAD_BYTES);
    }

    private ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> fetch(
            List<InventoryItem> items
    ) {
        return fetch(items, "fbn-generation-20260802");
    }

    private ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> fetchTwoPass(
            List<InventoryItem> items
    ) {
        return fetch(items, null);
    }

    private ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> fetch(
            List<InventoryItem> items,
            String generationToken
    ) {
        InventoryPage page = new InventoryPage(
                1, false, 1, false, generationToken, null,
                generationToken == null ? null : (long) items.size(),
                items, objectMapper.createObjectNode()
        );
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        when(resolver.resolve(any(com.nuono.next.noonpull.NoonInterfacePullRequest.class)))
                .thenReturn(new NoonPullStoreBinding(
                        307L, "PRJ108065", "STR108065-NSA", "SA", "108065",
                        "user", "session=redacted"
                ));
        Dp07InventorySnapshotProvider provider = new Dp07InventorySnapshotProvider(
                new StubProvider(objectMapper, page), resolver, objectMapper
        );
        return provider.fetchPage(SnapshotPageRequest.from(task(), 1));
    }

    private InventoryItem item(String partnerSku, int quantity) {
        var node = objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", quantity)
                .put("inventory_type", "saleable");
        if (!partnerSku.isEmpty()) node.put("partner_sku", partnerSku);
        return InventoryItem.from(node);
    }

    private DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                7001L, OperationCode.DP07A, "NOON_FBN_INVENTORY", 307L, 8001L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-1",
                LocalDateTime.of(2026, 8, 2, 15, 0), "complete-snapshot:2026-08-02",
                "SNAPSHOT_FETCH", LocalDateTime.of(2026, 8, 2, 15, 0)
        );
        task.setFenceEpoch(1L);
        return task;
    }

    private static final class StubProvider extends OfficialWarehouseFbnInventoryProvider {
        private final InventoryPage page;

        private StubProvider(ObjectMapper objectMapper, InventoryPage page) {
            super(objectMapper, null, null);
            this.page = page;
        }

        @Override
        public InventoryPage fetchPage(PullRequest request, int pageNo) {
            return page;
        }
    }
}
