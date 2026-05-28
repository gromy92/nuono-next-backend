package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MyBatisNoonSalesFactWriterListingStartedAtRefreshTest {

    @Test
    void upsertRefreshesListingStartedAtForAffectedOffer() throws Exception {
        NoonSalesFactMapper salesFactMapper = mock(NoonSalesFactMapper.class);
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        when(salesFactMapper.nextDailySalesFactId()).thenReturn(456L);

        Constructor<MyBatisNoonSalesFactWriter> constructor = MyBatisNoonSalesFactWriter.class.getConstructor(
                NoonSalesFactMapper.class,
                ProductManagementMapper.class
        );
        MyBatisNoonSalesFactWriter writer = constructor.newInstance(salesFactMapper, productManagementMapper);
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
