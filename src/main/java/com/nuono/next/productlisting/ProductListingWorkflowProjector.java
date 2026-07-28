package com.nuono.next.productlisting;

public final class ProductListingWorkflowProjector {

    public ProductListingWorkflowView project(
            ProductListingDraftView draft,
            ProductListingTaskView dryRunTask,
            ProductListingTaskView realRunTask
    ) {
        ProductListingWorkflowView view = new ProductListingWorkflowView();
        view.setDraft(draft);
        view.setDryRunTask(dryRunTask);
        view.setRealRunTask(realRunTask);
        if (isRealRunStatus(realRunTask, "submitted") || isRealRunStatus(realRunTask, "running")) {
            boolean submitted = "submitted".equalsIgnoreCase(realRunTask.getStatus());
            view.setPhase(ProductListingWorkflowView.Phase.PUBLISHING);
            view.setWriteCertainty(submitted
                    ? ProductListingWorkflowView.WriteCertainty.NOT_STARTED
                    : ProductListingWorkflowView.WriteCertainty.UNKNOWN);
            view.setNextAction(ProductListingWorkflowView.NextAction.WAIT);
            view.setReasonCode(
                    "REAL_RUN_" + upperOrDefault(realRunTask.getStatus(), "UNKNOWN"));
            view.setMessage(submitted ? "上架任务已提交，等待执行。" : "正在写入 Noon，请勿重复提交。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "succeeded")) {
            view.setPhase(ProductListingWorkflowView.Phase.PUBLISHED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.VERIFIED);
            view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
            view.setReasonCode("REAL_RUN_SUCCEEDED");
            view.setMessage("商品已成功上架并完成核对。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && "projection_backfill_failed".equalsIgnoreCase(realRunTask.getFailureCode())) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.VERIFIED);
            view.setNextAction(ProductListingWorkflowView.NextAction.REPLAY_PROJECTION);
            view.setReasonCode("PROJECTION_BACKFILL_FAILED");
            view.setMessage("Noon 上架已核对成功，请恢复本地商品投影。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && !hasCreateReferences(realRunTask)
                && isAuthenticationFailure(realRunTask)
                && ProductListingWorkflowEvidence.hasUnresolvedCreateOutcome(
                        realRunTask.getNoonResult())) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(
                    ProductListingWorkflowView.WriteCertainty.UNKNOWN
            );
            view.setNextAction(
                    ProductListingWorkflowView.NextAction.REAUTHENTICATE
            );
            view.setReasonCode("NOON_AUTH_REQUIRED");
            view.setMessage(
                    "诺诺本地草稿已保留，但尚未取得 Noon 正式商品引用；请重新授权，系统随后自动只读核对，禁止重复创建。"
            );
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && isCreateOutcomeUnknown(realRunTask)
                && !hasCreateReferences(realRunTask)) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.UNKNOWN);
            view.setNextAction(ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT);
            view.setReasonCode(upperOrDefault(
                    realRunTask.getFailureCode(), "CREATE_OUTCOME_UNKNOWN"));
            view.setMessage("诺诺本地草稿已保留，系统正在只读核对 Noon 正式商品引用；禁止重复创建。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && hasCreateReferences(realRunTask)
                && isCreateOutcomeUnknown(realRunTask)) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.WRITTEN);
            view.setNextAction(ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE);
            view.setReasonCode("CREATE_REFERENCE_RECOVERED");
            view.setMessage("已确认 Noon 商品引用，可继续完成创建后的剩余步骤。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && hasCreateReferences(realRunTask)
                && isAuthenticationFailure(realRunTask)) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(
                    ProductListingWorkflowView.WriteCertainty.WRITTEN
            );
            view.setNextAction(
                    ProductListingWorkflowView.NextAction.REAUTHENTICATE
            );
            view.setReasonCode("NOON_AUTH_REQUIRED");
            view.setMessage(
                    "Noon 商品已经创建，但后续授权已失效；请重新授权后继续，禁止重复创建。"
            );
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && hasCreateReferences(realRunTask)
                && ("real_run_in_progress".equalsIgnoreCase(
                        realRunTask.getFailureCode())
                || ProductListingWorkflowEvidence.hasFailedWriteStep(
                        realRunTask.getNoonResult()))) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.WRITTEN);
            view.setNextAction(ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE);
            view.setReasonCode("POST_CREATE_WRITE_FAILED");
            view.setMessage("Noon 已创建商品，但后续写入未完成；请从创建后步骤继续。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "written_verify_failed")
                && hasCreateReferences(realRunTask)
                && !ProductListingWorkflowEvidence.hasFailedWriteStep(
                        realRunTask.getNoonResult())) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.WRITTEN);
            view.setNextAction(ProductListingWorkflowView.NextAction.VERIFY_READBACK);
            view.setReasonCode(upperOrDefault(realRunTask.getFailureCode(), "READBACK_VERIFICATION_FAILED"));
            view.setMessage("Noon 已写入，但回读核对未通过；请重新核对，禁止重复创建。");
            return view;
        }
        if (isTerminalAttempt(realRunTask)
                && realRunTask.getNoonResult() != null
                && realRunTask.getNoonResult().isSuccess()) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.VERIFIED);
            view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
            view.setReasonCode("TERMINAL_TASK_WITH_VERIFIED_WRITE");
            view.setMessage("任务状态异常但包含已核对写入证据，禁止重新上架。");
            return view;
        }
        if (isTerminalAttempt(realRunTask) && hasCreateReferences(realRunTask)) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.WRITTEN);
            view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
            view.setReasonCode("TERMINAL_TASK_WITH_WRITE_REFERENCE");
            view.setMessage("任务包含 Noon 商品引用，需人工处理且禁止重新创建。");
            return view;
        }
        if (isTerminalAttempt(realRunTask) && isCreateOutcomeUnknown(realRunTask)) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.UNKNOWN);
            view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
            view.setReasonCode("TERMINAL_TASK_WITH_UNKNOWN_WRITE");
            view.setMessage("任务包含不确定写入证据，需人工核对且禁止重新创建。");
            return view;
        }
        if (isRealRunStatus(realRunTask, "failed") || isRealRunStatus(realRunTask, "rejected")) {
            boolean rejected = "rejected".equalsIgnoreCase(realRunTask.getStatus());
            boolean notStarted = rejected || isClearlyNotStartedFailure(realRunTask);
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(notStarted
                    ? ProductListingWorkflowView.WriteCertainty.NOT_STARTED
                    : ProductListingWorkflowView.WriteCertainty.UNKNOWN);
            if (!notStarted) {
                view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
                view.setMessage("写入结果缺少足够证据，需人工核对且禁止重新上架。");
            } else if (isAuthenticationFailure(realRunTask)) {
                view.setNextAction(ProductListingWorkflowView.NextAction.REAUTHENTICATE);
                view.setMessage("Noon 授权已失效，请重新授权后处理该任务；不要重复确认。");
            } else if (rejected && requiresDraftEdit(realRunTask)) {
                view.setNextAction(ProductListingWorkflowView.NextAction.EDIT_DRAFT);
                view.setMessage("本次确认已被拒绝，请修改草稿并生成新的 dry-run。");
            } else if (!rejected) {
                view.setNextAction(ProductListingWorkflowView.NextAction.REVIEW_DRAFT);
                view.setMessage("本次写入未开始，请重新检查草稿后生成新的 dry-run。");
            } else {
                view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
                view.setMessage("本次真实上架尝试已结束，系统不会再次开放相同 dry-run。");
            }
            view.setReasonCode(upperOrDefault(realRunTask.getFailureCode(), "REAL_RUN_ATTEMPT_TERMINAL"));
            return view;
        }
        if (realRunTask != null) {
            view.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.UNKNOWN);
            view.setNextAction(ProductListingWorkflowView.NextAction.NONE);
            view.setReasonCode("UNMAPPED_REAL_RUN_STATE");
            view.setMessage("上架任务状态需要人工处理；系统不会开放重复确认。");
            return view;
        }
        if (realRunTask == null
                && dryRunTask != null
                && "DRY_RUN".equalsIgnoreCase(dryRunTask.getMode())
                && "validated".equalsIgnoreCase(dryRunTask.getStatus())) {
            view.setPhase(ProductListingWorkflowView.Phase.READY_TO_CONFIRM);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.NOT_STARTED);
            view.setNextAction(ProductListingWorkflowView.NextAction.CONFIRM_PUBLISH);
            view.setReasonCode("DRY_RUN_VALIDATED");
            view.setMessage("上架检查已通过，等待确认发布。");
            return view;
        }
        if (realRunTask == null
                && dryRunTask != null
                && "DRY_RUN".equalsIgnoreCase(dryRunTask.getMode())
                && "validation_failed".equalsIgnoreCase(dryRunTask.getStatus())) {
            view.setPhase(ProductListingWorkflowView.Phase.EDITING);
            view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.NOT_STARTED);
            view.setNextAction(ProductListingWorkflowView.NextAction.EDIT_DRAFT);
            view.setReasonCode("DRY_RUN_VALIDATION_FAILED");
            view.setMessage("上架检查未通过，请按字段问题修改商品资料。");
            return view;
        }
        view.setPhase(ProductListingWorkflowView.Phase.EDITING);
        view.setWriteCertainty(ProductListingWorkflowView.WriteCertainty.NOT_STARTED);
        boolean readyForReview = draft != null && "ready_for_dry_run".equalsIgnoreCase(draft.getStatus());
        view.setNextAction(readyForReview
                ? ProductListingWorkflowView.NextAction.REVIEW_DRAFT
                : ProductListingWorkflowView.NextAction.EDIT_DRAFT);
        view.setReasonCode(readyForReview ? "DRAFT_READY_FOR_REVIEW" : "DRAFT_REQUIRES_EDIT");
        view.setMessage(readyForReview ? "草稿已保存，请重新执行上架检查。" : "请完善商品资料后重新检查。");
        return view;
    }

    private boolean isRealRunStatus(ProductListingTaskView task, String status) {
        return task != null
                && "REAL_RUN".equalsIgnoreCase(task.getMode())
                && status.equalsIgnoreCase(task.getStatus());
    }

    private boolean isTerminalAttempt(ProductListingTaskView task) {
        return isRealRunStatus(task, "failed") || isRealRunStatus(task, "rejected");
    }

    private boolean isCreateOutcomeUnknown(ProductListingTaskView task) {
        return "noon_create_outcome_unknown".equalsIgnoreCase(task.getFailureCode())
                || "real_run_interrupted".equalsIgnoreCase(task.getFailureCode());
    }

    private boolean hasCreateReferences(ProductListingTaskView task) {
        ProductListingNoonWriteResult result = task == null ? null : task.getNoonResult();
        if (result == null || result.getSteps() == null) {
            return false;
        }
        boolean skuParent = false;
        boolean pskuCode = false;
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            String reference = step == null ? null : step.getExternalReference();
            if (reference == null) {
                continue;
            }
            for (String token : reference.split(";")) {
                String value = token.trim();
                skuParent = skuParent || value.matches("(?i)skuParent=.+");
                pskuCode = pskuCode || value.matches("(?i)pskuCode=.+");
            }
        }
        return skuParent && pskuCode;
    }

    private String upperOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isAuthenticationFailure(ProductListingTaskView task) {
        return task != null
                && "noon_auth_required".equalsIgnoreCase(
                        task.getFailureCode()
                );
    }

    private boolean isClearlyNotStartedFailure(ProductListingTaskView task) {
        String code = task == null ? null : task.getFailureCode();
        String category = task == null ? null : task.getFailureCategory();
        if (isAmbiguousWriteFailure(code)) {
            return false;
        }
        return isAuthenticationFailure(task)
                || "validation".equalsIgnoreCase(category)
                || "guard".equalsIgnoreCase(category)
                || "noon_pre_create_failed".equalsIgnoreCase(code)
                || "noon_create_rejected".equalsIgnoreCase(code)
                || "noon_create_not_found_confirmed".equalsIgnoreCase(code)
                || "noon_warehouse_stock_not_supported".equalsIgnoreCase(code)
                || "real_run_interrupted_before_write".equalsIgnoreCase(code)
                || "partner_sku_already_exists".equalsIgnoreCase(code)
                || "barcode_already_exists".equalsIgnoreCase(code);
    }

    private boolean isAmbiguousWriteFailure(String code) {
        return "noon_write_exception".equalsIgnoreCase(code)
                || "noon_create_outcome_unknown".equalsIgnoreCase(code)
                || "noon_write_outcome_unknown".equalsIgnoreCase(code)
                || "real_run_interrupted".equalsIgnoreCase(code);
    }

    private boolean requiresDraftEdit(ProductListingTaskView task) {
        String code = task == null ? null : task.getFailureCode();
        return "dry_run_not_validated".equalsIgnoreCase(code)
                || "noon_pre_create_failed".equalsIgnoreCase(code)
                || "noon_create_rejected".equalsIgnoreCase(code)
                || "noon_create_not_found_confirmed".equalsIgnoreCase(code)
                || "noon_warehouse_stock_not_supported".equalsIgnoreCase(code)
                || "partner_sku_already_exists".equalsIgnoreCase(code)
                || "barcode_already_exists".equalsIgnoreCase(code);
    }
}
