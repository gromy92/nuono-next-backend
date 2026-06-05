package com.nuono.next.procurement.aliorder;

import java.util.List;
import java.util.stream.Collectors;

public final class Ali1688HistoricalOrderExcelImportView {

    private Ali1688HistoricalOrderExcelImportView() {
    }

    public static class SourceCreateRequest {
        private String accountLabel;
        private String storeCode;
        private String siteCode;

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
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
    }

    public static class SourceView {
        private Long authorizationId;
        private String providerCode;
        private String accountLabel;
        private String storeCode;
        private String siteCode;
        private String status;

        public static SourceView excelUpload(
                Long authorizationId,
                String accountLabel,
                String storeCode,
                String siteCode
        ) {
            SourceView view = new SourceView();
            view.setAuthorizationId(authorizationId);
            view.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
            view.setAccountLabel(accountLabel);
            view.setStoreCode(storeCode);
            view.setSiteCode(siteCode);
            view.setStatus("authorized");
            return view;
        }

        public static SourceView fromAuthorization(
                Ali1688HistoricalOrderAuthorizationRow row,
                String storeCode,
                String siteCode
        ) {
            return excelUpload(
                    row == null ? null : row.getId(),
                    row == null ? null : row.getAccountLabel(),
                    storeCode,
                    siteCode
            );
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class PreviewRequest {
        private Long authorizationId;
        private String storeCode;
        private String siteCode;

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
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
    }

    public static class PreviewView {
        private Long batchId;
        private String status;
        private String fileName;
        private Long fileSize;
        private String fileHash;
        private SourceView source;
        private String storeCode;
        private String siteCode;
        private HeaderValidationView headerValidation;
        private SummaryView summary;
        private List<RowMessageView> rowErrors = List.of();
        private List<RowMessageView> rowWarnings = List.of();

        static PreviewView fromBatch(
                Ali1688HistoricalOrderExcelImportBatchRow batch,
                Ali1688HistoricalOrderAuthorizationRow source,
                Ali1688HistoricalOrderExcelParseResult parseResult
        ) {
            PreviewView view = new PreviewView();
            view.setBatchId(batch.getId());
            view.setStatus(batch.getStatus());
            view.setFileName(batch.getFileName());
            view.setFileSize(batch.getFileSize());
            view.setFileHash(batch.getFileHash());
            view.setStoreCode(batch.getStoreCode());
            view.setSiteCode(batch.getSiteCode());
            view.setSource(SourceView.excelUpload(source.getId(), source.getAccountLabel(), batch.getStoreCode(), batch.getSiteCode()));
            view.setHeaderValidation(HeaderValidationView.from(parseResult.getHeaderValidation()));
            view.setSummary(SummaryView.from(parseResult.getSummary()));
            view.setRowErrors(RowMessageView.from(parseResult.getRowErrors()));
            view.setRowWarnings(RowMessageView.from(parseResult.getRowWarnings()));
            return view;
        }

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public String getFileHash() {
            return fileHash;
        }

        public void setFileHash(String fileHash) {
            this.fileHash = fileHash;
        }

        public SourceView getSource() {
            return source;
        }

        public void setSource(SourceView source) {
            this.source = source;
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

        public HeaderValidationView getHeaderValidation() {
            return headerValidation;
        }

        public void setHeaderValidation(HeaderValidationView headerValidation) {
            this.headerValidation = headerValidation;
        }

        public SummaryView getSummary() {
            return summary;
        }

        public void setSummary(SummaryView summary) {
            this.summary = summary;
        }

        public List<RowMessageView> getRowErrors() {
            return rowErrors;
        }

        public void setRowErrors(List<RowMessageView> rowErrors) {
            this.rowErrors = rowErrors == null ? List.of() : rowErrors;
        }

        public List<RowMessageView> getRowWarnings() {
            return rowWarnings;
        }

        public void setRowWarnings(List<RowMessageView> rowWarnings) {
            this.rowWarnings = rowWarnings == null ? List.of() : rowWarnings;
        }
    }

    public static class HeaderValidationView {
        private boolean valid;
        private int expectedHeaderCount;
        private int actualHeaderCount;
        private String message;
        private List<String> missingHeaders = List.of();
        private List<HeaderMismatchView> mismatchedHeaders = List.of();

        static HeaderValidationView from(Ali1688HistoricalOrderExcelParseResult.HeaderValidation validation) {
            HeaderValidationView view = new HeaderValidationView();
            view.setValid(validation.isValid());
            view.setExpectedHeaderCount(validation.getExpectedHeaderCount());
            view.setActualHeaderCount(validation.getActualHeaderCount());
            view.setMessage(validation.getMessage());
            view.setMissingHeaders(validation.getMissingHeaders());
            view.setMismatchedHeaders(validation.getMismatchedHeaders().stream()
                    .map(HeaderMismatchView::from)
                    .collect(Collectors.toList()));
            return view;
        }

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

        public List<HeaderMismatchView> getMismatchedHeaders() {
            return mismatchedHeaders;
        }

        public void setMismatchedHeaders(List<HeaderMismatchView> mismatchedHeaders) {
            this.mismatchedHeaders = mismatchedHeaders == null ? List.of() : mismatchedHeaders;
        }
    }

    public static class HeaderMismatchView {
        private int columnIndex;
        private String expected;
        private String actual;

        static HeaderMismatchView from(Ali1688HistoricalOrderExcelParseResult.HeaderMismatch mismatch) {
            HeaderMismatchView view = new HeaderMismatchView();
            view.setColumnIndex(mismatch.getColumnIndex());
            view.setExpected(mismatch.getExpected());
            view.setActual(mismatch.getActual());
            return view;
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

    public static class SummaryView {
        private int totalDataRowCount;
        private int orderHeaderRowCount;
        private int productLineCount;
        private int logisticsLineCount;
        private int validRowCount;
        private int duplicateCandidateCount;

        static SummaryView from(Ali1688HistoricalOrderExcelParseResult.Summary summary) {
            SummaryView view = new SummaryView();
            view.setTotalDataRowCount(summary.getTotalDataRowCount());
            view.setOrderHeaderRowCount(summary.getOrderHeaderRowCount());
            view.setProductLineCount(summary.getProductLineCount());
            view.setLogisticsLineCount(summary.getLogisticsLineCount());
            view.setValidRowCount(summary.getValidRowCount());
            view.setDuplicateCandidateCount(summary.getDuplicateCandidateCount());
            return view;
        }

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

    public static class RowMessageView {
        private int rowNumber;
        private String fieldName;
        private String code;
        private String message;

        static List<RowMessageView> from(List<Ali1688HistoricalOrderExcelParseResult.RowMessage> messages) {
            return messages.stream().map(RowMessageView::from).collect(Collectors.toList());
        }

        static RowMessageView from(Ali1688HistoricalOrderExcelParseResult.RowMessage message) {
            RowMessageView view = new RowMessageView();
            view.setRowNumber(message.getRowNumber());
            view.setFieldName(message.getFieldName());
            view.setCode(message.getCode());
            view.setMessage(message.getMessage());
            return view;
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

    public static class CommitRequest {
        private Long batchId;
        private String storeCode;
        private String siteCode;

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
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
    }

    public static class BatchView {
        private Long batchId;
        private Long authorizationId;
        private String providerCode;
        private String accountLabel;
        private String storeCode;
        private String siteCode;
        private String fileName;
        private Long fileSize;
        private String fileHash;
        private String status;
        private String headerVersion;
        private Integer orderHeaderRowCount;
        private Integer productLineCount;
        private Integer logisticsLineCount;
        private Integer validRowCount;
        private Integer duplicateCandidateCount;
        private Integer errorCount;
        private Integer warningCount;
        private String failureCode;
        private String failureMessage;
        private Long createdBy;
        private String createdAt;
        private String updatedAt;

        static BatchView from(Ali1688HistoricalOrderExcelImportBatchRow row) {
            BatchView view = new BatchView();
            copyBatch(row, view);
            return view;
        }

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
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

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public String getFileHash() {
            return fileHash;
        }

        public void setFileHash(String fileHash) {
            this.fileHash = fileHash;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getHeaderVersion() {
            return headerVersion;
        }

        public void setHeaderVersion(String headerVersion) {
            this.headerVersion = headerVersion;
        }

        public Integer getOrderHeaderRowCount() {
            return orderHeaderRowCount;
        }

        public void setOrderHeaderRowCount(Integer orderHeaderRowCount) {
            this.orderHeaderRowCount = orderHeaderRowCount;
        }

        public Integer getProductLineCount() {
            return productLineCount;
        }

        public void setProductLineCount(Integer productLineCount) {
            this.productLineCount = productLineCount;
        }

        public Integer getLogisticsLineCount() {
            return logisticsLineCount;
        }

        public void setLogisticsLineCount(Integer logisticsLineCount) {
            this.logisticsLineCount = logisticsLineCount;
        }

        public Integer getValidRowCount() {
            return validRowCount;
        }

        public void setValidRowCount(Integer validRowCount) {
            this.validRowCount = validRowCount;
        }

        public Integer getDuplicateCandidateCount() {
            return duplicateCandidateCount;
        }

        public void setDuplicateCandidateCount(Integer duplicateCandidateCount) {
            this.duplicateCandidateCount = duplicateCandidateCount;
        }

        public Integer getErrorCount() {
            return errorCount;
        }

        public void setErrorCount(Integer errorCount) {
            this.errorCount = errorCount;
        }

        public Integer getWarningCount() {
            return warningCount;
        }

        public void setWarningCount(Integer warningCount) {
            this.warningCount = warningCount;
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

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class BatchDetailView extends BatchView {
        private String errorSummaryJson;

        static BatchDetailView from(Ali1688HistoricalOrderExcelImportBatchRow row) {
            BatchDetailView view = new BatchDetailView();
            copyBatch(row, view);
            view.setErrorSummaryJson(row.getErrorSummaryJson());
            return view;
        }

        public String getErrorSummaryJson() {
            return errorSummaryJson;
        }

        public void setErrorSummaryJson(String errorSummaryJson) {
            this.errorSummaryJson = errorSummaryJson;
        }
    }

    private static void copyBatch(Ali1688HistoricalOrderExcelImportBatchRow row, BatchView view) {
        view.setBatchId(row.getId());
        view.setAuthorizationId(row.getAuthorizationId());
        view.setProviderCode(row.getProviderCode());
        view.setAccountLabel(row.getAccountLabel());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setFileName(row.getFileName());
        view.setFileSize(row.getFileSize());
        view.setFileHash(row.getFileHash());
        view.setStatus(row.getStatus());
        view.setHeaderVersion(row.getHeaderVersion());
        view.setOrderHeaderRowCount(row.getOrderHeaderRowCount());
        view.setProductLineCount(row.getProductLineCount());
        view.setLogisticsLineCount(row.getLogisticsLineCount());
        view.setValidRowCount(row.getValidRowCount());
        view.setDuplicateCandidateCount(row.getDuplicateCandidateCount());
        view.setErrorCount(row.getErrorCount());
        view.setWarningCount(row.getWarningCount());
        view.setFailureCode(row.getFailureCode());
        view.setFailureMessage(row.getFailureMessage());
        view.setCreatedBy(row.getCreatedBy());
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
    }

    public static class CommitView {
        private Long batchId;
        private String status;
        private CommitCountsView counts;

        public static CommitView committed(Long batchId, CommitCountsView counts) {
            CommitView view = new CommitView();
            view.setBatchId(batchId);
            view.setStatus("committed");
            view.setCounts(counts);
            return view;
        }

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public CommitCountsView getCounts() {
            return counts;
        }

        public void setCounts(CommitCountsView counts) {
            this.counts = counts;
        }
    }

    public static class CommitCountsView {
        private int insertedOrderCount;
        private int updatedOrderCount;
        private int skippedOrderCount;
        private int insertedItemCount;
        private int updatedItemCount;
        private int skippedItemCount;
        private int insertedLogisticsCount;
        private int updatedLogisticsCount;
        private int skippedLogisticsCount;

        public int getInsertedOrderCount() {
            return insertedOrderCount;
        }

        public void setInsertedOrderCount(int insertedOrderCount) {
            this.insertedOrderCount = insertedOrderCount;
        }

        public int getUpdatedOrderCount() {
            return updatedOrderCount;
        }

        public void setUpdatedOrderCount(int updatedOrderCount) {
            this.updatedOrderCount = updatedOrderCount;
        }

        public int getSkippedOrderCount() {
            return skippedOrderCount;
        }

        public void setSkippedOrderCount(int skippedOrderCount) {
            this.skippedOrderCount = skippedOrderCount;
        }

        public int getInsertedItemCount() {
            return insertedItemCount;
        }

        public void setInsertedItemCount(int insertedItemCount) {
            this.insertedItemCount = insertedItemCount;
        }

        public int getUpdatedItemCount() {
            return updatedItemCount;
        }

        public void setUpdatedItemCount(int updatedItemCount) {
            this.updatedItemCount = updatedItemCount;
        }

        public int getSkippedItemCount() {
            return skippedItemCount;
        }

        public void setSkippedItemCount(int skippedItemCount) {
            this.skippedItemCount = skippedItemCount;
        }

        public int getInsertedLogisticsCount() {
            return insertedLogisticsCount;
        }

        public void setInsertedLogisticsCount(int insertedLogisticsCount) {
            this.insertedLogisticsCount = insertedLogisticsCount;
        }

        public int getUpdatedLogisticsCount() {
            return updatedLogisticsCount;
        }

        public void setUpdatedLogisticsCount(int updatedLogisticsCount) {
            this.updatedLogisticsCount = updatedLogisticsCount;
        }

        public int getSkippedLogisticsCount() {
            return skippedLogisticsCount;
        }

        public void setSkippedLogisticsCount(int skippedLogisticsCount) {
            this.skippedLogisticsCount = skippedLogisticsCount;
        }
    }
}
