package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductImagePublishWorkflowMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductImagePublishWorkflowMapperRegistrationTest {

    @Test
    void inheritedPublishQueryShouldResolveTheSharedSuiteResultMapInEitherRegistrationOrder() {
        Configuration parentFirst = new Configuration();
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductImageProfileMapper.class));
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductImagePublishWorkflowMapper.class));
        assertPublishStatementResolves(parentFirst);

        Configuration childFirst = new Configuration();
        assertDoesNotThrow(() -> childFirst.addMapper(ProductImagePublishWorkflowMapper.class));
        assertDoesNotThrow(() -> childFirst.addMapper(ProductImageProfileMapper.class));
        assertPublishStatementResolves(childFirst);
    }

    private void assertPublishStatementResolves(Configuration configuration) {
        assertDoesNotThrow(() -> configuration.getMappedStatement(
                ProductImagePublishWorkflowMapper.class.getName() + ".selectSuiteByIdForUpdate"
        ));
    }
}
