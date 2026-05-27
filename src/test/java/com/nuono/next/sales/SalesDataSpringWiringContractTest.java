package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class SalesDataSpringWiringContractTest {

    @Test
    void salesImportComponentsAreSpringBeans() {
        assertNotNull(NoonProductViewsSalesReportParser.class.getAnnotation(Component.class));
        assertNotNull(NoonSalesCsvImportService.class.getAnnotation(Service.class));
        assertNotNull(LegacySalesBackfillService.class.getAnnotation(Service.class));
        assertNotNull(UnavailableLegacySalesBackfillRowProvider.class.getAnnotation(Component.class));
        assertNotNull(SalesImportQualityService.class.getAnnotation(Service.class));
        assertNotNull(MyBatisSalesFactRepository.class.getAnnotation(Repository.class));
    }

    @Test
    void salesSyncTaskComponentsAreSpringBeans() {
        assertNotNull(SalesSyncTaskService.class.getAnnotation(Service.class));
        assertNotNull(MyBatisSalesSyncTaskRepository.class.getAnnotation(Repository.class));
        assertNotNull(UnavailableNoonSalesReportProvider.class.getAnnotation(Component.class));
        assertNotNull(NoonSalesReportBindingResolver.class.getAnnotation(Service.class));
        assertNotNull(NoonProductViewsSalesReportExporter.class.getAnnotation(Service.class));
        assertNotNull(NoonProductViewsSalesReportProvider.class.getAnnotation(Component.class));
        assertNotNull(NoonSessionGatewaySalesReportSessionFactory.class.getAnnotation(Component.class));
    }

    @Test
    void legacySalesBackfillServiceCanWireWithUnavailableProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        LegacySalesBackfillService.class,
                        UnavailableLegacySalesBackfillRowProvider.class,
                        TestSalesFactRepositoryConfig.class
                )
                .run(context -> {
                    assertNotNull(context.getBean(LegacySalesBackfillService.class));
                    assertNotNull(context.getBean(LegacySalesBackfillRowProvider.class));
                });
    }

    @Test
    void salesAnalyticsServiceCanWireWithRepositoryWhenMultipleConstructorsExist() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        SalesAnalyticsService.class,
                        TestSalesFactRepositoryConfig.class
                )
                .run(context -> assertNotNull(context.getBean(SalesAnalyticsService.class)));
    }

    @Test
    void compositeSalesActivityWindowRepositoryCanWireWithMyBatisRepositoryAndCompatibilitySources() {
        new ApplicationContextRunner()
                .withBean(SalesDataMapper.class, () -> mock(SalesDataMapper.class))
                .withBean(MyBatisSalesActivityWindowRepository.class)
                .withBean(SalesActivityWindowCompatibilitySource.class, () -> scope -> List.of())
                .withBean(CompositeSalesActivityWindowRepository.class)
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        () -> String.valueOf(context.getStartupFailure())
                ));
    }

    @Test
    void salesSyncTaskServiceCanWireWithDefaultUnavailableNoonProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        SalesSyncTaskService.class,
                        NoonSalesCsvImportService.class,
                        NoonProductViewsSalesReportParser.class,
                        UnavailableNoonSalesReportProvider.class,
                        TestSalesSyncTaskRepositoryConfig.class,
                        TestSalesFactRepositoryConfig.class
                )
                .run(context -> {
                    assertNotNull(context.getBean(SalesSyncTaskService.class));
                    assertNotNull(context.getBean(NoonSalesReportProvider.class));
                });
    }

    @Configuration
    static class TestSalesFactRepositoryConfig {

        @Bean
        SalesFactRepository salesFactRepository() {
            return new SalesFactRepository() {
                @Override
                public long saveBatch(SalesImportBatch batch) {
                    return 1L;
                }

                @Override
                public void upsert(DailySalesFact fact) {
                }

                @Override
                public List<DailySalesFact> list(SalesFactQuery query) {
                    return List.of();
                }
            };
        }
    }

    @Configuration
    static class TestSalesSyncTaskRepositoryConfig {

        @Bean
        SalesSyncTaskRepository salesSyncTaskRepository() {
            return new SalesSyncTaskRepository() {
                @Override
                public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
                    return SalesSyncTaskRecord.queued(1L, command);
                }

                @Override
                public SalesSyncTaskRecord markRunning(Long taskId) {
                    return null;
                }

                @Override
                public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
                    return null;
                }

                @Override
                public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
                    return null;
                }

                @Override
                public SalesSyncTaskRecord findById(Long taskId) {
                    return null;
                }
            };
        }
    }
}
