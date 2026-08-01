package com.nuono.next.procurementorder;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

public class ProductForwarderEligibilityScopeAnchorRecord {

    public static final Comparator<ProductForwarderEligibilityScopeAnchorRecord> LOCK_ORDER =
            Comparator.comparing(
                            (ProductForwarderEligibilityScopeAnchorRecord scope) -> scope.ownerUserId,
                            Comparator.nullsFirst(Long::compareTo)
                    )
                    .thenComparing(scope -> scope.logicalStoreId, Comparator.nullsFirst(Long::compareTo))
                    .thenComparing(scope -> scope.partnerSkuNormalized, ProductForwarderEligibilityScopeAnchorRecord::compareUtf8);

    public Long ownerUserId;
    public Long logicalStoreId;
    public String partnerSkuNormalized;

    public ProductForwarderEligibilityScopeAnchorRecord() {
    }

    public ProductForwarderEligibilityScopeAnchorRecord(
            Long ownerUserId,
            Long logicalStoreId,
            String partnerSkuNormalized
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.partnerSkuNormalized = partnerSkuNormalized;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof ProductForwarderEligibilityScopeAnchorRecord)) {
            return false;
        }
        ProductForwarderEligibilityScopeAnchorRecord other =
                (ProductForwarderEligibilityScopeAnchorRecord) value;
        return Objects.equals(ownerUserId, other.ownerUserId)
                && Objects.equals(logicalStoreId, other.logicalStoreId)
                && Objects.equals(partnerSkuNormalized, other.partnerSkuNormalized);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, logicalStoreId, partnerSkuNormalized);
    }

    private static int compareUtf8(String left, String right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int commonLength = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < commonLength; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }
}
