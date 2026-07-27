package com.nuono.next.productlisting;

public class ProductListingWorkflowView {

    public enum Phase {
        EDITING,
        READY_TO_CONFIRM,
        PUBLISHING,
        PUBLISHED,
        ACTION_REQUIRED
    }

    public enum WriteCertainty {
        NOT_STARTED,
        UNKNOWN,
        WRITTEN,
        VERIFIED
    }

    public enum NextAction {
        REVIEW_DRAFT,
        EDIT_DRAFT,
        CONFIRM_PUBLISH,
        WAIT,
        WAIT_FOR_REAUTHENTICATION,
        REAUTHENTICATE,
        CHECK_CREATE_RESULT,
        CONTINUE_AFTER_CREATE,
        VERIFY_READBACK,
        REPLAY_PROJECTION,
        NONE
    }

    private Phase phase;
    private WriteCertainty writeCertainty;
    private NextAction nextAction;
    private String reasonCode;
    private String message;
    private ProductListingDraftView draft;
    private ProductListingTaskView dryRunTask;
    private ProductListingTaskView realRunTask;

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public WriteCertainty getWriteCertainty() {
        return writeCertainty;
    }

    public void setWriteCertainty(WriteCertainty writeCertainty) {
        this.writeCertainty = writeCertainty;
    }

    public NextAction getNextAction() {
        return nextAction;
    }

    public void setNextAction(NextAction nextAction) {
        this.nextAction = nextAction;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ProductListingDraftView getDraft() {
        return draft;
    }

    public void setDraft(ProductListingDraftView draft) {
        this.draft = draft;
    }

    public ProductListingTaskView getDryRunTask() {
        return dryRunTask;
    }

    public void setDryRunTask(ProductListingTaskView dryRunTask) {
        this.dryRunTask = dryRunTask;
    }

    public ProductListingTaskView getRealRunTask() {
        return realRunTask;
    }

    public void setRealRunTask(ProductListingTaskView realRunTask) {
        this.realRunTask = realRunTask;
    }
}
