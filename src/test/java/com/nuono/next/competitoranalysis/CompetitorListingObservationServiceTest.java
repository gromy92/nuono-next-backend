package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorListingObservationServiceTest {

    @Mock
    private CompetitorListingObservationMapper mapper;

    @Test
    void reusesATerminalExactObservationWithoutANewProviderLease() {
        CompetitorListingObservationRow row = foundRow();
        row.setAcquisitionMode("EXACT_SEARCH");
        when(mapper.nextListingObservationId()).thenReturn(280001L);
        when(mapper.insertExactClaim(any())).thenReturn(0);
        when(mapper.selectDaily(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("ZCOMP0001"),
                any(LocalDate.class)
        )).thenReturn(row);

        CompetitorListingObservationService.Lease lease =
                service().acquireExact(
                        watchProduct(),
                        "zcomp0001",
                        170001L,
                        601L
                );

        assertNull(lease.getNotFound());
        assertNotNull(lease.getCachedDetail());
        assertEquals(
                "عنوان القائمة",
                lease.getCachedDetail().getTitleAr()
        );
    }

    @Test
    void claimsARankObservationWhenTheOtherLanguageIsMissing() {
        CompetitorListingObservationRow row = foundRow();
        row.setAcquisitionMode("RANK_SCAN");
        row.setTitleAr(null);
        when(mapper.nextListingObservationId()).thenReturn(280002L);
        when(mapper.insertExactClaim(any())).thenReturn(0);
        when(mapper.selectDaily(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("ZCOMP0001"),
                any(LocalDate.class)
        )).thenReturn(row);
        when(mapper.claimRetryableOrStale(any())).thenReturn(1);

        CompetitorListingObservationService.Lease lease =
                service().acquireExact(
                        watchProduct(),
                        "ZCOMP0001",
                        170001L,
                        601L
                );

        assertEquals(true, lease.isAcquired());
        assertNull(lease.getCachedDetail());
    }

    @Test
    void persistsOnlyListFieldsWhenAnExactLeaseCompletes() {
        when(mapper.nextListingObservationId()).thenReturn(280003L);
        when(mapper.insertExactClaim(any())).thenReturn(1);
        when(mapper.completeExactFound(any())).thenReturn(1);
        CompetitorListingObservationService service = service();
        CompetitorListingObservationService.Lease lease =
                service.acquireExact(
                        watchProduct(),
                        "ZCOMP0001",
                        170001L,
                        601L
                );
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode("ZCOMP0001");
        detail.setTitleEn("English list title");
        detail.setTitleAr("عنوان القائمة");
        detail.setPriceAmount(new BigDecimal("32.95"));
        detail.setCurrencyCode("SAR");
        detail.setMainImageUrlNormalized(
                "https://f.nooncdn.com/p/list.jpg"
        );
        detail.setBadgesJson("[\"Best Seller\"]");

        service.completeFound(lease, detail, 601L);

        ArgumentCaptor<CompetitorListingObservationCommand> command =
                ArgumentCaptor.forClass(
                        CompetitorListingObservationCommand.class
                );
        verify(mapper).completeExactFound(command.capture());
        assertEquals("English list title", command.getValue().getTitleEn());
        assertEquals("عنوان القائمة", command.getValue().getTitleAr());
        assertEquals("[\"Best Seller\"]", command.getValue().getTagsJson());
    }

    private CompetitorListingObservationService service() {
        return new CompetitorListingObservationService(mapper);
    }

    private CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }

    private CompetitorListingObservationRow foundRow() {
        CompetitorListingObservationRow row =
                new CompetitorListingObservationRow();
        row.setId(280001L);
        row.setNoonProductCode("ZCOMP0001");
        row.setCodeType("Z_CODE");
        row.setStatus("FOUND");
        row.setTitleEn("English list title");
        row.setTitleAr("عنوان القائمة");
        row.setPriceAmount(new BigDecimal("32.95"));
        row.setCurrencyCode("SAR");
        return row;
    }
}
