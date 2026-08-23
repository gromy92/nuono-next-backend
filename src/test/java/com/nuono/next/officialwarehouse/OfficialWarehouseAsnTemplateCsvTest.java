package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnTemplateCsvTest {

    private final OfficialWarehouseAsnTemplateCsv csv = new OfficialWarehouseAsnTemplateCsv();

    @Test
    void fillsSelectedQuantitiesAndResetsUnselectedRowsWithoutChangingTemplateColumns() {
        String source = header() + "\r\n"
                + row("CODE-1", "PSKU-1", "Title, with \"quotes\"", "9") + "\r\n"
                + row("CODE-2", "PSKU-2", "Second title", "8") + "\r\n";

        byte[] result = csv.fillQuantities(
                source.getBytes(StandardCharsets.UTF_8),
                Map.of("PSKU-1", 12),
                "SA"
        );

        String exported = new String(result, StandardCharsets.UTF_8);
        assertThat(exported.lines().findFirst()).hasValue(header());
        assertThat(exported).contains("CODE-1,BARCODE-PSKU-1,PSKU-1,family,brand,\"Title, with \"\"quotes\"\"\",SKU-PSKU-1,12,SA");
        assertThat(exported).contains("CODE-2,BARCODE-PSKU-2,PSKU-2,family,brand,Second title,SKU-PSKU-2,0,SA");
    }

    @Test
    void preservesUtf8BomAndQuotedNewlines() {
        byte[] body = (header() + "\r\n" + row("CODE-1", "PSKU-1", "First line\nSecond line", "0") + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] source = new byte[body.length + 3];
        source[0] = (byte) 0xEF;
        source[1] = (byte) 0xBB;
        source[2] = (byte) 0xBF;
        System.arraycopy(body, 0, source, 3, body.length);

        byte[] result = csv.fillQuantities(source, Map.of("PSKU-1", 3), "sa");

        assertThat(result).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(result, 3, result.length - 3, StandardCharsets.UTF_8))
                .contains("\"First line\nSecond line\",SKU-PSKU-1,3,SA");
    }

    @Test
    void failsClosedWhenSelectedSkuIsMissingOrDuplicatedInTemplate() {
        String source = header() + "\n"
                + row("CODE-1", "PSKU-1", "One", "0") + "\n"
                + row("CODE-2", "PSKU-1", "Duplicate", "0") + "\n";

        assertThatThrownBy(() -> csv.fillQuantities(
                source.getBytes(StandardCharsets.UTF_8), Map.of("PSKU-1", 1), "SA"
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("SKU 重复");

        assertThatThrownBy(() -> csv.fillQuantities(
                source.getBytes(StandardCharsets.UTF_8), Map.of("PSKU-MISSING", 1), "SA"
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("不在 Noon 最新可约仓模板中");
    }

    @Test
    void rejectsChangedHeaderWrongSiteAndInvalidRequestedLines() {
        String wrongHeader = header().replace("partner_barcode", "barcode") + "\n"
                + row("CODE-1", "PSKU-1", "One", "0") + "\n";
        assertThatThrownBy(() -> csv.fillQuantities(
                wrongHeader.getBytes(StandardCharsets.UTF_8), Map.of("PSKU-1", 1), "SA"
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("列结构已变化");

        String ae = (header() + "\n" + row("CODE-1", "PSKU-1", "One", "0").replace(",SA,", ",AE,") + "\n");
        assertThatThrownBy(() -> csv.fillQuantities(
                ae.getBytes(StandardCharsets.UTF_8), Map.of("PSKU-1", 1), "SA"
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("站点与当前选择不一致");

        OfficialWarehouseAsnTemplateExportCommand.Line first = line("PSKU-1", 1);
        OfficialWarehouseAsnTemplateExportCommand.Line duplicate = line("PSKU-1", 2);
        assertThatThrownBy(() -> csv.normalizeQuantities(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复 SKU");
        assertThatThrownBy(() -> csv.normalizeQuantities(List.of(line("PSKU-2", 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数量必须大于 0");
    }

    private OfficialWarehouseAsnTemplateExportCommand.Line line(String partnerSku, int quantity) {
        OfficialWarehouseAsnTemplateExportCommand.Line line = new OfficialWarehouseAsnTemplateExportCommand.Line();
        line.partnerSku = partnerSku;
        line.quantity = quantity;
        return line;
    }

    private String header() {
        return String.join(",", OfficialWarehouseAsnTemplateCsv.EXPECTED_HEADERS);
    }

    private String row(String code, String partnerSku, String title, String quantity) {
        return String.join(",",
                code,
                "BARCODE-" + partnerSku,
                partnerSku,
                "family",
                "brand",
                csvField(title),
                "SKU-" + partnerSku,
                quantity,
                "SA",
                "10",
                "8",
                "2",
                "0.1",
                "standard",
                "0.01",
                "Noon System",
                "0"
        );
    }

    private String csvField(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\""
                : value;
    }
}
