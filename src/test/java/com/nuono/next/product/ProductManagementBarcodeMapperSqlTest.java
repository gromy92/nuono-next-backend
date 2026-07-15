package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class ProductManagementBarcodeMapperSqlTest {

    @Test
    void productBarcodeUpsertMustNotMoveExistingBarcodeToAnotherVariant() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "upsertProductBarcode",
                Long.class,
                Long.class,
                String.class,
                String.class,
                boolean.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("variant_id = VALUES(variant_id)");
    }

    @Test
    void draftBarcodeReconciliationDeletesOnlyNonRetainedValues() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "markOtherProductBarcodesDeletedByProductMasterId",
                Long.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("JOIN product_variant pv ON pv.id = pb.variant_id")
                .contains("pv.product_master_id = #{productMasterId}")
                .contains("pb.barcode <> #{retainedBarcode}")
                .contains("pb.is_deleted = 0");
    }

    @Test
    void productBarcodeSequenceCanBeAdvancedPastCurrentMaxId() throws Exception {
        Method selectMax = ProductManagementMapper.class.getMethod("selectMaxProductBarcodeId");
        String selectSql = String.join(" ", selectMax.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        Method advance = ProductManagementMapper.class.getMethod(
                "advanceProductManagementIdSequence",
                String.class,
                Long.class
        );
        String advanceSql = String.join(" ", advance.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(selectSql).contains("MAX(id)").contains("product_barcode");
        assertThat(advanceSql).contains("GREATEST(next_id, #{minimumAllocatedId})");
    }

    @Test
    void productBarcodeIdAllocationReturnsMaxPlusOneWhenSequenceIsStale() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            IdSequenceCommand command = invocation.getArgument(0);
            command.setAllocatedId(54145L);
            return 1;
        }).when(mapper).allocateProductManagementId(any(IdSequenceCommand.class));
        when(mapper.selectMaxProductBarcodeId()).thenReturn(54743L);

        Long allocatedId = mapper.nextProductBarcodeId();

        assertThat(allocatedId).isEqualTo(54744L);
        verify(mapper).advanceProductManagementIdSequence("product_barcode", 54744L);
    }
}
