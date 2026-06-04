package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductListingMapperSqlTest {

    @Test
    void insertDraftShouldPersistOptionalPurchaseOrderAndDraftPayload() {
        Method method = mapperMethod("insertDraft");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("INSERT INTO product_listing_draft"));
        assertTrue(sql.contains("optional_purchase_order_id"));
        assertTrue(sql.contains("draft_json"));
        assertTrue(sql.contains("#{draft.optionalPurchaseOrderId}"));
    }

    @Test
    void insertTaskShouldPersistDryRunModeAndValidationSnapshot() {
        Method method = mapperMethod("insertTask");
        Insert insert = method.getAnnotation(Insert.class);
        String sql = compact(insert.value());

        assertTrue(sql.contains("INSERT INTO product_listing_task"));
        assertTrue(sql.contains("mode"));
        assertTrue(sql.contains("input_snapshot_json"));
        assertTrue(sql.contains("validation_json"));
        assertTrue(sql.contains("#{task.mode}"));
    }

    @Test
    void taskLookupShouldStayScopedToOwner() {
        Method method = mapperMethod("selectTaskById");
        Select select = method.getAnnotation(Select.class);
        String sql = compact(select.value());

        assertTrue(sql.contains("FROM product_listing_task"));
        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
    }

    private Method mapperMethod(String name) {
        return Arrays.stream(ProductListingMapper.class.getDeclaredMethods())
                .filter((candidate) -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
    }

    private String compact(String[] sql) {
        return String.join(" ", sql).replaceAll("\\s+", " ").trim();
    }
}
