package com.nuono.next.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SalesImportListingProjectionBoundaryTest {

    private static final List<Path> SALES_IMPORT_SURFACE = List.of(
            Path.of("src/main/java/com/nuono/next/sales/SalesFactRepository.java"),
            Path.of("src/main/java/com/nuono/next/sales/MyBatisSalesFactRepository.java"),
            Path.of("src/main/java/com/nuono/next/sales/SalesDataController.java")
    );

    @Test
    void salesImportSurfaceContainsNoListingProjectionInputOrDependency() throws IOException {
        for (Path sourcePath : SALES_IMPORT_SURFACE) {
            assertThat(Files.readString(sourcePath))
                    .as(sourcePath.toString())
                    .doesNotContain(
                            "SalesListingCoverageMode",
                            "listingCoverageMode",
                            "markSiteOffersNotListedForEmptyReport",
                            "markSiteProductOffersNotListedForEmptySalesReport",
                            "ProductManagementMapper"
                    );
        }
        assertThat(Path.of("src/main/java/com/nuono/next/sales/SalesListingCoverageMode.java"))
                .doesNotExist();
    }

    @Test
    void repositoryAndMapperExposeNoSalesTriggeredListingProjectionSeam() {
        assertThat(fieldTypes(MyBatisSalesFactRepository.class))
                .doesNotContain(ProductManagementMapper.class);
        assertThat(constructorParameterTypes(MyBatisSalesFactRepository.class))
                .doesNotContain(ProductManagementMapper.class);
        assertThat(methodNames(SalesFactRepository.class))
                .doesNotContain("markSiteOffersNotListedForEmptyReport");
        assertThat(methodNames(ProductManagementMapper.class))
                .doesNotContain("markSiteProductOffersNotListedForEmptySalesReport");
    }

    private static List<Class<?>> fieldTypes(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toList());
    }

    private static List<Class<?>> constructorParameterTypes(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(java.util.Arrays::stream)
                .collect(Collectors.toList());
    }

    private static List<String> methodNames(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toList());
    }
}
