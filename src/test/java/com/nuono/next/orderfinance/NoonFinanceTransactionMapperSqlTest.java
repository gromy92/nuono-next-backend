package com.nuono.next.orderfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoonFinanceTransactionMapperSqlTest {
    @Mock
    private NoonFinanceTransactionMapper mapper;

    @Test
    void productDimensionCandidatesExposeOnlyCanonicalPartnerSku() throws Exception {
        for (String methodName : new String[] {
                "selectOverallSummary",
                "selectCurrencySummaryRows",
                "selectSkuSummaryRows"
        }) {
            Method method = NoonFinanceTransactionMapper.class.getMethod(methodName, OrderFinanceQuery.class);
            String sql = String.join(" ", method.getAnnotation(Select.class).value())
                    .replaceAll("\\s+", " ");

            assertThat(sql)
                    .contains("pv.partner_sku AS partnerSku")
                    .doesNotContain("pso.psku_code AS partnerSku")
                    .doesNotContain("pso.offer_code AS partnerSku");
        }
    }

    @Test
    void factWriterDoesNotRunSchemaDdlOnBusinessWrite() {
        MyBatisNoonFinanceTransactionFactWriter writer = new MyBatisNoonFinanceTransactionFactWriter(mapper);
        NoonFinanceTransactionFact fact = sampleFact();
        when(mapper.nextFinanceTransactionFactId()).thenReturn(300001L);

        writer.upsert(fact);

        verify(mapper).nextFinanceTransactionFactId();
        verify(mapper).upsertFinanceTransactionFact(eq(300001L), eq(fact));
        verifyNoSchemaEnsureCalls();
    }

    @Test
    void analyticsQueriesDoNotRunSchemaDdl() {
        OrderFinanceAnalyticsService service = new OrderFinanceAnalyticsService(mapper);
        OrderFinanceQuery query = OrderFinanceQuery.summary(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                List.of()
        );
        when(mapper.selectOverallSummary(query)).thenReturn(new OrderFinanceSummaryView("SAR", false));
        when(mapper.selectCurrencySummaryRows(query)).thenReturn(List.of());
        when(mapper.selectSkuSummaryRows(query)).thenReturn(List.of());
        when(mapper.selectDataStatus(query)).thenReturn(new OrderFinanceDataStatus());

        service.skuSummary(query);

        verify(mapper).selectOverallSummary(query);
        verify(mapper).selectCurrencySummaryRows(query);
        verify(mapper).selectSkuSummaryRows(query);
        verify(mapper).selectDataStatus(query);
        verifyNoSchemaEnsureCalls();
    }

    private void verifyNoSchemaEnsureCalls() {
        verify(mapper, never()).ensureIdSequenceTable();
        verify(mapper, never()).ensureFactSequence();
        verify(mapper, never()).ensureFactTable();
        verify(mapper, never()).ensureFactNaturalUniqueKey();
        verify(mapper, never()).dropNaturalUniqueKey();
        verify(mapper, never()).addNaturalUniqueKey();
        verify(mapper, never()).deleteDuplicateNaturalKeyRows();
    }

    private NoonFinanceTransactionFact sampleFact() {
        LocalDate transactionDate = LocalDate.of(2026, 5, 31);
        return new NoonFinanceTransactionFact(
                307L,
                "STR108065-NSA",
                "SA",
                "batch-1",
                "digest",
                "row-hash",
                "contract",
                "Contract",
                "ref-1",
                "order-1",
                "item-1",
                transactionDate,
                transactionDate,
                "Title",
                "sku",
                "psku",
                "order",
                "SAR",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                LocalDate.of(2026, 5, 1),
                transactionDate
        );
    }
}
