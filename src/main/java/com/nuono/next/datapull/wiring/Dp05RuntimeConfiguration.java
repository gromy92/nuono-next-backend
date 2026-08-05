package com.nuono.next.datapull.wiring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.BackoffHoldStore;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.NoonDataPullScopeSource;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.infrastructure.mapper.Dp05RuntimeMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.productpublicdetail.ProductPublicDetailRuntimeFactWriter;
import com.nuono.next.productpublicdetail.datapull.Dp05CheckpointCodec;
import com.nuono.next.productpublicdetail.datapull.Dp05FrontendDetailProviderAdapter;
import com.nuono.next.productpublicdetail.datapull.Dp05ProductDetailJob;
import com.nuono.next.productpublicdetail.datapull.Dp05StageBackoff;
import com.nuono.next.productpublicdetail.datapull.MyBatisDp05ProductCursor;
import com.nuono.next.productpublicdetail.datapull.NoonPartnerDp05DetailProvider;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the item-routed DP-05 Module. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Dp05RuntimeConfiguration {

    @Bean
    MyBatisDp05ProductCursor dp05ProductCursor(Dp05RuntimeMapper mapper) {
        return new MyBatisDp05ProductCursor(mapper);
    }

    @Bean
    Dp05FrontendDetailProviderAdapter dp05FrontendDetailProvider(
            NoonPublicProductDetailAdapter adapter
    ) {
        return new Dp05FrontendDetailProviderAdapter(adapter);
    }

    @Bean
    NoonPartnerDp05DetailProvider dp05PartnerDetailProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        return new NoonPartnerDp05DetailProvider(
                objectMapper,
                bindingResolver,
                sessionFactory
        );
    }

    @Bean
    Dp05StageBackoff dp05StageBackoff(
            BackoffHoldStore holdStore,
            ProviderWaitTransition providerWaitTransition
    ) {
        return new Dp05StageBackoff(holdStore, providerWaitTransition);
    }

    @Bean("dp05ProductDetailJob")
    DataPullJob dp05ProductDetailJob(
            NoonDataPullScopeMapper scopeMapper,
            MyBatisDp05ProductCursor productCursor,
            Dp05FrontendDetailProviderAdapter frontendProvider,
            NoonPartnerDp05DetailProvider partnerProvider,
            ProductPublicDetailRuntimeFactWriter factWriter,
            ObjectMapper objectMapper,
            Dp05StageBackoff stageBackoff
    ) {
        return new Dp05ProductDetailJob(
                new NoonDataPullScopeSource(scopeMapper),
                productCursor,
                frontendProvider,
                partnerProvider,
                factWriter,
                new Dp05CheckpointCodec(objectMapper),
                stageBackoff
        );
    }
}
