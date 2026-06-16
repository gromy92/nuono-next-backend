package com.nuono.next.intransit;

public final class InTransitForwarderCommands {

    private InTransitForwarderCommands() {
    }

    public static class SaveForwarderCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private String forwarderCode;
        private String forwarderName;

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

        public String getForwarderCode() {
            return forwarderCode;
        }

        public void setForwarderCode(String forwarderCode) {
            this.forwarderCode = forwarderCode;
        }

        public String getForwarderName() {
            return forwarderName;
        }

        public void setForwarderName(String forwarderName) {
            this.forwarderName = forwarderName;
        }
    }

    public static class SaveForwarderAliasCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private Long standardForwarderId;
        private String rawForwarderName;

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

        public Long getStandardForwarderId() {
            return standardForwarderId;
        }

        public void setStandardForwarderId(Long standardForwarderId) {
            this.standardForwarderId = standardForwarderId;
        }

        public String getRawForwarderName() {
            return rawForwarderName;
        }

        public void setRawForwarderName(String rawForwarderName) {
            this.rawForwarderName = rawForwarderName;
        }
    }

    public static class ResolveForwarderCommand {
        private Long ownerUserId;
        private String rawForwarderName;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getRawForwarderName() {
            return rawForwarderName;
        }

        public void setRawForwarderName(String rawForwarderName) {
            this.rawForwarderName = rawForwarderName;
        }
    }
}
