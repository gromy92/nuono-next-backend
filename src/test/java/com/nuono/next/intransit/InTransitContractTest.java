package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class InTransitContractTest {

    @Test
    void transportModeOnlyAllowsSeaAndAir() {
        assertEquals(List.of("SEA", "AIR"), InTransitTransportMode.codes());
        assertEquals(InTransitTransportMode.SEA, InTransitTransportMode.require("SEA"));
        assertEquals(InTransitTransportMode.AIR, InTransitTransportMode.require("air"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> InTransitTransportMode.require("EXPRESS")
        );
        assertEquals("运输方式只支持海运或空运。", exception.getMessage());
    }

    @Test
    void contractViewExposesNoPurchaseOrderOrFeeFields() {
        InTransitContractView view = InTransitContractView.build();

        assertEquals(List.of("SEA", "AIR"), view.transportModeCodes());
        assertEquals("draft", view.getBatchStatuses().get(0).getCode());
        assertEquals("forwarder_unmatched", view.getQualityStatuses().get(0).getCode());
        assertEquals(List.of(), view.getPurchaseOrderFields());
        assertEquals(List.of(), view.getFeeFields());
    }
}
