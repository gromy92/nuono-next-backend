package com.nuono.next.datapull.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

class Dp05Dp06CompositionOwnershipTest {

    @Test
    void eachOperationOwnsOnlyItsBeanFactories() {
        Set<String> dp05 = beanFactories(Dp05RuntimeConfiguration.class);
        Set<String> dp06 = beanFactories(Dp06RuntimeConfiguration.class);

        assertThat(dp05).containsExactlyInAnyOrder(
                "dp05ProductCursor",
                "dp05FrontendDetailProvider",
                "dp05PartnerDetailProvider",
                "dp05StageBackoff",
                "dp05ProductDetailJob"
        );
        assertThat(dp06).containsExactlyInAnyOrder(
                "dp06AdvertisingStagedFactCodec",
                "dp06SnapshotStageStore",
                "dp06AdvertisingJob"
        );
        assertThat(dp05).doesNotContainAnyElementsOf(dp06);
    }

    private Set<String> beanFactories(Class<?> configuration) {
        return Arrays.stream(configuration.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Bean.class) != null)
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
