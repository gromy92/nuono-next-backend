package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationConfigProductDimensionRepository implements OperationConfigProductDimensionRepository {

    private final ProductManagementMapper mapper;

    public MyBatisOperationConfigProductDimensionRepository(ProductManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProductClassificationOptionRecord> listBrandOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    ) {
        List<ProductClassificationOptionRecord> dictionary = mapper.selectBrandDictionaryOptions(
                ownerUserId,
                storeCode,
                query,
                limit
        );
        return dictionary.isEmpty()
                ? mapper.selectBrandProjectionClassificationOptions(ownerUserId, storeCode, query, limit)
                : dictionary;
    }

    @Override
    public List<ProductClassificationOptionRecord> listProductFulltypeOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    ) {
        List<ProductClassificationOptionRecord> dictionary = mapper.selectFulltypeDictionaryOptions(
                ownerUserId,
                storeCode,
                query,
                limit
        );
        return dictionary.isEmpty()
                ? mapper.selectFulltypeProjectionClassificationOptions(ownerUserId, storeCode, query, limit)
                : dictionary;
    }
}
