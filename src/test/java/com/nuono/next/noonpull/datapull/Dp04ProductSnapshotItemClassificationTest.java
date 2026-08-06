package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class Dp04ProductSnapshotItemClassificationTest {

    @Test
    void unsupportedPartnerSkuScalarBecomesAnAbsenceSafetyVeto() {
        Dp04ProductSnapshotItem item = Dp04ProductSnapshotItem.fromProvider(
                Map.of("partner_sku", 42, "csku_parent", "Z-BAD"),
                1,
                0
        );

        assertThat(item.isWritableProjection()).isFalse();
        assertThat(item.isAbsenceReconciliationSafe()).isFalse();
        assertThat(item.getPresencePartnerSku()).isNull();
    }
}
