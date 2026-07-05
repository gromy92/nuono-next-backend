package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class ProductSelectionGroupViewAssembler {

    private final ObjectMapper objectMapper;

    public ProductSelectionGroupViewAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductSelectionGroupView toGroupView(
            ProductSelectionGroupRow group,
            List<ProductSelectionGroupMaterialRow> materials,
            ProductSelectionGroupProcurementRow procurement,
            List<ProductSelectionGroupCompetitorRow> competitors,
            Function<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionView> sourceCollectionViewMapper
    ) {
        List<ProductSelectionGroupMaterialRow> safeMaterials = materials == null ? List.of() : materials;
        List<ProductSelectionGroupCompetitorRow> safeCompetitors = competitors == null ? List.of() : competitors;
        ProductSelectionGroupView view = new ProductSelectionGroupView();
        view.setGroupId(group.getGroupId() == null ? null : String.valueOf(group.getGroupId()));
        view.setGroupNo(defaultText(group.getGroupNo(), ""));
        view.setGroupName(defaultText(group.getGroupName(), "未命名选品组"));
        view.setSiteCode(normalizeSiteCode(group.getSiteCode()));
        view.setStatus(defaultText(group.getGroupStatus(), "active"));
        view.setMaterialCount(group.getMaterialCount() == null ? safeMaterials.size() : group.getMaterialCount());
        view.setMaterials(safeMaterials.stream()
                .map(row -> toGroupMaterialView(row, sourceCollectionViewMapper))
                .collect(Collectors.toList()));
        view.setProcurement(toGroupProcurementView(procurement));
        view.setCompetitors(safeCompetitors.stream()
                .map(this::toGroupCompetitorView)
                .collect(Collectors.toList()));
        return view;
    }

    public ProductSelectionGroupProfitSnapshotView toGroupProfitSnapshotView(ProductSelectionGroupProfitSnapshotRow row) {
        ProductSelectionGroupProfitSnapshotView view = new ProductSelectionGroupProfitSnapshotView();
        if (row == null) {
            view.setStatus("missing");
            return view;
        }
        view.setSnapshotId(row.getSnapshotId() == null ? null : String.valueOf(row.getSnapshotId()));
        view.setGroupId(row.getGroupId() == null ? null : String.valueOf(row.getGroupId()));
        view.setCurrencyCode(defaultText(row.getCurrencyCode(), "RMB"));
        view.setProfitAmount(row.getProfitAmount());
        view.setProfitMargin(row.getProfitMargin());
        view.setStatus(defaultText(row.getStatus(), "saved"));
        view.setCreatedAt(defaultText(row.getCreatedAt(), ""));
        view.setSnapshot(readMapJson(row.getSnapshotJson()));
        return view;
    }

    private ProductSelectionGroupMaterialView toGroupMaterialView(
            ProductSelectionGroupMaterialRow row,
            Function<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionView> sourceCollectionViewMapper
    ) {
        ProductSelectionGroupMaterialView view = new ProductSelectionGroupMaterialView();
        view.setMaterialId(row.getMaterialId() == null ? null : String.valueOf(row.getMaterialId()));
        view.setGroupId(row.getGroupId() == null ? null : String.valueOf(row.getGroupId()));
        view.setSourceCollectionId(row.getSourceCollectionId() == null ? null : String.valueOf(row.getSourceCollectionId()));
        view.setStatus(defaultText(row.getMaterialStatus(), "active"));
        view.setSourceCollection(sourceCollectionViewMapper.apply(row));
        return view;
    }

    private ProductSelectionGroupProcurementView toGroupProcurementView(ProductSelectionGroupProcurementRow row) {
        ProductSelectionGroupProcurementView view = new ProductSelectionGroupProcurementView();
        if (row == null) {
            view.setAli1688PurchaseUrl("");
            view.setStatus("missing");
            return view;
        }
        view.setAli1688PurchaseUrl(defaultText(row.getAli1688PurchaseUrl(), ""));
        view.setPurchasePriceRmb(row.getPurchasePriceRmb());
        view.setStatus(defaultText(row.getProcurementStatus(), "active"));
        return view;
    }

    private ProductSelectionGroupCompetitorView toGroupCompetitorView(ProductSelectionGroupCompetitorRow row) {
        ProductSelectionGroupCompetitorView view = new ProductSelectionGroupCompetitorView();
        view.setId(row.getCompetitorId() == null ? null : String.valueOf(row.getCompetitorId()));
        view.setGroupId(row.getGroupId() == null ? null : String.valueOf(row.getGroupId()));
        view.setUrl(defaultText(row.getCompetitorUrl(), ""));
        view.setNote(defaultText(row.getNote(), ""));
        view.setFetchStatus(defaultText(row.getFetchStatus(), "pending"));
        ProductSelectionAnalysisCommand.CompetitorContext payload = readCompetitorPayload(row.getFetchedPayloadJson());
        if (payload != null) {
            view.setFetchedTitle(defaultText(payload.getFetchedTitle(), ""));
            view.setFetchedTitleAr(defaultText(payload.getFetchedTitleAr(), ""));
            view.setFetchedSourceImageUrl(defaultText(payload.getFetchedSourceImageUrl(), ""));
            view.setFetchedImageUrls(payload.getFetchedImageUrls());
            view.setFetchedDescriptionEn(defaultText(payload.getFetchedDescriptionEn(), ""));
            view.setFetchedDescriptionAr(defaultText(payload.getFetchedDescriptionAr(), ""));
            view.setFetchedSellingPointsEn(payload.getFetchedSellingPointsEn());
            view.setFetchedSellingPointsAr(payload.getFetchedSellingPointsAr());
            view.setFetchedSourceHost(defaultText(payload.getFetchedSourceHost(), ""));
            view.setFetchedPriceSummary(defaultText(payload.getFetchedPriceSummary(), ""));
            view.setFetchedCompleteness(defaultText(payload.getFetchedCompleteness(), ""));
            view.setFetchedCollectionSource(defaultText(payload.getFetchedCollectionSource(), ""));
            view.setFetchMessage(defaultText(payload.getFetchMessage(), ""));
        }
        view.setFetchedAt(defaultText(row.getFetchedAt(), payload == null ? "" : defaultText(payload.getFetchedAt(), "")));
        return view;
    }

    private ProductSelectionAnalysisCommand.CompetitorContext readCompetitorPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductSelectionAnalysisCommand.CompetitorContext.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> readMapJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            return new HashMap<>();
        }
    }

    private String normalizeSiteCode(String value) {
        return defaultText(value, "").toUpperCase();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
