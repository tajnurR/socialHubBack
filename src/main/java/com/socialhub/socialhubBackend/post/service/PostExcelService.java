package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Generates the bulk-upload Excel template and parses uploaded sheets into raw
 * rows. Parsing is value-extraction only; the caller validates against the user's
 * own pages/products.
 */
@Service
public class PostExcelService {

    /** Column headers (order matters for the template; lookup on upload is by name). */
    public static final List<String> COLUMNS =
            List.of("message", "pageId", "productSku", "link", "scheduledAt");

    /** A parsed-but-unvalidated row (1-based {@code rowNumber} as shown in Excel). */
    public record RawRow(
            int rowNumber, String message, String pageId, String productSku, String link, String scheduledAt) {

        public boolean isBlank() {
            return message.isBlank() && pageId.isBlank() && productSku.isBlank()
                    && link.isBlank() && scheduledAt.isBlank();
        }
    }

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Posts");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            // Example row to show the expected shape.
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Check out our new launch! 🚀");
            example.createCell(1).setCellValue("1234567890   (your Page ID)");
            example.createCell(2).setCellValue("SKU-PRO   (optional)");
            example.createCell(3).setCellValue("https://example.com   (optional)");
            example.createCell(4).setCellValue("2026-07-01T09:00   (optional)");

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Failed to generate the template");
        }
    }

    public List<RawRow> parse(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("The uploaded file has no sheets.");
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new BusinessException("The sheet is empty — download the template first.");
            }
            Map<String, Integer> columnIndex = headerIndex(header);
            for (String required : List.of("message", "pageId")) {
                if (!columnIndex.containsKey(required)) {
                    throw new BusinessException(
                            "Missing required column '" + required + "'. Use the provided template.");
                }
            }

            List<RawRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                RawRow raw = new RawRow(
                        r + 1,
                        value(row, columnIndex, "message"),
                        value(row, columnIndex, "pageId"),
                        value(row, columnIndex, "productSku"),
                        value(row, columnIndex, "link"),
                        value(row, columnIndex, "scheduledAt"));
                if (!raw.isBlank()) {
                    rows.add(raw);
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new BusinessException("Could not read the Excel file. Upload a valid .xlsx file.");
        }
    }

    private Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> index = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String name = cellString(header.getCell(c)).trim();
            if (!name.isBlank()) {
                index.put(name.toLowerCase(), c);
            }
        }
        // Map our canonical names (case-insensitive) to the found columns.
        Map<String, Integer> resolved = new HashMap<>();
        for (String col : COLUMNS) {
            Integer idx = index.get(col.toLowerCase());
            if (idx != null) {
                resolved.put(col, idx);
            }
        }
        return resolved;
    }

    private String value(Row row, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        return idx == null ? "" : cellString(row.getCell(idx)).trim();
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) && !Double.isInfinite(d)
                        ? BigDecimal.valueOf(d).toBigInteger().toString()
                        : BigDecimal.valueOf(d).toPlainString();
            }
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
