package com.nuono.next.noonpull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MyBatisNoonSalesFactWriterListingStartedAtRefreshTest {

    @Test
    void upsertPersistsSalesFactWithoutProductProjectionDependency() {
        NoonSalesFactMapper salesFactMapper = mock(NoonSalesFactMapper.class);
        when(salesFactMapper.nextDailySalesFactId()).thenReturn(456L);

        MyBatisNoonSalesFactWriter writer = new MyBatisNoonSalesFactWriter(salesFactMapper);
        NoonSalesDailyFact fact = new NoonSalesDailyFact(
                307L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2025, 6, 24),
                "MILKYWAYA01",
                "MILKYWAYA01-BLACK",
                1L,
                BigDecimal.TEN,
                "AED",
                "batch-1"
        );

        writer.upsert(fact);

        verify(salesFactMapper).upsertDailySalesFact(456L, fact);
        assertFalse(Arrays.stream(MyBatisNoonSalesFactWriter.class.getDeclaredFields())
                .anyMatch((field) -> ProductManagementMapper.class.equals(field.getType())));
        assertFalse(Arrays.stream(MyBatisNoonSalesFactWriter.class.getDeclaredConstructors())
                .flatMap((constructor) -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(ProductManagementMapper.class::equals));
    }
}
