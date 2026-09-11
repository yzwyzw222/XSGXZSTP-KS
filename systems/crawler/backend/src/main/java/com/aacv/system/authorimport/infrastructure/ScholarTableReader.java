package com.aacv.system.authorimport.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class ScholarTableReader {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ROWS = 2020;
    public static final int MAX_COLUMNS = 100;

    public record Table(List<String> sheets, List<List<String>> rows) { }

    public Table read(byte[] bytes, String fileName, int sheetIndex) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("请选择非空信息表，单文件最多 10 MB");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!name.matches(".*\\.(xlsx|xls|csv)$")) {
            throw new IllegalArgumentException("仅支持 XLSX、XLS、CSV 信息表");
        }
        try {
            boolean zipped = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
            boolean ole = bytes.length > 3 && (bytes[0] & 255) == 0xd0 && (bytes[1] & 255) == 0xcf;
            if (zipped || ole) {
                if (zipped) checkExpansion(bytes);
                return excel(bytes, sheetIndex);
            }
            if (name.endsWith("xlsx")) throw new IllegalArgumentException("文件内容不是有效的 XLSX 工作簿");
            if (sheetIndex != 0) throw new IllegalArgumentException("该文件仅包含一个工作表");
            String text = decode(bytes).replaceFirst("^\\uFEFF", "");
            if (name.endsWith("xls") && text.stripLeading().startsWith("<")) {
                return new Table(List.of("信息表"), html(text));
            }
            return new Table(List.of("信息表"), csv(text));
        } catch (IOException | org.apache.poi.EncryptedDocumentException | org.apache.poi.ooxml.POIXMLException
                | org.apache.poi.util.RecordFormatException exception) {
            throw new IllegalArgumentException("文件损坏、已加密或编码无效，请重新导出为 XLSX、XLS 或 CSV");
        }
    }

    private Table excel(byte[] bytes, int sheetIndex) throws IOException {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() > 20 || sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("工作表不存在或数量超过 20 个");
            }
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
            var sheet = workbook.getSheetAt(sheetIndex);
            if (sheet.getLastRowNum() >= MAX_ROWS) throw new IllegalArgumentException("每张表最多导入 2000 条记录");
            var formatter = new DataFormatter(Locale.ROOT);
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("每张表最多 100 列");
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        var cell = row.getCell(j);
                        if (cell != null && cell.getCellType() == CellType.FORMULA) {
                            throw new IllegalArgumentException("第 " + (i + 1) + " 行包含公式，请先粘贴为值再导入");
                        }
                        if (cell != null && cell.getCellType() == CellType.NUMERIC
                                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                            cells.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                        } else cells.add(checkedCell(formatter.formatCellValue(cell)));
                    }
                }
                rows.add(cells);
            }
            return new Table(names, rows);
        }
    }

    private List<List<String>> csv(String text) throws IOException {
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.contains("\t") ? '\t' : ',';
        if (firstLine.startsWith("sep=") && firstLine.length() == 5) {
            delimiter = firstLine.charAt(4);
            text = text.substring(text.indexOf('\n') + 1);
        }
        List<List<String>> rows = new ArrayList<>();
        try (var parser = CSVFormat.RFC4180.builder().setDelimiter(delimiter).setIgnoreEmptyLines(false)
                .get().parse(new StringReader(text))) {
            for (var record : parser) {
                if (record.size() > MAX_COLUMNS) throw new IllegalArgumentException("每张表最多 100 列");
                List<String> cells = new ArrayList<>();
                for (String value : record) cells.add(checkedCell(value));
                rows.add(cells);
                if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("每张表最多导入 2000 条记录");
            }
        } catch (java.io.UncheckedIOException exception) {
            throw new IllegalArgumentException("CSV 引号或换行格式无效，请重新导出");
        }
        return rows;
    }

    private List<List<String>> html(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        // 知网部分 XLS 实际为 HTML 表格；只读取单元格文本，不执行脚本或加载外部资源。
        new ParserDelegator().parse(new StringReader(text), new HTMLEditorKit.ParserCallback() {
            private List<String> row;
            private StringBuilder cell;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.TR) row = new ArrayList<>();
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row != null) cell = new StringBuilder();
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.BR && cell != null) cell.append('\n');
            }
            @Override public void handleText(char[] data, int position) { if (cell != null) cell.append(data); }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && cell != null && row != null) {
                    row.add(cell.toString()); cell = null;
                }
                if (tag == HTML.Tag.TR && row != null) { rows.add(row); row = null; }
            }
        }, true);
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("每张表最多导入 2000 条记录");
        for (var row : rows) {
            if (row.size() > MAX_COLUMNS) throw new IllegalArgumentException("每张表最多 100 列");
            row.replaceAll(ScholarTableReader::checkedCell);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("XLS 文件中未找到可识别的表格");
        return rows;
    }

    private static String checkedCell(String value) {
        if (value.length() > 32767) throw new IllegalArgumentException("单元格内容超过 32767 字符");
        return value.strip();
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) return strictDecode(bytes, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) return strictDecode(bytes, StandardCharsets.UTF_16BE);
        try { return strictDecode(bytes, StandardCharsets.UTF_8); }
        catch (CharacterCodingException exception) { return strictDecode(bytes, Charset.forName("GB18030")); }
    }

    private String strictDecode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private void checkExpansion(byte[] bytes) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long expanded = 0;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 500) throw new IllegalArgumentException("工作簿压缩条目过多");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 40L * 1024 * 1024) throw new IllegalArgumentException("工作簿解压后过大，请拆分文件");
                }
            }
        }
    }
}
