package com.nuono.next.sales;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MyBatisSalesFactRepositoryListingStartedAtRefreshTest {

    @Test
    void upsertPersistsSalesFactWithoutRefreshingListingStartedAt() throws Exception {
        SalesDataMapper salesDataMapper = mock(SalesDataMapper.class);
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        when(salesDataMapper.nextDailySalesFactId()).thenReturn(123L);

        Constructor<MyBatisSalesFactRepository> constructor = MyBatisSalesFactRepository.class.getConstructor(
                SalesDataMapper.class,
                ProductManagementMapper.class
        );
        MyBatisSalesFactRepository repository = constructor.newInstance(salesDataMapper, productManagementMapper);
        DailySalesFact fact = new DailySalesFact(
                "noon_productviewsandsalesdata",
                10001L,
                307L,
                245027L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2025, 6, 24),
                "MILKYWAYA01",
                "MILKYWAYA01-BLACK",
                null,
                "AE",
                "AED",
                "Milkyway A01",
                7,
                9,
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                null,
                null,
                null
        );

        repository.upsert(fact);

        verify(salesDataMapper).upsertDailySalesFact(123L, fact);
        verifyNoInteractions(productManagementMapper);
    }

    @Test
    void emptyReportMarksSiteOffersAsNotListed() throws Exception {
        SalesDataMapper salesDataMapper = mock(SalesDataMapper.class);
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        Constructor<MyBatisSalesFactRepository> constructor = MyBatisSalesFactRepository.class.getConstructor(
                SalesDataMapper.class,
                ProductManagementMapper.class
        );
        MyBatisSalesFactRepository repository = constructor.newInstance(salesDataMapper, productManagementMapper);

        repository.markSiteOffersNotListedForEmptyReport(307L, "STR245027-NSA", "SA", 307L);

        Method method = ProductManagementMapper.class.getMethod(
                "markSiteProductOffersNotListedForEmptySalesReport",
                Long.class,
                String.class,
                String.class,
                Long.class
        );
        method.invoke(
                verify(productManagementMapper),
                eq(307L),
                eq("STR245027-NSA"),
                eq("SA"),
                eq(307L)
        );
    }

}
