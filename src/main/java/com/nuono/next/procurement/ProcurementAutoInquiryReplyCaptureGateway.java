package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.util.ArrayList;
import java.util.List;

public interface ProcurementAutoInquiryReplyCaptureGateway {

    ReplyCaptureResult collectReplies(AutoInquiryTaskView task, AutoInquirySessionView session);

    class ReplyCaptureResult {

        private boolean listening;

        private String checkpoint;

        private String failureCode;

        private String failureMessage;

        private List<SupplierReplyView> replies = new ArrayList<>();

        public boolean isListening() {
            return listening;
        }

        public void setListening(boolean listening) {
            this.listening = listening;
        }

        public String getCheckpoint() {
            return checkpoint;
        }

        public void setCheckpoint(String checkpoint) {
            this.checkpoint = checkpoint;
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

        public List<SupplierReplyView> getReplies() {
            return replies;
        }

        public void setReplies(List<SupplierReplyView> replies) {
            this.replies = replies;
        }
    }

    class SupplierReplyView {

        private String messageDigest;

        private String replyAt;

        private String sourceSide;

        public String getMessageDigest() {
            return messageDigest;
        }

        public void setMessageDigest(String messageDigest) {
            this.messageDigest = messageDigest;
        }

        public String getReplyAt() {
            return replyAt;
        }

        public void setReplyAt(String replyAt) {
            this.replyAt = replyAt;
        }

        public String getSourceSide() {
            return sourceSide;
        }

        public void setSourceSide(String sourceSide) {
            this.sourceSide = sourceSide;
        }
    }
}
