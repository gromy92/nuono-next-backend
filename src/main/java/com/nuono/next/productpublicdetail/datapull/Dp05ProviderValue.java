package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.util.Objects;

/** A fact payload or a deterministic, item-local business rejection. */
public final class Dp05ProviderValue {

    private final NoonPublicProductDetailResult detailResult;
    private final String businessItemCode;

    private Dp05ProviderValue(
            NoonPublicProductDetailResult detailResult,
            String businessItemCode
    ) {
        this.detailResult = detailResult;
        this.businessItemCode = businessItemCode;
    }

    public static Dp05ProviderValue fact(NoonPublicProductDetailResult detailResult) {
        return new Dp05ProviderValue(
                Objects.requireNonNull(detailResult, "detailResult"),
                null
        );
    }

    public static Dp05ProviderValue skipBusinessItem(String sanitizedCode) {
        String code = Objects.requireNonNull(sanitizedCode, "sanitizedCode");
        if (code.isBlank() || !code.equals(code.trim())) {
            throw new IllegalArgumentException("business item code must be stable and non-blank");
        }
        return new Dp05ProviderValue(null, code);
    }

    public boolean isBusinessItemSkip() {
        return businessItemCode != null;
    }

    public NoonPublicProductDetailResult getDetailResult() {
        return detailResult;
    }

    public String getBusinessItemCode() {
        return businessItemCode;
    }
}
