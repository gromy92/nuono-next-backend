package com.nuono.next.datapull.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.noonpull.NoonProductInterfaceSmokeProvider;
import com.nuono.next.noonpull.NoonSalesPageQueryProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

class DpSnapshotRuntimeConfigurationTest {

    @Test
    void dp02Dp04AndDp07aProviderAvailabilityAreIndependent() {
        assertThat(requiredBeans(Dp02PageRuntimeConfiguration.class))
                .containsExactly(NoonSalesPageQueryProvider.class);
        assertThat(requiredBeans(Dp04SnapshotRuntimeConfiguration.class))
                .containsExactly(NoonProductInterfaceSmokeProvider.class);
        assertThat(requiredBeans(Dp07SnapshotRuntimeConfiguration.class))
                .containsExactly(OfficialWarehouseFbnInventoryProvider.class);
    }

    private Class<?>[] requiredBeans(Class<?> configuration) {
        ConditionalOnBean condition = configuration.getAnnotation(ConditionalOnBean.class);
        assertThat(condition).isNotNull();
        return condition.value();
    }
}
