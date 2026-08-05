package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.snapshot.InMemorySnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotStageProof;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Dp04ProductSnapshotCodecTest {

    private final Dp04ProductSnapshotCodec codec =
            new Dp04ProductSnapshotCodec(new ObjectMapper());

    @Test
    void providerPayloadIsMinimizedAndRoundTripsDeterministically() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("partner_sku", "PAPERSAYSB422");
        raw.put("csku_parent", "Z422");
        raw.put("sku", "Z422-1");
        raw.put("partner_barcodes", List.of("PAPERSAYSB422", "PAPERSAYS422"));
        raw.put("content", Map.of(
                "title", "Cards",
                "brand", "PAPERSAY",
                "image", "p/cards.jpg",
                "provider_internal_field", "discarded"
        ));
        raw.put("provider_internal_field", "discarded");
        Dp04ProductSnapshotItem item = Dp04ProductSnapshotItem.fromProvider(raw, 1, 0);

        String payload = codec.encode(item);
        Dp04ProductSnapshotItem decoded = codec.decode(payload);

        assertThat(codec.encode(decoded)).isEqualTo(payload);
        assertThat(decoded.getStableIdentity()).isEqualTo(item.getStableIdentity());
        assertThat(decoded.toProjectionPayload())
                .doesNotContainKey("provider_internal_field");
        assertThat(((Map<?, ?>) decoded.toProjectionPayload().get("content"))
                .containsKey("provider_internal_field")).isFalse();
    }

    @Test
    void deterministicBusinessRowsBecomePresenceEvidenceOrAbsenceSafetyVetoes() {
        Dp04ProductSnapshotItem presenceOnly = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "PAPERSAYSB422"
        ), 1, 0);
        Dp04ProductSnapshotItem unidentified = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "csku_parent", "Z422"
        ), 1, 1);

        assertThat(presenceOnly.isWritableProjection()).isFalse();
        assertThat(presenceOnly.isAbsenceReconciliationSafe()).isTrue();
        assertThat(presenceOnly.getPresencePartnerSku()).isEqualTo("PAPERSAYSB422");
        assertThat(unidentified.isWritableProjection()).isFalse();
        assertThat(unidentified.isAbsenceReconciliationSafe()).isFalse();
        assertThat(unidentified.getPresencePartnerSku()).isNull();
        assertThat(codec.decode(codec.encode(presenceOnly)).getPresencePartnerSku())
                .isEqualTo("PAPERSAYSB422");
        assertThat(codec.decode(codec.encode(unidentified)).isAbsenceReconciliationSafe())
                .isFalse();
    }

    @Test
    void targetColumnBusinessDefectSkipsOnlyTheFactWhileKeepingMappablePresence() {
        Dp04ProductSnapshotItem oversizedTitle = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "PAPERSAYSB422",
                "csku_parent", "Z422",
                "title", "x".repeat(501)
        ), 1, 0);
        Dp04ProductSnapshotItem oversizedPartnerSku = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "x".repeat(101),
                "csku_parent", "Z422"
        ), 1, 1);

        assertThat(oversizedTitle.isWritableProjection()).isFalse();
        assertThat(oversizedTitle.isAbsenceReconciliationSafe()).isTrue();
        assertThat(oversizedPartnerSku.isAbsenceReconciliationSafe()).isFalse();
    }

    @Test
    void businessDefectivePresenceDoesNotReserveIdentityFromALaterValidProjection() {
        Dp04ProductSnapshotItem presenceOnly = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "PAPERSAYSB422"
        ), 1, 0);
        Dp04ProductSnapshotItem validProjection = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "PAPERSAYSB422",
                "csku_parent", "Z422"
        ), 1, 1);
        InMemorySnapshotStageStore<Dp04ProductSnapshotItem> store =
                new InMemorySnapshotStageStore<>(codec);
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "dp04-codec-generation", LocalDateTime.of(2026, 8, 2, 3, 0), 2L
        );

        store.stagePage(4001L, 1L, new SnapshotPage<>(
                1, null, true, 1, List.of(presenceOnly, validProjection),
                authority, 2, 0
        ));
        SnapshotStageProof<Dp04ProductSnapshotItem> proof = store.proveComplete(4001L, 1L);

        assertThat(proof.isComplete()).isTrue();
        assertThat(proof.getItems()).singleElement()
                .matches(Dp04ProductSnapshotItem::isWritableProjection)
                .matches(Dp04ProductSnapshotItem::isAbsenceReconciliationSafe);
        assertThat(proof.getSkippedIdentityCount()).isEqualTo(1);
    }

    @Test
    void storedPayloadRejectsUnversionedOrUnexpectedFields() {
        Dp04ProductSnapshotItem item = Dp04ProductSnapshotItem.fromProvider(Map.of(
                "partner_sku", "PAPERSAYSB422",
                "csku_parent", "Z422"
        ), 1, 0);
        String payload = codec.encode(item);

        assertThatThrownBy(() -> codec.decode(payload.replace(
                "\"projection\":{",
                "\"projection\":{\"unexpected\":\"x\","
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(payload.replace(
                "\"schemaVersion\":2",
                "\"schemaVersion\":3"
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
