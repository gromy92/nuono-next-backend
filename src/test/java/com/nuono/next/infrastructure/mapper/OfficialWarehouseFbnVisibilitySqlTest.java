package com.nuono.next.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnVisibilitySqlTest {

    @Test
    void everyBusinessReaderOfFbnChildrenRequiresAnActiveImportHeader() {
        List<String> readers = new ArrayList<>();
        assertBusinessReadersAreHeaderGuarded(OfficialWarehouseMapper.class, readers);
        assertBusinessReadersAreHeaderGuarded(OfficialWarehouseStatisticsMapper.class, readers);

        assertThat(readers)
                .as("the contract test must discover the current FBN child readers")
                .isNotEmpty();
    }

    @Test
    void sourceOnlyRowsAreNotReportedAsLocalMatchingFailures() {
        Method summary = java.util.Arrays.stream(
                        OfficialWarehouseStatisticsMapper.class.getDeclaredMethods()
                )
                .filter(method -> method.getName().equals("selectInboundReceiptSummary"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", summary.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("l.match_status IN ('NO_LOCAL_ASN', 'LINE_UNMATCHED', 'PRODUCT_UNMATCHED')")
                .doesNotContain("l.match_status != 'MATCHED'");
    }

    private void assertBusinessReadersAreHeaderGuarded(
            Class<?> mapperType,
            List<String> readers
    ) {
        for (Method method : mapperType.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
            if (!sql.contains("FROM official_warehouse_inbound_receipt_line")
                    && !sql.contains("FROM official_warehouse_report_row")) {
                continue;
            }
            if (method.getName().equals("selectMaxReportRowId")
                    || method.getName().equals("selectMaxInboundReceiptLineId")) {
                // Sequence bootstrap inspects physical IDs; it is not a business-data reader.
                continue;
            }
            readers.add(mapperType.getSimpleName() + "." + method.getName());
            assertThat(sql)
                    .as(readers.get(readers.size() - 1))
                    .contains("JOIN official_warehouse_report_import i ON i.id =")
                    .contains("i.is_deleted = b'0'");
        }
    }
}
