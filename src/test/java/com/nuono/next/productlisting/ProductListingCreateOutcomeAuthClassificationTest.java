package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingCreateOutcomeAuthClassificationTest
        extends ProductListingCreateOutcomeTestSupport {

    @Test
    void bareRedirectForbiddenAndPermanent401RemainSafeLookupFailures()
            throws Exception {
        for (RuntimeException failure : List.of(
                wrapped(new NoonHttpException(307, "", "/offer/list")),
                wrapped(new NoonHttpException(
                        403, "auth_required", "/offer/list")),
                wrapped(new NoonHttpException(
                        401, "invalid username or password", "/offer/list"))
        )) {
            Fixture fixture = fixture(failure);

            ProductListingCreateOutcomeVerificationView view =
                    fixture.service.verify(fixture.context, 20002L);

            assertEquals("lookup_failed", view.getStatus());
            assertEquals(
                    "noon_create_reference_lookup_failed",
                    view.getFailureCode()
            );
            verify(fixture.mapper, never())
                    .markCreateOutcomeLookupAuthenticationRequired(
                            any(), any(), any(), any());
            verify(fixture.mapper, never())
                    .persistRecoveredCreateReference(
                            any(), any(), any(), any());
        }
    }

    @Test
    void typedAuthenticationSignalStillTransitionsToReauthentication()
            throws Exception {
        Fixture fixture = fixture(wrapped(
                new NoonAuthenticationRequiredException(
                        "authorization required")
        ));
        when(fixture.mapper.markCreateOutcomeLookupAuthenticationRequired(
                eq(20002L),
                eq(10002L),
                eq(fixture.record.getNoonResultJson()),
                any()
        )).thenReturn(1);

        ProductListingCreateOutcomeVerificationView view =
                fixture.service.verify(fixture.context, 20002L);

        assertEquals("reauthentication_required", view.getStatus());
        assertEquals("noon_auth_required", view.getFailureCode());
        verify(fixture.mapper).markCreateOutcomeLookupAuthenticationRequired(
                eq(20002L),
                eq(10002L),
                eq(fixture.record.getNoonResultJson()),
                any()
        );
    }

    private Fixture fixture(RuntimeException failure) throws Exception {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService =
                mock(ProductListingService.class);
        ProductListingNoonWriteAdapter adapter =
                mock(ProductListingNoonWriteAdapter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService service =
                new ProductListingCreateOutcomeService(
                        mapper, listingService, adapter, objectMapper);
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L, 90001L, "STR245027-NAE");
        ProductListingTaskRecord record = uncertainTaskRecord(objectMapper);
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(record);
        when(adapter.resolveCreateReference(any())).thenThrow(failure);
        return new Fixture(mapper, service, context, record);
    }

    private RuntimeException wrapped(RuntimeException cause) {
        return new IllegalStateException("lookup failed", cause);
    }

    private static final class Fixture {
        private final ProductListingMapper mapper;
        private final ProductListingCreateOutcomeService service;
        private final BusinessAccessContext context;
        private final ProductListingTaskRecord record;

        private Fixture(
                ProductListingMapper mapper,
                ProductListingCreateOutcomeService service,
                BusinessAccessContext context,
                ProductListingTaskRecord record
        ) {
            this.mapper = mapper;
            this.service = service;
            this.context = context;
            this.record = record;
        }
    }
}
