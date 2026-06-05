package com.nuono.next.procurement.aliorder;

import java.util.ArrayList;
import java.util.List;

public class Ali1688HistoricalOrderProductLinkView {

    public static class AuditView {
        private Long auditId;
        private Long assignmentId;
        private String actionType;
        private Long oldLinkId;
        private Long newLinkId;
        private String oldSkuParent;
        private String newSkuParent;
        private Long createdBy;
        private String createdAt;

        public static AuditView fromRow(Ali1688HistoricalOrderProductLinkAuditRow row) {
            AuditView view = new AuditView();
            view.setAuditId(row.getId());
            view.setAssignmentId(row.getAssignmentId());
            view.setActionType(row.getActionType());
            view.setOldLinkId(row.getOldLinkId());
            view.setNewLinkId(row.getNewLinkId());
            view.setOldSkuParent(row.getOldSkuParent());
            view.setNewSkuParent(row.getNewSkuParent());
            view.setCreatedBy(row.getCreatedBy());
            view.setCreatedAt(row.getCreatedAt());
            return view;
        }

        public Long getAuditId() {
            return auditId;
        }

        public void setAuditId(Long auditId) {
            this.auditId = auditId;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public Long getOldLinkId() {
            return oldLinkId;
        }

        public void setOldLinkId(Long oldLinkId) {
            this.oldLinkId = oldLinkId;
        }

        public Long getNewLinkId() {
            return newLinkId;
        }

        public void setNewLinkId(Long newLinkId) {
            this.newLinkId = newLinkId;
        }

        public String getOldSkuParent() {
            return oldSkuParent;
        }

        public void setOldSkuParent(String oldSkuParent) {
            this.oldSkuParent = oldSkuParent;
        }

        public String getNewSkuParent() {
            return newSkuParent;
        }

        public void setNewSkuParent(String newSkuParent) {
            this.newSkuParent = newSkuParent;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class LinkedProductView {
        private String status;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private String productImageUrl;
        private String displayText;

        public static LinkedProductView fromRow(Ali1688HistoricalOrderProductLinkRow row) {
            if (row == null || row.getSkuParent() == null || row.getSkuParent().trim().isEmpty()) {
                return null;
            }
            LinkedProductView view = new LinkedProductView();
            view.setStatus("linked");
            view.setSkuParent(row.getSkuParent());
            view.setPartnerSku(row.getPartnerSku());
            view.setPskuCode(row.getPskuCode());
            view.setProductTitle(row.getProductTitle());
            view.setProductImageUrl(normalizeNoonImageUrl(row.getProductImageUrl()));
            view.setDisplayText("已关联: " + row.getSkuParent());
            return view;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getProductImageUrl() {
            return productImageUrl;
        }

        public void setProductImageUrl(String productImageUrl) {
            this.productImageUrl = productImageUrl;
        }

        public String getDisplayText() {
            return displayText;
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
        }
    }

    public static class LinkRequest {
        private Long assignmentId;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private String productImageUrl;

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getProductImageUrl() {
            return productImageUrl;
        }

        public void setProductImageUrl(String productImageUrl) {
            this.productImageUrl = productImageUrl;
        }
    }

    public static class LinkBatchRequest {
        private List<LinkRequest> links = new ArrayList<>();

        public List<LinkRequest> getLinks() {
            return links;
        }

        public void setLinks(List<LinkRequest> links) {
            this.links = links == null ? List.of() : links;
        }
    }

    public static class LinkBatchResult {
        private String status;
        private int linkedLineCount;
        private String skuParent;

        public static LinkBatchResult linked(int linkedLineCount, String skuParent) {
            LinkBatchResult result = new LinkBatchResult();
            result.setStatus("linked");
            result.setLinkedLineCount(linkedLineCount);
            result.setSkuParent(skuParent);
            return result;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getLinkedLineCount() {
            return linkedLineCount;
        }

        public void setLinkedLineCount(int linkedLineCount) {
            this.linkedLineCount = linkedLineCount;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }
    }

    public static class CandidateView {
        private String storeCode;
        private String siteCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String offerCode;
        private String productTitle;
        private String productImageUrl;
        private String linkStatus;
        private Integer linkedAssignmentCount;

        public static CandidateView fromRow(Ali1688HistoricalOrderProductLinkCandidateRow row) {
            CandidateView view = new CandidateView();
            view.setStoreCode(row.getStoreCode());
            view.setSiteCode(row.getSiteCode());
            view.setSkuParent(row.getSkuParent());
            view.setPartnerSku(row.getPartnerSku());
            view.setPskuCode(row.getPskuCode());
            view.setOfferCode(row.getOfferCode());
            view.setProductTitle(row.getProductTitle());
            view.setProductImageUrl(normalizeNoonImageUrl(row.getProductImageUrl()));
            view.setLinkStatus(row.getLinkStatus());
            view.setLinkedAssignmentCount(row.getLinkedAssignmentCount());
            return view;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getOfferCode() {
            return offerCode;
        }

        public void setOfferCode(String offerCode) {
            this.offerCode = offerCode;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getProductImageUrl() {
            return productImageUrl;
        }

        public void setProductImageUrl(String productImageUrl) {
            this.productImageUrl = productImageUrl;
        }

        public String getLinkStatus() {
            return linkStatus;
        }

        public void setLinkStatus(String linkStatus) {
            this.linkStatus = linkStatus;
        }

        public Integer getLinkedAssignmentCount() {
            return linkedAssignmentCount;
        }

        public void setLinkedAssignmentCount(Integer linkedAssignmentCount) {
            this.linkedAssignmentCount = linkedAssignmentCount;
        }
    }

    public static class LinkResult {
        private String status;
        private Long assignmentId;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private String productImageUrl;
        private String displayText;

        public static LinkResult linked(Ali1688HistoricalOrderProductLinkRow row) {
            LinkResult result = new LinkResult();
            result.setStatus("linked");
            result.setAssignmentId(row.getAssignmentId());
            result.setSkuParent(row.getSkuParent());
            result.setPartnerSku(row.getPartnerSku());
            result.setPskuCode(row.getPskuCode());
            result.setProductTitle(row.getProductTitle());
            result.setProductImageUrl(normalizeNoonImageUrl(row.getProductImageUrl()));
            result.setDisplayText("已关联: " + row.getSkuParent());
            return result;
        }

        public static LinkResult unlinked(Long assignmentId) {
            LinkResult result = new LinkResult();
            result.setStatus("unlinked");
            result.setAssignmentId(assignmentId);
            result.setDisplayText("未关联");
            return result;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getProductImageUrl() {
            return productImageUrl;
        }

        public void setProductImageUrl(String productImageUrl) {
            this.productImageUrl = productImageUrl;
        }

        public String getDisplayText() {
            return displayText;
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
        }
    }

    static String normalizeNoonImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.startsWith("https://f.nooncdn.com/pzsku/")
                || trimmed.startsWith("http://f.nooncdn.com/pzsku/")) {
            String protocol = trimmed.startsWith("https://") ? "https://" : "http://";
            String normalized = protocol + "f.nooncdn.com/p/" + trimmed.substring((protocol + "f.nooncdn.com/").length());
            return hasImageExtension(normalized) ? normalized : normalized + ".jpg";
        }
        return trimmed;
    }

    private static boolean hasImageExtension(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.contains(".jpg?")
                || lower.contains(".jpeg?")
                || lower.contains(".png?")
                || lower.contains(".webp?");
    }
}
