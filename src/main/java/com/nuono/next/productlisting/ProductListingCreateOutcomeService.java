package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingCreateOutcomeService {

    private final ProductListingCreateOutcomeVerifier verifier;
    private final ProductListingCreateOutcomeConfirmer confirmer;

    public ProductListingCreateOutcomeService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ObjectMapper objectMapper
    ) {
        ProductListingCreateOutcomeSupport support =
                new ProductListingCreateOutcomeSupport(objectMapper);
        this.verifier = new ProductListingCreateOutcomeVerifier(
                mapper, listingService, noonWriteAdapter, support);
        this.confirmer = new ProductListingCreateOutcomeConfirmer(
                mapper, listingService, support);
    }

    public ProductListingCreateOutcomeVerificationView verify(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        return verifier.verify(context, realRunTaskId);
    }

    @Transactional
    public Long confirmNotCreated(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        return confirmer.confirmNotCreated(context, realRunTaskId);
    }
}
