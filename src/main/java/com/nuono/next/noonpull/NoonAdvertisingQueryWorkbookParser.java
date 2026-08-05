package com.nuono.next.noonpull;

import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingQueryReport;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.util.StringUtils;

/** Validates one complete Ad Manager query workbook and skips only invalid business rows. */
final class NoonAdvertisingQueryWorkbookParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "query", "sku", "views", "clicks", "orders", "assisted_orders", "atc",
            "spends", "revenue", "ctr", "roas", "cpc", "cps", "cvr"
    );

    private final NoonAdvertisingMetricParser metrics = new NoonAdvertisingMetricParser();

    AdvertisingQueryReport parse(
            byte[] content,
            AdvertisingCampaignRef campaign
    ) {
        if (!looksLikeXlsx(content)) {
            throw contract("ADS_QUERY_REPORT_NOT_XLSX");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() < 1) {
                throw contract("ADS_QUERY_REPORT_SHEET_MISSING");
            }
            Sheet sheet = workbook.getSheet("(Product) Queries");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> headers = headers(
                    sheet.getRow(sheet.getFirstRowNum()),
                    formatter
            );
            if (!headers.keySet().containsAll(REQUIRED_HEADERS)) {
                throw contract("ADS_QUERY_REPORT_COLUMNS_MISSING");
            }
            List<NoonAdvertisingQueryFact> facts = new ArrayList<>();
            int sourceCount = 0;
            int businessSkipped = 0;
            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (isBlankRow(row, formatter)) {
                    continue;
                }
                sourceCount = Math.incrementExact(sourceCount);
                String query = sheetText(row, formatter, headers, "query");
                if (!StringUtils.hasText(query)) {
                    businessSkipped = Math.incrementExact(businessSkipped);
                    continue;
                }
                try {
                    facts.add(queryFact(row, formatter, headers, campaign, query));
                } catch (NoonAdvertisingContractException rowFailure) {
                    if (!isBusinessRowFailure(rowFailure)) {
                        throw rowFailure;
                    }
                    businessSkipped = Math.incrementExact(businessSkipped);
                }
            }
            return new AdvertisingQueryReport(facts, sourceCount, businessSkipped);
        } catch (NoonAdvertisingContractException contractFailure) {
            throw contractFailure;
        } catch (Exception parseFailure) {
            throw new NoonAdvertisingContractException(
                    "ADS_QUERY_REPORT_PARSE_FAILED",
                    parseFailure
            );
        }
    }

    private NoonAdvertisingQueryFact queryFact(
            Row row,
            DataFormatter formatter,
            Map<String, Integer> headers,
            AdvertisingCampaignRef campaign,
            String query
    ) {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode(metrics.boundedText(campaign.getCampaignCode(), 120));
        String reportName = sheetText(row, formatter, headers, "campaign_name");
        fact.setCampaignName(metrics.boundedText(StringUtils.hasText(reportName)
                ? reportName
                : campaign.getCampaignName(), 500));
        fact.setAdSkuCode(metrics.boundedText(
                sheetText(row, formatter, headers, "sku"), 160
        ));
        fact.setPartnerSku("");
        fact.setQueryText(metrics.boundedText(query, 1000));
        fact.setQueryKind(metrics.classifyQuery(query));
        fact.setViews(metrics.nonNegativeLong(sheetText(row, formatter, headers, "views")));
        fact.setClicks(metrics.nonNegativeLong(sheetText(row, formatter, headers, "clicks")));
        fact.setOrdersCount(metrics.nonNegativeLong(sheetText(row, formatter, headers, "orders")));
        fact.setAssistedOrders(metrics.nonNegativeLong(
                sheetText(row, formatter, headers, "assisted_orders")
        ));
        fact.setAtcCount(metrics.nonNegativeLong(sheetText(row, formatter, headers, "atc")));
        fact.setSpendAmount(metrics.decimal(sheetText(row, formatter, headers, "spends")));
        fact.setAdRevenue(metrics.decimal(sheetText(row, formatter, headers, "revenue")));
        fact.setCtrPercentage(metrics.percentFraction(sheetText(row, formatter, headers, "ctr")));
        fact.setRoas(metrics.decimal(sheetText(row, formatter, headers, "roas")));
        fact.setCpc(metrics.decimal(sheetText(row, formatter, headers, "cpc")));
        fact.setCps(metrics.decimal(sheetText(row, formatter, headers, "cps")));
        fact.setCvrPercentage(metrics.percentFraction(sheetText(row, formatter, headers, "cvr")));
        return fact;
    }

    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        if (row == null) {
            throw contract("ADS_QUERY_REPORT_HEADER_MISSING");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            String value = formatter.formatCellValue(row.getCell(index));
            if (StringUtils.hasText(value)) {
                result.putIfAbsent(metrics.normalizeHeader(value), index);
            }
        }
        return result;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            if (StringUtils.hasText(formatter.formatCellValue(row.getCell(index)))) {
                return false;
            }
        }
        return true;
    }

    private String sheetText(
            Row row,
            DataFormatter formatter,
            Map<String, Integer> headers,
            String key
    ) {
        Integer index = headers.get(metrics.normalizeHeader(key));
        return index == null || row == null
                ? ""
                : formatter.formatCellValue(row.getCell(index)).trim();
    }

    private boolean isBusinessRowFailure(NoonAdvertisingContractException failure) {
        String code = failure.getSanitizedCode();
        return "ADS_NEGATIVE_COUNT".equals(code)
                || "ADS_COUNT_INVALID".equals(code)
                || "ADS_NUMBER_INVALID".equals(code)
                || "ADS_NUMBER_OUT_OF_RANGE".equals(code)
                || "ADS_FIELD_TOO_LONG".equals(code)
                || "ADS_FIELD_INVALID".equals(code);
    }

    private boolean looksLikeXlsx(byte[] content) {
        return content != null && content.length >= 2 && content[0] == 'P' && content[1] == 'K';
    }

    private NoonAdvertisingContractException contract(String code) {
        return new NoonAdvertisingContractException(code);
    }
}
