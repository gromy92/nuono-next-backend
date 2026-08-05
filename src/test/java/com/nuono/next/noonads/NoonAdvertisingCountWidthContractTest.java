package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NoonAdvertisingCountWidthContractTest {

    @Test
    void persistedAndEffectiveGenerationCountsRemainWiderThanInteger() {
        long beyondInteger = (long) Integer.MAX_VALUE + 1L;
        NoonAdvertisingReportBatch batch = new NoonAdvertisingReportBatch();
        batch.setCampaignRowCount(beyondInteger);
        batch.setQueryRowCount(beyondInteger + 1L);

        NoonAdvertisingDataStatus status = new NoonAdvertisingDataStatus(
                beyondInteger + 2L,
                beyondInteger + 3L,
                beyondInteger + 4L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
                true
        );

        assertEquals(beyondInteger, batch.getCampaignRowCount());
        assertEquals(beyondInteger + 1L, batch.getQueryRowCount());
        assertEquals(beyondInteger + 2L, status.getBatchCount());
        assertEquals(beyondInteger + 3L, status.getCampaignRowCount());
        assertEquals(beyondInteger + 4L, status.getQueryRowCount());
    }
}
