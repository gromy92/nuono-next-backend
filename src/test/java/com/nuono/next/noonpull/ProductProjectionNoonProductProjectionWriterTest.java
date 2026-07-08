package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductProjectionNoonProductProjectionWriterTest {

    @Test
    void shouldForwardCompleteSiteScopeToPersistenceService() {
        ProductProjectionPersistenceService persistenceService =
                mock(ProductProjectionPersistenceService.class);
        ProductProjectionNoonProductProjectionWriter writer =
                new ProductProjectionNoonProductProjectionWriter(persistenceService);
        List<ProductProjectionPersistenceService.SiteSeed> siteSeeds = List.of(
                new ProductProjectionPersistenceService.SiteSeed("STR244978-NAE", "AE", "LOCAL_READY", true)
        );
        List<ProductProjectionPersistenceService.ProductMasterSeed> productSeeds = List.of(
                new ProductProjectionPersistenceService.ProductMasterSeed()
        );
        List<String> warnings = new ArrayList<>();
        NoonProductProjectionWriteCommand command = new NoonProductProjectionWriteCommand();
        command.setOwnerUserId(307L);
        command.setProjectCode("PRJ244978");
        command.setProjectName("chenwu");
        command.setReferenceStoreCode("STR244978-NAE");
        command.setSiteSeeds(siteSeeds);
        command.setProductSeeds(productSeeds);
        command.setWarnings(warnings);
        command.setPreserveDrafts(true);
        command.setCompleteSiteScope(true);

        writer.write(command);

        verify(persistenceService).persistInitializationProjection(
                eq(307L),
                eq("PRJ244978"),
                eq("chenwu"),
                eq("STR244978-NAE"),
                same(siteSeeds),
                same(productSeeds),
                same(warnings),
                eq(true),
                eq(true)
        );
    }
}
