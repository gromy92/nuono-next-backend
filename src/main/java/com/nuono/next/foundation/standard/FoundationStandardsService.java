package com.nuono.next.foundation.standard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FoundationStandardsService {

    private static final List<FoundationFactDefinition> FACT_DEFINITIONS = List.of(
            new FoundationFactDefinition(
                    "daily_sales_fact",
                    "Daily sales fact",
                    "Canonical daily marketplace sales fact used as the first reference fact.",
                    "com.nuono.next.sales.DailySalesFact",
                    List.of(
                            "ownerUserId",
                            "logicalStoreId",
                            "storeCode",
                            "siteCode",
                            "partnerSku",
                            "sku",
                            "eventDate"
                    ),
                    List.of(
                            "sourceSystem",
                            "sourceBatchId",
                            "sourceRecordId",
                            "idempotencyKey",
                            "snapshotAt"
                    ),
                    List.of("raw_source_values", "derived_values"),
                    List.of(
                            "factKey",
                            "factType",
                            "sourceSystem",
                            "ownerUserId",
                            "logicalStoreId",
                            "storeCode",
                            "siteCode",
                            "partnerSku",
                            "sku",
                            "eventDate",
                            "snapshotAt",
                            "sourceBatchId",
                            "sourceTaskId",
                            "sourceRecordId",
                            "idempotencyKey",
                            "qualityState"
                    ),
                    List.of(
                            "missing_source_values_remain_missing",
                            "derived_values_must_be_marked_as_derived",
                            "source_zero_must_only_come_from_explicit_source_zero"
                    )
            )
    );

    private static final List<FoundationQualityState> QUALITY_STATES = List.of(
            new FoundationQualityState("stale_data", "Stale data", "The fact is older than the freshness rule allows."),
            new FoundationQualityState("missing_mapping", "Missing mapping", "A required internal mapping is missing."),
            new FoundationQualityState("partial_success", "Partial success", "The source returned usable data with incomplete coverage."),
            new FoundationQualityState("empty_source_report", "Empty source report", "The source report completed but contained no rows."),
            new FoundationQualityState("challenge_page", "Challenge page", "The source returned a captcha, challenge, or anti-bot page."),
            new FoundationQualityState("provider_unavailable", "Provider unavailable", "The upstream provider is unavailable."),
            new FoundationQualityState("unmapped_identity", "Unmapped identity", "The source identity cannot be bound to an owner, store, site, or product."),
            new FoundationQualityState("missing_source_value", "Missing source value", "A source field needed for calculation is missing and must not be treated as zero.")
    );

    private static final List<FoundationMetricDefinition> METRIC_DEFINITIONS = List.of(
            new FoundationMetricDefinition(
                    "sales_net_units",
                    "Sales net units",
                    "Authoritative shipped-minus-cancelled unit metric for daily sales analysis.",
                    List.of("daily_sales_fact"),
                    "foundation_metric_registry",
                    "eventDate must be within the consumer's declared freshness window.",
                    "stale_data, missing_mapping, partial_success, empty_source_report, and unmapped_identity must be propagated.",
                    "integer count keyed by owner, logical store, site, partner SKU, SKU, and event date.",
                    true
            )
    );

    private static final List<FoundationAiOutputModeDefinition> AI_OUTPUT_MODES = List.of(
            new FoundationAiOutputModeDefinition(
                    "file_document_parse",
                    "File document parse",
                    "AI or rule-assisted document extraction governed by file-management version lineage.",
                    List.of(
                            "target_output_plan",
                            "immutable_revision",
                            "human_processing",
                            "complete_version_snapshot",
                            "active_pointer"
                    ),
                    List.of(
                            "file_management_version_lineage",
                            "file_management_version_item_reference",
                            "result_lineage_reference",
                            "active_business_selection_reference",
                            "review_payload",
                            "effective_payload",
                            "publish_snapshot"
                    ),
                    false
            ),
            new FoundationAiOutputModeDefinition(
                    "direct_ai_analysis",
                    "Direct AI analysis",
                    "A bounded analysis response returned to the requester without external writes.",
                    List.of("request", "model_invocation", "schema_validation", "audited_response"),
                    List.of(
                            "backend_ai_capability_service",
                            "prompt_template",
                            "model_selection",
                            "language_metadata",
                            "type_metadata",
                            "schema_expectation",
                            "user_scope",
                            "usage_audit",
                            "error_handling"
                    ),
                    false
            ),
            new FoundationAiOutputModeDefinition(
                    "business_suggestion",
                    "Business suggestion",
                    "A proposed operational action with evidence, review, confirmation, and readback.",
                    List.of("suggestion_created", "review_pending", "confirmed", "execution_placeholder", "readback_recorded"),
                    List.of(
                            "backend_ai_capability_service",
                            "suggestion_payload",
                            "evidence_references",
                            "human_review",
                            "human_confirmation",
                            "execution_placeholder",
                            "readback_audit"
                    ),
                    false
            )
    );

    private static final FoundationAdapterStandard ADAPTER_STANDARD = new FoundationAdapterStandard(
            List.of(
                    "success",
                    "partial_success",
                    "business_failure",
                    "unknown_result"
            ),
            List.of(
                    "login_required",
                    "captcha_required",
                    "rate_limited",
                    "timeout",
                    "no_candidates",
                    "challenge_page",
                    "provider_unavailable",
                    "unexpected_response",
                    "unmapped_identity"
            ),
            List.of(
                    "traceId",
                    "providerTraceId",
                    "timeoutCategory",
                    "retryable",
                    "rawDiagnosticPointer",
                    "confirmationRequired"
            ),
            List.of(
                    "backend_adapter_required",
                    "frontend_must_not_call_provider_directly",
                    "business_service_uses_shared_adapter_when_available"
            ),
            "com.nuono.next.productselection.Ali1688ImageSearchGateway"
    );

    private static final FoundationWorkflowStandard WORKFLOW_STANDARD = new FoundationWorkflowStandard(
            List.of(
                    "queued",
                    "running",
                    "waiting_for_review",
                    "confirmed",
                    "executed",
                    "failed",
                    "cancelled",
                    "readback_unknown"
            ),
            List.of(
                    "queued->running",
                    "queued->cancelled",
                    "running->waiting_for_review",
                    "running->confirmed",
                    "running->failed",
                    "running->cancelled",
                    "running->readback_unknown",
                    "waiting_for_review->confirmed",
                    "waiting_for_review->cancelled",
                    "confirmed->executed",
                    "confirmed->failed",
                    "executed->readback_unknown",
                    "readback_unknown->waiting_for_review",
                    "readback_unknown->failed"
            ),
            List.of(
                    "failed_work_may_retry_by_creating_new_queued_attempt",
                    "stale_running_work_must_recover_to_retry_or_failed",
                    "cancelled_is_terminal",
                    "readback_unknown_requires_manual_review_or_retry"
            ),
            "com.nuono.next.product.ProductPublishTaskLifecycleService"
    );

    public List<FoundationFactDefinition> listFactDefinitions() {
        return FACT_DEFINITIONS;
    }

    public List<FoundationQualityState> listQualityStates() {
        return QUALITY_STATES;
    }

    public List<FoundationMetricDefinition> listMetricDefinitions() {
        return METRIC_DEFINITIONS;
    }

    public FoundationMetricPreview previewSalesNetUnits(Integer shippedUnits, Integer cancelledUnits, boolean stale) {
        List<String> qualityStates = new ArrayList<>();
        if (stale) {
            qualityStates.add("stale_data");
        }
        if (shippedUnits == null || cancelledUnits == null) {
            qualityStates.add("missing_source_value");
            return new FoundationMetricPreview("sales_net_units", null, qualityStates);
        }
        return new FoundationMetricPreview("sales_net_units", shippedUnits - cancelledUnits, qualityStates);
    }

    public List<FoundationAiOutputModeDefinition> listAiOutputModes() {
        return AI_OUTPUT_MODES;
    }

    public FoundationAdapterStandard adapterStandard() {
        return ADAPTER_STANDARD;
    }

    public FoundationWorkflowStandard workflowStandard() {
        return WORKFLOW_STANDARD;
    }
}
