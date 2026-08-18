package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchDiagnosticMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialWarehouseShippingBatchDiagnosticService {
    private static final Set<String> ELIGIBLE_BATCH_STATUSES = Set.of(
            "shipped", "in_transit", "customs_clearance", "delivering", "warehouse_received"
    );
    private static final Set<String> ELIGIBLE_NODE_STATUSES = Set.of(
            "departed_origin", "in_transit", "arrived_port", "customs_clearance",
            "customs_released", "delivering", "warehouse_received"
    );

    private final OfficialWarehouseMapper warehouseMapper;
    private final OfficialWarehouseShippingBatchDiagnosticMapper diagnosticMapper;

    public OfficialWarehouseShippingBatchDiagnosticService(
            OfficialWarehouseMapper warehouseMapper,
            OfficialWarehouseShippingBatchDiagnosticMapper diagnosticMapper
    ) {
        this.warehouseMapper = warehouseMapper;
        this.diagnosticMapper = diagnosticMapper;
    }

    public OfficialWarehouseShippingBatchDiagnosticView diagnose(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword
    ) {
        String store = requireText(storeCode, "请选择店铺。").toUpperCase(Locale.ROOT);
        String site = requireText(siteCode, "请选择站点。").toUpperCase(Locale.ROOT);
        String exactKeyword = requireText(keyword, "请输入完整物流批次号。");
        Long ownerUserId = OfficialWarehouseBusinessScope.resolve(access, store).ownerUserId();
        StoreSiteRecord storeSite = warehouseMapper.selectStoreSite(ownerUserId, store, site);
        if (storeSite == null) {
            throw new IllegalArgumentException("当前店铺未配置该站点。");
        }
        OfficialWarehouseShippingBatchDiagnosticRecord row = diagnosticMapper.selectExactBatchDiagnostic(
                ownerUserId, storeSite.storeCode, storeSite.siteCode, exactKeyword
        );
        return resolve(row, exactKeyword, site);
    }

    private OfficialWarehouseShippingBatchDiagnosticView resolve(
            OfficialWarehouseShippingBatchDiagnosticRecord row,
            String keyword,
            String requestedSite
    ) {
        if (row == null) {
            return view("BATCH_NOT_FOUND", "warning", "未找到物流批次",
                    "当前账号下未找到物流批次 " + keyword + "。请核对批次号、运单号或外部货件号是否完整。",
                    "核对单号后重新查询", null, keyword);
        }
        String batchNo = firstText(row.batchNo, keyword);
        String destinationSite = destinationSite(row.targetSiteCode, row.targetStoreCode);
        if (destinationSite == null) {
            return view("SITE_UNRESOLVED", "warning", "物流批次缺少目标站点",
                    "批次 " + batchNo + " 未识别到目标站点，无法确认是否属于当前 " + requestedSite + " 站点。",
                    "先补充物流批次目标站点", row, batchNo);
        }
        if (!destinationSite.equalsIgnoreCase(requestedSite)) {
            return view("SITE_MISMATCH", "warning", "物流批次站点不匹配",
                    "批次 " + batchNo + " 属于 " + destinationSite + " 站点，当前为 "
                            + requestedSite + "，不能跨站点约仓。",
                    "切换到正确站点后重新查询", row, batchNo);
        }
        if (!eligible(row.status, row.latestNodeStatus)) {
            String status = statusText(firstText(row.latestNodeStatus, row.status));
            return view("STATUS_NOT_ELIGIBLE", "warning", "物流批次状态不可约仓",
                    "批次 " + batchNo + " 当前状态为" + status + "，尚不在可约仓状态范围内。",
                    "等待物流状态更新或核对批次状态", row, batchNo);
        }
        int goodsLines = positive(row.goodsLineCount);
        int sourceCandidates = positive(row.sourceCandidateCount);
        if (goodsLines == 0 && sourceCandidates == 0) {
            int packages = positive(row.packageCount);
            if (packages == 0) {
                return view("NO_PACKAGE_DETAILS", "warning", "物流批次缺少装箱信息",
                        "批次 " + batchNo + " 尚未同步箱子和商品明细，系统无法生成 ASN 商品。",
                        "重新同步物流批次或导入装箱单", row, batchNo);
            }
            return view("NO_PRODUCT_DETAILS", "warning", "物流批次缺少商品明细",
                    "批次 " + batchNo + " 已同步 " + packages
                            + " 个箱子，但没有商品明细，系统无法生成 ASN 商品。请重新同步或导入装箱单。",
                    "补充装箱单商品明细后重新查询", row, batchNo);
        }
        if (goodsLines == 0 && positive(row.currentScopeCandidateCount) == 0) {
            return view("SOURCE_SCOPE_MISMATCH", "warning", "装箱商品店铺或站点不匹配",
                    "批次 " + batchNo + " 的装箱商品不属于当前店铺/站点，因此不能用于本次 ASN。",
                    "切换正确店铺/站点，或订正装箱单商品归属", row, batchNo);
        }
        int unmatched = positive(row.unmatchedCandidateCount);
        if (goodsLines == 0 && unmatched > 0) {
            return view("PRODUCT_MATCH_PENDING", "warning", "物流商品仍待匹配",
                    "批次 " + batchNo + " 有 " + unmatched
                            + " 条商品仍待条码匹配，暂时不能进入 ASN。",
                    "点击“刷新物流匹配”；仍失败时维护商品 barcode", row, batchNo);
        }
        int excluded = positive(row.excludedCandidateCount);
        if (goodsLines == 0 && sourceCandidates > 0 && excluded == sourceCandidates) {
            return view("ALL_PRODUCTS_EXCLUDED", "warning", "物流商品均已排除",
                    "批次 " + batchNo + " 的商品均已标记为不参与 ASN，因此没有可约仓商品。",
                    "核对排除结果或补充正确装箱单", row, batchNo);
        }
        if (positive(row.resolvedLineCount) == 0) {
            return view("PRODUCT_SCOPE_MISMATCH", "warning", "商品与当前店铺不匹配",
                    "批次 " + batchNo + " 已有 " + goodsLines
                            + " 条商品明细，但 barcode 无法唯一匹配当前店铺/站点商品。",
                    "核对商品 barcode、店铺归属和站点", row, batchNo);
        }
        if (positive(row.shippedQuantity) == 0) {
            return view("NO_SHIPPED_QUANTITY", "warning", "物流商品发货数量为零",
                    "批次 " + batchNo + " 的已匹配商品发货数量为 0，不能创建 ASN。",
                    "补充正确发货数量后重新查询", row, batchNo);
        }
        if (positive(row.remainingQuantity) == 0) {
            return view("NO_AVAILABLE_QUANTITY", "warning", "物流批次暂无可约数量",
                    "批次 " + batchNo + " 当前可约仓数量为 0，可能已被有效预约占用。",
                    "核对已有 ASN/预约和实际可用发货数量", row, batchNo);
        }
        return view("AVAILABLE", "info", "物流批次当前可约仓",
                "批次 " + batchNo + " 当前满足约仓条件，列表可能刚发生更新。",
                "刷新物流批次列表", row, batchNo);
    }

    private OfficialWarehouseShippingBatchDiagnosticView view(
            String code,
            String severity,
            String title,
            String message,
            String action,
            OfficialWarehouseShippingBatchDiagnosticRecord row,
            String batchNo
    ) {
        OfficialWarehouseShippingBatchDiagnosticView view = new OfficialWarehouseShippingBatchDiagnosticView();
        view.code = code;
        view.severity = severity;
        view.title = title;
        view.message = message;
        view.action = action;
        view.batchNo = batchNo;
        if (row != null) {
            view.batchId = row.id == null ? null : String.valueOf(row.id);
            view.status = row.status;
            view.latestNodeStatus = row.latestNodeStatus;
            view.targetSiteCode = destinationSite(row.targetSiteCode, row.targetStoreCode);
            view.packageCount = positive(row.packageCount);
            view.sourceCandidateCount = positive(row.sourceCandidateCount);
            view.currentScopeCandidateCount = positive(row.currentScopeCandidateCount);
            view.goodsLineCount = positive(row.goodsLineCount);
            view.resolvedLineCount = positive(row.resolvedLineCount);
            view.shippedQuantity = positive(row.shippedQuantity);
            view.remainingQuantity = positive(row.remainingQuantity);
        }
        return view;
    }

    private boolean eligible(String status, String latestNodeStatus) {
        return ELIGIBLE_BATCH_STATUSES.contains(normalize(status))
                || ELIGIBLE_NODE_STATUSES.contains(normalize(latestNodeStatus));
    }

    private String destinationSite(String siteCode, String targetStoreCode) {
        if (StringUtils.hasText(siteCode)) {
            return siteCode.trim().toUpperCase(Locale.ROOT);
        }
        String destination = normalize(targetStoreCode).toUpperCase(Locale.ROOT);
        if (destination.startsWith("RUH") || destination.startsWith("JED")) return "SA";
        if (destination.startsWith("DB") || destination.startsWith("DXB") || destination.startsWith("AUH")) return "AE";
        return null;
    }

    private String statusText(String status) {
        switch (normalize(status)) {
            case "draft": return "草稿";
            case "pending_shipment": return "待发货";
            case "exception": return "异常";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            case "created": return "已创建";
            case "handed_to_forwarder": return "已交货代";
            default: return firstText(status, "未知");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
