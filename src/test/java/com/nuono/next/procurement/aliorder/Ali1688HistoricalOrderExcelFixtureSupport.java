package com.nuono.next.procurement.aliorder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class Ali1688HistoricalOrderExcelFixtureSupport {

    private Ali1688HistoricalOrderExcelFixtureSupport() {
    }

    static byte[] sanitizedWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单列表");
            List<String> headers = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            }

            Row firstOrder = sheet.createRow(1);
            put(headers, firstOrder, "订单编号", "ALI-SAFE-20260525-001");
            put(headers, firstOrder, "买家公司名", "脱敏买家公司 A");
            put(headers, firstOrder, "买家会员名", "safe-buyer-a");
            put(headers, firstOrder, "卖家公司名", "义乌脱敏源头工厂");
            put(headers, firstOrder, "卖家会员名", "safe-seller-a");
            put(headers, firstOrder, "货品总价(元)", "128.00");
            put(headers, firstOrder, "运费(元)", "0.00");
            put(headers, firstOrder, "涨价或折扣(元)", "0.00");
            put(headers, firstOrder, "实付款(元)", "128.00");
            put(headers, firstOrder, "订单状态", "交易成功");
            put(headers, firstOrder, "订单创建时间", "2026-05-25 10:30:00");
            put(headers, firstOrder, "订单付款时间", "2026-05-25 10:35:00");
            put(headers, firstOrder, "发货方", "义乌脱敏仓");
            put(headers, firstOrder, "收货人姓名", "测试收货人");
            put(headers, firstOrder, "收货地址", "浙江省杭州市脱敏地址 1 号");
            put(headers, firstOrder, "邮编", "310000");
            put(headers, firstOrder, "联系电话", "057100000000");
            put(headers, firstOrder, "联系手机", "13800000000");
            put(headers, firstOrder, "货品标题", "脱敏仿真花束 6 支装");
            put(headers, firstOrder, "单价(元)", "12.80");
            put(headers, firstOrder, "数量", "10");
            put(headers, firstOrder, "单位", "套");
            put(headers, firstOrder, "货号", "SAFE-FLOWER");
            put(headers, firstOrder, "型号", "红色");
            put(headers, firstOrder, "Offer ID", "745600000001");
            put(headers, firstOrder, "SKU ID", "SKU-SAFE-RED");
            put(headers, firstOrder, "物料编号", "MAT-SAFE-001");
            put(headers, firstOrder, "单品货号", "SINGLE-SAFE-001");
            put(headers, firstOrder, "货品种类", "家居装饰");
            put(headers, firstOrder, "买家留言", "脱敏留言");
            put(headers, firstOrder, "物流公司", "中通快递(ZTO)");
            put(headers, firstOrder, "运单号", "ZTO000000001");
            put(headers, firstOrder, "发票：购货单位名称", "脱敏发票抬头");
            put(headers, firstOrder, "发票：纳税人识别号", "TAX-SAFE-001");
            put(headers, firstOrder, "发票：地址、电话", "脱敏发票地址");
            put(headers, firstOrder, "发票：开户行及账号", "脱敏银行账号");
            put(headers, firstOrder, "发票收取地址", "脱敏收票地址");
            put(headers, firstOrder, "关联编号", "REL-SAFE-001");
            put(headers, firstOrder, "代理商姓名", "脱敏代理");
            put(headers, firstOrder, "代理商联系方式", "13900000000");
            put(headers, firstOrder, "是否代发订单", "否");
            put(headers, firstOrder, "下单批次号", "BATCH-SAFE-001");
            put(headers, firstOrder, "下游渠道", "AE");
            put(headers, firstOrder, "下游订单号", "DOWNSTREAM-SAFE-001");
            put(headers, firstOrder, "下单公司主体", "脱敏主体");
            put(headers, firstOrder, "发起人登录名", "safe-initiator");
            put(headers, firstOrder, "是否发起免密支付(1:淘货源诚e赊免密支付2:批量下单免密支付)", "否");

            Row continuation = sheet.createRow(2);
            put(headers, continuation, "货品标题", "脱敏复古锁心本");
            put(headers, continuation, "单价(元)", "20.80");
            put(headers, continuation, "数量", "5");
            put(headers, continuation, "单位", "件");
            put(headers, continuation, "货号", "SAFE-NOTEBOOK");
            put(headers, continuation, "型号", "B6 粉色");
            put(headers, continuation, "Offer ID", "745600000002");
            put(headers, continuation, "SKU ID", "SKU-SAFE-PINK");
            put(headers, continuation, "单品货号", "SINGLE-SAFE-002");

            Row secondOrder = sheet.createRow(3);
            put(headers, secondOrder, "订单编号", "ALI-SAFE-20260525-002");
            put(headers, secondOrder, "买家公司名", "脱敏买家公司 A");
            put(headers, secondOrder, "买家会员名", "safe-buyer-a");
            put(headers, secondOrder, "卖家公司名", "深圳脱敏工厂");
            put(headers, secondOrder, "卖家会员名", "safe-seller-b");
            put(headers, secondOrder, "实付款(元)", "36.00");
            put(headers, secondOrder, "订单状态", "等待买家收货");
            put(headers, secondOrder, "订单创建时间", "2026-05-25 11:00:00");
            put(headers, secondOrder, "货品标题", "脱敏标签贴纸");
            put(headers, secondOrder, "单价(元)", "3.60");
            put(headers, secondOrder, "数量", "10");
            put(headers, secondOrder, "单位", "包");
            put(headers, secondOrder, "货号", "SAFE-LABEL");
            put(headers, secondOrder, "型号", "白色");
            put(headers, secondOrder, "Offer ID", "745600000003");
            put(headers, secondOrder, "SKU ID", "SKU-SAFE-WHITE");
            put(headers, secondOrder, "物流公司", "圆通速递(YTO)");
            put(headers, secondOrder, "运单号", "YTO000000002");
            put(headers, secondOrder, "下游渠道", "AE");
            put(headers, secondOrder, "下游订单号", "DOWNSTREAM-SAFE-002");
            put(headers, secondOrder, "发起人登录名", "safe-initiator");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    static byte[] workbookWithFalseWorksheetDimension() throws IOException {
        byte[] workbookBytes = sanitizedWorkbook();
        try (
                ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(workbookBytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zipOut = new ZipOutputStream(out)
        ) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                zipOut.putNextEntry(new ZipEntry(entry.getName()));
                byte[] bytes = in.readAllBytes();
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    String xml = new String(bytes, StandardCharsets.UTF_8)
                            .replaceFirst("<dimension ref=\"[^\"]+\"/>", "<dimension ref=\"A1\"/>");
                    bytes = xml.getBytes(StandardCharsets.UTF_8);
                }
                zipOut.write(bytes);
                zipOut.closeEntry();
            }
            zipOut.finish();
            return out.toByteArray();
        }
    }

    static byte[] workbookWithShiftedHeaders() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单列表");
            List<String> headers = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("非1688模板");
            for (int columnIndex = 0; columnIndex < headers.size() - 1; columnIndex++) {
                header.createCell(columnIndex + 1).setCellValue(headers.get(columnIndex));
            }
            Row data = sheet.createRow(1);
            put(headers, data, "收货地址", "浙江省杭州市脱敏地址不应出现在错误消息里");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static byte[] workbookWithOrphanAndInvalidRows() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单列表");
            List<String> headers = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
            writeHeader(sheet, headers);

            Row orphan = sheet.createRow(1);
            put(headers, orphan, "货品标题", "脱敏孤儿续行货品");
            put(headers, orphan, "Offer ID", "745600009001");

            Row invalid = sheet.createRow(2);
            put(headers, invalid, "订单编号", "ALI-SAFE-INVALID-001");
            put(headers, invalid, "买家公司名", "脱敏买家公司 A");
            put(headers, invalid, "卖家公司名", "脱敏工厂");
            put(headers, invalid, "订单创建时间", "不是时间");
            put(headers, invalid, "订单付款时间", "bad-paid-at");
            put(headers, invalid, "实付款(元)", "not-money");
            put(headers, invalid, "货品标题", "脱敏错误格式货品");
            put(headers, invalid, "单价(元)", "abc");
            put(headers, invalid, "数量", "many");
            put(headers, invalid, "单位", "件");
            put(headers, invalid, "Offer ID", "745600009002");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    static byte[] workbookWithManyInvalidRows(int rowCount) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单列表");
            List<String> headers = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
            writeHeader(sheet, headers);
            for (int rowIndex = 1; rowIndex <= rowCount; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                put(headers, row, "订单编号", "ALI-SAFE-BAD-" + rowIndex);
                put(headers, row, "订单创建时间", "bad-date-" + rowIndex);
                put(headers, row, "货品标题", "脱敏错误货品 " + rowIndex);
                put(headers, row, "单价(元)", "bad-money-" + rowIndex);
                put(headers, row, "数量", "bad-quantity-" + rowIndex);
                put(headers, row, "Offer ID", "745600099" + rowIndex);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static byte[] workbookWithNoImportableProductRows() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单列表");
            List<String> headers = Ali1688HistoricalOrderExcelHeaderContract.expectedHeaders();
            writeHeader(sheet, headers);
            Row row = sheet.createRow(1);
            put(headers, row, "订单编号", "ALI-SAFE-NO-PRODUCT-001");
            put(headers, row, "买家公司名", "脱敏买家公司 A");
            put(headers, row, "卖家公司名", "脱敏工厂");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void writeHeader(Sheet sheet, List<String> headers) {
        Row header = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            header.createCell(columnIndex).setCellValue(headers.get(columnIndex));
        }
    }

    private static void put(List<String> headers, Row row, String header, String value) {
        int columnIndex = headers.indexOf(header);
        if (columnIndex < 0) {
            throw new IllegalArgumentException("Unknown header: " + header);
        }
        row.createCell(columnIndex).setCellValue(value);
    }
}
