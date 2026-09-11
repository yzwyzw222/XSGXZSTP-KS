package com.aacv.system.authorimport.api;

import com.aacv.system.authorimport.application.AuthorImportService;
import com.aacv.system.authorimport.application.ScholarImportParser;
import com.aacv.system.authorimport.application.ScholarBundleParser;
import com.aacv.system.authorimport.domain.ImportBundle;
import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportPreview;
import com.aacv.system.authorimport.domain.ImportSummary;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/author-import")
public class AuthorImportController {
    private final ScholarImportParser parser;
    private final AuthorImportService service;
    private final ObjectMapper json;
    private final ScholarBundleParser bundles;
    public AuthorImportController(ScholarImportParser parser, AuthorImportService service, ObjectMapper json, ScholarBundleParser bundles) {
        this.parser = parser; this.service = service; this.json = json; this.bundles = bundles;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportPreview preview(@RequestParam MultipartFile file, @RequestParam String options) throws IOException {
        return parser.parse(file.getBytes(), file.getOriginalFilename(), options(options)).preview();
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportSummary confirm(@RequestParam MultipartFile file, @RequestParam String options,
            @RequestParam String previewKey) throws IOException {
        ImportOptions parsedOptions = options(options);
        var parsed = parser.parse(file.getBytes(), file.getOriginalFilename(), parsedOptions);
        return service.save(parsedOptions, parsed, previewKey, file.getOriginalFilename());
    }

    @PostMapping(value = "/files/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportBundle.Preview previewFiles(@RequestParam List<MultipartFile> files, @RequestParam String options) throws IOException {
        return parseFiles(files, options).preview();
    }

    @PostMapping(value = "/files/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AUTHOR_IMPORT')")
    public ImportBundle.Summary confirmFiles(@RequestParam List<MultipartFile> files, @RequestParam String options,
            @RequestParam String previewKey) throws IOException {
        return service.saveBundle(parseFiles(files, options), previewKey);
    }

    private ScholarBundleParser.Parsed parseFiles(List<MultipartFile> files, String value) throws IOException {
        if (files.isEmpty() || files.size() > 10 || value.length() > 10000
                || files.stream().mapToLong(MultipartFile::getSize).sum() > com.aacv.system.authorimport.infrastructure.ScholarTableReader.MAX_BYTES) {
            throw new IllegalArgumentException("请选择 1 至 10 份文件，合计不超过 10 MB，并使用有效工作表设置");
        }
        ImportBundle.Options options;
        try { options = json.readValue(value, ImportBundle.Options.class); }
        catch (JacksonException exception) { throw new IllegalArgumentException("多文件导入设置无效，请重新选择文件"); }
        if (options == null) throw new IllegalArgumentException("缺少文件设置");
        var contents = new java.util.ArrayList<ScholarBundleParser.FileData>();
        for (var file : files) contents.add(new ScholarBundleParser.FileData(file.getOriginalFilename(), file.getBytes()));
        return bundles.parse(contents, options);
    }

    @GetMapping
    public List<ImportSummary> recent() { return service.recent(); }

    @GetMapping("/achievements/{id}/evidence")
    public List<Map<String, Object>> evidence(@PathVariable long id) {
        if (id <= 0) throw new IllegalArgumentException("成果编号必须为正数");
        return service.evidence(id);
    }

    private ImportOptions options(String value) {
        if (value.length() > 10000) throw new IllegalArgumentException("导入设置过大");
        try {
            ImportOptions result = json.readValue(value, ImportOptions.class);
            if (result == null) throw new IllegalArgumentException("缺少导入设置");
            return result;
        } catch (JacksonException exception) { throw new IllegalArgumentException("导入设置无效，请检查姓名、表头行和字段映射"); }
    }
}
