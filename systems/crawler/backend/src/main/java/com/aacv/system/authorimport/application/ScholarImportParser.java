package com.aacv.system.authorimport.application;

import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportPreview;
import com.aacv.system.authorimport.domain.ImportRow;
import com.aacv.system.authorimport.infrastructure.ScholarTableReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ScholarImportParser {
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("database", List.of("SrcDatabase", "来源库", "数据库", "成果类型", "文献类型")),
            Map.entry("title", List.of("Title", "题名", "标题", "篇名", "专利名称")),
            Map.entry("authors", List.of("Author", "作者", "发明人")),
            Map.entry("organizations", List.of("Organ", "单位", "机构", "作者单位", "学位授予单位")),
            Map.entry("venue", List.of("Source", "文献来源", "期刊", "刊名")),
            Map.entry("keywords", List.of("Keyword", "关键词", "关键字")),
            Map.entry("abstract", List.of("Summary", "摘要", "Abstract")),
            Map.entry("date", List.of("PubTime", "发表时间", "发表日期", "公开日期", "出版日期")),
            Map.entry("firstAuthor", List.of("FirstDuty", "第一责任人", "第一作者")),
            Map.entry("year", List.of("Year", "年", "年份")),
            Map.entry("issn", List.of("ISSN", "国际标准刊号")),
            Map.entry("url", List.of("URL", "网址", "链接")),
            Map.entry("doi", List.of("DOI")));
    private final ScholarTableReader reader;
    private final ObjectMapper json;

    public record Parsed(ImportPreview preview, List<ImportRow> rows) { }

    public ScholarImportParser(ScholarTableReader reader, ObjectMapper json) { this.reader = reader; this.json = json; }

    public Parsed parse(byte[] bytes, String fileName, ImportOptions options) {
        return parseFile(bytes, fileName, options.sheetIndex(), options.headerRow(), options.mapping(), options);
    }

    public Parsed inspect(byte[] bytes, String fileName, int sheetIndex, int headerRow, Map<String, Integer> overrides) {
        return parseFile(bytes, fileName, sheetIndex, headerRow, overrides, null);
    }

    private Parsed parseFile(byte[] bytes, String fileName, int sheetIndex, int headerRow,
            Map<String, Integer> overrides, ImportOptions options) {
        var table = reader.read(bytes, fileName, sheetIndex);
        if (table.rows().size() < headerRow) throw new IllegalArgumentException("表头行不存在");
        List<String> headers = table.rows().get(headerRow - 1);
        if (headers.isEmpty() || headers.stream().allMatch(String::isBlank)) throw new IllegalArgumentException("表头行为空");
        List<String> originalNames = java.util.stream.IntStream.range(0, headers.size())
                .mapToObj(index -> headers.get(index).isBlank() ? "列" + (index + 1) : headers.get(index)).toList();
        if (originalNames.stream().distinct().count() != headers.size()) {
            throw new IllegalArgumentException("存在重复列名，请先为重复列设置不同名称");
        }
        Map<String, Integer> mapping = new TreeMap<>();
        ALIASES.forEach((field, aliases) -> {
            for (int i = 0; i < headers.size(); i++) {
                String header = normalized(headers.get(i));
                if (aliases.stream().anyMatch(alias -> header.equals(normalized(alias))
                        || header.startsWith(normalized(alias) + "-"))) {
                    mapping.put(field, i); break;
                }
            }
        });
        overrides.forEach((field, column) -> {
            if (!ALIASES.containsKey(field) || column < -1 || column >= headers.size()) {
                throw new IllegalArgumentException("字段映射包含未知字段或无效列号");
            }
            if (column == -1) mapping.remove(field); else mapping.put(field, column);
        });
        List<ImportRow> rows = new ArrayList<>();
        for (int i = headerRow; i < table.rows().size(); i++) {
            var cells = table.rows().get(i);
            if (cells.stream().allMatch(String::isBlank)) continue;
            if (rows.size() >= 2000) throw new IllegalArgumentException("每张表最多导入 2000 条记录");
            rows.add(parseRow(i + 1, headers, cells, mapping, options));
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("表头下没有可导入的记录");
        String previewKey = options == null ? hash(json.writeValueAsString(List.of(hash(bytes), sheetIndex, headerRow, mapping)))
                : hash(json.writeValueAsString(List.of(
                hash(bytes), options.scholarName(), options.scholarOrganization(),
                options.authorId() == null ? 0 : options.authorId(), options.sheetIndex(), options.headerRow(),
                options.mode(), mapping)));
        var issues = rows.stream().filter(row -> !row.errors().isEmpty() || !row.warnings().isEmpty())
                .map(row -> new ImportPreview.RowIssue(row.rowNumber(), row.errors(), row.warnings())).toList();
        return new Parsed(new ImportPreview(previewKey, table.sheets(), headers, mapping, rows.size(),
                (int) rows.stream().filter(row -> row.errors().isEmpty()).count(),
                rows.subList(0, Math.min(20, rows.size())), issues), List.copyOf(rows));
    }

    private ImportRow parseRow(int number, List<String> headers, List<String> cells,
            Map<String, Integer> mapping, ImportOptions options) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> original = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) original.put(headers.get(i).isBlank() ? "列" + (i + 1) : headers.get(i), i < cells.size() ? cells.get(i) : "");
        if (cells.size() > headers.size() && cells.subList(headers.size(), cells.size()).stream().anyMatch(value -> !value.isBlank())) errors.add("存在超出表头的非空列");
        String title = value(cells, mapping, "title");
        if (title.isEmpty()) errors.add("缺少题名，请检查表头行或题名映射");
        if (title.length() > 1000) errors.add("题名超过 1000 字符");
        String authorText = value(cells, mapping, "authors");
        if (authorText.isBlank()) {
            authorText = value(cells, mapping, "firstAuthor");
            if (!authorText.isEmpty()) warnings.add("作者为空，使用第一责任人；可能缺少其他署名作者");
        }
        List<String> authors = split(authorText);
        if (authors.isEmpty()) errors.add("缺少论文作者或专利发明人");
        if (authors.size() > 100 || authors.stream().anyMatch(name -> name.length() > 200)) errors.add("作者数量或姓名长度超出上限");
        if (options != null) validateScholar(authors, options, errors);
        String database = value(cells, mapping, "database");
        String type = inferType(database);
        if (options != null && options.supervision()) {
            String degreeType = options.mode().equals("MASTER_SUPERVISION") ? "master-thesis" : "doctoral-thesis";
            if (!database.isBlank() && !degreeType.equals(type)) errors.add("来源库与所选硕博指导类型不一致");
            type = degreeType;
        }
        List<String> organizations = split(value(cells, mapping, "organizations"));
        List<String> keywords = split(value(cells, mapping, "keywords"));
        if (organizations.size() > 100 || organizations.stream().anyMatch(name -> name.length() > 500)) errors.add("机构数量或名称长度超出上限");
        if (keywords.size() > 100 || keywords.stream().anyMatch(name -> name.length() > 500)) errors.add("关键词数量或长度超出上限");
        String dateText = value(cells, mapping, "date");
        if (dateText.isBlank()) dateText = value(cells, mapping, "year");
        LocalDate date = null;
        String precision = null;
        if (!dateText.isBlank()) {
            String normalizedDate = dateText.replace('年', '-').replace('月', '-').replace("日", "")
                    .replace('/', '-').replace('.', '-').strip().split("[ T]", 2)[0].replaceFirst("-$", "");
            try {
                if (normalizedDate.matches("\\d{4}")) { date = LocalDate.of(Integer.parseInt(normalizedDate), 1, 1); precision = "YEAR"; }
                else if (normalizedDate.matches("\\d{4}-\\d{1,2}")) {
                    String[] parts = normalizedDate.split("-"); date = YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).atDay(1); precision = "MONTH";
                } else {
                    String[] parts = normalizedDate.split("-");
                    if (parts.length != 3) throw new DateTimeParseException("日期格式无效", normalizedDate, 0);
                    date = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); precision = "DAY";
                }
                if (date.getYear() < 1000 || date.getYear() > 9999) errors.add("发表年份应在 1000 至 9999 之间");
            } catch (java.time.DateTimeException | NumberFormatException exception) { errors.add("发表时间无效，请使用年、年月或完整日期"); }
        } else warnings.add("未提供发表时间或年份");
        String doi = value(cells, mapping, "doi").replaceFirst("(?i)^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "").toLowerCase(Locale.ROOT);
        if (!doi.isEmpty() && (doi.length() > 255 || !doi.matches("10\\.\\d{4,9}/\\S+"))) errors.add("DOI 格式无效");
        String venue = value(cells, mapping, "venue");
        String issn = value(cells, mapping, "issn");
        String url = value(cells, mapping, "url");
        if (venue.length() > 500 || issn.length() > 16) errors.add("文献来源或 ISSN 长度超出上限");
        if (!url.isEmpty() && (url.length() > 1000 || !url.matches("(?i)https?://[^\\s]+"))) errors.add("网址必须是有效的 HTTP 或 HTTPS 链接");
        String abstractText = value(cells, mapping, "abstract");
        if (abstractText.isEmpty()) warnings.add("未提供摘要");
        return new ImportRow(number, title, type, authors, organizations, keywords, abstractText, date, precision,
                doi.isEmpty() ? null : doi, venue, issn, url, original, List.copyOf(errors), List.copyOf(warnings));
    }

    static String inferType(String source) {
        String name = normalized(source);
        if (name.contains("专利") || name.contains("patent")) return "patent";
        if (name.contains("博士") || name.contains("doctoral")) return "doctoral-thesis";
        if (name.contains("硕士") || name.contains("硕论") || name.contains("master")) return "master-thesis";
        if (name.contains("会议") || name.contains("conference")) return "proceedings-article";
        return "article";
    }

    static void validateScholar(List<String> authors, ImportOptions options, List<String> errors) {
        boolean signed = authors.stream().anyMatch(name -> normalized(name).equals(normalized(options.scholarName())));
        if (!options.supervision() && !signed) errors.add("署名中未找到当前学者；请核对姓名或选择硕博论文指导");
        if (options.supervision() && signed) errors.add("当前学者同时出现在论文作者中，请确认指导关系");
    }

    private static String value(List<String> cells, Map<String, Integer> mapping, String key) {
        Integer index = mapping.get(key);
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }

    private static List<String> split(String value) {
        // 空格可能属于英文姓名或机构名称，不按空格拆分实体。
        Map<String, String> names = new LinkedHashMap<>();
        Arrays.stream(value.split("[;；、\\r\\n]+"))
                .map(String::strip).filter(item -> !item.isEmpty()).forEach(item -> names.putIfAbsent(normalized(item), item));
        return List.copyOf(names.values());
    }

    public static String normalized(String value) { return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT); }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("运行环境不支持 SHA-256", exception); }
    }
}
