package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.infrastructure.mapper.ProductDeleteTaskSubmissionMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductDeleteTaskSubmissionMapperRegistrationTest {

    private static final String SHARED_RESULT_MAP =
            ProductManagementMapper.class.getName() + ".ProductPublishTaskRecordMap";

    @Test
    void deleteLookupResolvesTheSharedPublishTaskResultMapInEitherRegistrationOrder() {
        Configuration parentFirst = new Configuration();
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductManagementMapper.class));
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductDeleteTaskSubmissionMapper.class));
        assertDeleteLookupResolves(parentFirst);

        Configuration childFirst = new Configuration();
        assertDoesNotThrow(() -> childFirst.addMapper(ProductDeleteTaskSubmissionMapper.class));
        assertDoesNotThrow(() -> childFirst.addMapper(ProductManagementMapper.class));
        assertDeleteLookupResolves(childFirst);
    }

    private void assertDeleteLookupResolves(Configuration configuration) {
        MappedStatement statement = assertDoesNotThrow(() -> configuration.getMappedStatement(
                ProductDeleteTaskSubmissionMapper.class.getName()
                        + ".selectLatestProductPublishTask"
        ));
        assertEquals(SHARED_RESULT_MAP, statement.getResultMaps().get(0).getId());
    }
}
