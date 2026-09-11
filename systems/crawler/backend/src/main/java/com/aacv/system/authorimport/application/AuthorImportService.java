package com.aacv.system.authorimport.application;

import static com.aacv.system.authorimport.application.ScholarImportParser.hash;
import static com.aacv.system.authorimport.application.ScholarImportParser.normalized;

import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportBundle;
import com.aacv.system.authorimport.domain.ImportRow;
import com.aacv.system.authorimport.domain.ImportSummary;
import com.aacv.system.authorimport.infrastructure.AuthorImportMapper;
import com.aacv.system.authorimport.infrastructure.ImportId;
import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthorImportService {
    private final AuthorImportMapper mapper;
    private final GraphProjectionRequestPort projection;
    private final AuditService audit;
    private final ObjectMapper json;

    public AuthorImportService(AuthorImportMapper mapper, GraphProjectionRequestPort projection, AuditService audit, ObjectMapper json) {
        this.mapper = mapper; this.projection = projection; this.audit = audit; this.json = json;
    }

    @Transactional(timeout = 60)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportBundle.Summary saveBundle(ScholarBundleParser.Parsed bundle, String previewKey) {
        if (previewKey == null || !previewKey.equals(bundle.preview().previewKey())) throw new IllegalArgumentException("文件或候选已改变，请重新解析预览");
        if (!bundle.preview().canConfirm() || bundle.groups().isEmpty()) throw new IllegalArgumentException("请先处理候选学者和所有问题行，本次没有写入数据");
        mapper.lockImport();
        String name = bundle.preview().scholarName();
        var authored = bundle.groups().stream().filter(group -> !group.options().supervision()
                        && normalized(group.options().scholarName()).equals(normalized(name)))
                .flatMap(group -> group.parsed().rows().stream()).toList();
        if (authored.isEmpty()) throw new IllegalArgumentException("缺少本人署名成果，无法确认本批学者身份");
        var candidates = new HashSet<Long>();
        for (var row : authored) {
            Long workId = mapper.findWork(workKey(row));
            if (workId == null && row.doi() != null) workId = mapper.findDoi(row.doi());
            if (workId != null) candidates.addAll(mapper.findWorkAuthors(workId, name));
        }
        if (candidates.size() > 1) throw new ResourceConflictException("这些成果对应多个同名学者，请核对文件归属");
        // 自动导入以共同成果复用身份；没有共同成果时不只凭姓名合并不同学者。
        String identity = "file-scholar:" + normalized(name) + ":" + hash(json.writeValueAsString(authored.stream().map(AuthorImportService::workKey).distinct().sorted().toList()));
        long scholarId = candidates.size() == 1 ? candidates.iterator().next() : person(identity, name);
        var batches = new java.util.ArrayList<ImportSummary>();
        // 先导入主学者成果，再复用共同作者身份保存独立署名的科技成果。
        var orderedGroups = bundle.groups().stream().sorted(java.util.Comparator.comparing(
                group -> !normalized(group.options().scholarName()).equals(normalized(name)))).toList();
        for (var group : orderedGroups) {
            var source = group.options();
            long ownerId = normalized(source.scholarName()).equals(normalized(name)) ? scholarId
                    : person("contributor:" + scholarId + ":" + normalized(source.scholarName()), source.scholarName());
            var options = new ImportOptions(source.scholarName(), "", ownerId, source.sheetIndex(), source.headerRow(), source.mode(), source.mapping());
            boolean existing = mapper.findBatch(group.parsed().preview().previewKey()) != null;
            var batch = save(options, group.parsed(), group.parsed().preview().previewKey(), group.fileName());
            if (batch.authorId() != ownerId) throw new ResourceConflictException("既有导入与本批学者身份不一致，请核对文件归属");
            batches.add(batch);
            if (!existing && options.supervision()) audit.record(AuditAction.AUTHOR_IMPORTED, "AUTHOR_IMPORT", Long.toString(batch.id()), AuditResult.SUCCESS,
                    Map.of("relationshipBasis", "SAME_SCHOLAR_ADVISOR_FILTER", "importMode", options.mode()));
        }
        return new ImportBundle.Summary(scholarId, name, batches.stream().mapToInt(ImportSummary::importedCount).sum(),
                batches.stream().mapToInt(ImportSummary::linkedCount).sum(), batches.stream().mapToInt(ImportSummary::skippedCount).sum(), List.copyOf(batches));
    }

    @Transactional(timeout = 60)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportSummary save(ImportOptions options, ScholarImportParser.Parsed parsed, String previewKey, String fileName) {
        if (previewKey == null || !previewKey.equals(parsed.preview().previewKey())) throw new IllegalArgumentException("文件或导入设置已改变，请重新预览");
        if (parsed.rows().isEmpty() || parsed.rows().stream().anyMatch(row -> !row.errors().isEmpty())) throw new IllegalArgumentException("信息表仍有问题行，请修正后重新预览；本次没有写入数据");
        // 人工文件导入按事务串行归并，避免并发重复请求创建同名实体或重复成果。
        mapper.lockImport();
        ImportSummary previous = mapper.findBatch(previewKey);
        if (previous != null) return previous;
        long scholarId = scholar(options, parsed.rows());
        Long homeOrg = options.scholarOrganization().isBlank() ? null : organization(options.scholarOrganization());
        ImportId batch = new ImportId();
        String safeName = fileName == null ? "信息表" : fileName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (safeName.length() > 240) safeName = safeName.substring(0, 240);
        mapper.insertBatch(batch, previewKey, scholarId, safeName,
                parsed.preview().sheets().get(options.sheetIndex()), options, parsed.rows().size());
        int imported = 0, linked = 0, skipped = 0;
        for (ImportRow row : parsed.rows()) {
            String key = workKey(row);
            Long workId = mapper.findWork(key);
            boolean keyExists = workId != null;
            if (workId == null && row.doi() != null) workId = mapper.findDoi(row.doi());
            boolean created = workId == null;
            if (!created && options.supervision() && !row.type().equals(mapper.findWorkType(workId))) {
                throw new ResourceConflictException("已存在成果的类型与本次学位论文不一致，请核对 DOI 和导入关系");
            }
            if (created) {
                ImportId work = new ImportId();
                Long venueId = row.venue().isBlank() ? null : venue(row.venue(), row.issn());
                mapper.insertAchievement(work, row, key, normalized(row.title()), venueId);
                workId = work.getId();
                mapper.insertAbstract(workId, row.abstractText(), row.warnings().stream().anyMatch(value -> value.contains("第一责任人")));
                int position = 1;
                for (String name : row.authors()) {
                    long authorId = normalized(name).equals(normalized(options.scholarName())) ? scholarId
                            : person("contributor:" + scholarId + ":" + normalized(name), name);
                    mapper.insertAuthorLink(workId, authorId, position++);
                }
                for (String name : row.organizations()) mapper.insertInstitution(workId, organization(name));
                position = 1;
                for (String name : row.keywords()) mapper.insertKeywordLink(workId, keyword(name), position++);
            }
            if (!keyExists) mapper.insertWorkKey(key, workId);
            boolean changed = created;
            if (options.supervision() && mapper.advisorExists(workId, scholarId) == 0) {
                mapper.insertAdvisor(workId, scholarId); changed = true;
            }
            if (!options.supervision() && !mapper.findWorkAuthors(workId, options.scholarName()).contains(scholarId)) {
                throw new ResourceConflictException("已存在成果的作者身份与当前学者不一致，请选择已有作者后重试");
            }
            if (homeOrg != null && mapper.affiliationExists(workId, scholarId, homeOrg) == 0) {
                mapper.insertAffiliation(workId, scholarId, homeOrg); changed = true;
            }
            mapper.insertRecord(batch.getId(), row.rowNumber(), workId, json.writeValueAsString(row.original()));
            if (changed) projection.requestAchievement(workId);
            if (created) imported++; else if (changed) linked++; else skipped++;
        }
        mapper.finishBatch(batch.getId(), imported, linked, skipped);
        audit.record(AuditAction.AUTHOR_IMPORTED, "AUTHOR_IMPORT", batch.getId().toString(), AuditResult.SUCCESS,
                Map.of("imported", Integer.toString(imported), "linked", Integer.toString(linked), "skipped", Integer.toString(skipped)));
        return mapper.findBatch(previewKey);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public List<ImportSummary> recent() { return mapper.recentBatches(); }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<Map<String, Object>> evidence(long achievementId) {
        return mapper.evidence(achievementId).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            result.put("originalColumns", json.readValue(row.get("originalColumns").toString(), Map.class));
            return result;
        }).toList();
    }

    private long scholar(ImportOptions options, List<ImportRow> rows) {
        if (options.authorId() != null) {
            String name = mapper.findAuthorName(options.authorId());
            if (name == null || !normalized(name).equals(normalized(options.scholarName()))) throw new IllegalArgumentException("所选作者不存在或与学者姓名不一致");
            return options.authorId();
        }
        String key = hash("scholar:" + normalized(options.scholarName()) + ":" + normalized(options.scholarOrganization()));
        Long existing = mapper.findPerson(key);
        if (existing != null) return existing;
        var candidates = new HashSet<Long>();
        if (!options.supervision()) for (ImportRow row : rows) {
            Long workId = mapper.findWork(workKey(row));
            if (workId == null && row.doi() != null) workId = mapper.findDoi(row.doi());
            if (workId != null) candidates.addAll(mapper.findWorkAuthors(workId, options.scholarName()));
        }
        if (candidates.size() > 1) throw new ResourceConflictException("已存在多个同名署名身份，请明确选择已有作者");
        if (candidates.size() == 1) {
            long id = candidates.iterator().next(); mapper.insertPerson(key, id); return id;
        }
        return personWithKey(key, options.scholarName());
    }

    private long person(String identity, String name) { return personWithKey(hash(identity), name); }
    private long personWithKey(String key, String name) {
        Long existing = mapper.findPerson(key);
        if (existing != null) return existing;
        ImportId row = new ImportId(); mapper.insertAuthor(row, name); mapper.insertPerson(key, row.getId()); return row.getId();
    }
    private long organization(String name) {
        List<Long> candidates = mapper.findOrganizations(name);
        if (candidates.size() > 1) throw new ResourceConflictException("存在多个同名机构，请先确认机构名称");
        if (!candidates.isEmpty()) return candidates.getFirst();
        ImportId row = new ImportId(); mapper.insertOrganization(row, name); return row.getId();
    }
    private long venue(String name, String issn) {
        List<Long> candidates = mapper.findVenues(name);
        if (candidates.size() > 1) throw new ResourceConflictException("存在多个同名文献来源，请先确认名称");
        if (!candidates.isEmpty()) return candidates.getFirst();
        ImportId row = new ImportId(); mapper.insertVenue(row, name, issn.isEmpty() ? null : issn); return row.getId();
    }
    private long keyword(String name) {
        String key = "cnki:" + hash(normalized(name));
        Long existing = mapper.findKeyword(key);
        if (existing != null) return existing;
        ImportId row = new ImportId(); mapper.insertKeyword(row, key, name); return row.getId();
    }
    static String workKey(ImportRow row) {
        if (row.doi() != null) return hash("doi:" + row.doi());
        // URL 可能包含临时检索参数，使用题名、类型、年份与完整署名构成稳定的回退标识。
        return hash("cnki:" + normalized(row.title()) + "|" + row.type() + "|"
                + (row.publicationDate() == null ? "" : row.publicationDate().getYear()) + "|"
                + row.authors().stream().map(ScholarImportParser::normalized).sorted().toList());
    }
}
