package com.aacv.system.authorimport.application;

import static com.aacv.system.authorimport.application.ScholarImportParser.hash;
import static com.aacv.system.authorimport.application.ScholarImportParser.normalized;

import com.aacv.system.authorimport.domain.ImportBundle;
import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportPreview;
import com.aacv.system.authorimport.domain.ImportRow;
import com.aacv.system.authorimport.infrastructure.ScholarTableReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ScholarBundleParser {
    public record FileData(String name, byte[] bytes) { }
    public record Group(String fileName, ImportOptions options, ScholarImportParser.Parsed parsed) { }
    public record Parsed(ImportBundle.Preview preview, List<Group> groups) { }
    private final ScholarImportParser parser;
    private final ObjectMapper json;

    public ScholarBundleParser(ScholarImportParser parser, ObjectMapper json) { this.parser = parser; this.json = json; }

    public Parsed parse(List<FileData> files, ImportBundle.Options options) {
        if (files == null || files.size() != options.files().size()) throw new IllegalArgumentException("文件与工作表设置不一致");
        long size = 0;
        for (var file : files) {
            if (file == null || file.bytes() == null) throw new IllegalArgumentException("缺少文件内容");
            size += file.bytes().length;
        }
        if (size > ScholarTableReader.MAX_BYTES) throw new IllegalArgumentException("本批文件合计不能超过 10 MB");
        List<ScholarImportParser.Parsed> raw = new ArrayList<>();
        var seen = new java.util.HashSet<String>();
        Map<String, String> common = null;
        Map<String, String> organizations = new TreeMap<>();
        int total = 0;
        for (int i = 0; i < files.size(); i++) {
            var settings = options.files().get(i);
            ScholarImportParser.Parsed data;
            try {
                data = parser.inspect(files.get(i).bytes(), files.get(i).name(), settings.sheetIndex(), settings.headerRow(), settings.mapping());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个文件：" + exception.getMessage(), exception);
            }
            if (!seen.add(data.preview().previewKey())) throw new IllegalArgumentException("同一文件内容与工作表重复，请移除重复文件");
            var sourcePreview = data.preview();
            var checkedRows = data.rows().stream().map(row -> checkEvidence(row, sourcePreview)).toList();
            data = new ScholarImportParser.Parsed(preview(sourcePreview, checkedRows, sourcePreview.previewKey()), checkedRows);
            raw.add(data);
            total += data.rows().size();
            if (total > 2000) throw new IllegalArgumentException("本批文件合计最多导入 2000 条记录");
            for (var row : data.rows()) {
                row.organizations().forEach(name -> organizations.putIfAbsent(normalized(name), name));
                if (!mode(row).equals("AUTHOR") || row.authors().isEmpty()) continue;
                Map<String, String> names = new TreeMap<>();
                row.authors().forEach(name -> names.putIfAbsent(normalized(name), name));
                if (common == null) common = names; else common.keySet().retainAll(names.keySet());
            }
        }
        List<String> candidates = common == null ? List.of() : List.copyOf(common.values());
        String scholar = options.scholarName();
        if (!scholar.isEmpty()) {
            String selected = normalized(scholar);
            scholar = candidates.stream().filter(name -> normalized(name).equals(selected)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("所选姓名不在当前文件的共同作者候选中，请重新解析"));
        } else if (candidates.size() == 1) scholar = candidates.getFirst();
        List<String> messages = new ArrayList<>();
        if (common == null) messages.add("请同时上传该学者的本人署名成果表；硕博文件中的作者是学生，不能据此识别导师。");
        else if (candidates.isEmpty()) messages.add("本人署名成果没有共同作者，请检查是否混入其他学者的文件或缺少完整署名。");
        else if (scholar.isEmpty()) messages.add("有多位作者出现在全部本人署名成果中，请从文件候选中选择本次学者。");
        messages.add("机构按成果保留；表内单位列表不作为当前学者所属机构的直接证明。");
        List<Group> groups = new ArrayList<>();
        List<ImportBundle.FilePreview> previews = new ArrayList<>();
        int valid = 0;
        for (int i = 0; i < raw.size(); i++) {
            var source = raw.get(i);
            var settings = options.files().get(i);
            Map<String, List<ImportRow>> modes = new LinkedHashMap<>();
            for (var row : source.rows()) modes.computeIfAbsent(mode(row), ignored -> new ArrayList<>()).add(row);
            List<ImportRow> checked = new ArrayList<>();
            for (var entry : modes.entrySet()) {
                if (scholar.isEmpty()) { checked.addAll(entry.getValue()); continue; }
                var resolved = new ImportOptions(scholar, "", null, settings.sheetIndex(), settings.headerRow(), entry.getKey(), source.preview().mapping());
                List<ImportRow> rows = entry.getValue().stream().map(row -> validate(row, resolved)).toList();
                String key = hash(json.writeValueAsString(List.of("same-scholar-advisor-filter-v1", source.preview().previewKey(), scholar, entry.getKey())));
                var parsed = new ScholarImportParser.Parsed(preview(source.preview(), rows, key), rows);
                groups.add(new Group(safeName(files.get(i).name()), resolved, parsed));
                checked.addAll(rows);
            }
            checked.sort(java.util.Comparator.comparingInt(ImportRow::rowNumber));
            var checkedPreview = preview(source.preview(), checked, source.preview().previewKey());
            valid += checkedPreview.validRows();
            previews.add(new ImportBundle.FilePreview(safeName(files.get(i).name()), List.copyOf(modes.keySet()), checkedPreview));
        }
        if (previews.stream().anyMatch(file -> file.modes().stream().anyMatch(value -> !value.equals("AUTHOR")))) {
            messages.add("硕士、博士论文按来源库区分；指导关系依据本批同一学者、硕博按导师姓名筛选导出的约定建立。");
        }
        String key = hash(json.writeValueAsString(List.of("same-scholar-advisor-filter-v1", scholar,
                previews.stream().map(file -> List.of(file.fileName(), file.preview().previewKey())).toList())));
        return new Parsed(new ImportBundle.Preview(key, scholar, candidates, List.copyOf(organizations.values()), List.copyOf(messages),
                !scholar.isEmpty() && valid == total, total, valid, List.copyOf(previews)), List.copyOf(groups));
    }

    private ImportRow validate(ImportRow row, ImportOptions options) {
        List<String> errors = new ArrayList<>(row.errors());
        ScholarImportParser.validateScholar(row.authors(), options, errors);
        return withErrors(row, errors);
    }

    private ImportRow checkEvidence(ImportRow row, ImportPreview preview) {
        List<String> errors = new ArrayList<>(row.errors());
        Integer column = preview.mapping().get("database");
        String database = column == null ? "" : normalized(row.original().getOrDefault(preview.headers().get(column), ""));
        if (database.isBlank() || row.type().equals("article") && !database.contains("期刊")
                && !database.contains("journal") && !database.contains("article") && !database.equals("论文")) {
            errors.add("自动识别需要明确的来源库，请补充来源库列或调整映射");
        }
        if (row.warnings().stream().anyMatch(message -> message.contains("第一责任人"))) {
            errors.add("自动识别学者需要完整作者列，不能仅凭第一责任人确定学者");
        }
        return withErrors(row, errors);
    }

    private ImportRow withErrors(ImportRow row, List<String> errors) {
        return new ImportRow(row.rowNumber(), row.title(), row.type(), row.authors(), row.organizations(), row.keywords(),
                row.abstractText(), row.publicationDate(), row.datePrecision(), row.doi(), row.venue(), row.issn(), row.url(),
                row.original(), List.copyOf(errors), row.warnings());
    }

    private ImportPreview preview(ImportPreview source, List<ImportRow> rows, String key) {
        var issues = rows.stream().filter(row -> !row.errors().isEmpty() || !row.warnings().isEmpty())
                .map(row -> new ImportPreview.RowIssue(row.rowNumber(), row.errors(), row.warnings())).toList();
        return new ImportPreview(key, source.sheets(), source.headers(), source.mapping(), rows.size(),
                (int) rows.stream().filter(row -> row.errors().isEmpty()).count(), List.copyOf(rows.subList(0, Math.min(20, rows.size()))), issues);
    }

    public static String mode(ImportRow row) {
        return switch (row.type()) {
            case "master-thesis" -> "MASTER_SUPERVISION";
            case "doctoral-thesis" -> "DOCTOR_SUPERVISION";
            default -> "AUTHOR";
        };
    }

    private String safeName(String name) {
        if (name == null) return "信息表";
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return value.substring(0, Math.min(240, value.length()));
    }
}
