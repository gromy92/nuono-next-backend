package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyBatisOperationConfigProductDimensionRepositoryTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private ProductLiteMapper productLiteMapper;

    @Test
    void usesProductLiteProjectionFallbackWhenBrandDictionaryIsEmpty() {
        ProductClassificationOptionRecord fallback = option("Acme");
        when(productManagementMapper.selectBrandDictionaryOptions(501L, "STR-X-NAE", "ac", 20))
                .thenReturn(List.of());
        when(productLiteMapper.selectBrandProjectionClassificationOptions(501L, "STR-X-NAE", "ac", 20))
                .thenReturn(List.of(fallback));
        MyBatisOperationConfigProductDimensionRepository repository =
                new MyBatisOperationConfigProductDimensionRepository(productManagementMapper, productLiteMapper);

        List<ProductClassificationOptionRecord> options =
                repository.listBrandOptions(501L, "STR-X-NAE", "ac", 20);

        assertEquals(List.of(fallback), options);
        verify(productManagementMapper).selectBrandDictionaryOptions(501L, "STR-X-NAE", "ac", 20);
        verify(productLiteMapper).selectBrandProjectionClassificationOptions(501L, "STR-X-NAE", "ac", 20);
    }

    @Test
    void usesProductLiteProjectionFallbackWhenFulltypeDictionaryIsEmpty() {
        ProductClassificationOptionRecord fallback = option("home-bedding-duvet");
        when(productManagementMapper.selectFulltypeDictionaryOptions(501L, "STR-X-NAE", "home", 20))
                .thenReturn(List.of());
        when(productLiteMapper.selectFulltypeProjectionClassificationOptions(501L, "STR-X-NAE", "home", 20))
                .thenReturn(List.of(fallback));
        MyBatisOperationConfigProductDimensionRepository repository =
                new MyBatisOperationConfigProductDimensionRepository(productManagementMapper, productLiteMapper);

        List<ProductClassificationOptionRecord> options =
                repository.listProductFulltypeOptions(501L, "STR-X-NAE", "home", 20);

        assertEquals(List.of(fallback), options);
        verify(productManagementMapper).selectFulltypeDictionaryOptions(501L, "STR-X-NAE", "home", 20);
        verify(productLiteMapper).selectFulltypeProjectionClassificationOptions(501L, "STR-X-NAE", "home", 20);
    }

    private static ProductClassificationOptionRecord option(String value) {
        ProductClassificationOptionRecord record = new ProductClassificationOptionRecord();
        record.setValue(value);
        record.setLabel(value);
        return record;
    }
}
