package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.Test;

class ProductListingOfficialTaxonomyDryRunTest extends ProductListingServiceTest {

    @Test
    void dryRunRejectsWellFormedFulltypeMissingFromOfficialNoonCatalog() {
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties
        );
        service.setOfficialTaxonomyMapper(productFulltype -> null);
        BusinessAccessContext context =
                businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        command.setProductFullType(
                "electronic_accessories-accessories-mobile_phone_cases"
        );
        command.setIdProductFullType(null);

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand =
                new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");

        ProductListingTaskView dryRun =
                service.submitDryRun(context, dryRunCommand);

        assertEquals("validation_failed", dryRun.getStatus());
        assertIssue(
                dryRun.getValidationIssues(),
                "productFullType",
                "noon_product_fulltype_not_found"
        );
    }

    @Test
    void dryRunFailsClosedWhenOfficialNoonCatalogCannotBeRead() {
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties
        );
        service.setOfficialTaxonomyMapper(productFulltype -> {
            throw new IllegalStateException("catalog unavailable");
        });
        BusinessAccessContext context =
                businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand =
                new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");

        ProductListingTaskView dryRun =
                service.submitDryRun(context, dryRunCommand);

        assertEquals("validation_failed", dryRun.getStatus());
        assertIssue(
                dryRun.getValidationIssues(),
                "productFullType",
                "noon_product_fulltype_catalog_unavailable"
        );
    }

    @Test
    void dryRunHydratesVerifiedOfficialTaxonomyLabelsIntoSnapshot() {
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties
        );
        ProductListingOfficialTaxonomyRecord taxonomy =
                new ProductListingOfficialTaxonomyRecord();
        taxonomy.setIdProductFulltype(2969L);
        taxonomy.setProductFulltypeCode(
                "electronic_accessories-phone_accessories-mobile_phone_cases_and_covers"
        );
        taxonomy.setFamilyNameEn("Electronic Accessories");
        taxonomy.setProductTypeNameEn("Phone Accessories");
        taxonomy.setProductSubtypeNameEn("Mobile Phone Cases and Covers");
        service.setOfficialTaxonomyMapper(productFulltype -> taxonomy);
        BusinessAccessContext context =
                businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        command.setProductFullType(taxonomy.getProductFulltypeCode());

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand =
                new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView dryRun =
                service.submitDryRun(context, dryRunCommand);

        assertEquals("validated", dryRun.getStatus());
        String snapshot = mapper.insertedTask().getInputSnapshotJson();
        assertTrue(snapshot.contains("\"idProductFullType\":2969"));
        assertTrue(snapshot.contains("\"productType\":\"Phone Accessories\""));
        assertTrue(snapshot.contains(
                "\"productSubType\":\"Mobile Phone Cases and Covers\""
        ));
    }
}
