package com.billing.simple.billsoft.util;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility for parsing CSV and Excel (.xlsx) files and generating templates for RupeeCRM bulk product import.
 * Zero external dependencies — uses standard Java SE XML streaming and ZIP archive capabilities.
 */
public class ProductBulkImportUtils {

    // Magic bytes
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public static boolean isZipOrXlsx(byte[] bytes, String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
            return true;
        }
        if (bytes != null && bytes.length >= 4) {
            return bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1]
                    && bytes[2] == ZIP_MAGIC[2] && bytes[3] == ZIP_MAGIC[3];
        }
        return false;
    }

    public static boolean isLegacyXls(byte[] bytes, String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return true;
        }
        if (bytes != null && bytes.length >= 4) {
            return bytes[0] == OLE2_MAGIC[0] && bytes[1] == OLE2_MAGIC[1]
                    && bytes[2] == OLE2_MAGIC[2] && bytes[3] == OLE2_MAGIC[3];
        }
        return false;
    }

    /**
     * Strips UTF-8 BOM if present and converts byte array to String.
     */
    public static String cleanUtf8String(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            offset = 3;
        }
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    /**
     * Robust RFC 4180 CSV parser with auto-detected delimiter (comma, semicolon, tab).
     * Preserves leading zeros, handles newlines inside quoted fields, and escaped quotes ("").
     */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return rows;
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        char delimiter = detectDelimiter(text);

        List<String> currentRow = new ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        curVal.append('"');
                        i++; // Skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    curVal.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == delimiter || (delimiter == ',' && c == '\t')) {
                    currentRow.add(curVal.toString().trim());
                    curVal.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < chars.length && chars[i + 1] == '\n') {
                        i++;
                    }
                    currentRow.add(curVal.toString().trim());
                    curVal.setLength(0);
                    if (!isRowEmpty(currentRow)) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else if (c == '\n') {
                    currentRow.add(curVal.toString().trim());
                    curVal.setLength(0);
                    if (!isRowEmpty(currentRow)) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else {
                    curVal.append(c);
                }
            }
        }

        if (curVal.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(curVal.toString().trim());
            if (!isRowEmpty(currentRow)) {
                rows.add(currentRow);
            }
        }

        return rows;
    }

    private static char detectDelimiter(String text) {
        String firstLine = text.split("\r?\n", 2)[0];
        int commaCount = 0;
        int semiCount = 0;
        int tabCount = 0;
        boolean inQuotes = false;
        for (char c : firstLine.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == ',') commaCount++;
                else if (c == ';') semiCount++;
                else if (c == '\t') tabCount++;
            }
        }
        if (tabCount > commaCount && tabCount > semiCount) return '\t';
        if (semiCount > commaCount) return ';';
        return ',';
    }

    private static boolean isRowEmpty(List<String> row) {
        if (row == null || row.isEmpty()) return true;
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses an OpenXML Excel (.xlsx) file into a list of rows without any external dependencies.
     */
    public static List<List<String>> parseXlsx(byte[] bytes) throws Exception {
        return parseXlsx(new ByteArrayInputStream(bytes));
    }

    public static List<List<String>> parseXlsx(InputStream inputStream) throws Exception {
        Map<String, byte[]> zipEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    zipEntries.put(entry.getName(), baos.toByteArray());
                }
                zis.closeEntry();
            }
        }

        // 1. Read shared strings if present
        List<String> sharedStrings = new ArrayList<>();
        byte[] sharedStringsBytes = zipEntries.get("xl/sharedStrings.xml");
        if (sharedStringsBytes != null) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            // Protect against XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(sharedStringsBytes));

            StringBuilder currentSiText = null;
            boolean insideText = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("si".equals(localName)) {
                        currentSiText = new StringBuilder();
                    } else if ("t".equals(localName)) {
                        insideText = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    if (insideText && currentSiText != null) {
                        currentSiText.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("t".equals(localName)) {
                        insideText = false;
                    } else if ("si".equals(localName)) {
                        if (currentSiText != null) {
                            sharedStrings.add(currentSiText.toString());
                            currentSiText = null;
                        }
                    }
                }
            }
            reader.close();
        }

        // 2. Find primary worksheet (sheet1.xml or first matching sheet*.xml)
        String sheetPath = "xl/worksheets/sheet1.xml";
        if (!zipEntries.containsKey(sheetPath)) {
            for (String key : zipEntries.keySet()) {
                if (key.startsWith("xl/worksheets/sheet") && key.endsWith(".xml")) {
                    sheetPath = key;
                    break;
                }
            }
        }

        byte[] sheetBytes = zipEntries.get(sheetPath);
        if (sheetBytes == null) {
            throw new IllegalArgumentException("No valid worksheet found in the Excel workbook");
        }

        List<List<String>> resultRows = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(sheetBytes));

        Map<Integer, String> currentRowCells = null;
        int maxColIdx = -1;
        String currentCellRef = null;
        String currentCellType = null;
        StringBuilder currentVal = null;
        boolean insideValue = false;
        boolean insideInlineStr = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("row".equals(localName)) {
                    currentRowCells = new HashMap<>();
                    maxColIdx = -1;
                } else if ("c".equals(localName)) {
                    currentCellRef = reader.getAttributeValue(null, "r");
                    currentCellType = reader.getAttributeValue(null, "t");
                    currentVal = new StringBuilder();
                } else if ("v".equals(localName)) {
                    insideValue = true;
                } else if ("t".equals(localName) && "inlineStr".equals(currentCellType)) {
                    insideInlineStr = true;
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                if ((insideValue || insideInlineStr) && currentVal != null) {
                    currentVal.append(reader.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String localName = reader.getLocalName();
                if ("v".equals(localName)) {
                    insideValue = false;
                } else if ("t".equals(localName)) {
                    insideInlineStr = false;
                } else if ("c".equals(localName)) {
                    if (currentRowCells != null && currentCellRef != null) {
                        int colIdx = getColumnIndex(currentCellRef);
                        String val = currentVal != null ? currentVal.toString() : "";
                        if ("s".equals(currentCellType)) {
                            try {
                                int sIdx = Integer.parseInt(val.trim());
                                if (sIdx >= 0 && sIdx < sharedStrings.size()) {
                                    val = sharedStrings.get(sIdx);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        currentRowCells.put(colIdx, val);
                        if (colIdx > maxColIdx) {
                            maxColIdx = colIdx;
                        }
                    }
                    currentCellRef = null;
                    currentCellType = null;
                    currentVal = null;
                } else if ("row".equals(localName)) {
                    if (currentRowCells != null && maxColIdx >= 0) {
                        List<String> rowList = new ArrayList<>(maxColIdx + 1);
                        for (int c = 0; c <= maxColIdx; c++) {
                            rowList.add(currentRowCells.getOrDefault(c, ""));
                        }
                        if (!isRowEmpty(rowList)) {
                            resultRows.add(rowList);
                        }
                    }
                    currentRowCells = null;
                }
            }
        }
        reader.close();
        return resultRows;
    }

    private static int getColumnIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isLetter(c)) {
                col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    /**
     * Generates a valid Microsoft Excel OpenXML (.xlsx) template with headers and sample rows.
     */
    public static byte[] generateXlsxTemplate() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // 1. [Content_Types].xml
                addZipEntry(zos, "[Content_Types].xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                                "  <Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>\n" +
                                "</Types>");

                // 2. _rels/.rels
                addZipEntry(zos, "_rels/.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                                "</Relationships>");

                // 3. xl/_rels/workbook.xml.rels
                addZipEntry(zos, "xl/_rels/workbook.xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>\n" +
                                "</Relationships>");

                // 4. xl/workbook.xml
                addZipEntry(zos, "xl/workbook.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                                "  <sheets>\n" +
                                "    <sheet name=\"Products\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                                "  </sheets>\n" +
                                "</workbook>");

                // 5. Shared strings
                List<String> strings = Arrays.asList(
                        // Headers
                        "Product Name", "HSN/SAC Code", "SKU", "Barcode", "Category", "Item Type",
                        "Selling Price", "Cost Price", "GST %", "Initial Stock", "Min Stock", "Unit", "Description",
                        // Sample Row 1
                        "Sample Product A", "84713010", "SKU-PROD-001", "8901234567890", "Electronics", "GOODS",
                        "25000.00", "20000.00", "18", "10", "2", "pcs", "Sample Goods Item",
                        // Sample Row 2
                        "Consulting Service", "998311", "SKU-SRV-002", "", "Services", "SERVICE",
                        "5000.00", "0.00", "18", "0", "0", "hours", "Professional Services"
                );

                StringBuilder sst = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"" + strings.size() + "\" uniqueCount=\"" + strings.size() + "\">\n");
                for (String s : strings) {
                    sst.append("  <si><t>").append(escapeXml(s)).append("</t></si>\n");
                }
                sst.append("</sst>");
                addZipEntry(zos, "xl/sharedStrings.xml", sst.toString());

                // 6. xl/worksheets/sheet1.xml
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                        "  <sheetData>\n");

                // Header row (row 1, strings 0..12)
                sheet.append("    <row r=\"1\">\n");
                for (int i = 0; i < 13; i++) {
                    String colRef = getColLetter(i) + "1";
                    sheet.append("      <c r=\"").append(colRef).append("\" t=\"s\"><v>").append(i).append("</v></c>\n");
                }
                sheet.append("    </row>\n");

                // Sample row 1 (row 2, strings 13..25)
                sheet.append("    <row r=\"2\">\n");
                for (int i = 0; i < 13; i++) {
                    String colRef = getColLetter(i) + "2";
                    sheet.append("      <c r=\"").append(colRef).append("\" t=\"s\"><v>").append(13 + i).append("</v></c>\n");
                }
                sheet.append("    </row>\n");

                // Sample row 2 (row 3, strings 26..38)
                sheet.append("    <row r=\"3\">\n");
                for (int i = 0; i < 13; i++) {
                    String colRef = getColLetter(i) + "3";
                    sheet.append("      <c r=\"").append(colRef).append("\" t=\"s\"><v>").append(26 + i).append("</v></c>\n");
                }
                sheet.append("    </row>\n");

                sheet.append("  </sheetData>\n</worksheet>");
                addZipEntry(zos, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate XLSX template: " + e.getMessage(), e);
        }
    }

    private static String getColLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        int temp = colIndex + 1;
        while (temp > 0) {
            int rem = (temp - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            temp = (temp - 1) / 26;
        }
        return sb.toString();
    }

    private static void addZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

