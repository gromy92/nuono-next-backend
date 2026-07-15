package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfficialWarehouseBatchSelectionValidatorTest {

    @Test
    void reportsMissingProductsGroupedByBatchWithoutBlockingPartialSelection() {
        OfficialWarehouseBatchSelectionValidator.Result result = OfficialWarehouseBatchSelectionValidator.validate(
                List.of(
                        allocation("10", "A05730775PN", "PSKU-A", "商品 A", 10),
                        allocation("10", "A05730775PN", "PSKU-B", "商品 B", 5),
                        allocation("11", "BATCH-B", "PSKU-C", "商品 C", 8)
                ),
                Map.of("PSKU-A", 10, "PSKU-C", 3)
        );

        assertThat(result.getMissingBatches()).hasSize(2);
        assertThat(result.getMissingBatches().get(0).getBatchNo()).isEqualTo("A05730775PN");
        assertThat(result.getMissingBatches().get(0).getItems())
                .extracting(OfficialWarehouseBatchSelectionValidator.MissingItem::getProductKey,
                        OfficialWarehouseBatchSelectionValidator.MissingItem::getMissingQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PSKU-B", 5));
        assertThat(result.getMissingBatches().get(1).getItems())
                .extracting(OfficialWarehouseBatchSelectionValidator.MissingItem::getProductKey,
                        OfficialWarehouseBatchSelectionValidator.MissingItem::getMissingQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PSKU-C", 5));
    }

    @Test
    void allocatesSelectedQuantityAcrossBatchesInRequestOrder() {
        OfficialWarehouseBatchSelectionValidator.Result result = OfficialWarehouseBatchSelectionValidator.validate(
                List.of(
                        allocation("10", "FIRST", "PSKU-A", "商品 A", 4),
                        allocation("11", "SECOND", "PSKU-A", "商品 A", 6)
                ),
                Map.of("PSKU-A", 7)
        );

        assertThat(result.getMissingBatches()).hasSize(1);
        assertThat(result.getMissingBatches().get(0).getBatchNo()).isEqualTo("SECOND");
        assertThat(result.getMissingBatches().get(0).getItems().get(0).getMissingQuantity()).isEqualTo(3);
    }

    @Test
    void returnsNoWarningWhenEveryScopedBatchQuantityIsCovered() {
        OfficialWarehouseBatchSelectionValidator.Result result = OfficialWarehouseBatchSelectionValidator.validate(
                List.of(allocation("10", "A05730775PN", "PSKU-A", "商品 A", 12)),
                Map.of("PSKU-A", 12)
        );

        assertThat(result.getMissingBatches()).isEmpty();
    }

    private OfficialWarehouseBatchSelectionValidator.Allocation allocation(
            String batchId,
            String batchNo,
            String productKey,
            String title,
            int quantity
    ) {
        return new OfficialWarehouseBatchSelectionValidator.Allocation(
                batchId,
                batchNo,
                productKey,
                title,
                productKey,
                "NOON-" + productKey,
                quantity
        );
    }
}
