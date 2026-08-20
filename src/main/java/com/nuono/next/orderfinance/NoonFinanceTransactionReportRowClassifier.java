package com.nuono.next.orderfinance;

import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.hasRequiredColumns;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.headerIndex;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.isBlankRow;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.missingColumnsDiagnostic;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.nullable;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.rowHash;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.value;

import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportRowDecision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** DP-03 row contract shared by the unified Runtime and the legacy direct-write adapter. */
@Service
public final class NoonFinanceTransactionReportRowClassifier {
    public void requireHeader(String[] header) {
        Map<String, Integer> indexes = headerIndex(header == null ? new String[0] : header);
        if (!hasRequiredColumns(indexes)) {
            throw new IllegalArgumentException(missingColumnsDiagnostic(indexes));
        }
    }

    public List<NoonReportRowDecision<NoonFinanceTransactionFact>> classifyRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonFinanceTransactionFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlankRow(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stableIdentity(NoonFinanceTransactionFact fact) {
        return fact.getRowHash();
    }

    private NoonReportRowDecision<NoonFinanceTransactionFact> classifyRow(
            NoonReportDownloadedFile file,
            String[] row,
            Map<String, Integer> indexes
    ) {
        try {
            return toFact(file, row, indexes);
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private NoonReportRowDecision<NoonFinanceTransactionFact> toFact(
            NoonReportDownloadedFile file,
            String[] row,
            Map<String, Integer> indexes
    ) {
        NoonReportPullRequest request = file.getRequest();
        String contractCode = nullable(value(row, indexes, "Contract"));
        String contractTitle = nullable(value(row, indexes, "Contract Title"));
        String rawCurrency = value(row, indexes, "Currency");
        String currency = StringUtils.hasText(rawCurrency)
                ? rawCurrency.toUpperCase(Locale.ROOT)
                : "";
        String detectedSiteCode = detectSiteCode(contractCode, contractTitle, currency);
        String requestedSiteCode = normalizeSiteCode(request.getSiteCode());
        if (!"SA".equals(requestedSiteCode) && !"AE".equals(requestedSiteCode)) {
            return NoonReportRowDecision.containerContractError();
        }
        if (StringUtils.hasText(detectedSiteCode)
                && !detectedSiteCode.equals(requestedSiteCode)) {
            return NoonReportRowDecision.containerContractError();
        }
        LocalDate transactionDate = NoonFinanceReportValueParser.requiredBusinessDate(
                value(row, indexes, "Transaction Date"),
                "Transaction Date"
        );
        if (transactionDate != null && outsideRequestedWindow(transactionDate, request)) {
            return NoonReportRowDecision.containerContractError();
        }

        String referenceNr = value(row, indexes, "Reference Nr");
        String orderNr = value(row, indexes, "Order Nr");
        String transactionType = value(row, indexes, "Transaction Type");
        LocalDate orderDate = NoonFinanceReportValueParser.optionalDate(
                value(row, indexes, "Order Date"));
        BigDecimal netProceeds = amount(row, indexes, "Net Proceeds");
        BigDecimal referralFee = amount(row, indexes, "Referral Fee including VAT");
        BigDecimal fulfillmentFees = amount(
                row, indexes, "Fullfilment & Logistics Fees including VAT");
        BigDecimal shippingCredits = amount(
                row, indexes, "Shipping Credits including VAT");
        BigDecimal otherOrderFees = amount(
                row, indexes, "Other Order Fees including VAT");
        BigDecimal orderSubsidies = amount(
                row, indexes, "Order Subsidies including VAT");
        BigDecimal nonOrderFees = amount(
                row, indexes, "Non-Order Fees including VAT");
        BigDecimal nonOrderSubsidies = amount(
                row, indexes, "Non-Order Subsidies including VAT");
        BigDecimal others = amount(row, indexes, "Others including VAT");
        BigDecimal total = amount(row, indexes, "Total");
        if (!StringUtils.hasText(referenceNr)
                || !StringUtils.hasText(orderNr)
                || !StringUtils.hasText(transactionType)
                || !StringUtils.hasText(currency)
                || transactionDate == null
                || NoonFinanceReportValueParser.hasNullAmount(
                        netProceeds,
                        referralFee,
                        fulfillmentFees,
                        shippingCredits,
                        otherOrderFees,
                        orderSubsidies,
                        nonOrderFees,
                        nonOrderSubsidies,
                        others,
                        total
                )) {
            return NoonReportRowDecision.businessSkip();
        }
        return NoonReportRowDecision.accept(NoonFinanceFactColumnContract.requirePersistable(
                new NoonFinanceTransactionFact(
                        request.getOwnerUserId(),
                        request.getStoreCode(),
                        requestedSiteCode,
                        file.getSourceBatchId(),
                        file.getDigestSha256(),
                        rowHash(row),
                        contractCode,
                        contractTitle,
                        referenceNr,
                        orderNr,
                        value(row, indexes, "Item Nr"),
                        orderDate,
                        transactionDate,
                        nullable(value(row, indexes, "Title")),
                        nullable(value(row, indexes, "SKUs")),
                        nullable(value(row, indexes, "Partner SKUs")),
                        transactionType.toLowerCase(Locale.ROOT),
                        currency,
                        netProceeds,
                        referralFee,
                        fulfillmentFees,
                        shippingCredits,
                        otherOrderFees,
                        orderSubsidies,
                        nonOrderFees,
                        nonOrderSubsidies,
                        others,
                        total,
                        request.getDateFrom(),
                        request.getDateTo()
                )));
    }

    private String detectSiteCode(String contractCode, String contractTitle, String currency) {
        String normalizedCurrency = normalizeSiteCode(currency);
        if ("AED".equals(normalizedCurrency)) {
            return "AE";
        }
        if ("SAR".equals(normalizedCurrency)) {
            return "SA";
        }
        String contractText = (
                (contractCode == null ? "" : contractCode)
                        + " "
                        + (contractTitle == null ? "" : contractTitle)
        ).toUpperCase(Locale.ROOT);
        if (contractText.contains("NOON-SA")
                || contractText.contains("KSA")
                || contractText.endsWith("SA")) {
            return "SA";
        }
        if (contractText.contains("NOON AE")
                || contractText.contains("UAE")
                || contractText.endsWith("AE")) {
            return "AE";
        }
        return null;
    }

    private BigDecimal amount(String[] row, Map<String, Integer> indexes, String column) {
        return NoonFinanceReportValueParser.optionalDecimal(value(row, indexes, column));
    }

    private String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean outsideRequestedWindow(LocalDate date, NoonReportPullRequest request) {
        return request.getDateFrom() != null
                && request.getDateTo() != null
                && (date.isBefore(request.getDateFrom()) || date.isAfter(request.getDateTo()));
    }
}
