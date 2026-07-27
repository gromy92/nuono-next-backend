package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingRealRunSubmissionServiceTest extends ProductListingServiceTest {
    @Test
    void dryRunTaskAllowsMissingPurchaseCost() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand invalid = validCommand();
        invalid.setPurchasePrice(null);
        ProductListingDraftView draft = service.saveDraft(context, invalid);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "purchasePrice".equals(issue.getFieldKey())));
    }

    @Test
    void dryRunTaskFailsWhenPartnerSkuAlreadyHasSuccessfulListingTask() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .anyMatch(issue -> "psku".equals(issue.getFieldKey())
                        && "partner_sku_already_exists".equals(issue.getCode())));
    }

    @Test
    void saveDraftFailsWhenPartnerSkuAlreadyExistsInLocalStore() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60001L);

        ProductListingDraftView draft = service.saveDraft(context, validCommand());

        assertEquals("draft", draft.getStatus());
        assertTrue(draft.getValidationIssues().stream()
                .anyMatch(issue -> "psku".equals(issue.getFieldKey())
                        && "partner_sku_already_exists".equals(issue.getCode())
                        && issue.getMessage().contains("本地店铺")));
    }

    @Test
    void dryRunTaskFailsWhenBarcodeAlreadyExistsInLocalStore() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60002L);
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .anyMatch(issue -> "barcode".equals(issue.getFieldKey())
                        && "barcode_already_exists".equals(issue.getCode())
                        && issue.getMessage().contains("本地店铺")));
    }

    @Test
    void dryRunAllowsLocalProjectionCreatedBySameDraft() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60003L, draft.getDraftId());
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60003L, draft.getDraftId());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())
                        || "barcode_already_exists".equals(issue.getCode())));
    }

    @Test
    void dryRunAllowsProductRebuildToReuseSourceProductPartnerSkuAndBarcode() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand rebuild = validProductRebuildCommand(60004L);
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60004L);
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60004L);
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, rebuild);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())
                        || "barcode_already_exists".equals(issue.getCode())));
    }

    @Test
    void dryRunAllowsPartnerSkuAfterSuccessfulProductDelete() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        mapper.seedSuccessfulProductDelete(10002L, "STR245027-NAE", "NN-TEST-PSKU");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())));
    }

    @Test
    void confirmedRealRunAllowsProductRebuildToReuseHistoricalPartnerSkuTask() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, validProductRebuildCommand(60004L));
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView dryRun = service.submitDryRun(context, dryRunCommand);
        ProductListingRealRunCommand confirmCommand = new ProductListingRealRunCommand();
        confirmCommand.setConfirmRealNoonWrite(true);

        ProductListingTaskView realRun = service.confirmRealRun(context, dryRun.getTaskId(), confirmCommand);

        assertEquals("submitted", realRun.getStatus());
    }

    @Test
    void confirmedRealRunRejectsStaleValidatedDryRunWhenPartnerSkuAlreadyExists() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView staleDryRun = service.submitDryRun(context, dryRunCommand);
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingRealRunCommand confirmCommand = new ProductListingRealRunCommand();
        confirmCommand.setConfirmRealNoonWrite(true);

        ProductListingTaskView realRun = service.confirmRealRun(context, staleDryRun.getTaskId(), confirmCommand);

        assertEquals("rejected", realRun.getStatus());
        assertEquals("partner_sku_already_exists", realRun.getFailureCode());
        assertTrue(realRun.getFailureMessage().contains("PSKU 已存在"));
    }

    @Test
    void validateDraftRejectsStoreOutsideSessionScopeBeforeUpdating() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");
        mapper.resetUpdateCount();

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.validateDraft(storeSaContext, draft.getDraftId())
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void updateDraftRejectsOriginalStoreOutsideSessionScopeBeforeUpdating() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDraftCommand changeStore = validCommand();
        changeStore.setDraftId(draft.getDraftId());
        changeStore.setStoreCode("STR245027-NSA");
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");
        mapper.resetUpdateCount();

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.saveDraft(storeSaContext, changeStore)
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void updateDraftRejectsChangingStoreEvenWhenBothStoresAreAccessible() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDraftCommand changeStore = validCommand();
        changeStore.setDraftId(draft.getDraftId());
        changeStore.setStoreCode("STR245027-NSA");
        BusinessAccessContext bothStoresContext = businessContext(
                10002L,
                90003L,
                Set.of("STR245027-NAE", "STR245027-NSA")
        );
        mapper.resetUpdateCount();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(bothStoresContext, changeStore)
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void loadTaskRejectsStoreOutsideSessionScope() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");
        ProductListingTaskView task = service.submitDryRun(storeAeContext, command);
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.loadTask(storeSaContext, task.getTaskId())
        );
    }

}
