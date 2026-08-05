package com.nuono.next.datapull.advertising;

import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.util.List;
import java.util.Objects;

/** Complete query workbook with explicit physical-row and business-skip accounting. */
public final class AdvertisingQueryReport {
    private final List<NoonAdvertisingQueryFact> facts;
    private final int sourceItemCount;
    private final int businessSkippedItemCount;

    public AdvertisingQueryReport(
            List<NoonAdvertisingQueryFact> facts,
            int sourceItemCount,
            int businessSkippedItemCount
    ) {
        this.facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        if (sourceItemCount < 0 || businessSkippedItemCount < 0
                || sourceItemCount != Math.addExact(this.facts.size(), businessSkippedItemCount)) {
            throw new IllegalArgumentException("query report row accounting is invalid");
        }
        this.sourceItemCount = sourceItemCount;
        this.businessSkippedItemCount = businessSkippedItemCount;
    }

    public static AdvertisingQueryReport complete(List<NoonAdvertisingQueryFact> facts) {
        List<NoonAdvertisingQueryFact> value = List.copyOf(Objects.requireNonNull(facts, "facts"));
        return new AdvertisingQueryReport(value, value.size(), 0);
    }

    public List<NoonAdvertisingQueryFact> getFacts() { return facts; }
    public int getSourceItemCount() { return sourceItemCount; }
    public int getBusinessSkippedItemCount() { return businessSkippedItemCount; }
}
