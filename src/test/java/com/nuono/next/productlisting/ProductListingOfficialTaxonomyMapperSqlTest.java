package com.nuono.next.productlisting;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductListingOfficialTaxonomyMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductListingOfficialTaxonomyMapperSqlTest {

    @Test
    void resolvesExactCompleteOfficialNoonFulltype() throws Exception {
        Method method = Arrays.stream(
                        ProductListingOfficialTaxonomyMapper.class
                                .getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName()
                        .equals("selectOfficialNoonProductFulltype"))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = Arrays.stream(select.value())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("from cross_border_erp.goods_category")
                .contains("binary product_fulltype_code = binary #{productfulltype}")
                .contains("id_product_fulltype is not null")
                .contains("nullif(trim(family_name_en), '') is not null")
                .contains("nullif(trim(product_type_name_en), '') is not null")
                .contains("nullif(trim(product_subtype_name_en), '') is not null");
    }
}
