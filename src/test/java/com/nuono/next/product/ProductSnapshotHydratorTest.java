package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductSnapshotHydratorTest {

    private ProductSnapshotHydrator hydrator;

    @BeforeEach
    void setUp() {
        hydrator = new ProductSnapshotHydrator(new ObjectMapper());
    }

    @Test
    void shouldSanitizeSnapshotAndHydrateProjectionOnlyFields() {
        ProductMasterSnapshotView fallback = snapshot("STR245027-NSA");
        fallback.getPlatformSignals().put("listingStartedAt", "2026-01-02");
        fallback.getPricing().put("finalPrice", "88.00");
        fallback.getSiteOffers().get(0).put("finalPrice", "88.00");
        fallback.getSiteOffers().get(0).put("fbnStock", 15);
        fallback.getSiteOffers().get(0).put("statusCode", "LIVE");

        ProductMasterSnapshotView draft = snapshot("STR245027-NSA");
        draft.setMode("remote");
        draft.setReady(false);
        draft.setWarnings(new ArrayList<>(List.of(
                "读取 Fulltype 模板失败：timeout",
                "业务告警",
                ""
        )));
        draft.setMissingCoreTables(null);
        draft.setMissingOperationalKeys(null);
        draft.getPricing().put("price", "99.00");
        draft.getSiteOffers().get(0).put("price", "99.00");

        ProductMasterSnapshotView sanitized = hydrator.sanitizeSnapshot(draft, fallback);

        assertEquals("local-db", sanitized.getMode());
        assertTrue(sanitized.isReady());
        assertEquals(List.of("业务告警"), sanitized.getWarnings());
        assertEquals(List.of(), sanitized.getMissingCoreTables());
        assertEquals(List.of(), sanitized.getMissingOperationalKeys());
        assertEquals("99.00", sanitized.getPricing().get("price"));
        assertEquals("88.00", sanitized.getPricing().get("finalPrice"));
        assertEquals("88.00", sanitized.getSiteOffers().get(0).get("finalPrice"));
        assertEquals(15, sanitized.getSiteOffers().get(0).get("fbnStock"));
        assertEquals("LIVE", sanitized.getSiteOffers().get(0).get("statusCode"));
        assertEquals("2026-01-02", sanitized.getPlatformSignals().get("listingStartedAt"));
    }

    @Test
    void shouldDeepCopySnapshotsAndRecordLists() {
        ProductMasterSnapshotView source = snapshot("STR245027-NAE");
        source.getIdentity().put("skuParent", "PARENT-001");
        source.getSiteOffers().get(0).put("finalPrice", "42.00");

        ProductMasterSnapshotView copy = hydrator.copySnapshot(source);
        copy.getIdentity().put("skuParent", "PARENT-CHANGED");
        copy.getSiteOffers().get(0).put("finalPrice", "84.00");

        assertNotSame(source, copy);
        assertNotSame(source.getIdentity(), copy.getIdentity());
        assertNotSame(source.getSiteOffers().get(0), copy.getSiteOffers().get(0));
        assertEquals("PARENT-001", source.getIdentity().get("skuParent"));
        assertEquals("42.00", source.getSiteOffers().get(0).get("finalPrice"));
    }

    @Test
    void shouldMergeWarningsAsUserVisibleUniqueList() {
        List<String> warnings = hydrator.mergeWarnings(
                List.of("价格信息失败：timeout", "业务告警"),
                List.of("业务告警", "另一个告警", "类目模板读取已跳过")
        );

        assertEquals(List.of("业务告警", "另一个告警"), warnings);
    }

    @Test
    void shouldClearResolvedOperationalWarnings() {
        ProductMasterSnapshotView snapshot = snapshot("STR245027-NAE");
        snapshot.setDegraded(true);
        snapshot.getIdentity().put("partnerSku", "PARTNER-001");
        snapshot.getIdentity().put("pskuCode", "PSKU-001");
        snapshot.setMissingOperationalKeys(new ArrayList<>(List.of("partnerSku", "pskuCode")));
        snapshot.setWarnings(new ArrayList<>(List.of(
                "当前索引缺少 partnerSku / pskuCode",
                "读取 Fulltype 模板失败：timeout",
                "业务告警"
        )));

        hydrator.clearResolvedOperationalWarnings(snapshot);

        assertEquals(List.of(), snapshot.getMissingOperationalKeys());
        assertEquals(List.of("业务告警"), snapshot.getWarnings());
        assertFalse(snapshot.isDegraded());
    }

    private ProductMasterSnapshotView snapshot(String storeCode) {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("partnerSku", "PARTNER-001");
        identity.put("pskuCode", "PSKU-001");
        view.setIdentity(identity);
        view.setPricing(new LinkedHashMap<>());
        view.setPlatformSignals(new LinkedHashMap<>());

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", storeCode);
        siteOffer.put("price", "48.00");
        view.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return view;
    }
}
