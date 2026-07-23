package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class ZdWarehousePackingTemplateExporter {

    private static final String TEMPLATE =
            "warehouse-dispatch/packing-templates/zd-packing-list.xlsx";
    private static final int ITEM_ROW = 5;
    private static final int FOOTER_ROW = 6;

    private ZdWarehousePackingTemplateExporter() {}

    static byte[] export(
            ShippingBatchView batch,
            List<WarehousePackingTemplateRows.BoxRow> boxes
    ) throws IOException {
        try (XSSFWorkbook workbook = WarehousePackingTemplateSupport.load(TEMPLATE)) {
            for (int index = 1; index < boxes.size(); index++) workbook.cloneSheet(0);
            WarehousePackingTemplateImages images = new WarehousePackingTemplateImages();
            Set<String> usedNames = new HashSet<>();
            for (int index = 0; index < boxes.size(); index++) {
                XSSFSheet sheet = workbook.getSheetAt(index);
                WarehousePackingTemplateRows.BoxRow box = boxes.get(index);
                workbook.setSheetName(index, WarehousePackingTemplateSupport.sheetName(
                        "箱" + (index + 1), index + 1, usedNames
                ));
                writeBox(workbook, sheet, batch, index + 1, box, images);
            }
            return WarehousePackingTemplateSupport.bytes(workbook);
        }
    }

    private static void writeBox(
            XSSFWorkbook workbook,
            XSSFSheet sheet,
            ShippingBatchView batch,
            int exportBoxNumber,
            WarehousePackingTemplateRows.BoxRow box,
            WarehousePackingTemplateImages images
    ) {
        sheet.getRow(3).getCell(3).setCellValue(
                "shipment ID：" + WarehousePackingTemplateSupport.value(batch.batchNo)
        );
        int itemCount = Math.max(1, box.items.size());
        if (itemCount > 1) {
            sheet.shiftRows(FOOTER_ROW, sheet.getLastRowNum(), itemCount - 1, true, false);
        }
        for (int itemIndex = 1; itemIndex < itemCount; itemIndex++) {
            WarehousePackingTemplateSupport.copyRow(sheet, ITEM_ROW, ITEM_ROW + itemIndex);
        }
        for (int itemIndex = 0; itemIndex < box.items.size(); itemIndex++) {
            WarehousePackingTemplateRows.ItemRow item = box.items.get(itemIndex);
            Row row = sheet.getRow(ITEM_ROW + itemIndex);
            writeItem(row, item);
            images.add(workbook, sheet, imageUrl(item.line), ITEM_ROW + itemIndex, 0);
        }
        replaceFooter(sheet, FOOTER_ROW + itemCount - 1, footer(exportBoxNumber, box));
    }

    private static void writeItem(Row row, WarehousePackingTemplateRows.ItemRow item) {
        OutboundOrderLineView line = item.line;
        WarehousePackingTemplateSupport.text(row, 0, "");
        WarehousePackingTemplateSupport.text(row, 1, line == null ? "" : line.productTitle);
        WarehousePackingTemplateSupport.text(row, 2, "");
        WarehousePackingTemplateSupport.text(row, 3, item.item.partnerSku);
        WarehousePackingTemplateSupport.number(row, 4, item.item.quantity);
        WarehousePackingTemplateSupport.text(row, 5, "");
        WarehousePackingTemplateSupport.formula(row, 6, "E" + (row.getRowNum() + 1)
                + "*F" + (row.getRowNum() + 1));
    }

    private static String footer(int exportBoxNumber, WarehousePackingTemplateRows.BoxRow box) {
        return "箱" + exportBoxNumber
                + "：尺寸：" + WarehousePackingTemplateSupport.dimension(box.box.lengthCm)
                + "×" + WarehousePackingTemplateSupport.dimension(box.box.widthCm)
                + "×" + WarehousePackingTemplateSupport.dimension(box.box.heightCm)
                + "   重量：" + WarehousePackingTemplateSupport.weight(box.box.grossWeightKg);
    }

    private static void replaceFooter(XSSFSheet sheet, int rowIndex, String value) {
        Row source = sheet.getRow(rowIndex);
        short height = source == null ? sheet.getDefaultRowHeight() : source.getHeight();
        int cellCount = source == null ? 7 : Math.max(7, source.getLastCellNum());
        CellStyle[] styles = new CellStyle[cellCount];
        for (int index = 0; source != null && index < cellCount; index++) {
            if (source.getCell(index) != null) styles[index] = source.getCell(index).getCellStyle();
        }
        if (source != null) sheet.removeRow(source);
        Row footer = sheet.createRow(rowIndex);
        footer.setHeight(height);
        for (int index = 0; index < cellCount; index++) {
            if (styles[index] != null) footer.createCell(index).setCellStyle(styles[index]);
        }
        WarehousePackingTemplateSupport.text(footer, 0, value);
    }

    private static String imageUrl(OutboundOrderLineView line) {
        return line == null ? null : line.productImageUrl;
    }
}
