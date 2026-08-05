package com.nuono.next.officialwarehouse.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class Dp07InventorySnapshotCodecTest {

    private final Dp07InventorySnapshotCodec codec =
            new Dp07InventorySnapshotCodec(new ObjectMapper());

    @Test
    void fixedFieldPayloadRoundTripsWithStableIdentityAndFingerprint() {
        Dp07InventorySnapshotItem item = item(7);

        String payload = codec.encode(item);
        Dp07InventorySnapshotItem decoded = codec.decode(payload);

        assertThat(codec.encode(decoded)).isEqualTo(payload);
        assertThat(codec.stableIdentity(decoded)).isEqualTo(codec.stableIdentity(item));
        assertThat(codec.stableContentFingerprint(decoded))
                .isEqualTo(codec.stableContentFingerprint(item));
    }

    @Test
    void sameInventoryIdentityWithDifferentFactContentKeepsIdentityButChangesFingerprint() {
        Dp07InventorySnapshotItem first = item(7);
        Dp07InventorySnapshotItem laterConflict = item(9);

        assertThat(codec.stableIdentity(laterConflict)).isEqualTo(codec.stableIdentity(first));
        assertThat(codec.stableContentFingerprint(laterConflict))
                .isNotEqualTo(codec.stableContentFingerprint(first));
    }

    @Test
    void payloadRejectsUnknownVersionAndInvalidBusinessQuantity() {
        String payload = codec.encode(item(7));

        assertThatThrownBy(() -> codec.decode(payload.replace(
                "\"schemaVersion\":1",
                "\"schemaVersion\":2"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dp07InventorySnapshotItem.restore(
                "RUH01", -1, "saleable", null, "SELLABLE", null,
                "PAPERSAYSB422", "N422", "PAPERSAYSB422", "SA", null,
                "Cards", "PAPERSAY", "2026-08-02 23:00:00", "{}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Dp07InventorySnapshotItem item(int quantity) {
        return Dp07InventorySnapshotItem.restore(
                "RUH01",
                quantity,
                "saleable",
                null,
                "SELLABLE",
                null,
                "PAPERSAYSB422",
                "N422",
                "PAPERSAYSB422",
                "SA",
                "standard_parcel",
                "Cards",
                "PAPERSAY",
                "2026-08-02 23:00:00",
                "{\"qty\":" + quantity + ",\"warehouse_code\":\"RUH01\"}"
        );
    }
}
