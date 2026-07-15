package com.nuono.next.product;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ProductPublishTaskSchedulerTest {

    @Test
    void shouldDelegateToProductMasterServiceWhenEnabled() throws Exception {
        LocalDbProductMasterService service = mock(LocalDbProductMasterService.class);
        ProductPublishTaskScheduler scheduler = new ProductPublishTaskScheduler(service);
        setField(scheduler, "schedulerEnabled", true);

        scheduler.runProductPublishTaskScheduler();

        verify(service).runProductPublishTaskScheduler();
    }

    @Test
    void shouldSkipProductMasterServiceWhenDisabled() throws Exception {
        LocalDbProductMasterService service = mock(LocalDbProductMasterService.class);
        ProductPublishTaskScheduler scheduler = new ProductPublishTaskScheduler(service);
        setField(scheduler, "schedulerEnabled", false);

        scheduler.runProductPublishTaskScheduler();

        verify(service, never()).runProductPublishTaskScheduler();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
