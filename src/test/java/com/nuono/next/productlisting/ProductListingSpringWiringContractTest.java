package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.permission.access.BusinessAccessResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class ProductListingSpringWiringContractTest {

    @Test
    void productListingComponentsAreSpringBeans() {
        assertNotNull(ProductListingValidator.class.getAnnotation(Component.class));
        assertNotNull(ProductListingService.class.getAnnotation(Service.class));
        assertNotNull(ProductListingRealRunTaskListener.class.getAnnotation(Component.class));
        assertNotNull(ProductListingController.class.getAnnotation(RestController.class));
    }

    @Test
    void productListingControllerCanWireWithMinimalDependencies() {
        new ApplicationContextRunner()
                .withBean(ProductListingMapper.class, () -> mock(ProductListingMapper.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(BusinessAccessResolver.class, () -> mock(BusinessAccessResolver.class))
                .withBean(ProductListingRealWriteProperties.class, ProductListingRealWriteProperties::new)
                .withBean(ProductListingNoonWriteAdapter.class, UnavailableProductListingNoonWriteAdapter::new)
                .withUserConfiguration(ProductListingWiringConfig.class)
                .run(context -> {
                    assertTrue(
                            context.getStartupFailure() == null,
                            () -> String.valueOf(context.getStartupFailure())
                    );
                    assertNotNull(context.getBean(ProductListingService.class));
                    assertNotNull(context.getBean(ProductListingController.class));
                });
    }

    @Test
    void realWriteAdapterWinsWhenRealWriteIsEnabledAndSessionFactoryExists() {
        new ApplicationContextRunner()
                .withPropertyValues("nuono.product-listing.real-write.enabled=true")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ProductListingRealWriteProperties.class, ProductListingRealWriteProperties::new)
                .withBean(NoonPullStoreBindingResolver.class, () -> mock(NoonPullStoreBindingResolver.class))
                .withBean(NoonPullGatewaySessionFactory.class, () -> mock(NoonPullGatewaySessionFactory.class))
                .withBean(ProductListingOfferStockWriteAdapter.class, UnavailableProductListingOfferStockWriteAdapter::new)
                .withUserConfiguration(ProductListingRealWriteAdapterConfig.class)
                .run(context -> {
                    assertTrue(
                            context.getStartupFailure() == null,
                            () -> String.valueOf(context.getStartupFailure())
                    );
                    assertTrue(context.getBean(ProductListingNoonWriteAdapter.class)
                            instanceof RealProductListingNoonWriteAdapter);
                });
    }

    @Configuration
    @Import({
            ProductListingValidator.class,
            ProductListingService.class,
            ProductListingRealRunTaskListener.class,
            ProductListingController.class
    })
    static class ProductListingWiringConfig {
    }

    @Configuration
    @Import({
            RealProductListingNoonWriteAdapter.class,
            UnavailableProductListingNoonWriteAdapter.class
    })
    static class ProductListingRealWriteAdapterConfig {
    }
}
