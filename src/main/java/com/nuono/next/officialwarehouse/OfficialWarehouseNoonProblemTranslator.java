package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noon.NoonResponseClassification;
import com.nuono.next.noon.NoonResponseClassifier;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.web.ApiProblemException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

final class OfficialWarehouseNoonProblemTranslator {

    private OfficialWarehouseNoonProblemTranslator() {
    }

    static ApiProblemException createAsnProblem(
            NoonOperationException exception,
            Long localAsnId,
            String localAsnNo,
            String noonAsnNo,
            List<AsnLineInsertRecord> lineRows
    ) {
        NoonResponseClassification classification = exception.getClassification();
        boolean partialSuccess = StringUtils.hasText(noonAsnNo);
        List<Map<String, Object>> affectedProducts = affectedProducts(
                classification.getAffectedPskuCodes(),
                lineRows
        );
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recognitionRuleVersion", NoonResponseClassifier.RULESET_VERSION);
        details.put("providerStatus", classification.getProviderStatus());
        if (localAsnId != null) {
            details.put("localAsnId", String.valueOf(localAsnId));
        }
        putIfText(details, "localAsnNo", localAsnNo);
        putIfText(details, "noonAsnNo", noonAsnNo);
        if (!classification.getAffectedPskuCodes().isEmpty()) {
            details.put("affectedPskuCodes", classification.getAffectedPskuCodes());
        }
        if (!affectedProducts.isEmpty()) {
            details.put("affectedProducts", affectedProducts);
        }

        String message = createAsnMessage(classification, noonAsnNo, affectedProducts);
        return new ApiProblemException(
                HttpStatus.valueOf(classification.getApiStatus()),
                classification.getCode(),
                classification.getCategory().name(),
                classification.getOperation(),
                message,
                classification.isRetryable(),
                partialSuccess,
                firstNonBlank(noonAsnNo, localAsnNo),
                details,
                exception
        );
    }

    private static String createAsnMessage(
            NoonResponseClassification classification,
            String noonAsnNo,
            List<Map<String, Object>> affectedProducts
    ) {
        StringBuilder message = new StringBuilder();
        if (StringUtils.hasText(noonAsnNo)) {
            message.append("Noon 已创建 ASN ")
                    .append(noonAsnNo.trim())
                    .append("，但后续步骤未完成，请勿重复创建。 ");
        }
        if ("NOON_PBARCODE_UNMAPPED".equals(classification.getCode()) && !affectedProducts.isEmpty()) {
            String productLabels = affectedProducts.stream()
                    .map(OfficialWarehouseNoonProblemTranslator::productLabel)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("、"));
            message.append("Noon 未给以下商品建立有效 pbarcode 映射：")
                    .append(productLabels)
                    .append("。请先在 Noon 后台补全商品映射。");
        } else {
            message.append(classification.getUserMessage());
        }
        return message.toString().trim();
    }

    private static List<Map<String, Object>> affectedProducts(
            List<String> affectedPskuCodes,
            List<AsnLineInsertRecord> lineRows
    ) {
        if (affectedPskuCodes == null || affectedPskuCodes.isEmpty() || lineRows == null || lineRows.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedPskuCodes = affectedPskuCodes.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AsnLineInsertRecord line : lineRows) {
            if (line == null
                    || !StringUtils.hasText(line.pskuCode)
                    || !normalizedPskuCodes.contains(line.pskuCode.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Map<String, Object> product = new LinkedHashMap<>();
            putIfText(product, "partnerSku", line.partnerSku);
            putIfText(product, "pskuCode", line.pskuCode);
            putIfText(product, "noonSku", line.noonSku);
            putIfText(product, "title", line.titleCache);
            result.add(product);
        }
        return result;
    }

    private static String productLabel(Map<String, Object> product) {
        String partnerSku = text(product.get("partnerSku"));
        String pskuCode = text(product.get("pskuCode"));
        if (StringUtils.hasText(partnerSku) && StringUtils.hasText(pskuCode)) {
            return partnerSku + "（Noon PSKU " + pskuCode + "）";
        }
        return firstNonBlank(partnerSku, pskuCode);
    }

    private static void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value.trim());
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
