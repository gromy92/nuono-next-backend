package com.nuono.next.productlisting;

public class ProductListingWorkflowSummaryView {

    private ProductListingWorkflowView.Phase phase;
    private ProductListingWorkflowView.WriteCertainty writeCertainty;
    private ProductListingWorkflowView.NextAction nextAction;
    private String reasonCode;
    private String message;

    public static ProductListingWorkflowSummaryView from(ProductListingWorkflowView workflow) {
        if (workflow == null) {
            return null;
        }
        ProductListingWorkflowSummaryView summary = new ProductListingWorkflowSummaryView();
        summary.setPhase(workflow.getPhase());
        summary.setWriteCertainty(workflow.getWriteCertainty());
        summary.setNextAction(workflow.getNextAction());
        summary.setReasonCode(workflow.getReasonCode());
        summary.setMessage(workflow.getMessage());
        return summary;
    }

    public ProductListingWorkflowView.Phase getPhase() {
        return phase;
    }

    public void setPhase(ProductListingWorkflowView.Phase phase) {
        this.phase = phase;
    }

    public ProductListingWorkflowView.WriteCertainty getWriteCertainty() {
        return writeCertainty;
    }

    public void setWriteCertainty(ProductListingWorkflowView.WriteCertainty writeCertainty) {
        this.writeCertainty = writeCertainty;
    }

    public ProductListingWorkflowView.NextAction getNextAction() {
        return nextAction;
    }

    public void setNextAction(ProductListingWorkflowView.NextAction nextAction) {
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
}
