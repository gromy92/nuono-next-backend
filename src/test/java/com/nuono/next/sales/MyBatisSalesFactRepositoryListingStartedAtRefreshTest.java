package com.nuono.next.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MyBatisSalesFactRepositoryListingStartedAtRefreshTest {

    @Test
    void upsertRefreshesListingStartedAtForAffectedOffer() throws Exception {
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
        verifyRefreshCall(
                productManagementMapper,
                307L,
                "STR245027-NAE",
                "AE",
                "MILKYWAYA01",
                "MILKYWAYA01-BLACK"
        );
    }

    private void verifyRefreshCall(
            ProductManagementMapper productManagementMapper,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku
    ) throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "refreshProductSiteOfferListingStartedAtBySalesFact",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                Long.class
        );
        method.invoke(
                verify(productManagementMapper),
                eq(ownerUserId),
                eq(storeCode),
                eq(siteCode),
                eq(partnerSku),
                eq(sku),
                any(LocalDateTime.class),
                eq(ownerUserId)
        );
    }
}
