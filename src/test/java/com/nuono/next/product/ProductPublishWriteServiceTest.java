package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublishWriteServiceTest {

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    @Mock
    private ProductGroupPublishService productGroupPublishService;

    private FakeWriteOperations writeOperations;
    private ProductPublishWriteService service;

    @BeforeEach
    void setUp() {
        writeOperations = new FakeWriteOperations();
        service = new ProductPublishWriteService(
                storeSyncMapper,
                productNoonAdapter,
                productGroupPublishService,
                writeOperations
        );
    }

    @Test
    void shouldResolveLoginContextAndDispatchSupportedWrites() {
        StoreSyncOwnerContext owner = ownerContext();
        StoreSyncStoreRecord store = storeRecord();
        ProductMasterActionCommand command = command();
        ProductMasterSnapshotView draft = snapshot();
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView liveBeforePublish = snapshot();
        ProductPublishUnsupportedChanges unsupportedChanges = new ProductPublishUnsupportedChanges();
        unsupportedChanges.setVariantStructureChanged(true);
        unsupportedChanges.getUnsupportedAttributeCodes().add("barcode");
        unsupportedChanges.markUnsupportedSiteField("STR245027-NAE", "barcode");
        List<String> actionWarnings = new ArrayList<>();

        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        service.publishSupportedChanges(
                command,
                store,
                draft,
                baseline,
                liveBeforePublish,
                "STR245027-NAE",
                unsupportedChanges,
                actionWarnings
        );

        verify(productNoonAdapter).login(
                307L,
                "store-project-user",
                "store-pwd",
                "store-cookie",
                "PRJ-LOCAL",
                "STR245027-NAE"
        );
        verify(productGroupPublishService).publishGroupChanges(null, draft, baseline, 307L, "STR245027-NAE");
        assertEquals(List.of("resolve-project", "publish-shared", "publish-offer:PSKU-BASE"), writeOperations.calls);
        assertTrue(actionWarnings.contains("project resolved"));
        assertTrue(actionWarnings.contains("当前尺码结构存在新增或移除，暂未开启真实 Noon 写回。"));
        assertTrue(actionWarnings.contains("有部分复杂属性值暂未写回 Noon，仍保留在诺诺草稿中。"));
        assertTrue(actionWarnings.contains("库存汇总和状态码仍保留展示，本轮发布未写回 Noon。"));
    }

    @Test
    void shouldFailBeforeLoginWhenOwnerContextIsMissing() {
        StoreSyncStoreRecord store = storeRecord();
        ProductMasterActionCommand command = command();
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishSupportedChanges(
                        command,
                        store,
                        snapshot(),
                        snapshot(),
                        snapshot(),
                        "STR245027-NAE",
                        new ProductPublishUnsupportedChanges(),
                        new ArrayList<>()
                )
        );

        assertEquals("老板账号不存在，无法执行商品发布。", exception.getMessage());
        verify(productNoonAdapter, never()).login(
                307L,
                "project-user",
                "pwd",
                "cookie",
                "PRJ-LOCAL",
                "STR245027-NAE"
        );
    }

    private ProductMasterActionCommand command() {
        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR245027-NAE");
        return command;
    }

    private StoreSyncOwnerContext ownerContext() {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(307L);
        owner.setNoonPartnerProjectUser("project-user");
        owner.setNoonPartnerUser("partner-user");
        owner.setNoonPartnerPwd("pwd");
        owner.setNoonPartnerCookie("cookie");
        owner.setNoonPartnerId("PRJ-OWNER");
        return owner;
    }

    private StoreSyncStoreRecord storeRecord() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ-LOCAL");
        store.setStoreCode("STR245027-NAE");
        store.setNoonPartnerUser("store-main-user");
        store.setNoonPartnerProjectUser("store-project-user");
        store.setNoonPartnerPwd("store-pwd");
        store.setNoonPartnerCookie("store-cookie");
        return store;
    }

    private ProductMasterSnapshotView snapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setIdentity(new LinkedHashMap<>());
        snapshot.setTaxonomy(new LinkedHashMap<>());
        snapshot.setContent(new LinkedHashMap<>());
        snapshot.setSiteOffers(List.of(siteOffer("STR245027-NAE", "PSKU-BASE")));
        return snapshot;
    }

    private Map<String, Object> siteOffer(String storeCode, String pskuCode) {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", storeCode);
        siteOffer.put("pskuCode", pskuCode);
        return siteOffer;
    }

    private static class FakeWriteOperations implements ProductPublishWriteService.WriteOperations {

        private final List<String> calls = new ArrayList<>();

        @Override
        public String resolveProjectCode(
                NoonSession session,
                String localProjectCode,
                StoreSyncStoreRecord store,
                List<String> warnings
        ) {
            calls.add("resolve-project");
            warnings.add("project resolved");
            return "PRJ-REAL";
        }

        @Override
        public NoonSession withProjectAndStore(
                NoonSession session,
                String projectCode,
                String storeCode
        ) {
            return session;
        }

        @Override
        public NoonSession withStore(
                NoonSession session,
                String storeCode
        ) {
            return session;
        }

        @Override
        public boolean sharedZskuChanged(
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline
        ) {
            return true;
        }

        @Override
        public boolean groupChanged(
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline
        ) {
            return true;
        }

        @Override
        public void publishSharedAttributes(
                NoonSession session,
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline,
                ProductMasterSnapshotView liveBeforePublish,
                ProductPublishUnsupportedChanges unsupportedChanges,
                List<String> actionWarnings
        ) {
            calls.add("publish-shared");
        }

        @Override
        public List<Map<String, Object>> targetOffers(
                ProductMasterSnapshotView draft,
                String currentSiteCode
        ) {
            return draft.getSiteOffers();
        }

        @Override
        public Map<String, Map<String, Object>> baselineOffers(ProductMasterSnapshotView baseline) {
            Map<String, Map<String, Object>> offers = new LinkedHashMap<>();
            offers.put("STR245027-NAE", new LinkedHashMap<>(baseline.getSiteOffers().get(0)));
            return offers;
        }

        @Override
        public boolean siteOfferChanged(
                Map<String, Object> siteOffer,
                Map<String, Object> baselineOffer
        ) {
            return true;
        }

        @Override
        public void publishOffer(
                NoonSession session,
                String pskuCode,
                Map<String, Object> siteOffer,
                Map<String, Object> baselineOffer,
                List<String> actionWarnings
        ) {
            calls.add("publish-offer:" + pskuCode);
        }
    }
}
