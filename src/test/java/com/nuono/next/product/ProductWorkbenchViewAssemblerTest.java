package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductWorkbenchViewAssemblerTest {

    private final ProductWorkbenchViewAssembler assembler = new ProductWorkbenchViewAssembler();

    @Test
    void missingBaselineWorkbenchCarriesCommandIdentityAndBackfillPrompt() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("PAPERSAYSB132");
        command.setPartnerSku("PAPERSAYS-B132");
        command.setPskuCode("ZSKU-PAPERSAYSB132");

        ProductMasterWorkbenchView view = assembler.buildLocalBaselineMissingWorkbench(command);
        assembler.applyMissingBaselineBackfillPrompt(view, "preparing");

        assertFalse(view.isReady());
        assertEquals("local-db", view.getMode());
        assertEquals("preparing", view.getSyncStatus());
        assertEquals("STR245027-NAE", view.getStoreContext().get("storeCode"));
        assertEquals("PAPERSAYSB132", view.getIdentity().get("skuParent"));
        assertTrue(view.getWarnings().stream().anyMatch((warning) -> warning.contains("后台从 Noon 补齐")));
    }

    @Test
    void buildWorkbenchViewCopiesSnapshotsAndFiltersDebugWarnings() {
        ProductMasterSnapshotView baseline = snapshot("PAPERSAYSB132", "Baseline title");
        ProductMasterSnapshotView draft = snapshot("PAPERSAYSB132", "Draft title");
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(baseline);
        record.setDraftSnapshot(draft);
        record.setSyncStatus("draft");
        record.setLastSyncedAt("2026-06-04 10:00:00");
        record.setNote("已从本地库恢复未发布草稿。");

        ProductMasterWorkbenchView view = assembler.buildWorkbenchView(
                record,
                "本地工作台已就绪。",
                List.of("debug: internal timing", "用户可见提示")
        );

        assertEquals("draft", view.getSyncStatus());
        assertEquals("本地工作台已就绪。", view.getMessage());
        assertEquals("Draft title", view.getContent().get("title"));
        assertEquals("Baseline title", view.getBaselineSnapshot().getContent().get("title"));
        assertEquals("Draft title", view.getDraftSnapshot().getContent().get("title"));
        assertEquals(List.of("用户可见提示"), view.getWarnings());

        draft.getContent().put("title", "Mutated after build");
        assertEquals("Draft title", view.getContent().get("title"));
        assertEquals("Draft title", view.getDraftSnapshot().getContent().get("title"));
    }

    private ProductMasterSnapshotView snapshot(String skuParent, String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("local-db");
        snapshot.setReady(true);
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getIdentity().put("skuParent", skuParent);
        snapshot.getContent().put("title", title);
        return snapshot;
    }
}
