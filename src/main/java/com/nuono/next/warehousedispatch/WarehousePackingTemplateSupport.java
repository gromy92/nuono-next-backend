package com.nuono.next.warehousedispatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class WarehousePackingTemplateSupport {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern BOX_NUMBER = Pattern.compile("\\d+");

    private WarehousePackingTemplateSupport() {}

    static XSSFWorkbook load(String resourcePath) throws IOException {
        InputStream input = WarehousePackingTemplateSupport.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (input == null) throw new IllegalStateException("装箱单模板不存在：" + resourcePath);
        try (InputStream stream = input) {
            return new XSSFWorkbook(stream);
        }
    }

    static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }

    static Row copyRow(Sheet sheet, int sourceIndex, int targetIndex) {
        Row source = sheet.getRow(sourceIndex);
        Row target = sheet.getRow(targetIndex);
        if (target == null) target = sheet.createRow(targetIndex);
        target.setHeight(source.getHeight());
        for (int index = 0; index < source.getLastCellNum(); index++) {
            Cell sourceCell = source.getCell(index);
            if (sourceCell == null) continue;
            Cell targetCell = target.getCell(index);
            if (targetCell == null) targetCell = target.createCell(index);
            targetCell.setCellStyle(sourceCell.getCellStyle());
            targetCell.setCellType(CellType.BLANK);
        }
        return target;
    }

    static void text(Row row, int column, String value) {
        Cell cell = cell(row, column);
        cell.setCellValue(value == null ? "" : value);
    }

    static void number(Row row, int column, Integer value) {
        Cell cell = cell(row, column);
        if (value == null) cell.setBlank(); else cell.setCellValue(value);
    }

    static void decimal(Row row, int column, String value) {
        Cell cell = cell(row, column);
        BigDecimal number = decimal(value);
        if (number == null) cell.setBlank(); else cell.setCellValue(number.doubleValue());
    }

    static void boxNumber(Row row, int column, String boxNo) {
        String value = boxNumber(boxNo);
        try {
            cell(row, column).setCellValue(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            text(row, column, value);
        }
    }

    static void formula(Row row, int column, String formula) {
        cell(row, column).setCellFormula(formula);
    }

    static String boxNumber(String boxNo) {
        if (boxNo == null) return "";
        Matcher matcher = BOX_NUMBER.matcher(boxNo);
        return matcher.find() ? matcher.group() : boxNo.trim();
    }

    static String sheetName(String preferred, int fallback, Set<String> usedNames) {
        String base = WorkbookUtil.createSafeSheetName(
                preferred == null || preferred.isBlank() ? "箱" + fallback : preferred.trim()
        );
        if (base.length() > 31) base = base.substring(0, 31);
        String candidate = base;
        int suffix = 2;
        while (usedNames.contains(candidate)) {
            String tail = "-" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
        }
        usedNames.add(candidate);
        return candidate;
    }

    static String value(String value) {
        return value == null ? "" : value;
    }

    static String dimension(String value) {
        return rounded(value, 0);
    }

    static String weight(String value) {
        return rounded(value, 1);
    }

    private static Cell cell(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? row.createCell(column) : cell;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = NUMBER.matcher(value.trim());
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String rounded(String value, int scale) {
        BigDecimal number = decimal(value);
        return number == null ? "-" : number.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}
