package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class YiteWarehousePackingTemplateExporter {

    private static final String TEMPLATE =
            "warehouse-dispatch/packing-templates/yite-packing-list.xlsx";
    private static final int ITEM_ROW = 23;

    private YiteWarehousePackingTemplateExporter() {}

    static byte[] export(
            ShippingBatchView batch,
            List<WarehousePackingTemplateRows.BoxRow> boxes
    ) throws IOException {
        try (XSSFWorkbook workbook = WarehousePackingTemplateSupport.load(TEMPLATE)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            sheet.getRow(0).getCell(1).setCellValue(WarehousePackingTemplateSupport.value(batch.batchNo));
            sheet.getRow(20).getCell(1).setCellValue(WarehousePackingTemplateSupport.value(batch.batchNo));
            sheet.getRow(21).getCell(1).setCellValue(boxes.size());
            WarehousePackingTemplateImages images = new WarehousePackingTemplateImages();
            int rowIndex = ITEM_ROW;
            for (int boxIndex = 0; boxIndex < boxes.size(); boxIndex++) {
                WarehousePackingTemplateRows.BoxRow box = boxes.get(boxIndex);
                for (WarehousePackingTemplateRows.ItemRow item : box.items) {
                    Row row = rowIndex == ITEM_ROW
                            ? sheet.getRow(ITEM_ROW)
                            : WarehousePackingTemplateSupport.copyRow(sheet, ITEM_ROW, rowIndex);
                    writeItem(row, boxIndex + 1, box, item);
                    images.add(workbook, sheet, imageUrl(item.line), rowIndex, 11);
                    rowIndex += 1;
                }
            }
            return WarehousePackingTemplateSupport.bytes(workbook);
        }
    }

    private static void writeItem(
            Row row,
            int exportBoxNumber,
            WarehousePackingTemplateRows.BoxRow box,
            WarehousePackingTemplateRows.ItemRow item
    ) {
        OutboundOrderLineView line = item.line;
        WarehousePackingTemplateSupport.number(row, 0, exportBoxNumber);
        WarehousePackingTemplateSupport.decimal(row, 1, box.box.grossWeightKg);
        WarehousePackingTemplateSupport.decimal(row, 2, box.box.lengthCm);
        WarehousePackingTemplateSupport.decimal(row, 3, box.box.widthCm);
        WarehousePackingTemplateSupport.decimal(row, 4, box.box.heightCm);
        WarehousePackingTemplateSupport.text(row, 5, item.item.partnerSku);
        WarehousePackingTemplateSupport.text(row, 6, "");
        WarehousePackingTemplateSupport.text(row, 7, line == null ? "" : line.productTitle);
        WarehousePackingTemplateSupport.number(row, 8, item.item.quantity);
        for (int column = 9; column <= 14; column++) {
            WarehousePackingTemplateSupport.text(row, column, "");
        }
    }

    private static String imageUrl(OutboundOrderLineView line) {
        return line == null ? null : line.productImageUrl;
    }
}
