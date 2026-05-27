package com.nuono.next.foundation.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FoundationStandardsServiceTest {

    @Test
    void factStandardRegistersDailySalesWithCanonicalIdentityAndQualityVocabulary() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationFactDefinition fact = service.listFactDefinitions()
                .stream()
                .filter(candidate -> "daily_sales_fact".equals(candidate.getKey()))
                .findFirst()
                .orElseThrow();

        assertTrue(fact.getIdentityFields().containsAll(List.of(
                "ownerUserId",
                "logicalStoreId",
                "storeCode",
                "siteCode",
                "partnerSku",
                "sku",
                "eventDate"
        )));
        assertTrue(fact.getSourceFields().containsAll(List.of(
                "sourceSystem",
                "sourceBatchId",
                "sourceRecordId",
                "idempotencyKey",
                "snapshotAt"
        )));
        assertTrue(fact.getValueSemantics().containsAll(List.of(
                "raw_source_values",
                "derived_values"
        )));
        assertTrue(fact.getRequiredContractFields().containsAll(List.of(
                "factKey",
                "factType",
                "sourceSystem",
                "ownerUserId",
                "logicalStoreId",
                "qualityState"
        )));
        assertTrue(fact.getValueRules().contains("missing_source_values_remain_missing"));
        assertTrue(service.listQualityStates()
                .stream()
                .map(FoundationQualityState::getKey)
                .collect(java.util.stream.Collectors.toList())
                .containsAll(List.of(
                        "stale_data",
                        "missing_mapping",
                        "partial_success",
                        "empty_source_report",
                        "challenge_page",
                        "provider_unavailable",
                        "unmapped_identity"
                )));
    }

    @Test
    void metricStandardReferencesRegisteredFactsAndDeclaresCalculationRules() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationMetricDefinition metric = service.listMetricDefinitions()
                .stream()
                .filter(candidate -> "sales_net_units".equals(candidate.getKey()))
                .findFirst()
                .orElseThrow();

        assertTrue(metric.getInputFactKeys().contains("daily_sales_fact"));
        assertTrue(service.listFactDefinitions()
                .stream()
                .anyMatch(fact -> metric.getInputFactKeys().contains(fact.getKey())));
        assertTrue(metric.isAuthoritative());
        assertTrue(metric.getCalculationOwner().contains("foundation"));
        assertTrue(metric.getFreshnessRule().contains("eventDate"));
        assertTrue(metric.getQualityRule().contains("stale_data"));
        assertTrue(metric.getResultShape().contains("integer"));
    }

    @Test
    void referenceMetricPreviewCalculatesNetUnitsAndPreservesMissingOrStaleQuality() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationMetricPreview normal = service.previewSalesNetUnits(12, 3, false);
        FoundationMetricPreview missing = service.previewSalesNetUnits(null, 3, false);
        FoundationMetricPreview stale = service.previewSalesNetUnits(12, 3, true);

        assertEquals(9, normal.getIntegerValue());
        assertTrue(normal.getQualityStates().isEmpty());
        assertNull(missing.getIntegerValue());
        assertTrue(missing.getQualityStates().contains("missing_source_value"));
        assertEquals(9, stale.getIntegerValue());
        assertTrue(stale.getQualityStates().contains("stale_data"));
    }

    @Test
    void aiStandardSeparatesDocumentAnalysisAndSuggestionOutputModes() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationAiOutputModeDefinition documentMode = aiMode(service, "file_document_parse");
        FoundationAiOutputModeDefinition analysisMode = aiMode(service, "direct_ai_analysis");
        FoundationAiOutputModeDefinition suggestionMode = aiMode(service, "business_suggestion");

        assertTrue(documentMode.getLifecycleSteps().containsAll(List.of(
                "immutable_revision",
                "human_processing",
                "complete_version_snapshot",
                "active_pointer"
        )));
        assertTrue(documentMode.getRequiredContracts().containsAll(List.of(
                "file_management_version_lineage",
                "file_management_version_item_reference",
                "result_lineage_reference",
                "active_business_selection_reference"
        )));

        assertTrue(analysisMode.getRequiredContracts().containsAll(List.of(
                "backend_ai_capability_service",
                "prompt_template",
                "schema_expectation",
                "type_metadata",
                "usage_audit"
        )));
        assertTrue(!analysisMode.isExternalWriteAllowed());

        assertTrue(suggestionMode.getRequiredContracts().containsAll(List.of(
                "backend_ai_capability_service",
                "evidence_references",
                "human_confirmation",
                "execution_placeholder",
                "readback_audit"
        )));
        assertTrue(!suggestionMode.isExternalWriteAllowed());
    }

    @Test
    void adapterStandardClassifiesChallengePagesAndUnknownResultsAsNonSuccess() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationAdapterStandard adapter = service.adapterStandard();

        assertTrue(adapter.getOutcomeKeys().containsAll(List.of(
                "success",
                "partial_success",
                "business_failure",
                "unknown_result"
        )));
        assertTrue(adapter.getErrorCodes().containsAll(List.of(
                "login_required",
                "captcha_required",
                "rate_limited",
                "timeout",
                "no_candidates",
                "challenge_page",
                "provider_unavailable",
                "unexpected_response",
                "unmapped_identity"
        )));
        assertTrue(adapter.getDiagnosticFields().containsAll(List.of(
                "traceId",
                "providerTraceId",
                "timeoutCategory",
                "retryable",
                "rawDiagnosticPointer",
                "confirmationRequired"
        )));
        assertTrue(adapter.isSuccessfulOutcome("success"));
        assertTrue(!adapter.isSuccessfulOutcome("challenge_page"));
        assertTrue(!adapter.isSuccessfulOutcome("unknown_result"));
        assertTrue(adapter.getCallBoundaryRules().containsAll(List.of(
                "backend_adapter_required",
                "frontend_must_not_call_provider_directly",
                "business_service_uses_shared_adapter_when_available"
        )));
    }

    @Test
    void workflowStandardAllowsOnlyAuditableLifecycleTransitions() {
        FoundationStandardsService service = new FoundationStandardsService();

        FoundationWorkflowStandard workflow = service.workflowStandard();

        assertTrue(workflow.getStatusKeys().containsAll(List.of(
                "queued",
                "running",
                "waiting_for_review",
                "confirmed",
                "executed",
                "failed",
                "cancelled",
                "readback_unknown"
        )));
        assertTrue(workflow.isAllowedTransition("queued", "running"));
        assertTrue(workflow.isAllowedTransition("running", "waiting_for_review"));
        assertTrue(workflow.isAllowedTransition("waiting_for_review", "confirmed"));
        assertTrue(workflow.isAllowedTransition("confirmed", "executed"));
        assertTrue(workflow.isAllowedTransition("running", "readback_unknown"));
        assertTrue(workflow.isAllowedTransition("readback_unknown", "waiting_for_review"));
        assertTrue(!workflow.isAllowedTransition("executed", "running"));
        assertTrue(!workflow.isAllowedTransition("cancelled", "executed"));
        assertTrue(!workflow.isAllowedTransition("failed", "executed"));
        assertTrue(!workflow.isAllowedTransition("queued", "executed"));
        assertTrue(workflow.getRecoveryRules().containsAll(List.of(
                "failed_work_may_retry_by_creating_new_queued_attempt",
                "stale_running_work_must_recover_to_retry_or_failed",
                "cancelled_is_terminal",
                "readback_unknown_requires_manual_review_or_retry"
        )));
    }

    private FoundationAiOutputModeDefinition aiMode(FoundationStandardsService service, String key) {
        return service.listAiOutputModes()
                .stream()
                .filter(candidate -> key.equals(candidate.getKey()))
                .findFirst()
                .orElseThrow();
    }
}
