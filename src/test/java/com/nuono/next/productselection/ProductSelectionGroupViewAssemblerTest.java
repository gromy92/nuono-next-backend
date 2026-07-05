package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSelectionGroupViewAssemblerTest {

    @Test
    void assemblesGroupReadModelWithMaterialsProcurementAndCompetitors() {
        ProductSelectionGroupViewAssembler assembler = new ProductSelectionGroupViewAssembler(new ObjectMapper());

        ProductSelectionGroupView view = assembler.toGroupView(
                groupRow(),
                List.of(materialRow()),
                procurementRow(),
                List.of(competitorRow()),
                this::sourceCollectionView
        );

        assertEquals("91001", view.getGroupId());
        assertEquals("PSG-91001", view.getGroupNo());
        assertEquals("Sharpie 记号笔组", view.getGroupName());
        assertEquals("AE", view.getSiteCode());
        assertEquals(1, view.getMaterialCount());
        assertEquals("86001", view.getMaterials().get(0).getSourceCollectionId());
        assertEquals("Sharpie Permanent Markers", view.getMaterials().get(0).getSourceCollection().getSourceTitle());
        assertEquals("https://detail.1688.com/offer/91001.html", view.getProcurement().getAli1688PurchaseUrl());
        assertEquals(new BigDecimal("12.80"), view.getProcurement().getPurchasePriceRmb());
        assertEquals("Cased marker competitor", view.getCompetitors().get(0).getFetchedTitle());
        assertEquals("2026-07-01 10:20:00", view.getCompetitors().get(0).getFetchedAt());
    }

    @Test
    void assemblesMissingProfitSnapshotAsMissingStatus() {
        ProductSelectionGroupViewAssembler assembler = new ProductSelectionGroupViewAssembler(new ObjectMapper());

        ProductSelectionGroupProfitSnapshotView view = assembler.toGroupProfitSnapshotView(null);

        assertEquals("missing", view.getStatus());
    }

    private ProductSelectionGroupRow groupRow() {
        ProductSelectionGroupRow row = new ProductSelectionGroupRow();
        row.setGroupId(91001L);
        row.setGroupNo("PSG-91001");
        row.setGroupName("Sharpie 记号笔组");
        row.setSiteCode("ae");
        row.setGroupStatus("active");
        row.setMaterialCount(1);
        return row;
    }

    private ProductSelectionGroupMaterialRow materialRow() {
        ProductSelectionGroupMaterialRow row = new ProductSelectionGroupMaterialRow();
        row.setMaterialId(92001L);
        row.setGroupId(91001L);
        row.setSourceCollectionId(86001L);
        row.setMaterialStatus("active");
        row.setId(86001L);
        row.setSourceTitle("Sharpie Permanent Markers");
        return row;
    }

    private ProductSelectionGroupProcurementRow procurementRow() {
        ProductSelectionGroupProcurementRow row = new ProductSelectionGroupProcurementRow();
        row.setGroupId(91001L);
        row.setAli1688PurchaseUrl("https://detail.1688.com/offer/91001.html");
        row.setPurchasePriceRmb(new BigDecimal("12.80"));
        row.setProcurementStatus("active");
        return row;
    }

    private ProductSelectionGroupCompetitorRow competitorRow() {
        ProductSelectionGroupCompetitorRow row = new ProductSelectionGroupCompetitorRow();
        row.setCompetitorId(93001L);
        row.setGroupId(91001L);
        row.setCompetitorUrl("https://www.noon.com/uae-en/competitor/p/");
        row.setFetchStatus("success");
        row.setFetchedAt("2026-07-01 10:20:00");
        row.setFetchedPayloadJson("{\"fetchedTitle\":\"Cased marker competitor\",\"fetchedSourceHost\":\"noon.com\"}");
        return row;
    }

    private ProductSelectionSourceCollectionView sourceCollectionView(ProductSelectionSourceCollectionRow row) {
        ProductSelectionSourceCollectionView view = new ProductSelectionSourceCollectionView();
        view.setId(row.getId() == null ? null : String.valueOf(row.getId()));
        view.setSourceTitle(row.getSourceTitle());
        return view;
    }
}
