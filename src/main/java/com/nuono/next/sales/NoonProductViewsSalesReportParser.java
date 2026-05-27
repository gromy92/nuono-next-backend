package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoonProductViewsSalesReportParser {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "Visit_Date",
            "Partner_SKU",
            "Mp_Code",
            "SKU_CONFIG",
            "SKU",
            "Family",
            "Product_Type",
            "Product_Subtype",
            "Brand",
            "Currency_Code",
            "Product_Title",
            "Country_Code",
            "Your_Visitors",
            "Total_Visitors",
            "Gross_Units",
            "Shipped_Units",
            "Cancelled_Units",
            "Revenue_Shipped",
            "Buy_Box_Visitor_Percentage",
            "Conversion_Visitors_Percentage",
            "ASP_shipped_Percentage"
    );

    public List<NoonProductViewsSalesReportRow> parse(String csv) {
        NoonProductViewsSalesReportParseResult result = parseWithQuality(csv, null);
        if (!result.getExceptions().isEmpty()) {
            throw new IllegalArgumentException(result.getExceptions().get(0).getMessage());
        }
        return result.getRows();
    }

    public NoonProductViewsSalesReportParseResult parseWithQuality(String csv, String sourceFilename) {
        List<List<String>> records = parseRecords(csv == null ? "" : csv);
        if (records.isEmpty()) {
            return new NoonProductViewsSalesReportParseResult(List.of(), List.of(), 0);
        }
        Map<String, Integer> headers = indexHeaders(records.get(0));
        validateHeaders(headers);
        List<NoonProductViewsSalesReportRow> rows = new ArrayList<>();
        List<SalesImportExceptionRecord> exceptions = new ArrayList<>();
        int totalRows = 0;
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(value -> value == null || value.trim().isEmpty())) {
                continue;
            }
            totalRows++;
            try {
                rows.add(toRow(headers, record));
            } catch (NoonSalesReportRowException exception) {
                exceptions.add(exception.toRecord(i + 1, sourceFilename, record));
            }
        }
        return new NoonProductViewsSalesReportParseResult(rows, exceptions, totalRows);
    }

    private void validateHeaders(Map<String, Integer> headers) {
        List<String> missing = new ArrayList<>();
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headers.containsKey(requiredHeader)) {
                missing.add(requiredHeader);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing Noon product views and sales report columns: " + String.join(", ", missing)
            );
        }
    }

    private NoonProductViewsSalesReportRow toRow(Map<String, Integer> headers, List<String> record) {
        Integer grossUnits = parseInt(value(headers, record, "Gross_Units"), "Gross_Units");
        Integer shippedUnits = parseInt(value(headers, record, "Shipped_Units"), "Shipped_Units");
        Integer cancelledUnits = parseInt(value(headers, record, "Cancelled_Units"), "Cancelled_Units");
        return new NoonProductViewsSalesReportRow(
                parseDate(value(headers, record, "Visit_Date"), "Visit_Date"),
                requireText(value(headers, record, "Partner_SKU"), "Partner_SKU"),
                value(headers, record, "Mp_Code"),
                value(headers, record, "SKU_CONFIG"),
                requireText(value(headers, record, "SKU"), "SKU"),
                value(headers, record, "Family"),
                value(headers, record, "Product_Type"),
                value(headers, record, "Product_Subtype"),
                value(headers, record, "Brand"),
                value(headers, record, "Currency_Code"),
                value(headers, record, "Product_Title"),
                value(headers, record, "Country_Code"),
                parseInt(value(headers, record, "Your_Visitors"), "Your_Visitors"),
                parseInt(value(headers, record, "Total_Visitors"), "Total_Visitors"),
                grossUnits,
                shippedUnits,
                cancelledUnits,
                parseDecimal(value(headers, record, "Revenue_Shipped"), "Revenue_Shipped"),
                parseDecimal(value(headers, record, "Buy_Box_Visitor_Percentage"), "Buy_Box_Visitor_Percentage"),
                parseDecimal(value(headers, record, "Conversion_Visitors_Percentage"), "Conversion_Visitors_Percentage"),
                parseDecimal(value(headers, record, "ASP_shipped_Percentage"), "ASP_shipped_Percentage")
        );
    }

    private Map<String, Integer> indexHeaders(List<String> headerRecord) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < headerRecord.size(); i++) {
            headers.put(headerRecord.get(i).trim(), i);
        }
        return headers;
    }

    private String value(Map<String, Integer> headers, List<String> record, String header) {
        Integer index = headers.get(header);
        if (index == null || index >= record.size()) {
            return "";
        }
        return record.get(index).trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new NoonSalesReportRowException(
                    "mapping_failed",
                    fieldName,
                    value,
                    "Missing required mapping value: " + fieldName,
                    "确认来源报表中该行的 " + fieldName + " 是否为空，并补齐映射后重导。"
            );
        }
        return value;
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new NoonSalesReportRowException(
                    "malformed_date",
                    fieldName,
                    value,
                    "Invalid date value for " + fieldName + ": " + value,
                    "确认日期格式为 yyyy-MM-dd 后重导。"
            );
        }
    }

    private Integer parseInt(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new NoonSalesReportRowException(
                    "malformed_number",
                    fieldName,
                    value,
                    "Invalid numeric value for " + fieldName + ": " + value,
                    "确认该字段是整数或留空后重导。"
            );
        }
    }

    private BigDecimal parseDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            String type = fieldName.contains("Percentage") ? "malformed_percentage" : "malformed_number";
            throw new NoonSalesReportRowException(
                    type,
                    fieldName,
                    value,
                    "Invalid decimal value for " + fieldName + ": " + value,
                    "确认该字段是数字、百分比数字或留空后重导。"
            );
        }
    }

    private List<List<String>> parseRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    boolean escapedQuote = i + 1 < csv.length() && csv.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        currentValue.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    currentValue.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                currentRecord.add(currentValue.toString());
                currentValue.setLength(0);
            } else if (ch == '\n') {
                currentRecord.add(currentValue.toString());
                records.add(currentRecord);
                currentRecord = new ArrayList<>();
                currentValue.setLength(0);
            } else if (ch != '\r') {
                currentValue.append(ch);
            }
        }

        currentRecord.add(currentValue.toString());
        if (!(currentRecord.size() == 1 && currentRecord.get(0).isBlank() && records.size() > 0)) {
            records.add(currentRecord);
        }
        return records;
    }

    private static class NoonSalesReportRowException extends RuntimeException {
        private final String exceptionType;
        private final String fieldName;
        private final String sourceValue;
        private final String resolutionHint;

        private NoonSalesReportRowException(
                String exceptionType,
                String fieldName,
                String sourceValue,
                String message,
                String resolutionHint
        ) {
            super(message);
            this.exceptionType = exceptionType;
            this.fieldName = fieldName;
            this.sourceValue = sourceValue;
            this.resolutionHint = resolutionHint;
        }

        private SalesImportExceptionRecord toRecord(int rowNumber, String sourceFilename, List<String> record) {
            return new SalesImportExceptionRecord(
                    null,
                    null,
                    sourceFilename,
                    null,
                    null,
                    null,
                    rowNumber,
                    exceptionType,
                    fieldName,
                    sourceValue,
                    String.join(",", record),
                    getMessage(),
                    resolutionHint
            );
        }
    }
}
