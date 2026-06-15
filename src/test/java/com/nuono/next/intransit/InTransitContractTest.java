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
    void destinationOnlyAllowsRuhAndDb() {
        assertEquals(List.of("RUH", "DB"), InTransitDestination.codes());
        assertEquals(InTransitDestination.RUH, InTransitDestination.require("RUH"));
        assertEquals(InTransitDestination.RUH, InTransitDestination.require("利雅得"));
        assertEquals(InTransitDestination.DB, InTransitDestination.require("DB"));
        assertEquals(InTransitDestination.DB, InTransitDestination.require("迪拜"));
        assertEquals(InTransitDestination.DB, InTransitDestination.infer("STR245027-NAE", "AE", "FBN-DXB", "XGGEUAE04029"));
        assertEquals(InTransitDestination.RUH, InTransitDestination.infer("STORE-RUH01S", "SA", "FBN-RUH", "XGGEKSA04075"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> InTransitDestination.require("JED")
        );
        assertEquals("目的地只支持 RUH 利雅得或 DB 迪拜。", exception.getMessage());
    }

    @Test
    void contractViewExposesNoPurchaseOrderOrFeeFields() {
        InTransitContractView view = InTransitContractView.build();

        assertEquals(List.of("SEA", "AIR"), view.transportModeCodes());
        assertEquals(List.of("RUH", "DB"), view.destinationCodes());
        assertEquals("draft", view.getBatchStatuses().get(0).getCode());
        assertEquals("forwarder_unmatched", view.getQualityStatuses().get(0).getCode());
        assertEquals(List.of(), view.getPurchaseOrderFields());
        assertEquals(List.of(), view.getFeeFields());
    }
}
