package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseRequestFingerprintStrictnessTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void requestIdsRejectControlCharactersBeforeQueries() {
        assertThatThrownBy(() -> service.createDispatchPlan(
                access(), dispatchCommand("dispatch\trequest", 5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("控制字符");

        verifyNoInteractions(mapper);
    }

    @Test
    void dispatchReplayRejectsMissingBlankUppercaseAndMalformedPersistedFingerprints() {
        DispatchPlanRecord existing = existingDispatchPlan("dispatch-request-invalid-fingerprint");
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance("CONFIRMED", "SUBMITTED")));
        when(mapper.selectDispatchPlanByClientRequestId(
                307L,
                "dispatch-request-invalid-fingerprint"
        )).thenReturn(existing);

        existing.requestFingerprint = null;
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint = " ";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint =
                "1719BE8EB7BBF3AE9225E8CD9C8D5ABBC679D9162B8791EFB787524EF89FCC21";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint = "deadbeef";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void legacyDispatchFingerprintCannotIgnoreChangedRemark() {
        DispatchPlanRecord existing = existingDispatchPlan("dispatch-request-legacy");
        existing.remark = "原备注";
        existing.requestFingerprint =
                "8af1aeebcef57ea41741a3b0eea3b7aa0f876e346cd9f4800552fd4b7b570816";
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance("CONFIRMED", "SUBMITTED")));
        when(mapper.selectDispatchPlanByClientRequestId(307L, "dispatch-request-legacy"))
                .thenReturn(existing);

        CreateDispatchPlanCommand changed = dispatchCommand("dispatch-request-legacy", 5);
        changed.remark = "修改后的备注";

        assertThatThrownBy(() -> service.createDispatchPlan(access(), changed))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    private void assertFingerprintConflict(String clientRequestId) {
        assertThatThrownBy(() -> service.createDispatchPlan(
                access(),
                dispatchCommand(clientRequestId, 5)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");
    }

    private DispatchPlanRecord existingDispatchPlan(String clientRequestId) {
        DispatchPlanRecord existing = new DispatchPlanRecord();
        existing.id = 340001L;
        existing.ownerUserId = 307L;
        existing.clientRequestId = clientRequestId;
        existing.planNo = "DP-340001";
        existing.status = "DRAFT";
        return existing;
    }

    private CreateDispatchPlanCommand dispatchCommand(String clientRequestId, int quantity) {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = clientRequestId;
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = quantity;
        source.targetSiteCode = "SA";
        source.actualTransportMode = "AIR";
        command.sources = List.of(source);
        return command;
    }
}
