package com.nuono.next.intransit;

public final class InTransitForwarderRecords {

    private InTransitForwarderRecords() {
    }

    public static class ForwarderRow {
        private Long id;
        private Long ownerUserId;
        private String forwarderCode;
        private String forwarderName;
        private String status;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }
    }

    public static class ForwarderAliasRow {
        private Long id;
        private Long ownerUserId;
        private Long standardForwarderId;
        private String rawForwarderName;
        private String normalizedRawForwarderName;
        private String status;
        private Long createdBy;
        private Long updatedBy;
        private String standardForwarderCode;
        private String standardForwarderName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
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

        public String getNormalizedRawForwarderName() {
            return normalizedRawForwarderName;
        }

        public void setNormalizedRawForwarderName(String normalizedRawForwarderName) {
            this.normalizedRawForwarderName = normalizedRawForwarderName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public String getStandardForwarderCode() {
            return standardForwarderCode;
        }

        public void setStandardForwarderCode(String standardForwarderCode) {
            this.standardForwarderCode = standardForwarderCode;
        }

        public String getStandardForwarderName() {
            return standardForwarderName;
        }

        public void setStandardForwarderName(String standardForwarderName) {
            this.standardForwarderName = standardForwarderName;
        }
    }

    public static class ForwarderView {
        private Long id;
        private String forwarderCode;
        private String forwarderName;
        private String status;

        public static ForwarderView from(ForwarderRow row) {
            ForwarderView view = new ForwarderView();
            view.setId(row.getId());
            view.setForwarderCode(row.getForwarderCode());
            view.setForwarderName(row.getForwarderName());
            view.setStatus(row.getStatus());
            return view;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class ForwarderAliasView {
        private Long id;
        private Long standardForwarderId;
        private String rawForwarderName;
        private String normalizedRawForwarderName;
        private String standardForwarderCode;
        private String standardForwarderName;
        private String status;

        public static ForwarderAliasView from(ForwarderAliasRow row) {
            ForwarderAliasView view = new ForwarderAliasView();
            view.setId(row.getId());
            view.setStandardForwarderId(row.getStandardForwarderId());
            view.setRawForwarderName(row.getRawForwarderName());
            view.setNormalizedRawForwarderName(row.getNormalizedRawForwarderName());
            view.setStandardForwarderCode(row.getStandardForwarderCode());
            view.setStandardForwarderName(row.getStandardForwarderName());
            view.setStatus(row.getStatus());
            return view;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getNormalizedRawForwarderName() {
            return normalizedRawForwarderName;
        }

        public void setNormalizedRawForwarderName(String normalizedRawForwarderName) {
            this.normalizedRawForwarderName = normalizedRawForwarderName;
        }

        public String getStandardForwarderCode() {
            return standardForwarderCode;
        }

        public void setStandardForwarderCode(String standardForwarderCode) {
            this.standardForwarderCode = standardForwarderCode;
        }

        public String getStandardForwarderName() {
            return standardForwarderName;
        }

        public void setStandardForwarderName(String standardForwarderName) {
            this.standardForwarderName = standardForwarderName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class ForwarderResolveView {
        private String qualityStatus;
        private String rawForwarderName;
        private String normalizedRawForwarderName;
        private Long standardForwarderId;
        private String standardForwarderCode;
        private String standardForwarderName;

        public static ForwarderResolveView matched(ForwarderAliasRow row) {
            ForwarderResolveView view = new ForwarderResolveView();
            view.setQualityStatus(InTransitQualityStatus.FORWARDER_MATCHED.code());
            view.setRawForwarderName(row.getRawForwarderName());
            view.setNormalizedRawForwarderName(row.getNormalizedRawForwarderName());
            view.setStandardForwarderId(row.getStandardForwarderId());
            view.setStandardForwarderCode(row.getStandardForwarderCode());
            view.setStandardForwarderName(row.getStandardForwarderName());
            return view;
        }

        public static ForwarderResolveView matched(ForwarderRow row, String rawForwarderName, String normalizedRawForwarderName) {
            ForwarderResolveView view = new ForwarderResolveView();
            view.setQualityStatus(InTransitQualityStatus.FORWARDER_MATCHED.code());
            view.setRawForwarderName(rawForwarderName);
            view.setNormalizedRawForwarderName(normalizedRawForwarderName);
            view.setStandardForwarderId(row.getId());
            view.setStandardForwarderCode(row.getForwarderCode());
            view.setStandardForwarderName(row.getForwarderName());
            return view;
        }

        public static ForwarderResolveView unmatched(String rawForwarderName, String normalizedRawForwarderName) {
            ForwarderResolveView view = new ForwarderResolveView();
            view.setQualityStatus(InTransitQualityStatus.FORWARDER_UNMATCHED.code());
            view.setRawForwarderName(rawForwarderName);
            view.setNormalizedRawForwarderName(normalizedRawForwarderName);
            return view;
        }

        public String getQualityStatus() {
            return qualityStatus;
        }

        public void setQualityStatus(String qualityStatus) {
            this.qualityStatus = qualityStatus;
        }

        public String getRawForwarderName() {
            return rawForwarderName;
        }

        public void setRawForwarderName(String rawForwarderName) {
            this.rawForwarderName = rawForwarderName;
        }

        public String getNormalizedRawForwarderName() {
            return normalizedRawForwarderName;
        }

        public void setNormalizedRawForwarderName(String normalizedRawForwarderName) {
            this.normalizedRawForwarderName = normalizedRawForwarderName;
        }

        public Long getStandardForwarderId() {
            return standardForwarderId;
        }

        public void setStandardForwarderId(Long standardForwarderId) {
            this.standardForwarderId = standardForwarderId;
        }

        public String getStandardForwarderCode() {
            return standardForwarderCode;
        }

        public void setStandardForwarderCode(String standardForwarderCode) {
            this.standardForwarderCode = standardForwarderCode;
        }

        public String getStandardForwarderName() {
            return standardForwarderName;
        }

        public void setStandardForwarderName(String standardForwarderName) {
            this.standardForwarderName = standardForwarderName;
        }
    }
}
