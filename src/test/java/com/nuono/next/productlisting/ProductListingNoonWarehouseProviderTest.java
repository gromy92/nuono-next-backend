package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingNoonWarehouseProviderTest {

    @Test
    void listWarehousesUsesNoonWarehouseEndpointAndFiltersInactiveRows() {
        FakeBindingResolver bindingResolver = new FakeBindingResolver();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWarehouseProvider provider = new RealProductListingNoonWarehouseProvider(
                new ObjectMapper(),
                bindingResolver,
                sessionFactory,
                new ProductListingRealWriteProperties()
        );

        List<ProductListingWarehouseView> warehouses = provider.listWarehouses(10002L, "STR108065-NSA");

        assertEquals(NoonPullDataDomain.PRODUCT, bindingResolver.request.getDataDomain());
        assertEquals("STR108065-NSA", bindingResolver.request.getStoreCode());
        assertEquals(1, warehouses.size());
        ProductListingWarehouseView warehouse = warehouses.get(0);
        assertEquals("W00752151SA", warehouse.getWarehouseCode());
        assertEquals("hank", warehouse.getWarehouseName());
        assertEquals("73001", warehouse.getIdPartnerWarehouse());
        assertEquals("SA", warehouse.getCountryCode());

        FakeSession.Call call = sessionFactory.session.readCalls.get(0);
        assertEquals(
                ProductListingRealWriteProperties.Endpoints.DEFAULT_WAREHOUSE_LIST_URL,
                call.url
        );
        assertEquals(false, call.withProject);
        assertEquals("PRJ240053", call.extraHeaders.get("x-project"));
        assertEquals("SA", call.extraHeaders.get("Country-Code"));
        assertEquals("240053", call.extraHeaders.get("Id-Partner"));
        assertEquals("application/json", call.extraHeaders.get("Accept"));
    }

    private static class FakeBindingResolver extends NoonPullStoreBindingResolver {
        private NoonInterfacePullRequest request;

        FakeBindingResolver() {
            super(null);
        }

        @Override
        public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
            this.request = request;
            return new NoonPullStoreBinding(
                    request.getOwnerUserId(),
                    "PRJ240053",
                    request.getStoreCode(),
                    "SA",
                    "240053",
                    "merchant@example.test",
                    "secret",
                    "sid=test"
            );
        }
    }

    private static class FakeSessionFactory implements NoonPullGatewaySessionFactory {
        private final FakeSession session = new FakeSession();

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            return session;
        }
    }

    private static class FakeSession implements NoonPullGatewaySession {
        private final List<Call> readCalls = new ArrayList<>();

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            throw new AssertionError("warehouse listing must use Noon GET endpoint");
        }

        @Override
        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            throw new AssertionError("warehouse listing must not write Noon");
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            readCalls.add(new Call(url, withProject, extraHeaders));
            return ("["
                    + "{\"warehouseCode\":\"W00752151SA\",\"warehouseName\":\"hank\","
                    + "\"idPartnerWarehouse\":73001,\"countryCode\":\"SA\","
                    + "\"isWarehouseActive\":true,\"onboardingStatus\":\"active\"},"
                    + "{\"warehouseCode\":\"W_INACTIVE\",\"warehouseName\":\"inactive\","
                    + "\"idPartnerWarehouse\":73002,\"countryCode\":\"SA\","
                    + "\"isWarehouseActive\":false,\"onboardingStatus\":\"active\"},"
                    + "{\"warehouseCode\":\"W_PENDING\",\"warehouseName\":\"pending\","
                    + "\"idPartnerWarehouse\":73003,\"countryCode\":\"SA\","
                    + "\"isWarehouseActive\":true,\"onboardingStatus\":\"pending\"}"
                    + "]").getBytes(StandardCharsets.UTF_8);
        }

        private static class Call {
            private final String url;
            private final boolean withProject;
            private final Map<String, String> extraHeaders;

            private Call(String url, boolean withProject, Map<String, String> extraHeaders) {
                this.url = url;
                this.withProject = withProject;
                this.extraHeaders = extraHeaders;
            }
        }
    }
}
