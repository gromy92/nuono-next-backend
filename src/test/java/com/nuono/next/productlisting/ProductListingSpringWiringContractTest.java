package com.nuono.next.productlisting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.NoonSessionGatewayPullSessionFactory;
import com.nuono.next.permission.access.BusinessAccessResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
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
        assertNotNull(ProductListingRealRunTaskScheduler.class.getAnnotation(Component.class));
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

    @Test
    void realWriteAdapterFailsFastWhenNoonPullProviderIsMissing() {
        new ApplicationContextRunner()
                .withPropertyValues("nuono.product-listing.real-write.enabled=true")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ProductListingRealWriteProperties.class, ProductListingRealWriteProperties::new)
                .withBean(NoonPullStoreBindingResolver.class, () -> mock(NoonPullStoreBindingResolver.class))
                .withBean(ProductListingOfferStockWriteAdapter.class, UnavailableProductListingOfferStockWriteAdapter::new)
                .withUserConfiguration(ProductListingRealWriteAdapterConfig.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("nuono.noon.pull.real-provider.enabled=true"));
    }

    @Test
    void productListingRealWriteWiresActualNoonPullSessionFactoryWhenSwitchesAreEnabled() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-db"))
                .withPropertyValues(
                        "nuono.product-listing.real-write.enabled=true",
                        "nuono.noon.pull.real-provider.enabled=true"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ProductListingRealWriteProperties.class, ProductListingRealWriteProperties::new)
                .withBean(NoonPullStoreBindingResolver.class, () -> mock(NoonPullStoreBindingResolver.class))
                .withBean(ProductListingOfferStockWriteAdapter.class, UnavailableProductListingOfferStockWriteAdapter::new)
                .withUserConfiguration(ProductListingRealWriteAdapterWithSessionConfig.class)
                .run(context -> {
                    assertTrue(
                            context.getStartupFailure() == null,
                            () -> String.valueOf(context.getStartupFailure())
                    );
                    assertThat(context).hasSingleBean(NoonPullGatewaySessionFactory.class);
                    assertTrue(context.getBean(ProductListingNoonWriteAdapter.class)
                            instanceof RealProductListingNoonWriteAdapter);
                });
    }

    @Configuration
    @Import({
            ProductListingValidator.class,
            ProductListingService.class,
            ProductListingRealRunTaskListener.class,
            ProductListingRealRunTaskScheduler.class,
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

    @Configuration
    @Import({
            NoonSessionGatewayPullSessionFactory.class,
            RealProductListingNoonWriteAdapter.class,
            UnavailableProductListingNoonWriteAdapter.class
    })
    static class ProductListingRealWriteAdapterWithSessionConfig {
        @Bean
        NoonSessionGateway noonSessionGateway() {
            return mock(NoonSessionGateway.class);
        }
    }
}
