package com.nuono.next.procurement.aliorder;

import java.util.ArrayList;
import java.util.List;

public class Ali1688HistoricalOrderExcelParseResult {

    private HeaderValidation headerValidation = new HeaderValidation();
    private Summary summary = new Summary();
    private List<Row> rows = new ArrayList<>();
    private List<RowMessage> rowErrors = new ArrayList<>();
    private List<RowMessage> rowWarnings = new ArrayList<>();

    public HeaderValidation getHeaderValidation() {
        return headerValidation;
    }

    public void setHeaderValidation(HeaderValidation headerValidation) {
        this.headerValidation = headerValidation == null ? new HeaderValidation() : headerValidation;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary == null ? new Summary() : summary;
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? List.of() : rows;
    }

    public List<RowMessage> getRowErrors() {
        return rowErrors;
    }

    public void setRowErrors(List<RowMessage> rowErrors) {
        this.rowErrors = rowErrors == null ? List.of() : rowErrors;
    }

    public List<RowMessage> getRowWarnings() {
        return rowWarnings;
    }

    public void setRowWarnings(List<RowMessage> rowWarnings) {
        this.rowWarnings = rowWarnings == null ? List.of() : rowWarnings;
    }

    public static class HeaderValidation {
        private boolean valid;
        private int expectedHeaderCount;
        private int actualHeaderCount;
        private String message;
        private List<String> missingHeaders = new ArrayList<>();
        private List<HeaderMismatch> mismatchedHeaders = new ArrayList<>();

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public int getExpectedHeaderCount() {
            return expectedHeaderCount;
        }

        public void setExpectedHeaderCount(int expectedHeaderCount) {
            this.expectedHeaderCount = expectedHeaderCount;
        }

        public int getActualHeaderCount() {
            return actualHeaderCount;
        }

        public void setActualHeaderCount(int actualHeaderCount) {
            this.actualHeaderCount = actualHeaderCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getMissingHeaders() {
            return missingHeaders;
        }

        public void setMissingHeaders(List<String> missingHeaders) {
            this.missingHeaders = missingHeaders == null ? List.of() : missingHeaders;
        }

        public List<HeaderMismatch> getMismatchedHeaders() {
            return mismatchedHeaders;
        }

        public void setMismatchedHeaders(List<HeaderMismatch> mismatchedHeaders) {
            this.mismatchedHeaders = mismatchedHeaders == null ? List.of() : mismatchedHeaders;
        }
    }

    public static class HeaderMismatch {
        private int columnIndex;
        private String expected;
        private String actual;

        public HeaderMismatch() {
        }

        public HeaderMismatch(int columnIndex, String expected, String actual) {
            this.columnIndex = columnIndex;
            this.expected = expected;
            this.actual = actual;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        public void setColumnIndex(int columnIndex) {
            this.columnIndex = columnIndex;
        }

        public String getExpected() {
            return expected;
        }

        public void setExpected(String expected) {
            this.expected = expected;
        }

        public String getActual() {
            return actual;
        }

        public void setActual(String actual) {
            this.actual = actual;
        }
    }

    public static class Summary {
        private int totalDataRowCount;
        private int orderHeaderRowCount;
        private int productLineCount;
        private int logisticsLineCount;
        private int validRowCount;
        private int duplicateCandidateCount;

        public int getTotalDataRowCount() {
            return totalDataRowCount;
        }

        public void setTotalDataRowCount(int totalDataRowCount) {
            this.totalDataRowCount = totalDataRowCount;
        }

        public int getOrderHeaderRowCount() {
            return orderHeaderRowCount;
        }

        public void setOrderHeaderRowCount(int orderHeaderRowCount) {
            this.orderHeaderRowCount = orderHeaderRowCount;
        }

        public int getProductLineCount() {
            return productLineCount;
        }

        public void setProductLineCount(int productLineCount) {
            this.productLineCount = productLineCount;
        }

        public int getLogisticsLineCount() {
            return logisticsLineCount;
        }

        public void setLogisticsLineCount(int logisticsLineCount) {
            this.logisticsLineCount = logisticsLineCount;
        }

        public int getValidRowCount() {
            return validRowCount;
        }

        public void setValidRowCount(int validRowCount) {
            this.validRowCount = validRowCount;
        }

        public int getDuplicateCandidateCount() {
            return duplicateCandidateCount;
        }

        public void setDuplicateCandidateCount(int duplicateCandidateCount) {
            this.duplicateCandidateCount = duplicateCandidateCount;
        }
    }

    public static class Row {
        private int rowNumber;
        private boolean continuationRow;
        private String orderNo;
        private String buyerCompanyName;
        private String buyerMemberName;
        private String supplierName;
        private String sellerMemberName;
        private String goodsTotalText;
        private String freightText;
        private String adjustmentText;
        private String paidAmountText;
        private String orderStatus;
        private String orderTime;
        private String paidAt;
        private String shipperName;
        private String receiverName;
        private String receiverPostalCode;
        private String receiverTelephone;
        private String receiverMobile;
        private String receiverAddress;
        private String buyerRemark;
        private String title;
        private String offerId;
        private String skuId;
        private String productCode;
        private String modelText;
        private String singleProductCode;
        private String quantityText;
        private String unit;
        private String unitPriceText;
        private String logisticsCompany;
        private String trackingNo;
        private String sourceBatchNo;
        private String downstreamChannel;
        private String downstreamOrderNo;
        private String initiatorLoginName;

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public boolean isContinuationRow() {
            return continuationRow;
        }

        public void setContinuationRow(boolean continuationRow) {
            this.continuationRow = continuationRow;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getBuyerCompanyName() {
            return buyerCompanyName;
        }

        public void setBuyerCompanyName(String buyerCompanyName) {
            this.buyerCompanyName = buyerCompanyName;
        }

        public String getBuyerMemberName() {
            return buyerMemberName;
        }

        public void setBuyerMemberName(String buyerMemberName) {
            this.buyerMemberName = buyerMemberName;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getSellerMemberName() {
            return sellerMemberName;
        }

        public void setSellerMemberName(String sellerMemberName) {
            this.sellerMemberName = sellerMemberName;
        }

        public String getGoodsTotalText() {
            return goodsTotalText;
        }

        public void setGoodsTotalText(String goodsTotalText) {
            this.goodsTotalText = goodsTotalText;
        }

        public String getFreightText() {
            return freightText;
        }

        public void setFreightText(String freightText) {
            this.freightText = freightText;
        }

        public String getAdjustmentText() {
            return adjustmentText;
        }

        public void setAdjustmentText(String adjustmentText) {
            this.adjustmentText = adjustmentText;
        }

        public String getPaidAmountText() {
            return paidAmountText;
        }

        public void setPaidAmountText(String paidAmountText) {
            this.paidAmountText = paidAmountText;
        }

        public String getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getPaidAt() {
            return paidAt;
        }

        public void setPaidAt(String paidAt) {
            this.paidAt = paidAt;
        }

        public String getShipperName() {
            return shipperName;
        }

        public void setShipperName(String shipperName) {
            this.shipperName = shipperName;
        }

        public String getReceiverName() {
            return receiverName;
        }

        public void setReceiverName(String receiverName) {
            this.receiverName = receiverName;
        }

        public String getReceiverPostalCode() {
            return receiverPostalCode;
        }

        public void setReceiverPostalCode(String receiverPostalCode) {
            this.receiverPostalCode = receiverPostalCode;
        }

        public String getReceiverTelephone() {
            return receiverTelephone;
        }

        public void setReceiverTelephone(String receiverTelephone) {
            this.receiverTelephone = receiverTelephone;
        }

        public String getReceiverMobile() {
            return receiverMobile;
        }

        public void setReceiverMobile(String receiverMobile) {
            this.receiverMobile = receiverMobile;
        }

        public String getReceiverAddress() {
            return receiverAddress;
        }

        public void setReceiverAddress(String receiverAddress) {
            this.receiverAddress = receiverAddress;
        }

        public String getBuyerRemark() {
            return buyerRemark;
        }

        public void setBuyerRemark(String buyerRemark) {
            this.buyerRemark = buyerRemark;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getSkuId() {
            return skuId;
        }

        public void setSkuId(String skuId) {
            this.skuId = skuId;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getModelText() {
            return modelText;
        }

        public void setModelText(String modelText) {
            this.modelText = modelText;
        }

        public String getSingleProductCode() {
            return singleProductCode;
        }

        public void setSingleProductCode(String singleProductCode) {
            this.singleProductCode = singleProductCode;
        }

        public String getQuantityText() {
            return quantityText;
        }

        public void setQuantityText(String quantityText) {
            this.quantityText = quantityText;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getUnitPriceText() {
            return unitPriceText;
        }

        public void setUnitPriceText(String unitPriceText) {
            this.unitPriceText = unitPriceText;
        }

        public String getLogisticsCompany() {
            return logisticsCompany;
        }

        public void setLogisticsCompany(String logisticsCompany) {
            this.logisticsCompany = logisticsCompany;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getSourceBatchNo() {
            return sourceBatchNo;
        }

        public void setSourceBatchNo(String sourceBatchNo) {
            this.sourceBatchNo = sourceBatchNo;
        }

        public String getDownstreamChannel() {
            return downstreamChannel;
        }

        public void setDownstreamChannel(String downstreamChannel) {
            this.downstreamChannel = downstreamChannel;
        }

        public String getDownstreamOrderNo() {
            return downstreamOrderNo;
        }

        public void setDownstreamOrderNo(String downstreamOrderNo) {
            this.downstreamOrderNo = downstreamOrderNo;
        }

        public String getInitiatorLoginName() {
            return initiatorLoginName;
        }

        public void setInitiatorLoginName(String initiatorLoginName) {
            this.initiatorLoginName = initiatorLoginName;
        }
    }

    public static class RowMessage {
        private int rowNumber;
        private String fieldName;
        private String code;
        private String message;

        public static RowMessage error(int rowNumber, String fieldName, String code, String message) {
            RowMessage rowMessage = new RowMessage();
            rowMessage.setRowNumber(rowNumber);
            rowMessage.setFieldName(fieldName);
            rowMessage.setCode(code);
            rowMessage.setMessage(message);
            return rowMessage;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
