package com.nuono.next.procurement.aliorder;

import java.util.ArrayList;
import java.util.List;

public class Ali1688HistoricalOrderAssignmentView {

    public static class AssignRequest {
        private String targetType;
        private String targetStoreCode;
        private String targetSiteCode;
        private List<AssignLineRequest> lines = new ArrayList<>();

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public String getTargetStoreCode() {
            return targetStoreCode;
        }

        public void setTargetStoreCode(String targetStoreCode) {
            this.targetStoreCode = targetStoreCode;
        }

        public String getTargetSiteCode() {
            return targetSiteCode;
        }

        public void setTargetSiteCode(String targetSiteCode) {
            this.targetSiteCode = targetSiteCode;
        }

        public List<AssignLineRequest> getLines() {
            return lines;
        }

        public void setLines(List<AssignLineRequest> lines) {
            this.lines = lines == null ? List.of() : lines;
        }
    }

    public static class AssignBatchRequest {
        private List<AssignRequest> assignments = new ArrayList<>();

        public List<AssignRequest> getAssignments() {
            return assignments;
        }

        public void setAssignments(List<AssignRequest> assignments) {
            this.assignments = assignments == null ? List.of() : assignments;
        }
    }

    public static class AssignLineRequest {
        private Long itemId;
        private Integer quantity;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    public static class AssignResult {
        private String status;
        private int assignedLineCount;
        private int assignedQuantity;

        public static AssignResult assigned(int assignedLineCount, int assignedQuantity) {
            AssignResult result = new AssignResult();
            result.setStatus("assigned");
            result.setAssignedLineCount(assignedLineCount);
            result.setAssignedQuantity(assignedQuantity);
            return result;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getAssignedLineCount() {
            return assignedLineCount;
        }

        public void setAssignedLineCount(int assignedLineCount) {
            this.assignedLineCount = assignedLineCount;
        }

        public int getAssignedQuantity() {
            return assignedQuantity;
        }

        public void setAssignedQuantity(int assignedQuantity) {
            this.assignedQuantity = assignedQuantity;
        }
    }

    public static class AdjustRequest {
        private Integer quantity;

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    public static class RecordView {
        private Long assignmentId;
        private Long itemId;
        private String targetType;
        private String targetStoreCode;
        private String targetSiteCode;
        private Integer assignedQuantity;
        private String status;
        private Long createdBy;
        private Long updatedBy;
        private String createdAt;
        private String updatedAt;

        public static RecordView fromRow(Ali1688HistoricalOrderItemAssignmentRow row) {
            RecordView view = new RecordView();
            view.setAssignmentId(row.getId());
            view.setItemId(row.getItemId());
            view.setTargetType(row.getTargetType());
            view.setTargetStoreCode(row.getTargetStoreCode());
            view.setTargetSiteCode(row.getTargetSiteCode());
            view.setAssignedQuantity(row.getAssignedQuantity());
            view.setStatus(row.getStatus());
            view.setCreatedBy(row.getCreatedBy());
            view.setUpdatedBy(row.getUpdatedBy());
            view.setCreatedAt(row.getCreatedAt());
            view.setUpdatedAt(row.getUpdatedAt());
            return view;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public String getTargetStoreCode() {
            return targetStoreCode;
        }

        public void setTargetStoreCode(String targetStoreCode) {
            this.targetStoreCode = targetStoreCode;
        }

        public String getTargetSiteCode() {
            return targetSiteCode;
        }

        public void setTargetSiteCode(String targetSiteCode) {
            this.targetSiteCode = targetSiteCode;
        }

        public Integer getAssignedQuantity() {
            return assignedQuantity;
        }

        public void setAssignedQuantity(Integer assignedQuantity) {
            this.assignedQuantity = assignedQuantity;
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

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
