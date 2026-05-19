package com.nuono.next.procurement;

public class ProcurementRequirementConfirmationCommands {

    private ProcurementRequirementConfirmationCommands() {
    }

    public static class OperatorCommand {

        private Long ownerUserId;

        private Long operatorUserId;

        private String operatorRole;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getOperatorUserId() {
            return operatorUserId;
        }

        public void setOperatorUserId(Long operatorUserId) {
            this.operatorUserId = operatorUserId;
        }

        public String getOperatorRole() {
            return operatorRole;
        }

        public void setOperatorRole(String operatorRole) {
            this.operatorRole = operatorRole;
        }
    }

    public static class InitializePoolCommand extends OperatorCommand {

        private Boolean triggerInquiry;

        public Boolean getTriggerInquiry() {
            return triggerInquiry;
        }

        public void setTriggerInquiry(Boolean triggerInquiry) {
            this.triggerInquiry = triggerInquiry;
        }
    }

    public static class RemovePoolItemCommand extends OperatorCommand {

        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class AddPoolCandidateCommand extends OperatorCommand {

        private String reason;

        private Boolean triggerInquiry;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Boolean getTriggerInquiry() {
            return triggerInquiry;
        }

        public void setTriggerInquiry(Boolean triggerInquiry) {
            this.triggerInquiry = triggerInquiry;
        }
    }

    public static class FinishPoolInquiryCommand extends OperatorCommand {

        private String finishMode;

        private String note;

        private Boolean force;

        public String getFinishMode() {
            return finishMode;
        }

        public void setFinishMode(String finishMode) {
            this.finishMode = finishMode;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public Boolean getForce() {
            return force;
        }

        public void setForce(Boolean force) {
            this.force = force;
        }
    }

    public static class RecordPoolItemReplyCommand extends OperatorCommand {

        private String quotePriceText;

        private String quoteMoqText;

        private String quoteDeliveryText;

        private String replySummary;

        private String riskNote;

        public String getQuotePriceText() {
            return quotePriceText;
        }

        public void setQuotePriceText(String quotePriceText) {
            this.quotePriceText = quotePriceText;
        }

        public String getQuoteMoqText() {
            return quoteMoqText;
        }

        public void setQuoteMoqText(String quoteMoqText) {
            this.quoteMoqText = quoteMoqText;
        }

        public String getQuoteDeliveryText() {
            return quoteDeliveryText;
        }

        public void setQuoteDeliveryText(String quoteDeliveryText) {
            this.quoteDeliveryText = quoteDeliveryText;
        }

        public String getReplySummary() {
            return replySummary;
        }

        public void setReplySummary(String replySummary) {
            this.replySummary = replySummary;
        }

        public String getRiskNote() {
            return riskNote;
        }

        public void setRiskNote(String riskNote) {
            this.riskNote = riskNote;
        }
    }

    public static class AdvancePoolItemFollowUpCommand extends OperatorCommand {

        private String note;

        private String expectedStatus;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getExpectedStatus() {
            return expectedStatus;
        }

        public void setExpectedStatus(String expectedStatus) {
            this.expectedStatus = expectedStatus;
        }
    }

    public static class MarkPoolItemExceptionCommand extends OperatorCommand {

        private String reason;

        private String replySummary;

        private String riskNote;

        private String expectedStatus;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getReplySummary() {
            return replySummary;
        }

        public void setReplySummary(String replySummary) {
            this.replySummary = replySummary;
        }

        public String getRiskNote() {
            return riskNote;
        }

        public void setRiskNote(String riskNote) {
            this.riskNote = riskNote;
        }

        public String getExpectedStatus() {
            return expectedStatus;
        }

        public void setExpectedStatus(String expectedStatus) {
            this.expectedStatus = expectedStatus;
        }
    }

    public static class ConfirmFinalCandidatesCommand extends OperatorCommand {

        private Long primaryPoolItemId;

        private Long backupPoolItemId;

        private String decisionNote;

        public Long getPrimaryPoolItemId() {
            return primaryPoolItemId;
        }

        public void setPrimaryPoolItemId(Long primaryPoolItemId) {
            this.primaryPoolItemId = primaryPoolItemId;
        }

        public Long getBackupPoolItemId() {
            return backupPoolItemId;
        }

        public void setBackupPoolItemId(Long backupPoolItemId) {
            this.backupPoolItemId = backupPoolItemId;
        }

        public String getDecisionNote() {
            return decisionNote;
        }

        public void setDecisionNote(String decisionNote) {
            this.decisionNote = decisionNote;
        }
    }

    public static class GenerateSummaryCommand extends OperatorCommand {

        private Boolean regenerate;

        public Boolean getRegenerate() {
            return regenerate;
        }

        public void setRegenerate(Boolean regenerate) {
            this.regenerate = regenerate;
        }
    }
}
