package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewIssueView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewLineView;
import java.util.List;
import org.springframework.util.StringUtils;

final class InTransitBarcodeIdentitySupport {

    private InTransitBarcodeIdentitySupport() {
    }

    static Match require(InTransitGoodsMapper mapper, Long ownerUserId, String sourceBarcode) {
        String barcode = clean(sourceBarcode);
        if (barcode == null) {
            throw new IllegalArgumentException("物流商品 barcode 不能为空。");
        }
        BarcodeProductIdentity identity = mapper.selectProductIdentityByBarcode(ownerUserId, barcode);
        String partnerSku = clean(identity == null ? null : identity.getPartnerSku());
        if (partnerSku == null) {
            throw new IllegalArgumentException("物流商品 barcode 未唯一匹配当前货主商品：" + barcode);
        }
        return new Match(barcode, partnerSku);
    }

    static void applyImportIdentity(
            InTransitGoodsMapper mapper,
            Long ownerUserId,
            ImportPreviewLineView line,
            int rowNumber,
            List<ImportPreviewIssueView> issues
    ) {
        if (line == null || !StringUtils.hasText(line.getSku())) {
            return;
        }
        String barcode = clean(line.getSku());
        String sourcePsku = clean(line.getPsku());
        BarcodeProductIdentity identity = mapper.selectProductIdentityByBarcode(ownerUserId, barcode);
        String partnerSku = clean(identity == null ? null : identity.getPartnerSku());
        if (partnerSku == null) {
            issues.add(new ImportPreviewIssueView(
                    "error", "barcode_unmatched",
                    "物流商品 barcode 未唯一匹配当前货主商品：" + barcode,
                    rowNumber, "sku"));
            return;
        }
        line.setPsku(partnerSku);
        if (sourcePsku != null && !sourcePsku.equals(partnerSku)) {
            issues.add(new ImportPreviewIssueView(
                    "warning", "source_psku_ignored",
                    "来源 PSKU 已忽略，系统按 barcode 匹配为 " + partnerSku + "。",
                    rowNumber, "psku"));
        }
    }

    static boolean sameBarcode(String left, String right) {
        String cleanedLeft = clean(left);
        return cleanedLeft != null && cleanedLeft.equals(clean(right));
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static final class Match {
        private final String barcode;
        private final String partnerSku;

        private Match(String barcode, String partnerSku) {
            this.barcode = barcode;
            this.partnerSku = partnerSku;
        }

        String barcode() {
            return barcode;
        }

        String partnerSku() {
            return partnerSku;
        }
    }
}
