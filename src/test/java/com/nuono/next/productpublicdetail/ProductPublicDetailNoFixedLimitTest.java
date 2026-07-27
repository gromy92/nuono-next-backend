package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductPublicDetailNoFixedLimitTest {

    @Test
    void ignoresLegacyQuantityAndFailureCooldownSettings() {
        ProductPublicDetailSyncService service = new ProductPublicDetailSyncService(
                mock(ProductPublicDetailMapper.class),
                mock(OperationalTaskService.class),
                mock(NoonPublicProductDetailAdapter.class),
                (accountKey, task) -> {},
                new ObjectMapper(),
                Clock.systemUTC()
        );

        assertEquals(Integer.MAX_VALUE, ReflectionTestUtils.getField(service, "maxProductsPerTask"));
        assertEquals(0, ReflectionTestUtils.getField(service, "failureCooldownHours"));
    }
}
