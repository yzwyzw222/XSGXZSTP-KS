package com.aacv.system.authorimport;

import static org.junit.jupiter.api.Assertions.*;

import com.aacv.system.authorimport.application.ScholarImportParser;
import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.infrastructure.ScholarTableReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScholarImportParserTests {
    private final ScholarImportParser parser = new ScholarImportParser(new ScholarTableReader(), JsonMapper.builder().build());
    static final String HEADER = "SrcDatabase-来源库,Title-题名,Author-作者,Organ-单位,Source-文献来源,Keyword-关键词,Summary-摘要,PubTime-发表时间,FirstDuty-第一责任人,Fund-基金,Year-年,Volume-卷,Period-期,PageCount-页码,CLC-中图分类号,ISSN-国际标准刊号,URL-网址,DOI-DOI";
    static ImportOptions options(String mode) { return new ImportOptions("张三", "测试大学", null, 0, 1, mode, Map.of()); }

    @Test
    void parsesExactCnkiHeadersAndPreservesAllEighteenFields() {
        String csv = HEADER + "\n期刊,知识图谱研究,张三;李四,测试大学,测试期刊,知识图谱;数据治理,\"含逗号,引号\"\"和\n多行摘要\",2024-3-2,张三,科研基金,2024,12,3,21-30,TP391,1234-5678,https://kns.cnki.net/example,https://doi.org/10.1234/ABC";
        var parsed = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "学者.csv", options("AUTHOR"));
        assertEquals(1, parsed.preview().validRows());
        var row = parsed.rows().getFirst();
        assertEquals(18, row.original().size());
        assertEquals("科研基金", row.original().get("Fund-基金"));
        assertEquals("21-30", row.original().get("PageCount-页码"));
        assertEquals("10.1234/abc", row.doi());
        assertEquals("article", row.type());
        assertEquals(LocalDate.of(2024, 3, 2), row.publicationDate());
        assertEquals("含逗号,引号\"和\n多行摘要", row.abstractText());
        assertEquals(java.util.List.of("张三", "李四"), row.authors());
    }

    @Test
    void readsXlsxAndBinaryXlsWithSheetSelectionAndActualExcelDates() throws Exception {
        for (Workbook book : new Workbook[] {new XSSFWorkbook(), new HSSFWorkbook()}) {
            try (book; var output = new ByteArrayOutputStream()) {
                book.createSheet("说明");
                var sheet = book.createSheet("学者论文");
                sheet.createRow(0).createCell(0).setCellValue("说明行");
                var header = sheet.createRow(1);
                header.createCell(0).setCellValue("Title-题名"); header.createCell(1).setCellValue("Author-作者"); header.createCell(2).setCellValue("PubTime-发表时间");
                var row = sheet.createRow(2);
                row.createCell(0).setCellValue("中文论文"); row.createCell(1).setCellValue("张三");
                row.createCell(2).setCellValue(LocalDate.of(2025, 5, 20));
                var style = book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("yyyy年m月d日")); row.getCell(2).setCellStyle(style);
                book.write(output);
                var parsed = parser.parse(output.toByteArray(), book instanceof XSSFWorkbook ? "学者.xlsx" : "学者.xls",
                        new ImportOptions("张三", "", null, 1, 2, "AUTHOR", Map.of()));
                assertEquals(java.util.List.of("说明", "学者论文"), parsed.preview().sheets());
                assertEquals(1, parsed.preview().validRows());
                assertEquals(LocalDate.of(2025, 5, 20), parsed.rows().getFirst().publicationDate());
                assertEquals(3, parsed.rows().getFirst().rowNumber());
            }
        }
    }

    @Test
    void readsGb18030BomAndHtmlXlsWithoutEvaluatingMarkup() {
        String text = "Title-题名,Author-作者,Summary-摘要\n中文论文,张三,中文摘要";
        for (Charset charset : new Charset[] {StandardCharsets.UTF_8, Charset.forName("GB18030"), StandardCharsets.UTF_16LE}) {
            String content = charset == StandardCharsets.UTF_16LE ? "\uFEFF" + text : text;
            assertEquals("中文摘要", parser.parse(content.getBytes(charset), "信息.csv", options("AUTHOR")).rows().getFirst().abstractText());
        }
        String html = "<html><body><table><tr><th>Title-题名</th><th>Author-作者</th><th>Summary-摘要</th></tr><tr><td>表格论文</td><td>张三</td><td>第一行<br>第二行 &amp; 符号</td></tr></table><script>ignored()</script></body></html>";
        var parsed = parser.parse(html.getBytes(StandardCharsets.UTF_8), "知网.xls", options("AUTHOR"));
        assertEquals(1, parsed.preview().validRows());
        assertEquals("第一行\n第二行 & 符号", parsed.rows().getFirst().abstractText());
    }

    @Test
    void separatesStudentAuthorshipFromExplicitSupervisionAndRecognizesPatents() {
        String thesis = "SrcDatabase-来源库,Title-题名,Author-作者\n中国优秀硕士学位论文全文数据库,研究论文,李四";
        var advised = parser.parse(thesis.getBytes(StandardCharsets.UTF_8), "硕论.csv", options("MASTER_SUPERVISION"));
        assertEquals(1, advised.preview().validRows());
        assertEquals(java.util.List.of("李四"), advised.rows().getFirst().authors());
        assertEquals("master-thesis", advised.rows().getFirst().type());
        assertEquals(0, parser.parse(thesis.getBytes(StandardCharsets.UTF_8), "硕论.csv", options("AUTHOR")).preview().validRows());
        assertEquals(0, parser.parse(thesis.getBytes(StandardCharsets.UTF_8), "硕论.csv", options("DOCTOR_SUPERVISION")).preview().validRows());
        assertEquals(0, parser.parse(thesis.replace("中国优秀硕士学位论文全文数据库", "会议论文").getBytes(StandardCharsets.UTF_8), "会议.csv", options("MASTER_SUPERVISION")).preview().validRows());
        String patent = "SrcDatabase-来源库,Title-题名,Author-作者\n中国专利,检测装置,张三";
        assertEquals("patent", parser.parse(patent.getBytes(StandardCharsets.UTF_8), "专利.csv", options("AUTHOR")).rows().getFirst().type());
    }

    @Test
    void reportsEveryInvalidRowBeyondPreviewAndRejectsInvalidMappings() {
        StringBuilder csv = new StringBuilder("Title-题名,Author-作者,PubTime-发表时间\n");
        for (int i = 0; i < 21; i++) csv.append("论文,张三,2025\n");
        csv.append(",张三,2025-02-30\n");
        var parsed = parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8), "信息.csv", options("AUTHOR"));
        assertEquals(22, parsed.preview().totalRows()); assertEquals(21, parsed.preview().validRows()); assertEquals(20, parsed.preview().rows().size());
        assertTrue(parsed.preview().issues().stream().anyMatch(issue -> issue.rowNumber() == 23 && issue.errors().size() == 2));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8), "信息.csv",
                new ImportOptions("张三", "", null, 0, 1, "AUTHOR", Map.of("title", 100))));
    }

    @Test
    void mappingAndDatePrecisionProduceStableRepreviewKeys() {
        byte[] bytes = "自定义题名,署名,年\n论文,张三,2024".getBytes(StandardCharsets.UTF_8);
        var options = new ImportOptions("张三", "", null, 0, 1, "AUTHOR", Map.of("title", 0, "authors", 1));
        var parsed = parser.parse(bytes, "信息.csv", options);
        assertEquals(1, parsed.preview().validRows()); assertEquals("YEAR", parsed.rows().getFirst().datePrecision());
        var mapped = new ImportOptions("张三", "", null, 0, 1, "AUTHOR", parsed.preview().mapping());
        assertEquals(parsed.preview().previewKey(), parser.parse(bytes, "信息.csv", mapped).preview().previewKey());
    }

    @Test
    void readsChangingCnkiSectionHeadersAndPreservesEachRowsOriginalColumns() {
        String html = """
                <html><table>
                <tr><td>SrcDatabase-来源库</td><td>Title-题名</td><td>Author-作者</td><td>ISSN</td><td>URL</td></tr>
                <tr><td>期刊</td><td>合作论文</td><td>张三,李四</td><td>1234-5678</td><td>https://example.test/article</td></tr>
                <tr><td>SrcDatabase-来源库</td><td>Title-题名</td><td>Author-作者</td><td>URL</td></tr>
                <tr><td>博士</td><td>学生博士论文</td><td>王五</td><td>https://example.test/thesis</td></tr>
                <tr><td>SrcDatabase-来源库</td><td>Author-作者</td><td>Title-题名</td><td>PubTime</td></tr>
                <tr><td>科技成果</td><td>张三</td><td>研究项目成果</td><td>2005-01-01</td></tr>
                </table></html>
                """;
        var parsed = parser.inspect(html.getBytes(StandardCharsets.UTF_8), "知网.xls", 0, 1, Map.of());
        assertEquals(3, parsed.preview().totalRows());
        assertEquals(3, parsed.preview().validRows());
        assertEquals(java.util.List.of(2, 4, 6), parsed.rows().stream().map(row -> row.rowNumber()).toList());
        assertEquals(java.util.List.of("张三", "李四"), parsed.rows().getFirst().authors());
        assertEquals("https://example.test/thesis", parsed.rows().get(1).url());
        assertEquals("", parsed.rows().get(1).issn());
        assertFalse(parsed.rows().get(1).original().containsKey("ISSN"));
        assertEquals("研究项目成果", parsed.rows().get(2).title());
        assertEquals("scientific-result", parsed.rows().get(2).type());
        assertEquals(LocalDate.of(2005, 1, 1), parsed.rows().get(2).publicationDate());
        var replay = parser.inspect(html.getBytes(StandardCharsets.UTF_8), "知网.xls", 0, 1, parsed.preview().mapping());
        assertEquals(parsed.preview().previewKey(), replay.preview().previewKey());
        assertEquals(parsed.rows(), replay.rows());
    }

    @Test
    void splitsChineseCommaAuthorsWithoutSplittingWesternNamesOrInstitutions() {
        String csv = "SrcDatabase,Title,Author,Organ\n期刊,合作论文,\"张三，李四;王五,赵六;Smith, John;张三\",\"University, Department;合作大学\"";
        var row = parser.inspect(csv.getBytes(StandardCharsets.UTF_8), "作者.csv", 0, 1, Map.of()).rows().getFirst();
        assertEquals(java.util.List.of("张三", "李四", "王五", "赵六", "Smith, John"), row.authors());
        assertEquals(java.util.List.of("University, Department", "合作大学"), row.organizations());
    }

    @Test
    void trimsExportBomAtFieldBoundariesButPreservesOriginalEvidenceAndRejectsInternalSpaces() {
        String csv = "SrcDatabase,Title,Author,URL,DOI\n期刊,论文,张三,\"https://example.test/paper\n\uFEFF\",\"10.1234/test\n\uFEFF\"";
        var parsed = parser.inspect(csv.getBytes(StandardCharsets.UTF_8), "知网.csv", 0, 1, Map.of());
        assertEquals(1, parsed.preview().validRows());
        assertEquals("10.1234/test", parsed.rows().getFirst().doi());
        assertEquals("https://example.test/paper", parsed.rows().getFirst().url());
        assertTrue(parsed.rows().getFirst().original().get("DOI").endsWith("\uFEFF"));
        assertEquals(0, parser.inspect(csv.replace("10.1234/test", "10.1234/test space").getBytes(StandardCharsets.UTF_8), "知网.csv", 0, 1, Map.of()).preview().validRows());
    }

    @Test
    void rejectsEmptyCorruptOversizedAndFormulaWorkbooks() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Title,Author,,列3\n论文,张三,甲,乙".getBytes(StandardCharsets.UTF_8), "x.csv", options("AUTHOR")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[0], "x.csv", options("AUTHOR")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[ScholarTableReader.MAX_BYTES + 1], "x.csv", options("AUTHOR")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("not an xlsx".getBytes(), "x.xlsx", options("AUTHOR")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Title-题名,Author-作者\n".getBytes(StandardCharsets.UTF_8), "x.csv", options("AUTHOR")));
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(); sheet.createRow(0).createCell(0).setCellFormula("1+1"); book.write(output);
            assertThrows(IllegalArgumentException.class, () -> parser.parse(output.toByteArray(), "x.xlsx", options("AUTHOR")));
        }
    }
}
