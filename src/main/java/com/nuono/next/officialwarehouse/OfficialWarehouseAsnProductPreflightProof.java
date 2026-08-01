package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAsnProductPreflightProof {
    private final OfficialWarehouseAsnPreflightScope scope;
    private final List<FrozenLine> lines;
    private final int totalQuantity;

    OfficialWarehouseAsnProductPreflightProof(
            OfficialWarehouseAsnPreflightScope scope,
            List<FrozenLine> lines
    ) {
        this.scope = scope;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.totalQuantity = lines.stream().mapToInt(FrozenLine::quantity).sum();
    }

    void assertAuthorizes(NoonSalesReportBinding binding, NoonCallContext context) {
        scope.assertMatches(binding, context);
    }

    List<FrozenLine> lines() {
        return lines;
    }

    int totalQuantity() {
        return totalQuantity;
    }

    List<AsnLineInsertRecord> requestLineRows() {
        List<AsnLineInsertRecord> result = new ArrayList<>();
        for (FrozenLine line : lines) {
            result.add(line.requestLineRow());
        }
        return result;
    }

    static final class FrozenLine {
        private final String partnerSku;
        private final String pskuCode;
        private final String noonSku;
        private final int quantity;
        private final BigDecimal cubicFeet;
        private final String storageTypeCode;
        private final List<String> sourceBarcodes;
        private final List<String> pbarcodes;

        FrozenLine(
                AsnLineInsertRecord line,
                List<String> sourceBarcodes,
                List<String> pbarcodes
        ) {
            this.partnerSku = clean(line.partnerSku);
            this.pskuCode = clean(line.pskuCode);
            this.noonSku = clean(line.noonSku);
            this.quantity = line.quantity;
            this.cubicFeet = line.cubicFeet;
            this.storageTypeCode = clean(line.storageTypeCode);
            this.sourceBarcodes = List.copyOf(sourceBarcodes);
            this.pbarcodes = List.copyOf(pbarcodes);
        }

        String partnerSku() { return partnerSku; }
        String pskuCode() { return pskuCode; }
        String noonSku() { return noonSku; }
        int quantity() { return quantity; }
        BigDecimal cubicFeet() { return cubicFeet; }
        String storageTypeCode() { return storageTypeCode; }
        List<String> sourceBarcodes() { return sourceBarcodes; }
        List<String> pbarcodes() { return pbarcodes; }

        private AsnLineInsertRecord requestLineRow() {
            AsnLineInsertRecord row = new AsnLineInsertRecord();
            row.partnerSku = partnerSku;
            row.pskuCode = pskuCode;
            row.noonSku = noonSku;
            row.quantity = quantity;
            row.cubicFeet = cubicFeet;
            row.storageTypeCode = storageTypeCode;
            row.sourceBarcodes.addAll(sourceBarcodes);
            return row;
        }

        private static String clean(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }
}
