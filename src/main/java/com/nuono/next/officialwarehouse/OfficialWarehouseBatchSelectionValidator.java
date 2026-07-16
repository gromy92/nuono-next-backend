package com.nuono.next.officialwarehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OfficialWarehouseBatchSelectionValidator {

    private OfficialWarehouseBatchSelectionValidator() {
    }

    static Result validate(List<Allocation> allocations, Map<String, Integer> selectedQuantities) {
        Map<String, Integer> remainingSelected = new LinkedHashMap<>();
        if (selectedQuantities != null) {
            selectedQuantities.forEach((key, quantity) -> remainingSelected.put(key, positive(quantity)));
        }
        Map<String, MissingBatch> missingByBatch = new LinkedHashMap<>();
        if (allocations != null) {
            for (Allocation allocation : allocations) {
                if (allocation == null || positive(allocation.quantity) <= 0) {
                    continue;
                }
                int availableSelection = remainingSelected.getOrDefault(allocation.productKey, 0);
                int covered = Math.min(positive(allocation.quantity), availableSelection);
                remainingSelected.put(allocation.productKey, availableSelection - covered);
                int missing = positive(allocation.quantity) - covered;
                if (missing <= 0) {
                    continue;
                }
                MissingBatch batch = missingByBatch.computeIfAbsent(
                        allocation.batchId,
                        ignored -> new MissingBatch(allocation.batchId, allocation.batchNo)
                );
                batch.add(allocation, missing);
            }
        }
        return new Result(new ArrayList<>(missingByBatch.values()));
    }

    private static int positive(Integer value) {
        return Math.max(0, value == null ? 0 : value);
    }

    static final class Allocation {
        private final String batchId;
        private final String batchNo;
        private final String productKey;
        private final String title;
        private final String partnerSku;
        private final String noonSku;
        private final Integer quantity;

        Allocation(
                String batchId,
                String batchNo,
                String productKey,
                String title,
                String partnerSku,
                String noonSku,
                Integer quantity
        ) {
            this.batchId = batchId;
            this.batchNo = batchNo;
            this.productKey = productKey;
            this.title = title;
            this.partnerSku = partnerSku;
            this.noonSku = noonSku;
            this.quantity = quantity;
        }
    }

    static final class Result {
        private final List<MissingBatch> missingBatches;

        Result(List<MissingBatch> missingBatches) {
            this.missingBatches = missingBatches;
        }

        List<MissingBatch> getMissingBatches() {
            return missingBatches;
        }
    }

    static final class MissingBatch {
        private final String batchId;
        private final String batchNo;
        private final Map<String, MissingItem> itemsByProductKey = new LinkedHashMap<>();

        MissingBatch(String batchId, String batchNo) {
            this.batchId = batchId;
            this.batchNo = batchNo;
        }

        void add(Allocation allocation, int missingQuantity) {
            MissingItem existing = itemsByProductKey.get(allocation.productKey);
            if (existing == null) {
                itemsByProductKey.put(
                        allocation.productKey,
                        new MissingItem(
                                allocation.productKey,
                                allocation.title,
                                allocation.partnerSku,
                                allocation.noonSku,
                                missingQuantity
                        )
                );
                return;
            }
            existing.missingQuantity += missingQuantity;
        }

        String getBatchId() {
            return batchId;
        }

        String getBatchNo() {
            return batchNo;
        }

        List<MissingItem> getItems() {
            return new ArrayList<>(itemsByProductKey.values());
        }
    }

    static final class MissingItem {
        private final String productKey;
        private final String title;
        private final String partnerSku;
        private final String noonSku;
        private int missingQuantity;

        MissingItem(
                String productKey,
                String title,
                String partnerSku,
                String noonSku,
                int missingQuantity
        ) {
            this.productKey = productKey;
            this.title = title;
            this.partnerSku = partnerSku;
            this.noonSku = noonSku;
            this.missingQuantity = missingQuantity;
        }

        String getProductKey() {
            return productKey;
        }

        String getTitle() {
            return title;
        }

        String getPartnerSku() {
            return partnerSku;
        }

        String getNoonSku() {
            return noonSku;
        }

        int getMissingQuantity() {
            return missingQuantity;
        }
    }
}
