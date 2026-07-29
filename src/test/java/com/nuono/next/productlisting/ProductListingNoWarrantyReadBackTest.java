package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingNoWarrantyReadBackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductListingNoonReadBackComparator comparator =
            new ProductListingNoonReadBackComparator(
                    new ProductListingRealWriteProperties(),
                    new ProductListingNoonReadBackValueSupport()
            );

    @Test
    void missingWarrantyIsEquivalentToNoWarranty() {
        ProductListingDraftCommand draft = draftWithWarranty(0);

        List<String> mismatches = comparator.mismatches(
                draft,
                List.of(),
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode()
        );

        assertEquals(List.of(), mismatches);
    }

    @Test
    void missingWarrantyStillFailsForNonZeroWarranty() {
        ProductListingDraftCommand draft = draftWithWarranty(24);

        List<String> mismatches = comparator.mismatches(
                draft,
                List.of(),
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode()
        );

        assertEquals(List.of("id_warranty"), mismatches);
    }

    @Test
    void explicitNoWarrantyStillMatchesZero() {
        ProductListingDraftCommand draft = draftWithWarranty(0);
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("id_warranty", 0);

        List<String> mismatches = comparator.mismatches(
                draft,
                List.of(),
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                pricing
        );

        assertEquals(List.of(), mismatches);
    }

    private ProductListingDraftCommand draftWithWarranty(int idWarranty) {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setIdWarranty(idWarranty);
        return draft;
    }
}
