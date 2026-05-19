package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;

public interface ProcurementAutoInquirySendGateway {

    SendPreparationResult prepareInput(AutoInquiryTaskView task, AutoInquirySessionView session);

    SendAttemptResult send(AutoInquiryTaskView task, AutoInquirySessionView session);

    class SendPreparationResult {

        private boolean ready;

        private String inputLocator;

        private String contentEcho;

        private String evidence;

        private String failureCode;

        private String failureMessage;

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }

        public String getInputLocator() {
            return inputLocator;
        }

        public void setInputLocator(String inputLocator) {
            this.inputLocator = inputLocator;
        }

        public String getContentEcho() {
            return contentEcho;
        }

        public void setContentEcho(String contentEcho) {
            this.contentEcho = contentEcho;
        }

        public String getEvidence() {
            return evidence;
        }

        public void setEvidence(String evidence) {
            this.evidence = evidence;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }

    class SendAttemptResult {

        private boolean delivered;

        private String threadCheckpoint;

        private String messageDigest;

        private String evidence;

        private String failureCode;

        private String failureMessage;

        public boolean isDelivered() {
            return delivered;
        }

        public void setDelivered(boolean delivered) {
            this.delivered = delivered;
        }

        public String getThreadCheckpoint() {
            return threadCheckpoint;
        }

        public void setThreadCheckpoint(String threadCheckpoint) {
            this.threadCheckpoint = threadCheckpoint;
        }

        public String getMessageDigest() {
            return messageDigest;
        }

        public void setMessageDigest(String messageDigest) {
            this.messageDigest = messageDigest;
        }

        public String getEvidence() {
            return evidence;
        }

        public void setEvidence(String evidence) {
            this.evidence = evidence;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }
}
