package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class WarehousePackingWorkbookExporter {

    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] SUMMARY_HEADERS = {
            "内部发货单", "装箱单", "箱号", "箱状态", "货代", "报价类别", "物流线路",
            "长(cm)", "宽(cm)", "高(cm)", "毛重(kg)", "商品数", "件数"
    };
    private static final String[] DETAIL_HEADERS = {
            "发货单", "内部发货单", "装箱单", "箱号", "箱状态", "货代", "报价类别", "物流线路",
            "店铺", "站点", "运输方式", "来源采购单", "PSKU", "商品名称", "数量",
            "箱长(cm)", "箱宽(cm)", "箱高(cm)", "箱毛重(kg)"
    };

    private WarehousePackingWorkbookExporter() {}

    static ExportFile export(
            ShippingBatchView batch,
            List<OutboundOrderView> orders,
            Map<String, List<PackingListView>> packingListsByOrder,
            String forwarderCode,
            String routeCode
    ) {
        WarehousePackingExportChannel channel = WarehousePackingExportChannel.resolve(
                forwarderCode, routeCode, orders, packingListsByOrder
        );
        List<WarehousePackingTemplateRows.BoxRow> templateRows = WarehousePackingTemplateRows.select(
                orders, packingListsByOrder, channel
        );
        try {
            if ("YT".equalsIgnoreCase(channel.forwarderCode)) {
                return new ExportFile(
                        exportFilename(batch, channel),
                        YiteWarehousePackingTemplateExporter.export(batch, templateRows)
                );
            }
            if ("ZD".equalsIgnoreCase(channel.forwarderCode)) {
                return new ExportFile(
                        exportFilename(batch, channel),
                        ZdWarehousePackingTemplateExporter.export(batch, templateRows)
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取货代装箱单模板失败。", exception);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeSummarySheet(workbook, styles, batch, orders, packingListsByOrder, channel);
            writeDetailSheet(workbook, styles, batch, orders, packingListsByOrder, channel);
            workbook.write(output);
            return new ExportFile(exportFilename(batch, channel), output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("生成装箱单失败。", exception);
        }
    }

    private static void writeSummarySheet(
            XSSFWorkbook workbook,
            Styles styles,
            ShippingBatchView batch,
            List<OutboundOrderView> orders,
            Map<String, List<PackingListView>> packingListsByOrder,
            WarehousePackingExportChannel channel
    ) {
        Sheet sheet = workbook.createSheet("箱子汇总");
        title(sheet, styles, batch.batchNo + " - " + channel.label() + " 装箱单", SUMMARY_HEADERS.length);
        writeMetadata(sheet, styles, batch, channel);
        header(sheet.createRow(4), styles.header, SUMMARY_HEADERS);
        int rowIndex = 5;
        for (OutboundOrderView order : orders) {
            Map<Long, OutboundOrderLineView> lineById = lineById(order);
            for (PackingListView packingList : packingListsByOrder.getOrDefault(order.id, List.of())) {
                for (PackingBoxView box : packingList.boxes) {
                    OutboundOrderLineView line = firstLine(box, lineById);
                    if (!channel.matches(line)) continue;
                    Row row = sheet.createRow(rowIndex++);
                    String[] values = {
                            order.outboundNo, packingList.packingNo, box.boxNo, box.status,
                            value(line == null ? null : line.targetForwarderName),
                            cargoCategory(line), value(line == null ? null : line.routeName),
                            box.lengthCm, box.widthCm, box.heightCm, box.grossWeightKg,
                            String.valueOf(box.items.stream().map(item -> item.partnerSku).filter(Objects::nonNull).distinct().count()),
                            String.valueOf(number(box.quantity))
                    };
                    body(row, styles.body, values);
                }
            }
        }
        widths(sheet, new int[] {18, 18, 12, 12, 20, 18, 36, 11, 11, 11, 12, 11, 11});
        sheet.createFreezePane(0, 5);
    }

    private static void writeDetailSheet(
            XSSFWorkbook workbook,
            Styles styles,
            ShippingBatchView batch,
            List<OutboundOrderView> orders,
            Map<String, List<PackingListView>> packingListsByOrder,
            WarehousePackingExportChannel channel
    ) {
        Sheet sheet = workbook.createSheet("箱内商品明细");
        header(sheet.createRow(0), styles.header, DETAIL_HEADERS);
        int rowIndex = 1;
        for (OutboundOrderView order : orders) {
            Map<Long, OutboundOrderLineView> lineById = lineById(order);
            for (PackingListView packingList : packingListsByOrder.getOrDefault(order.id, List.of())) {
                for (PackingBoxView box : packingList.boxes) {
                    if (!channel.matches(firstLine(box, lineById))) continue;
                    for (PackingBoxItemView item : box.items) {
                        OutboundOrderLineView line = lineById.get(item.outboundOrderLineId);
                        Row row = sheet.createRow(rowIndex++);
                        String[] values = {
                                batch.batchNo, order.outboundNo, packingList.packingNo, box.boxNo, box.status,
                                value(line == null ? null : line.targetForwarderName), cargoCategory(line),
                                value(line == null ? null : line.routeName), value(line == null ? null : line.storeCode),
                                value(line == null ? item.siteCode : line.siteCode),
                                value(line == null ? item.actualTransportMode : line.actualTransportMode),
                                purchaseOrders(line), value(item.partnerSku), value(line == null ? null : line.productTitle),
                                String.valueOf(number(item.quantity)), box.lengthCm, box.widthCm, box.heightCm,
                                box.grossWeightKg
                        };
                        body(row, styles.body, values);
                    }
                }
            }
        }
        widths(sheet, new int[] {24, 18, 18, 12, 12, 20, 18, 36, 18, 10, 12, 20, 20, 38, 11, 11, 11, 11, 12});
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, DETAIL_HEADERS.length - 1));
    }

    private static void writeMetadata(
            Sheet sheet,
            Styles styles,
            ShippingBatchView batch,
            WarehousePackingExportChannel channel
    ) {
        body(sheet.createRow(1), styles.body, new String[] {
                "货代", channel.forwarderName, "渠道", channel.routeName, "状态", value(batch.status)
        });
        body(sheet.createRow(2), styles.body, new String[] {
                "箱数", String.valueOf(channel.totals.boxCount), "商品数", String.valueOf(channel.totals.skuCount()),
                "件数", String.valueOf(channel.totals.quantity)
        });
        body(sheet.createRow(3), styles.body, new String[] {
                "总毛重(kg)", decimal(channel.totals.grossWeightKg),
                "总体积(m³)", decimal(channel.totals.volumeCbm.stripTrailingZeros())
        });
    }

    private static Map<Long, OutboundOrderLineView> lineById(OutboundOrderView order) {
        Map<Long, OutboundOrderLineView> result = new LinkedHashMap<>();
        for (OutboundOrderLineView line : order.lines) {
            if (line.id != null) result.put(Long.valueOf(line.id), line);
        }
        return result;
    }

    private static OutboundOrderLineView firstLine(PackingBoxView box, Map<Long, OutboundOrderLineView> lineById) {
        return box.items.isEmpty() ? null : lineById.get(box.items.get(0).outboundOrderLineId);
    }

    private static String cargoCategory(OutboundOrderLineView line) {
        if (line == null) return "";
        return value(line.quoteCargoCategoryName != null ? line.quoteCargoCategoryName : line.cargoCategoryName);
    }

    private static String purchaseOrders(OutboundOrderLineView line) {
        if (line == null) return "";
        return line.sources.stream().map(source -> source.purchaseOrderNo).filter(Objects::nonNull)
                .distinct().collect(Collectors.joining(" / "));
    }

    private static void title(Sheet sheet, Styles styles, String value, int columns) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns - 1));
    }

    private static void header(Row row, CellStyle style, String[] values) {
        body(row, style, values);
    }

    private static void body(Row row, CellStyle style, String[] values) {
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(value(values[index]));
            cell.setCellStyle(style);
        }
    }

    private static void widths(Sheet sheet, int[] widths) {
        for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
    }

    private static String exportFilename(ShippingBatchView batch, WarehousePackingExportChannel channel) {
        String batchNo = value(batch.batchNo).isEmpty() ? batch.id : batch.batchNo;
        String raw = batchNo + "-" + channel.forwarderName + "-" + channel.routeName + "-装箱单.xlsx";
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String decimal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static int number(Integer value) {
        return value == null ? 0 : value;
    }

    static final class ExportFile {
        final String filename;
        final byte[] content;

        ExportFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle body;

        Styles(XSSFWorkbook workbook) {
            title = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            header = bordered(workbook);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            body = bordered(workbook);
        }

        private static CellStyle bordered(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setWrapText(true);
            return style;
        }
    }
}
