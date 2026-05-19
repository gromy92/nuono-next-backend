package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseQueryViewService {

    static final String OVERVIEW_EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int EXPORT_PAGE_SIZE = 1000;
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\r\\n]+");

    private final FileManagementParseMapper fileManagementParseMapper;
    private final FileParseResultItemViewAssembler itemViewAssembler;

    public FileParseQueryViewService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseResultItemViewAssembler itemViewAssembler
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.itemViewAssembler = itemViewAssembler;
    }

    public FileParseOverviewItemsView listOverviewItems(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        if (task.getCurrentResultId() == null) {
            throw new IllegalArgumentException("解析文档尚未生成解析结果。");
        }

        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 1000);
        int offset = (page - 1) * pageSize;
        int total = fileManagementParseMapper.countOverviewResultItems(task.getCurrentResultId());
        List<FileParseOverviewItemView> items = fileManagementParseMapper
                .selectOverviewResultItems(task.getCurrentResultId(), pageSize, offset)
                .stream()
                .map(this::toOverviewItemView)
                .collect(Collectors.toList());

        FileParseOverviewItemsView view = new FileParseOverviewItemsView();
        view.setTaskId(task.getId());
        view.setResultId(task.getCurrentResultId());
        view.setTotal(total);
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setColumns(itemViewAssembler.buildColumns(itemStandards));
        view.setItems(items);
        return view;
    }

    public FileParseExportFile exportOverviewItems(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards
    ) {
        if (task.getCurrentResultId() == null) {
            throw new IllegalArgumentException("解析文档尚未生成解析结果。");
        }

        int total = fileManagementParseMapper.countOverviewResultItems(task.getCurrentResultId());
        List<FileParseOverviewItemView> items = new ArrayList<>();
        for (int offset = 0; offset < total; offset += EXPORT_PAGE_SIZE) {
            fileManagementParseMapper
                    .selectOverviewResultItems(task.getCurrentResultId(), EXPORT_PAGE_SIZE, offset)
                    .stream()
                    .map(this::toOverviewItemView)
                    .forEach(items::add);
        }

        List<FileParseProcessingColumnView> columns = buildExportColumns(itemStandards, items);
        byte[] content = buildOverviewWorkbook(columns, items);
        return new FileParseExportFile(buildOverviewFileName(task), OVERVIEW_EXPORT_CONTENT_TYPE, content);
    }

    public FileParseVersionListView listVersions(
            Long targetPlanId,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 100);
        int offset = (page - 1) * pageSize;
        int total = fileManagementParseMapper.countVersionsByTargetPlan(targetPlanId);
        List<FileParseVersionSummaryView> versions = fileManagementParseMapper
                .selectVersionsByTargetPlan(targetPlanId, pageSize, offset)
                .stream()
                .map(this::toVersionSummaryView)
                .collect(Collectors.toList());

        FileParseVersionListView view = new FileParseVersionListView();
        view.setTargetPlanId(targetPlanId);
        view.setTotal(total);
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setItems(versions);
        return view;
    }

    public FileParseVersionItemsView listVersionItems(
            FileParseVersionSummaryRow version,
            List<FileParseItemStandardRow> itemStandards,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 1000);
        int offset = (page - 1) * pageSize;
        int total = fileManagementParseMapper.countVersionSnapshotItems(version.getId());
        List<FileParseVersionItemView> items = fileManagementParseMapper
                .selectVersionSnapshotItems(version.getId(), pageSize, offset)
                .stream()
                .map(this::toVersionItemView)
                .collect(Collectors.toList());

        FileParseVersionItemsView view = new FileParseVersionItemsView();
        view.setVersionId(version.getId());
        view.setVersionNo(version.getVersionNo());
        view.setTargetPlanId(version.getTargetPlanId());
        view.setTotal(total);
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setColumns(itemViewAssembler.buildColumns(itemStandards));
        view.setItems(items);
        return view;
    }

    private List<FileParseProcessingColumnView> buildExportColumns(
            List<FileParseItemStandardRow> itemStandards,
            List<FileParseOverviewItemView> items
    ) {
        List<FileParseProcessingColumnView> configuredColumns = itemViewAssembler.buildColumns(itemStandards);
        if (!configuredColumns.isEmpty()) {
            return configuredColumns;
        }

        Map<String, FileParseProcessingColumnView> columnsByKey = new LinkedHashMap<>();
        for (FileParseOverviewItemView item : items) {
            if (item.getFields() == null) {
                continue;
            }
            for (String key : item.getFields().keySet()) {
                if (!StringUtils.hasText(key) || columnsByKey.containsKey(key)) {
                    continue;
                }
                FileParseProcessingColumnView column = new FileParseProcessingColumnView();
                column.setKey(key);
                column.setLabel(key);
                column.setType("string");
                column.setTableVisible(true);
                column.setWidth(160);
                columnsByKey.put(key, column);
            }
        }
        return new ArrayList<>(columnsByKey.values());
    }

    private byte[] buildOverviewWorkbook(
            List<FileParseProcessingColumnView> columns,
            List<FileParseOverviewItemView> items
    ) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("解析总览");
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);
            Row headerRow = sheet.createRow(0);
            writeCell(headerRow, 0, "结果类型", headerStyle);
            writeCell(headerRow, 1, "自然键", headerStyle);
            for (int index = 0; index < columns.size(); index++) {
                writeCell(headerRow, index + 2, columns.get(index).getLabel(), headerStyle);
            }

            for (int rowIndex = 0; rowIndex < items.size(); rowIndex++) {
                FileParseOverviewItemView item = items.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                writeCell(row, 0, item.getItemType(), bodyStyle);
                writeCell(row, 1, item.getNaturalKey(), bodyStyle);
                Map<String, Object> fields = item.getFields() == null ? Map.of() : item.getFields();
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = fields.get(columns.get(columnIndex).getKey());
                    writeValue(row, columnIndex + 2, value, bodyStyle);
                }
            }

            sheet.createFreezePane(0, 1);
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 42 * 256);
            for (int index = 0; index < columns.size(); index++) {
                sheet.setColumnWidth(index + 2, 24 * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("解析总览导出失败。", error);
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorder(style);
        return style;
    }

    private CellStyle buildBodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        applyBorder(style);
        style.setWrapText(true);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private void writeCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeValue(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Map || value instanceof List) {
            cell.setCellValue(itemViewAssembler.writeJson(value));
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private String buildOverviewFileName(FileParseTaskRow task) {
        String title = StringUtils.hasText(task.getDocumentTitle())
                ? task.getDocumentTitle().trim()
                : "解析文档-" + task.getId();
        String safeTitle = UNSAFE_FILE_NAME_CHARS.matcher(title).replaceAll("_").trim();
        if (safeTitle.length() > 120) {
            safeTitle = safeTitle.substring(0, 120).trim();
        }
        if (!StringUtils.hasText(safeTitle)) {
            safeTitle = "解析文档-" + task.getId();
        }
        return safeTitle + "-解析总览.xlsx";
    }

    private FileParseOverviewItemView toOverviewItemView(FileParseResultItemRow row) {
        FileParseOverviewItemView view = new FileParseOverviewItemView();
        view.setItemId(row.getId());
        view.setTaskId(row.getTaskId());
        view.setResultId(row.getResultId());
        view.setItemType(row.getItemType());
        view.setNaturalKey(row.getNaturalKey());
        view.setFields(itemViewAssembler.currentPayload(row));
        view.setSortNo(row.getSortNo());
        return view;
    }

    private FileParseVersionSummaryView toVersionSummaryView(FileParseVersionSummaryRow row) {
        FileParseVersionSummaryView view = new FileParseVersionSummaryView();
        view.setVersionId(row.getId());
        view.setVersionNo(row.getVersionNo());
        view.setTargetPlanId(row.getTargetPlanId());
        view.setSourceTaskId(row.getSourceTaskId());
        view.setSourceResultId(row.getSourceResultId());
        view.setStandardVersionId(row.getStandardVersionId());
        view.setBaseVersionId(row.getBaseVersionId());
        view.setDataScopeType(row.getDataScopeType());
        view.setDataScopeKey(row.getDataScopeKey());
        view.setStatus(row.getVersionStatus());
        view.setPublishedAt(row.getPublishedAt());
        view.setPublishedBy(row.getPublishedBy());
        view.setSummary(itemViewAssembler.readMap(row.getSummaryJson()));
        return view;
    }

    private FileParseVersionItemView toVersionItemView(FileParseVersionItemRow row) {
        FileParseVersionItemView view = new FileParseVersionItemView();
        view.setVersionItemId(row.getId());
        view.setVersionId(row.getVersionId());
        view.setItemType(row.getItemType());
        view.setNaturalKey(row.getNaturalKey());
        view.setFields(itemViewAssembler.readMap(row.getVersionPayloadJson()));
        view.setSourceResultItemId(row.getSourceResultItemId());
        view.setSortNo(row.getSortNo());
        return view;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize, int defaultPageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        return Math.min(pageSize, 1000);
    }
}
