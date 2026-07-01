package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductWorkbenchViewAssembler {

    ProductMasterWorkbenchView buildWorkbenchView(
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings
    ) {
        ProductMasterWorkbenchView view = new ProductMasterWorkbenchView();
        copySnapshotFields(view, record.getDraftSnapshot());
        view.setBaselineSnapshot(copySnapshot(record.getBaselineSnapshot()));
        view.setDraftSnapshot(copySnapshot(record.getDraftSnapshot()));
        view.setSyncStatus(record.getSyncStatus());
        view.setLastSyncedAt(record.getLastSyncedAt());
        view.setNote(record.getNote());
        view.setRecentActions(copyRecordList(record.getRecentActions()));
        view.setKeyContentHistory(copyRecordList(record.getKeyContentHistory()));
        view.setPendingKeyContentHistoryCount(record.getPendingKeyContentHistoryCount());
        view.setPendingKeyContentHistoryVisibleAfter(record.getPendingKeyContentHistoryVisibleAfter());
        view.setPublishTask(record.getPublishTask());
        if (StringUtils.hasText(message)) {
            view.setMessage(message);
        }
        if (warnings != null) {
            view.setWarnings(userVisibleWarnings(warnings));
        }
        return view;
    }

    ProductMasterWorkbenchView buildLocalBaselineMissingWorkbench(ProductMasterFetchCommand command) {
        ProductMasterWorkbenchView view = new ProductMasterWorkbenchView();
        view.setMode("local-db");
        view.setReady(false);
        view.setSyncStatus("failed");
        view.setMessage("本地还没有这条商品的详情基线，详情编辑暂不可用。");
        view.setNote("系统会在后台补齐详情基线；完成后刷新列表或重新打开详情即可编辑。");
        if (command != null) {
            if (StringUtils.hasText(command.getStoreCode())) {
                view.getStoreContext().put("storeCode", command.getStoreCode());
            }
            String currentZCode = firstNonBlank(command.getCurrentZCode(), command.getSkuParent());
            if (StringUtils.hasText(currentZCode)) {
                view.setCurrentZCode(currentZCode);
                view.getIdentity().put("currentZCode", currentZCode);
                view.getIdentity().put("skuParent", currentZCode);
            }
            if (StringUtils.hasText(command.getPartnerSku())) {
                view.getIdentity().put("partnerSku", command.getPartnerSku());
            }
            if (StringUtils.hasText(command.getPskuCode())) {
                view.getIdentity().put("pskuCode", command.getPskuCode());
            }
        }
        return view;
    }

    void applyMissingBaselineBackfillPrompt(ProductMasterWorkbenchView view, String backfillStatus) {
        if (view == null) {
            return;
        }
        if ("preparing".equals(backfillStatus)) {
            view.setSyncStatus("preparing");
            view.setMessage("本地还没有这条商品的详情基线，系统正在后台准备。");
            view.setNote("已启动后台补齐详情基线；补齐完成后刷新列表或重新打开详情即可编辑。");
            view.getWarnings().add("系统正在后台从 Noon 补齐当前商品详情基线，详情打开不会等待该任务完成。");
            return;
        }
        if ("failed".equals(backfillStatus)) {
            view.setSyncStatus("failed");
            view.setMessage("详情基线后台补齐失败，详情编辑暂不可用。");
            view.setNote("详情基线上次后台补齐失败；请稍后重试或手动从 Noon 同步。");
        }
    }

    private void copySnapshotFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        if (source == null || target == null) {
            return;
        }
        target.setMode(source.getMode());
        target.setReady(source.isReady());
        target.setMessage(source.getMessage());
        target.setWarnings(userVisibleWarnings(source.getWarnings()));
        target.setStoreContext(new LinkedHashMap<>(source.getStoreContext()));
        target.setIdentity(new LinkedHashMap<>(source.getIdentity()));
        normalizeIdentityAliases(target);
        target.setTaxonomy(new LinkedHashMap<>(source.getTaxonomy()));
        target.setContent(new LinkedHashMap<>(source.getContent()));
        target.setPlatformSignals(new LinkedHashMap<>(source.getPlatformSignals()));
        target.setKeyAttributes(copyRecordList(source.getKeyAttributes()));
        target.setGroup(new LinkedHashMap<>(source.getGroup()));
        target.setVariants(copyRecordList(source.getVariants()));
        target.setPricing(new LinkedHashMap<>(source.getPricing()));
        target.setStock(new LinkedHashMap<>(source.getStock()));
        target.setSiteOffers(copyRecordList(source.getSiteOffers()));
        target.setDegraded(source.isDegraded());
    }

    private void normalizeIdentityAliases(ProductMasterSnapshotView target) {
        if (target == null || target.getIdentity() == null) {
            return;
        }
        String currentZCode = firstNonBlank(
                text(target.getIdentity().get("currentZCode")),
                text(target.getIdentity().get("skuParent"))
        );
        if (StringUtils.hasText(currentZCode)) {
            target.getIdentity().put("currentZCode", currentZCode);
            target.getIdentity().put("skuParent", currentZCode);
            if (target instanceof ProductMasterWorkbenchView) {
                ((ProductMasterWorkbenchView) target).setCurrentZCode(currentZCode);
            }
        }
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        if (source == null) {
            return null;
        }
        ProductMasterSnapshotView copy = new ProductMasterSnapshotView();
        copySnapshotFields(copy, source);
        return copy;
    }

    private List<Map<String, Object>> copyRecordList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (Map<String, Object> item : source) {
            copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
        }
        return copy;
    }

    private List<String> userVisibleWarnings(List<String> warnings) {
        List<String> visible = new ArrayList<>();
        if (warnings == null) {
            return visible;
        }
        for (String warning : warnings) {
            if (!StringUtils.hasText(warning)) {
                continue;
            }
            String normalized = warning.trim();
            if (normalized.startsWith("debug:")) {
                continue;
            }
            if (!visible.contains(normalized)) {
                visible.add(normalized);
            }
        }
        return visible;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : (StringUtils.hasText(second) ? second.trim() : null);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
