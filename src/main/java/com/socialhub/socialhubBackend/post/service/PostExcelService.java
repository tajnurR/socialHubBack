package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
            List.of("postContent", "product", "postTitle", "pageId", "productSku", "link", "imageUrl", "videoUrl", "googleDriveUrl");

    /** A parsed-but-unvalidated row (1-based {@code rowNumber} as shown in Excel). */
    public record RawRow(
            int rowNumber,
            String postContent,
            String product,
            String postTitle,
            String pageId,
            String productSku,
            String link,
            String imageUrl,
            String videoUrl,
            String googleDriveUrl) {

        public boolean isBlank() {
            return postContent.isBlank() && product.isBlank() && postTitle.isBlank()
                    && pageId.isBlank() && productSku.isBlank() && link.isBlank()
                    && imageUrl.isBlank() && videoUrl.isBlank() && googleDriveUrl.isBlank();
        }
    }

    public byte[] generateTemplate(SocialPlatform platform, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return generateCsvTemplate(platform);
        }
        return generateXlsxTemplate(platform);
    }

    private byte[] generateXlsxTemplate(SocialPlatform platform) {
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
            example.createCell(0).setCellValue(exampleMessage(platform));
            example.createCell(1).setCellValue("Pro Plan");
            example.createCell(2).setCellValue("Launch announcement");
            example.createCell(3).setCellValue("1234567890   (your " + accountLabel(platform) + " ID or name)");
            example.createCell(4).setCellValue("SKU-PRO   (optional)");
            example.createCell(5).setCellValue("https://example.com   (optional)");
            example.createCell(6).setCellValue("https://example.com/photo-1.jpg; https://example.com/photo-2.jpg   (optional)");
            example.createCell(7).setCellValue("https://example.com/video.mp4   (optional)");
            example.createCell(8).setCellValue("");
            Sheet instructions = workbook.createSheet("Instructions");
            instructions.createRow(0).createCell(0).setCellValue("Use one row per draft post. Required columns: postContent, product, postTitle, pageId.");
            instructions.createRow(1).createCell(0).setCellValue("Multiple media URLs are supported in imageUrl, videoUrl, or googleDriveUrl. Separate them with semicolons, commas, or new lines.");
            instructions.createRow(2).createCell(0).setCellValue("Public image/video URLs are imported into the Media Library. Google Drive URLs must be accessible by your connected Drive account.");
            instructions.autoSizeColumn(0);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Failed to generate the template");
        }
    }

    private byte[] generateCsvTemplate(SocialPlatform platform) {
        List<String> example = List.of(
                exampleMessage(platform),
                "Pro Plan",
                "Launch announcement",
                "1234567890",
                "SKU-PRO",
                "https://example.com",
                "https://example.com/photo-1.jpg; https://example.com/photo-2.jpg",
                "https://example.com/video.mp4",
                "");
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", COLUMNS)).append('\n');
        out.append(example.stream().map(this::csv).reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String exampleMessage(SocialPlatform platform) {
        return switch (platform) {
            case INSTAGRAM -> "New launch caption with campaign hashtags";
            case LINKEDIN -> "Professional update for our company audience";
            case X -> "Short update for X";
            default -> "Check out our new launch!";
        };
    }

    private String accountLabel(SocialPlatform platform) {
        return switch (platform) {
            case INSTAGRAM -> "Instagram account";
            case LINKEDIN -> "LinkedIn page/profile";
            case X -> "X account";
            default -> "Page/account";
        };
    }

    public List<RawRow> parse(InputStream inputStream, String originalFilename) {
        if (originalFilename != null && originalFilename.toLowerCase().endsWith(".csv")) {
            return parseCsv(inputStream);
        }
        return parseXlsx(inputStream);
    }

    private List<RawRow> parseXlsx(InputStream inputStream) {
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
            for (String required : List.of("postContent", "product", "postTitle", "pageId")) {
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
                        value(row, columnIndex, "postContent"),
                        value(row, columnIndex, "product"),
                        value(row, columnIndex, "postTitle"),
                        value(row, columnIndex, "pageId"),
                        value(row, columnIndex, "productSku"),
                        value(row, columnIndex, "link"),
                        value(row, columnIndex, "imageUrl"),
                        value(row, columnIndex, "videoUrl"),
                        value(row, columnIndex, "googleDriveUrl"));
                if (!raw.isBlank()) {
                    rows.add(raw);
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new BusinessException("Could not read the Excel file. Upload a valid .xlsx file.");
        }
    }

    private List<RawRow> parseCsv(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessException("The CSV file is empty — download the template first.");
            }
            Map<String, Integer> columnIndex = headerIndex(parseCsvLine(headerLine));
            for (String required : List.of("postContent", "product", "postTitle", "pageId")) {
                if (!columnIndex.containsKey(required)) {
                    throw new BusinessException(
                            "Missing required column '" + required + "'. Use the provided template.");
                }
            }
            List<RawRow> rows = new ArrayList<>();
            String line;
            int rowNumber = 2;
            while ((line = reader.readLine()) != null) {
                List<String> cells = parseCsvLine(line);
                RawRow raw = new RawRow(
                        rowNumber,
                        value(cells, columnIndex, "postContent"),
                        value(cells, columnIndex, "product"),
                        value(cells, columnIndex, "postTitle"),
                        value(cells, columnIndex, "pageId"),
                        value(cells, columnIndex, "productSku"),
                        value(cells, columnIndex, "link"),
                        value(cells, columnIndex, "imageUrl"),
                        value(cells, columnIndex, "videoUrl"),
                        value(cells, columnIndex, "googleDriveUrl"));
                if (!raw.isBlank()) {
                    rows.add(raw);
                }
                rowNumber++;
            }
            return rows;
        } catch (IOException ex) {
            throw new BusinessException("Could not read the CSV file.");
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

    private Map<String, Integer> headerIndex(List<String> headerCells) {
        Map<String, Integer> resolved = new HashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String name = headerCells.get(i).trim();
            if (!name.isBlank()) {
                for (String col : COLUMNS) {
                    if (col.equalsIgnoreCase(name)) {
                        resolved.put(col, i);
                    }
                }
            }
        }
        return resolved;
    }

    private String value(Row row, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        return idx == null ? "" : cellString(row.getCell(idx)).trim();
    }

    private String value(List<String> row, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        if (idx == null || idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
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
