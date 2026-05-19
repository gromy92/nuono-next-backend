package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

public class ProcurementManualCandidateBackfillCommand {

    private Long ownerUserId;

    private String orderNo;

    private Long demandItemId;

    private List<ManualCandidateInput> candidates = new ArrayList<>();

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getDemandItemId() {
        return demandItemId;
    }

    public void setDemandItemId(Long demandItemId) {
        this.demandItemId = demandItemId;
    }

    public List<ManualCandidateInput> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<ManualCandidateInput> candidates) {
        this.candidates = candidates;
    }

    public static class ManualCandidateInput {

        private String candidateUrl;

        private String title;

        private String supplierName;

        private String priceText;

        private String moqText;

        private String locationText;

        private String resultCardText;

        private String detailHighlightText;

        private String attributeSnapshotText;

        private String shippingSnapshotText;

        private String packageSnapshotText;

        private String mainImageUrl;

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public String getLocationText() {
            return locationText;
        }

        public void setLocationText(String locationText) {
            this.locationText = locationText;
        }

        public String getResultCardText() {
            return resultCardText;
        }

        public void setResultCardText(String resultCardText) {
            this.resultCardText = resultCardText;
        }

        public String getDetailHighlightText() {
            return detailHighlightText;
        }

        public void setDetailHighlightText(String detailHighlightText) {
            this.detailHighlightText = detailHighlightText;
        }

        public String getAttributeSnapshotText() {
            return attributeSnapshotText;
        }

        public void setAttributeSnapshotText(String attributeSnapshotText) {
            this.attributeSnapshotText = attributeSnapshotText;
        }

        public String getShippingSnapshotText() {
            return shippingSnapshotText;
        }

        public void setShippingSnapshotText(String shippingSnapshotText) {
            this.shippingSnapshotText = shippingSnapshotText;
        }

        public String getPackageSnapshotText() {
            return packageSnapshotText;
        }

        public void setPackageSnapshotText(String packageSnapshotText) {
            this.packageSnapshotText = packageSnapshotText;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }
    }
}
