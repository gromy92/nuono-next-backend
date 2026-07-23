package com.nuono.next.warehousedispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineSourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class WarehousePackingWorkbookExporterTest {

    @Test
    void exportsZdPackingListWithOneWorksheetPerBox() throws Exception {
        ShippingBatchView batch = batch();
        OutboundOrderView order = order();
        PackingListView packingList = packingList();

        WarehousePackingWorkbookExporter.ExportFile export = WarehousePackingWorkbookExporter.export(
                batch,
                List.of(order),
                Map.of(order.id, List.of(packingList)),
                "ZD",
                "ZD-SAU-SEA-FBN-RUH"
        );

        assertEquals(
                "0718-海运-2581件-44-众鸫供应链-众鸫沙特海运专线到海外仓-装箱单.xlsx",
                export.filename
        );
        assertTrue(export.content.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.content))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("箱1", workbook.getSheetAt(0).getSheetName());
            assertEquals("图片", workbook.getSheetAt(0).getRow(4).getCell(0).getStringCellValue());
            assertEquals("A6黑色PU硬壳笔记本", workbook.getSheetAt(0).getRow(5).getCell(1).getStringCellValue());
            assertEquals("PAPERSAYSB224", workbook.getSheetAt(0).getRow(5).getCell(3).getStringCellValue());
            assertEquals(28D, workbook.getSheetAt(0).getRow(5).getCell(4).getNumericCellValue());
            assertEquals("E6*F6", workbook.getSheetAt(0).getRow(5).getCell(6).getCellFormula());
            assertEquals("箱1：尺寸：10×10×10   重量：10.0",
                    workbook.getSheetAt(0).getRow(6).getCell(0).getStringCellValue());
        }
    }

    @Test
    void exportsYitePackingListWithRepeatedBoxRows() throws Exception {
        ShippingBatchView batch = batch();
        OutboundOrderView order = order();
        PackingListView packingList = packingList();

        WarehousePackingWorkbookExporter.ExportFile export = WarehousePackingWorkbookExporter.export(
                batch,
                List.of(order),
                Map.of(order.id, List.of(packingList)),
                "YT",
                "YT-SAU-SEA-FBN-RUH"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.content))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("模板", workbook.getSheetAt(0).getSheetName());
            assertEquals(batch.batchNo, workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals(1D, workbook.getSheetAt(0).getRow(21).getCell(1).getNumericCellValue());
            assertEquals(1D, workbook.getSheetAt(0).getRow(23).getCell(0).getNumericCellValue());
            assertEquals(12D, workbook.getSheetAt(0).getRow(23).getCell(2).getNumericCellValue());
            assertEquals("PAPERSAYSB248", workbook.getSheetAt(0).getRow(23).getCell(5).getStringCellValue());
            assertEquals("黑色记忆棉护腕鼠标垫", workbook.getSheetAt(0).getRow(23).getCell(7).getStringCellValue());
            assertEquals(60D, workbook.getSheetAt(0).getRow(23).getCell(8).getNumericCellValue());
        }
    }

    private ShippingBatchView batch() {
        ShippingBatchView batch = new ShippingBatchView();
        batch.id = "700044";
        batch.batchNo = "0718-海运-2581件-44";
        batch.status = "PACKED";
        batch.boxCount = 1;
        batch.skuCount = 1;
        batch.totalQuantity = 28;
        batch.optionCount = 6;
        batch.grossWeightKg = new BigDecimal("10.0");
        batch.volumeCbm = new BigDecimal("0.0010");
        return batch;
    }

    private OutboundOrderView order() {
        OutboundOrderView order = new OutboundOrderView();
        order.id = "800051";
        order.outboundNo = "WO-800051";
        OutboundOrderLineView line = new OutboundOrderLineView();
        line.id = "812500";
        line.storeCode = "STR108065-NSA";
        line.partnerSku = "PAPERSAYSB224";
        line.productTitle = "A6黑色PU硬壳笔记本";
        line.siteCode = "SA";
        line.actualTransportMode = "SEA";
        line.targetForwarderCode = "ZD";
        line.targetForwarderName = "众鸫供应链";
        line.routeCode = "ZD-SAU-SEA-FBN-RUH";
        line.routeName = "众鸫沙特海运专线到海外仓";
        line.quoteCargoCategoryName = "普货";
        OutboundOrderLineSourceView source = new OutboundOrderLineSourceView();
        source.purchaseOrderNo = "PO-200132";
        line.sources.add(source);
        order.lines.add(line);
        OutboundOrderLineView otherChannel = new OutboundOrderLineView();
        otherChannel.id = "812501";
        otherChannel.partnerSku = "PAPERSAYSB248";
        otherChannel.productTitle = "黑色记忆棉护腕鼠标垫";
        otherChannel.targetForwarderCode = "YT";
        otherChannel.targetForwarderName = "义特物流";
        otherChannel.routeCode = "YT-SAU-SEA-FBN-RUH";
        otherChannel.routeName = "义特沙特海运双清包税";
        order.lines.add(otherChannel);
        return order;
    }

    private PackingListView packingList() {
        PackingListView packingList = new PackingListView();
        packingList.id = "830052";
        packingList.packingNo = "PK-830052";
        PackingBoxView box = new PackingBoxView();
        box.boxNo = "箱2";
        box.status = "SEALED";
        box.lengthCm = "10";
        box.widthCm = "10";
        box.heightCm = "10";
        box.grossWeightKg = "10.0";
        box.quantity = 28;
        PackingBoxItemView item = new PackingBoxItemView();
        item.outboundOrderLineId = 812500L;
        item.partnerSku = "PAPERSAYSB224";
        item.siteCode = "SA";
        item.actualTransportMode = "SEA";
        item.quantity = 28;
        box.items.add(item);
        packingList.boxes.add(box);
        PackingBoxView otherBox = new PackingBoxView();
        otherBox.boxNo = "箱12";
        otherBox.status = "SEALED";
        otherBox.lengthCm = "12";
        otherBox.widthCm = "12";
        otherBox.heightCm = "12";
        otherBox.grossWeightKg = "12.0";
        otherBox.quantity = 60;
        PackingBoxItemView otherItem = new PackingBoxItemView();
        otherItem.outboundOrderLineId = 812501L;
        otherItem.partnerSku = "PAPERSAYSB248";
        otherItem.quantity = 60;
        otherBox.items.add(otherItem);
        packingList.boxes.add(otherBox);
        return packingList;
    }
}
