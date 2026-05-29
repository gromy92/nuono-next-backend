package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderAliasCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitForwarderServiceTest {

    @Mock
    private InTransitGoodsMapper mapper;

    @Mock
    private InTransitOperationAuditService auditService;

    private InTransitForwarderService service;

    @BeforeEach
    void setUp() {
        service = new InTransitForwarderService(mapper, auditService);
    }

    @Test
    void shouldCreateStandardForwarderWithoutPurchaseOrderOrFeeSideEffects() {
        SaveForwarderCommand command = new SaveForwarderCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setForwarderCode("YITE");
        command.setForwarderName("义特物流");

        when(mapper.selectForwarderByOwnerAndCode(10002L, "YITE")).thenReturn(null);
        when(mapper.nextForwarderId()).thenReturn(51001L);
        when(mapper.selectForwarderById(10002L, 51001L)).thenReturn(forwarder(51001L, "YITE", "义特物流"));

        ForwarderView result = service.saveForwarder(command);

        assertEquals(51001L, result.getId());
        assertEquals("YITE", result.getForwarderCode());
        assertEquals("义特物流", result.getForwarderName());
        verify(mapper).insertForwarder(any(ForwarderRow.class));
        assertAudit("forwarder_created", "forwarder", 51001L);
    }

    @Test
    void shouldAuditStandardForwarderUpdate() {
        SaveForwarderCommand command = new SaveForwarderCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setForwarderCode("YITE");
        command.setForwarderName("义特国际物流");

        when(mapper.selectForwarderByOwnerAndCode(10002L, "YITE")).thenReturn(forwarder(51001L, "YITE", "义特物流"));
        when(mapper.selectForwarderById(10002L, 51001L)).thenReturn(forwarder(51001L, "YITE", "义特国际物流"));

        ForwarderView result = service.saveForwarder(command);

        assertEquals(51001L, result.getId());
        assertEquals("义特国际物流", result.getForwarderName());
        verify(mapper).updateForwarder(any(ForwarderRow.class));
        assertAudit("forwarder_updated", "forwarder", 51001L);
    }

    @Test
    void shouldBindRawForwarderAliasToExistingStandardForwarder() {
        SaveForwarderAliasCommand command = new SaveForwarderAliasCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setStandardForwarderId(51001L);
        command.setRawForwarderName(" 义特 物流 ");

        when(mapper.selectForwarderById(10002L, 51001L)).thenReturn(forwarder(51001L, "YITE", "义特物流"));
        when(mapper.selectAliasByOwnerAndNormalized(10002L, "义特物流")).thenReturn(null);
        when(mapper.nextForwarderAliasId()).thenReturn(52001L);
        when(mapper.selectAliasById(10002L, 52001L)).thenReturn(alias(52001L, 51001L, " 义特 物流 ", "义特物流"));

        ForwarderAliasView result = service.saveForwarderAlias(command);

        assertEquals(52001L, result.getId());
        assertEquals(51001L, result.getStandardForwarderId());
        assertEquals("义特物流", result.getNormalizedRawForwarderName());
        verify(mapper).insertForwarderAlias(any(ForwarderAliasRow.class));
        assertAudit("forwarder_alias_saved", "forwarder_alias", 52001L);
    }

    @Test
    void shouldResolveMatchedAliasAsForwarderMatched() {
        ResolveForwarderCommand command = new ResolveForwarderCommand();
        command.setOwnerUserId(10002L);
        command.setRawForwarderName("义特 物流");

        when(mapper.selectActiveAliasByOwnerAndNormalized(10002L, "义特物流"))
                .thenReturn(alias(52001L, 51001L, "义特物流", "义特物流"));

        ForwarderResolveView result = service.resolveForwarder(command);

        assertEquals("forwarder_matched", result.getQualityStatus());
        assertEquals(51001L, result.getStandardForwarderId());
        assertEquals("YITE", result.getStandardForwarderCode());
        assertEquals("义特物流", result.getStandardForwarderName());
    }

    @Test
    void shouldReturnForwarderUnmatchedWithoutSilentlyCreatingStandardForwarder() {
        ResolveForwarderCommand command = new ResolveForwarderCommand();
        command.setOwnerUserId(10002L);
        command.setRawForwarderName("历史货代A");

        when(mapper.selectActiveAliasByOwnerAndNormalized(10002L, "历史货代a")).thenReturn(null);

        ForwarderResolveView result = service.resolveForwarder(command);

        assertEquals("forwarder_unmatched", result.getQualityStatus());
        assertEquals("历史货代A", result.getRawForwarderName());
        assertNull(result.getStandardForwarderId());
        verify(mapper, never()).insertForwarder(any(ForwarderRow.class));
    }

    @Test
    void shouldRejectUnsupportedTransportModeThroughContractService() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireTransportMode("rail")
        );
        assertEquals("运输方式只支持海运或空运。", exception.getMessage());
        verify(mapper, never()).insertForwarder(any(ForwarderRow.class));
    }

    private ForwarderRow forwarder(Long id, String code, String name) {
        ForwarderRow row = new ForwarderRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setForwarderCode(code);
        row.setForwarderName(name);
        row.setStatus("ACTIVE");
        return row;
    }

    private ForwarderAliasRow alias(Long id, Long forwarderId, String rawName, String normalizedName) {
        ForwarderAliasRow row = new ForwarderAliasRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setStandardForwarderId(forwarderId);
        row.setRawForwarderName(rawName);
        row.setNormalizedRawForwarderName(normalizedName);
        row.setStatus("ACTIVE");
        row.setStandardForwarderCode("YITE");
        row.setStandardForwarderName("义特物流");
        return row;
    }

    private void assertAudit(String operationType, String targetType, Long targetId) {
        ArgumentCaptor<InTransitOperationAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(InTransitOperationAuditService.AuditCommand.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals(operationType, auditCaptor.getValue().getOperationType());
        assertEquals(targetType, auditCaptor.getValue().getTargetType());
        assertEquals(targetId, auditCaptor.getValue().getTargetId());
        assertEquals(10002L, auditCaptor.getValue().getOwnerUserId());
        assertEquals(90001L, auditCaptor.getValue().getOperatorUserId());
    }
}
