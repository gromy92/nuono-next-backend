package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.product-listing.real-write", name = "enabled", havingValue = "true")
public class RealProductListingNoonWarehouseProvider implements ProductListingWarehouseProvider {
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final ProductListingRealWriteProperties properties;
    private final ProductListingNoonWarehouseClient warehouseClient;

    public RealProductListingNoonWarehouseProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            ProductListingRealWriteProperties properties
    ) {
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.properties = properties == null ? new ProductListingRealWriteProperties() : properties;
        this.warehouseClient = new ProductListingNoonWarehouseClient(objectMapper);
    }

    @Override
    public List<ProductListingWarehouseView> listWarehouses(Long ownerUserId, String storeCode) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("Product listing warehouse owner and store are required.");
        }
        NoonPullStoreBinding binding = bindingResolver.resolve(NoonInterfacePullRequest.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("product-listing-warehouses")
                .targetIdentity(storeCode)
                .requestSummary("product listing warehouse list")
                .build());
        NoonPullGatewaySession session = sessionFactory.login(binding);
        Map<String, String> headers = ProductListingNoonHeaders.writeHeaders(binding);
        return warehouseClient.listWarehouses(session, properties.getEndpoints(), binding, headers);
    }
}
