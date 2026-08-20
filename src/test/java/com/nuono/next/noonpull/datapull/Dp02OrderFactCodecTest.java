package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Dp02OrderFactCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Dp02OrderFactCodec codec = new Dp02OrderFactCodec(objectMapper);

    @Test
    void factRoundTripIsCanonicalAndKeepsStableIdentity() {
        NoonOrderLineFact fact = fact();

        String payload = codec.encode(fact);
        NoonOrderLineFact restored = codec.decode(payload);

        assertThat(codec.encode(restored)).isEqualTo(payload);
        assertThat(codec.stableIdentity(restored)).isEqualTo(codec.stableIdentity(fact));
        assertThat(codec.stableContentFingerprint(restored)).hasSize(64);
        assertThat(restored.getOfferPrice()).isEqualByComparingTo("49.50");
    }

    @Test
    void unknownFieldAndNonCanonicalPayloadAreRejected() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(codec.encode(fact()));
        root.put("unexpected", true);

        assertThatThrownBy(() -> codec.decode(objectMapper.writeValueAsString(root)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NoonOrderLineFact fact() {
        return new NoonOrderLineFact(
                307L,
                "STR244978-NAE",
                "AE",
                "244978",
                "AE",
                "AE",
                "AE",
                "",
                "NAEI50000000001-1",
                "NAEI50000000001",
                "PAPERSAYSB422",
                "Z422",
                "Shipped",
                new BigDecimal("49.50"),
                new BigDecimal("49.50"),
                "AED",
                "PAPERSAY",
                "Stationery",
                "FBN",
                LocalDateTime.of(2026, 7, 10, 12, 30),
                LocalDateTime.of(2026, 7, 10, 14, 0),
                null,
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                "dp02-page-2001"
        );
    }
}
