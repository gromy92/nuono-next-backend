package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Dp04ProjectionSchemaMapper;
import com.nuono.next.system.BootstrapProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp04ProjectionSchemaPreflightTest {

    @Test
    void failsOnMissingColumnsRatherThanOnlyCheckingTableNames() {
        Dp04ProjectionSchemaMapper mapper = mock(Dp04ProjectionSchemaMapper.class);
        when(mapper.findExistingColumnKeys(eq("nuono_new_dev"), anyList()))
                .thenReturn(List.of("logical_store.id"));
        Dp04ProjectionSchemaPreflight preflight = new Dp04ProjectionSchemaPreflight(
                mapper,
                new BootstrapProperties()
        );

        assertThatThrownBy(preflight::requireReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logical_store.owner_user_id")
                .hasMessageContaining("product_site_offer.listing_started_at")
                .hasMessageNotContaining("daily_sales_fact");
    }
}
