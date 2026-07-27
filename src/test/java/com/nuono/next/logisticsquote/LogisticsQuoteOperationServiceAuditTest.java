package com.nuono.next.logisticsquote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class LogisticsQuoteOperationServiceAuditTest {

    @Mock
    private ObjectProvider<LogisticsQuoteMapper> mapperProvider;

    @Mock
    private ObjectProvider<LocalDbBootstrapStatusService> bootstrapStatusProvider;

    @Mock
    private LogisticsQuoteMapper mapper;

    @Mock
    private LocalDbBootstrapStatusService bootstrapStatusService;

    private LogisticsQuoteOperationService service;

    @BeforeEach
    void setUp() {
        service = new LogisticsQuoteOperationService(mapperProvider, bootstrapStatusProvider);
    }

    @Test
    void authenticatedOperatorOwnsBothAdjustmentAndAuditLog() {
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        when(bootstrapStatusProvider.getIfAvailable()).thenReturn(bootstrapStatusService);
        when(bootstrapStatusService.inspect())
                .thenReturn(new CoreTableInspection("nuono_test", List.of(), List.of(), List.of()));
        when(mapper.countExistingOperationQuoteTables("nuono_test")).thenReturn(9);
        when(mapper.listOperationPriceItems(null, null, null)).thenReturn(List.of(priceItem()));
        when(mapper.nextNumericAdjustmentId()).thenReturn(930001L);
        when(mapper.selectActiveNumericAdjustmentId("BASE_PRICE", 912001L, "unit_price"))
                .thenReturn(930001L);
        when(mapper.nextNumericAdjustmentLogId()).thenReturn(940001L);

        LogisticsQuoteOperationPriceAdjustmentView result =
                service.savePriceAdjustment(90001L, command());

        assertThat(result.getAdjustmentId()).isEqualTo(930001L);
        assertThat(result.getLogId()).isEqualTo(940001L);
        verify(mapper).upsertNumericAdjustment(
                eq(930001L),
                eq(74001L),
                eq("BASE_PRICE"),
                eq(912001L),
                eq("unit_price"),
                eq(67d),
                eq(70d),
                eq("CNY"),
                eq("market change"),
                eq(90001L)
        );
        verify(mapper).insertNumericAdjustmentLog(
                eq(940001L),
                eq(930001L),
                eq(74001L),
                eq("BASE_PRICE"),
                eq(912001L),
                eq("unit_price"),
                eq(67d),
                eq(70d),
                eq("CREATE"),
                eq("market change"),
                eq(90001L)
        );
    }

    @Test
    void missingSessionOperatorFailsBeforePersistenceDiscovery() {
        assertThatThrownBy(() -> service.savePriceAdjustment(null, command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("缺少有效的当前登录用户。");

        verifyNoInteractions(mapperProvider, bootstrapStatusProvider, mapper, bootstrapStatusService);
    }

    private LogisticsQuoteOperationPriceAdjustmentCommand command() {
        LogisticsQuoteOperationPriceAdjustmentCommand command =
                new LogisticsQuoteOperationPriceAdjustmentCommand();
        command.setTargetType("BASE_PRICE");
        command.setTargetId(912001L);
        command.setNumericField("unit_price");
        command.setAdjustedValue(70d);
        command.setReason("market change");
        return command;
    }

    private LogisticsQuoteOperationPriceItemView priceItem() {
        LogisticsQuoteOperationPriceItemView item = new LogisticsQuoteOperationPriceItemView();
        item.setTargetType("BASE_PRICE");
        item.setTargetId(912001L);
        item.setNumericField("unit_price");
        item.setQuoteVersionId(74001L);
        item.setStandardValue(67d);
        item.setEffectiveValue(67d);
        item.setCurrency("CNY");
        item.setHasAdjustment(false);
        return item;
    }
}
