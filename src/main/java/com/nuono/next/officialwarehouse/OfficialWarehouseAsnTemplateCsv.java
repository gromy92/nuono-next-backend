package com.nuono.next.officialwarehouse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class OfficialWarehouseAsnTemplateCsv {

    static final List<String> EXPECTED_HEADERS = List.of(
            "code", "partner_barcode", "partner_sku", "family", "brand_code",
            "product_title", "sku", "quantity", "country_code", "longest_side",
            "median_side", "shortest_side", "weight", "storage_type_code", "cubic_feet",
            "volume_added_by", "update_dimension"
    );
    private static final byte[] UTF_8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final int PARTNER_SKU_INDEX = EXPECTED_HEADERS.indexOf("partner_sku");
    private static final int QUANTITY_INDEX = EXPECTED_HEADERS.indexOf("quantity");
    private static final int COUNTRY_CODE_INDEX = EXPECTED_HEADERS.indexOf("country_code");

    byte[] fillQuantities(byte[] source, Map<String, Integer> requestedQuantities, String siteCode) {
        if (source == null || source.length == 0) {
            throw new IllegalStateException("Noon 约仓模板为空，请先在 Noon 刷新模板后重试。");
        }
        if (requestedQuantities == null || requestedQuantities.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品后再导出约仓模板。");
        }
        boolean hasBom = startsWithBom(source);
        String csv = new String(source, hasBom ? UTF_8_BOM.length : 0,
                source.length - (hasBom ? UTF_8_BOM.length : 0), StandardCharsets.UTF_8);
        List<List<String>> records = parse(csv);
        if (records.isEmpty()) {
            throw new IllegalStateException("Noon 约仓模板没有表头。");
        }
        if (!EXPECTED_HEADERS.equals(records.get(0))) {
            throw new IllegalStateException("Noon 约仓模板列结构已变化，系统已停止导出，请联系管理员适配新模板。");
        }

        String expectedSite = requireText(siteCode, "缺少约仓模板站点。").toUpperCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = records.get(index);
            if (row.size() != EXPECTED_HEADERS.size()) {
                throw new IllegalStateException("Noon 约仓模板第 " + (index + 1) + " 行列数异常。");
            }
            String rowSite = trimToNull(row.get(COUNTRY_CODE_INDEX));
            if (rowSite == null || !expectedSite.equals(rowSite.toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException("Noon 约仓模板站点与当前选择不一致。");
            }
            row.set(QUANTITY_INDEX, "0");
            String partnerSku = trimToNull(row.get(PARTNER_SKU_INDEX));
            if (partnerSku == null || !requestedQuantities.containsKey(partnerSku)) {
                continue;
            }
            if (!matched.add(partnerSku)) {
                duplicated.add(partnerSku);
                continue;
            }
            row.set(QUANTITY_INDEX, String.valueOf(requestedQuantities.get(partnerSku)));
        }
        if (!duplicated.isEmpty()) {
            throw new IllegalStateException("Noon 约仓模板中 SKU 重复：" + summarize(duplicated) + "。");
        }
        Set<String> missing = new LinkedHashSet<>(requestedQuantities.keySet());
        missing.removeAll(matched);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下 SKU 不在 Noon 最新可约仓模板中：" + summarize(missing) + "。请刷新 Noon 模板或调整选择。");
        }
        return serialize(records, hasBom);
    }

    Map<String, Integer> normalizeQuantities(List<OfficialWarehouseAsnTemplateExportCommand.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品后再导出约仓模板。");
        }
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (OfficialWarehouseAsnTemplateExportCommand.Line line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("约仓模板商品行不能为空。");
            }
            String partnerSku = requireText(line.partnerSku, "所选商品缺少 partner SKU，不能导出约仓模板。");
            if (line.quantity == null || line.quantity <= 0) {
                throw new IllegalArgumentException(partnerSku + " 数量必须大于 0。");
            }
            if (quantities.putIfAbsent(partnerSku, line.quantity) != null) {
                throw new IllegalArgumentException("所选商品包含重复 SKU：" + partnerSku + "。");
            }
        }
        return quantities;
    }

    private List<List<String>> parse(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (current == '"' && field.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                field.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Noon 约仓模板包含未闭合的引号。");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }

    private byte[] serialize(List<List<String>> records, boolean includeBom) {
        StringBuilder result = new StringBuilder();
        for (List<String> record : records) {
            for (int index = 0; index < record.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                appendField(result, record.get(index));
            }
            result.append("\r\n");
        }
        byte[] body = result.toString().getBytes(StandardCharsets.UTF_8);
        if (!includeBom) {
            return body;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(UTF_8_BOM.length + body.length);
        output.write(UTF_8_BOM, 0, UTF_8_BOM.length);
        output.write(body, 0, body.length);
        return output.toByteArray();
    }

    private void appendField(StringBuilder result, String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.indexOf(',') < 0 && safeValue.indexOf('"') < 0
                && safeValue.indexOf('\r') < 0 && safeValue.indexOf('\n') < 0) {
            result.append(safeValue);
            return;
        }
        result.append('"').append(safeValue.replace("\"", "\"\"")).append('"');
    }

    private boolean startsWithBom(byte[] source) {
        return source.length >= UTF_8_BOM.length
                && Arrays.equals(UTF_8_BOM, Arrays.copyOf(source, UTF_8_BOM.length));
    }

    private String summarize(Set<String> values) {
        String summary = values.stream().limit(10).collect(Collectors.joining("、"));
        return values.size() > 10 ? summary + " 等 " + values.size() + " 个" : summary;
    }

    private String requireText(String value, String message) {
        String safeValue = trimToNull(value);
        if (safeValue == null) {
            throw new IllegalArgumentException(message);
        }
        return safeValue;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
