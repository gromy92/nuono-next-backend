package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnProductPreflightProof.FrozenLine;
import com.nuono.next.product.NoonProductListFieldSupport;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.web.ApiProblemException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/** Freezes the exact Noon product identities that authorize the first ASN write. */
final class OfficialWarehouseAsnProductPreflightModule {

    private final OfficialWarehouseNoonProductIdentityAdapter productIdentityAdapter;

    OfficialWarehouseAsnProductPreflightModule(OfficialWarehouseNoonInboundClient noonInboundClient) {
        this.productIdentityAdapter = new OfficialWarehouseNoonProductIdentityAdapter(noonInboundClient);
    }

    OfficialWarehouseAsnProductPreflightProof freeze(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            List<AsnLineInsertRecord> lineRows
    ) {
        OfficialWarehouseAsnPreflightScope scope =
                OfficialWarehouseAsnPreflightScope.capture(binding, context);
        if (lineRows == null || lineRows.isEmpty()) {
            throw problem(List.of(issue(null, null, "EMPTY_SELECTION", "请选择至少一个商品。")));
        }
        Map<String, List<JsonNode>> offersByPartnerSku = new LinkedHashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        List<FrozenLine> frozenLines = new ArrayList<>();
        for (AsnLineInsertRecord line : lineRows) {
            String partnerSku = text(line == null ? null : line.partnerSku);
            String pskuCode = text(line == null ? null : line.pskuCode);
            Integer quantity = line == null ? null : line.quantity;
            if (partnerSku == null || pskuCode == null || quantity == null || quantity <= 0) {
                issues.add(issue(
                        partnerSku,
                        pskuCode,
                        "LOCAL_IDENTITY_INCOMPLETE",
                        "商品缺少稳定 partnerSku、Noon PSKU 或有效数量。"
                ));
                continue;
            }
            List<JsonNode> offers = offersByPartnerSku.computeIfAbsent(
                    normalize(partnerSku),
                    ignored -> productIdentityAdapter.search(
                            session, binding, context, partnerSku
                    )
            );
            FrozenLine frozen = freezeLine(line, offers, issues);
            if (frozen != null) {
                frozenLines.add(frozen);
            }
        }
        if (!issues.isEmpty()) {
            throw problem(issues);
        }
        return new OfficialWarehouseAsnProductPreflightProof(scope, frozenLines);
    }

    private FrozenLine freezeLine(
            AsnLineInsertRecord line,
            List<JsonNode> offers,
            List<Map<String, Object>> issues
    ) {
        String partnerSku = text(line.partnerSku);
        String pskuCode = text(line.pskuCode);
        List<JsonNode> partnerMatches = new ArrayList<>();
        List<JsonNode> exactMatches = new ArrayList<>();
        for (JsonNode offer : offers == null ? List.<JsonNode>of() : offers) {
            if (!same(partnerSku, jsonText(offer, "partner_sku", "partnerSku"))) {
                continue;
            }
            partnerMatches.add(offer);
            if (same(pskuCode, NoonProductListFieldSupport.pskuCode(offer))) {
                exactMatches.add(offer);
            }
        }
        if (partnerMatches.isEmpty()) {
            issues.add(issue(partnerSku, pskuCode, "REMOTE_PRODUCT_MISSING", "Noon 当前店铺找不到该 partnerSku。"));
            return null;
        }
        if (exactMatches.isEmpty()) {
            issues.add(issue(partnerSku, pskuCode, "PSKU_MISMATCH", "Noon 当前 PSKU 与本地冻结值不一致。"));
            return null;
        }
        if (exactMatches.size() != 1) {
            issues.add(issue(partnerSku, pskuCode, "REMOTE_IDENTITY_AMBIGUOUS", "Noon 返回多个相同商品身份，不能安全创建 ASN。"));
            return null;
        }
        List<String> pbarcodes = partnerBarcodes(exactMatches.get(0));
        if (pbarcodes.isEmpty()) {
            issues.add(issue(partnerSku, pskuCode, "PBARCODE_UNMAPPED", "Noon 未给该 PSKU 建立有效 pbarcode 映射。"));
            return null;
        }
        for (String sourceBarcode : sourceBarcodes(line)) {
            if (!pbarcodes.contains(sourceBarcode)) {
                issues.add(issue(
                        partnerSku,
                        pskuCode,
                        sourceBarcode,
                        "BARCODE_PBARCODE_MISMATCH",
                        "物流 barcode 未出现在该 Noon PSKU 的 pbarcode 映射中。"
                ));
                return null;
            }
        }
        return new FrozenLine(line, sourceBarcodes(line), pbarcodes);
    }

    private static List<String> sourceBarcodes(AsnLineInsertRecord line) {
        Set<String> values = new LinkedHashSet<>();
        if (line != null && line.sourceBarcodes != null) {
            for (String sourceBarcode : line.sourceBarcodes) {
                String value = text(sourceBarcode);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    static void addSourceBarcode(AsnLineInsertRecord line, String sourceBarcode) {
        String value = text(sourceBarcode);
        if (line != null && value != null && !line.sourceBarcodes.contains(value)) {
            line.sourceBarcodes.add(value);
        }
    }

    private List<String> partnerBarcodes(JsonNode offer) {
        Set<String> values = new LinkedHashSet<>();
        addValues(values, offer == null ? null : offer.path("partner_barcodes"));
        addValues(values, offer == null ? null : offer.path("partnerBarcodes"));
        addValues(values, offer == null ? null : offer.path("partner_barcode"));
        addValues(values, offer == null ? null : offer.path("pbarcode"));
        addValues(values, offer == null ? null : offer.path("pbarcode_canonical"));
        List<String> result = new ArrayList<>(values);
        result.sort(Comparator.comparing(value -> value.toUpperCase(Locale.ROOT)));
        return Collections.unmodifiableList(result);
    }

    private void addValues(Set<String> values, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> addValues(values, item));
            return;
        }
        if (!node.isContainerNode()) {
            String value = text(node.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
    }

    private ApiProblemException problem(List<Map<String, Object>> issues) {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED",
                "VALIDATION",
                "CREATE_ASN",
                "所选商品尚未全部通过 Noon 身份与 pbarcode 预检，未创建 ASN。",
                false,
                false,
                null,
                Map.of("invalidLines", issues == null ? List.of() : issues),
                null
        );
    }

    private static Map<String, Object> issue(
            String partnerSku,
            String pskuCode,
            String reasonCode,
            String message
    ) {
        return issue(partnerSku, pskuCode, null, reasonCode, message);
    }

    private static Map<String, Object> issue(
            String partnerSku,
            String pskuCode,
            String sourceBarcode,
            String reasonCode,
            String message
    ) {
        Map<String, Object> issue = new LinkedHashMap<>();
        if (text(partnerSku) != null) {
            issue.put("partnerSku", text(partnerSku));
        }
        if (text(pskuCode) != null) {
            issue.put("pskuCode", text(pskuCode));
        }
        if (text(sourceBarcode) != null) {
            issue.put("sourceBarcode", text(sourceBarcode));
        }
        issue.put("reasonCode", reasonCode);
        issue.put("message", message);
        return issue;
    }

    private static String jsonText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String value = text(node.path(field).asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        String text = text(value);
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
