package com.nuono.next.procurement.aliorder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class Ali1688HistoricalOrderExcelParser {

    private static final int MAX_ROW_ERROR_SAMPLES = 20;
    private static final List<String> MONEY_FIELDS = Arrays.asList(
            "货品总价(元)",
            "运费(元)",
            "涨价或折扣(元)",
            "实付款(元)",
            "单价(元)"
    );
    private static final List<String> TIMESTAMP_FIELDS = Arrays.asList("订单创建时间", "订单付款时间");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm")
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d")
    );

    private final DataFormatter dataFormatter = new DataFormatter();

    public Ali1688HistoricalOrderExcelParseResult parse(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.EMPTY_WORKBOOK,
                    "请上传 1688 历史订单 Excel 文件。"
            );
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new Ali1688HistoricalOrderExcelImportException(
                        HttpStatus.BAD_REQUEST,
                        Ali1688HistoricalOrderExcelImportFailureCode.EMPTY_WORKBOOK,
                        "Excel 文件没有可读取的工作表。"
                );
            }
            return parseSheet(workbook.getSheetAt(0));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof Ali1688HistoricalOrderExcelImportException) {
                throw (Ali1688HistoricalOrderExcelImportException) ex;
            }
            if (ex instanceof ResponseStatusException) {
                throw (ResponseStatusException) ex;
            }
            throw new Ali1688HistoricalOrderExcelImportException(
                    HttpStatus.BAD_REQUEST,
                    Ali1688HistoricalOrderExcelImportFailureCode.DAMAGED_WORKBOOK,
                    "Excel 文件无法读取，请重新导出 1688 历史订单。"
            );
        }
    }

    private Ali1688HistoricalOrderExcelParseResult parseSheet(Sheet sheet) {
        Ali1688HistoricalOrderExcelParseResult result = new Ali1688HistoricalOrderExcelParseResult();
        List<String> expectedHeaders = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
        Row headerRow = sheet.getRow(0);
        result.setHeaderValidation(Ali1688HistoricalOrderExcelHeaderContract.validate(readHeaders(headerRow, expectedHeaders.size())));
        if (!result.getHeaderValidation().isValid()) {
            return result;
        }

        Ali1688HistoricalOrderExcelParseResult.Summary summary = new Ali1688HistoricalOrderExcelParseResult.Summary();
        Set<String> itemKeys = new HashSet<>();
        String currentOrderNo = null;
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
            Row sheetRow = sheet.getRow(rowIndex);
            if (sheetRow == null || isBlankRow(sheetRow, expectedHeaders.size())) {
                continue;
            }
            summary.setTotalDataRowCount(summary.getTotalDataRowCount() + 1);
            String orderNo = cell(sheetRow, expectedHeaders, "订单编号");
            boolean hasOrderNo = hasText(orderNo);
            if (hasOrderNo) {
                currentOrderNo = orderNo;
                summary.setOrderHeaderRowCount(summary.getOrderHeaderRowCount() + 1);
            }
            boolean hasProduct = hasProductFields(sheetRow, expectedHeaders);
            if (!hasProduct) {
                continue;
            }
            if (!hasOrderNo && !hasText(currentOrderNo)) {
                addRowError(
                        result,
                        rowIndex + 1,
                        "订单编号",
                        Ali1688HistoricalOrderExcelImportFailureCode.ORPHAN_CONTINUATION_ROW.getCode(),
                        "续行缺少可继承的订单编号。"
                );
                continue;
            }
            Ali1688HistoricalOrderExcelParseResult.Row parsedRow =
                    toParsedRow(sheetRow, expectedHeaders, rowIndex + 1, hasOrderNo ? orderNo : currentOrderNo, !hasOrderNo);
            summary.setProductLineCount(summary.getProductLineCount() + 1);
            if (hasText(parsedRow.getLogisticsCompany()) || hasText(parsedRow.getTrackingNo())) {
                summary.setLogisticsLineCount(summary.getLogisticsLineCount() + 1);
            }
            if (appendRowValidationErrors(result, sheetRow, expectedHeaders, rowIndex + 1)) {
                continue;
            }
            result.getRows().add(parsedRow);
            summary.setValidRowCount(summary.getValidRowCount() + 1);
            String itemKey = String.join("|",
                    nullToEmpty(parsedRow.getOrderNo()),
                    nullToEmpty(parsedRow.getOfferId()),
                    nullToEmpty(parsedRow.getSkuId()),
                    nullToEmpty(parsedRow.getProductCode()),
                    nullToEmpty(parsedRow.getModelText()),
                    nullToEmpty(parsedRow.getSingleProductCode())
            );
            if (!itemKeys.add(itemKey)) {
                summary.setDuplicateCandidateCount(summary.getDuplicateCandidateCount() + 1);
            }
        }
        if (summary.getProductLineCount() == 0) {
            addRowError(
                    result,
                    0,
                    "货品标题",
                    Ali1688HistoricalOrderExcelImportFailureCode.NO_IMPORTABLE_ROWS.getCode(),
                    "Excel 文件没有可预览的货品行。"
            );
        }
        result.setSummary(summary);
        return result;
    }

    private boolean appendRowValidationErrors(
            Ali1688HistoricalOrderExcelParseResult result,
            Row row,
            List<String> headers,
            int rowNumber
    ) {
        boolean hasError = false;
        for (String fieldName : MONEY_FIELDS) {
            String value = cell(row, headers, fieldName);
            if (hasText(value) && !isValidMoney(value)) {
                addRowError(
                        result,
                        rowNumber,
                        fieldName,
                        Ali1688HistoricalOrderExcelImportFailureCode.INVALID_MONEY.getCode(),
                        fieldName + "格式不正确。"
                );
                hasError = true;
            }
        }
        String quantity = cell(row, headers, "数量");
        if (hasText(quantity) && !isValidQuantity(quantity)) {
            addRowError(
                    result,
                    rowNumber,
                    "数量",
                    Ali1688HistoricalOrderExcelImportFailureCode.INVALID_QUANTITY.getCode(),
                    "数量格式不正确。"
            );
            hasError = true;
        }
        for (String fieldName : TIMESTAMP_FIELDS) {
            String value = cell(row, headers, fieldName);
            if (hasText(value) && !isValidTimestamp(value)) {
                addRowError(
                        result,
                        rowNumber,
                        fieldName,
                        Ali1688HistoricalOrderExcelImportFailureCode.INVALID_TIMESTAMP.getCode(),
                        fieldName + "格式不正确。"
                );
                hasError = true;
            }
        }
        return hasError;
    }

    private void addRowError(
            Ali1688HistoricalOrderExcelParseResult result,
            int rowNumber,
            String fieldName,
            String code,
            String message
    ) {
        if (result.getRowErrors().size() < MAX_ROW_ERROR_SAMPLES) {
            result.getRowErrors().add(Ali1688HistoricalOrderExcelParseResult.RowMessage.error(
                    rowNumber,
                    fieldName,
                    code,
                    message
            ));
            return;
        }
        boolean alreadyCapped = result.getRowWarnings().stream()
                .anyMatch(rowMessage -> "additional_errors_capped".equals(rowMessage.getCode()));
        if (!alreadyCapped) {
            result.getRowWarnings().add(Ali1688HistoricalOrderExcelParseResult.RowMessage.error(
                    0,
                    null,
                    "additional_errors_capped",
                    "行级错误较多，仅展示前 " + MAX_ROW_ERROR_SAMPLES + " 条。"
            ));
        }
    }

    private List<String> readHeaders(Row headerRow, int expectedCount) {
        if (headerRow == null) {
            return List.of();
        }
        java.util.ArrayList<String> headers = new java.util.ArrayList<>();
        for (int columnIndex = 0; columnIndex < expectedCount; columnIndex++) {
            headers.add(cellText(headerRow.getCell(columnIndex)));
        }
        return headers;
    }

    private boolean isBlankRow(Row row, int expectedCount) {
        for (int columnIndex = 0; columnIndex < expectedCount; columnIndex++) {
            if (hasText(cellText(row.getCell(columnIndex)))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasProductFields(Row row, List<String> headers) {
        return hasText(cell(row, headers, "货品标题"))
                || hasText(cell(row, headers, "Offer ID"))
                || hasText(cell(row, headers, "SKU ID"))
                || hasText(cell(row, headers, "货号"))
                || hasText(cell(row, headers, "型号"))
                || hasText(cell(row, headers, "单品货号"));
    }

    private Ali1688HistoricalOrderExcelParseResult.Row toParsedRow(
            Row row,
            List<String> headers,
            int rowNumber,
            String orderNo,
            boolean continuationRow
    ) {
        Ali1688HistoricalOrderExcelParseResult.Row parsed = new Ali1688HistoricalOrderExcelParseResult.Row();
        parsed.setRowNumber(rowNumber);
        parsed.setContinuationRow(continuationRow);
        parsed.setOrderNo(orderNo);
        parsed.setBuyerCompanyName(cell(row, headers, "买家公司名"));
        parsed.setBuyerMemberName(cell(row, headers, "买家会员名"));
        parsed.setSupplierName(cell(row, headers, "卖家公司名"));
        parsed.setSellerMemberName(cell(row, headers, "卖家会员名"));
        parsed.setGoodsTotalText(cell(row, headers, "货品总价(元)"));
        parsed.setFreightText(cell(row, headers, "运费(元)"));
        parsed.setAdjustmentText(cell(row, headers, "涨价或折扣(元)"));
        parsed.setPaidAmountText(cell(row, headers, "实付款(元)"));
        parsed.setOrderStatus(cell(row, headers, "订单状态"));
        parsed.setOrderTime(cell(row, headers, "订单创建时间"));
        parsed.setPaidAt(cell(row, headers, "订单付款时间"));
        parsed.setShipperName(cell(row, headers, "发货方"));
        parsed.setReceiverName(cell(row, headers, "收货人姓名"));
        parsed.setReceiverAddress(cell(row, headers, "收货地址"));
        parsed.setReceiverPostalCode(cell(row, headers, "邮编"));
        parsed.setReceiverTelephone(cell(row, headers, "联系电话"));
        parsed.setReceiverMobile(cell(row, headers, "联系手机"));
        parsed.setBuyerRemark(cell(row, headers, "买家留言"));
        parsed.setTitle(cell(row, headers, "货品标题"));
        parsed.setOfferId(cell(row, headers, "Offer ID"));
        parsed.setSkuId(cell(row, headers, "SKU ID"));
        parsed.setProductCode(cell(row, headers, "货号"));
        parsed.setModelText(cell(row, headers, "型号"));
        parsed.setSingleProductCode(cell(row, headers, "单品货号"));
        parsed.setQuantityText(cell(row, headers, "数量"));
        parsed.setUnit(cell(row, headers, "单位"));
        parsed.setUnitPriceText(cell(row, headers, "单价(元)"));
        parsed.setLogisticsCompany(cell(row, headers, "物流公司"));
        parsed.setTrackingNo(cell(row, headers, "运单号"));
        parsed.setSourceBatchNo(cell(row, headers, "下单批次号"));
        parsed.setDownstreamChannel(cell(row, headers, "下游渠道"));
        parsed.setDownstreamOrderNo(cell(row, headers, "下游订单号"));
        parsed.setInitiatorLoginName(cell(row, headers, "发起人登录名"));
        return parsed;
    }

    private String cell(Row row, List<String> headers, String header) {
        int columnIndex = headers.indexOf(header);
        if (columnIndex < 0) {
            return null;
        }
        return cellText(row.getCell(columnIndex));
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = dataFormatter.formatCellValue(cell);
        return hasText(value) ? value.trim() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isValidMoney(String value) {
        String normalized = value.replace(",", "").replace("¥", "").replace("￥", "").trim();
        try {
            new BigDecimal(normalized);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isValidQuantity(String value) {
        try {
            BigDecimal quantity = new BigDecimal(value.trim());
            return quantity.compareTo(BigDecimal.ZERO) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isValidTimestamp(String value) {
        String normalized = value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime.parse(normalized, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(normalized, formatter);
                return true;
            } catch (DateTimeParseException ignored) {
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
