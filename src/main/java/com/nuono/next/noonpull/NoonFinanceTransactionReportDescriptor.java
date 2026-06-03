package com.nuono.next.noonpull;

import java.util.Arrays;
import java.util.List;

public final class NoonFinanceTransactionReportDescriptor {
    public static final String DEFAULT_REPORT_TYPE = "noon_financeweb_transactionviewreportonitemlevelwithcontractselection";
    public static final String SOURCE_SYSTEM = "noon_finance_transaction_report";

    private NoonFinanceTransactionReportDescriptor() {
    }

    public static List<String> requiredColumns() {
        return Arrays.asList(
                "Contract",
                "Contract Title",
                "Reference Nr",
                "Order Nr",
                "Item Nr",
                "Order Date",
                "Transaction Date",
                "Title",
                "SKUs",
                "Partner SKUs",
                "Transaction Type",
                "Currency",
                "Net Proceeds",
                "Referral Fee including VAT",
                "Fullfilment & Logistics Fees including VAT",
                "Shipping Credits including VAT",
                "Other Order Fees including VAT",
                "Order Subsidies including VAT",
                "Non-Order Fees including VAT",
                "Non-Order Subsidies including VAT",
                "Others including VAT",
                "Total"
        );
    }
}
