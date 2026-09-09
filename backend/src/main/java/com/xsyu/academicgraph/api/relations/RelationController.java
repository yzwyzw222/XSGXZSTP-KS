package com.xsyu.academicgraph.api.relations;

import com.xsyu.academicgraph.api.relations.RelationDtos.AuthorTopicItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.CoauthorItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.FieldPartitionItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.InstitutionAuthorItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.InstitutionCollabItem;
import com.xsyu.academicgraph.application.relations.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关系分析接口（/api/v1/relations）：侧边栏"关系分析"板块的五个页面数据源。
 * 全部是只读查询，返回数组（不套分页包装）；必填 id 缺失时抛 IllegalArgumentException，
 * 由 GlobalExceptionHandler 翻译成 400 + 中文提示。权限沿用全局规则：登录即可访问。
 */
@RestController
@RequestMapping("/api/v1/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    /** 合作者网络：某作者的合作者明细（合著篇数/共同论文/同机构标签） */
    @GetMapping("/coauthors")
    public List<CoauthorItem> coauthors(@RequestParam(required = false) Long authorId,
                                        @RequestParam(required = false) Integer limit) {
        if (authorId == null) {
            throw new IllegalArgumentException("缺少必填参数 authorId（要查看的作者 id）");
        }
        return relationService.coauthors(authorId, limit);
    }

    /** 研究领域分区：全体作者按领域分区的扁平行（前端按领域分组） */
    @GetMapping("/field-partition")
    public List<FieldPartitionItem> fieldPartition(@RequestParam(required = false) Integer limit) {
        return relationService.fieldPartition(limit);
    }

    /** 研究领域分区：某作者的主题画像（关键词 × 篇数 × 出现年份） */
    @GetMapping("/author-topics")
    public List<AuthorTopicItem> authorTopics(@RequestParam(required = false) Long authorId,
                                              @RequestParam(required = false) Integer limit) {
        if (authorId == null) {
            throw new IllegalArgumentException("缺少必填参数 authorId（要查看的作者 id）");
        }
        return relationService.authorTopics(authorId, limit);
    }

    /** 科研机构：某机构下的作者明细（论文数/引用热度合计） */
    @GetMapping("/institution-authors")
    public List<InstitutionAuthorItem> institutionAuthors(@RequestParam(required = false) Long institutionId,
                                                          @RequestParam(required = false) Integer limit) {
        if (institutionId == null) {
            throw new IllegalArgumentException("缺少必填参数 institutionId（要查看的机构 id）");
        }
        return relationService.institutionAuthors(institutionId, limit);
    }

    /** 科研机构：机构间合作（两两机构共同署名论文数） */
    @GetMapping("/institution-collaborations")
    public List<InstitutionCollabItem> institutionCollaborations(@RequestParam(required = false) Integer limit) {
        return relationService.institutionCollaborations(limit);
    }
}
