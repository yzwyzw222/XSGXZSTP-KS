package com.xsyu.academicgraph.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportRequest;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportSummary;
import com.xsyu.academicgraph.application.admin.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 管理员数据导入接口（/api/v1/admin/import/**）。
 * SecurityConfig 已限定 /api/v1/admin/** 需要 ADMIN 角色。
 * 支持上传「论文为中心」的嵌套 JSON 文件，服务端按名称去重后批量落库。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class DataImportController {

    private final DataImportService dataImportService;
    private final ObjectMapper objectMapper;

    /**
     * 导入论文 JSON 文件（multipart/form-data，字段名 file）。
     * 解析失败（非法 JSON / 缺 papers 数组）返回 400 + 中文提示；
     * 单篇论文的问题不会中断整批，会在返回的 rows 里逐条标注。
     */
    @PostMapping("/import/papers")
    public ImportSummary importPapers(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传非空的 JSON 文件（字段名 file）");
        }
        ImportRequest request;
        try {
            request = objectMapper.readValue(file.getBytes(), ImportRequest.class);
        } catch (IOException e) {
            // Jackson 解析错误信息偏技术化，包一层中文再抛出（统一走 400）
            throw new IllegalArgumentException("文件解析失败：不是合法的导入 JSON（" + e.getMessage() + "）");
        }
        if (request == null || request.papers() == null || request.papers().isEmpty()) {
            throw new IllegalArgumentException("JSON 缺少 papers 数组或数组为空");
        }
        return dataImportService.importPapers(request);
    }
}
