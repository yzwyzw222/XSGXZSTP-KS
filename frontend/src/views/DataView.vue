<template>
  <!--
    数据管理页（DataView）：五大实体的 CRUD 操作集中管理
    ─────────────────────────────────────────────────────
    技术方案：
      1. el-tabs 切换 5 个实体（论文/作者/机构/关键词/渠道）
      2. 每个 tab 内部结构同构：工具栏（搜索 + 新建）+ el-table + el-pagination
      3. 新建/编辑共用一个 el-dialog，通过 dialogMode 区分（'create' | 'edit' | null）
      4. 删除使用 ElMessageBox.confirm 二次确认
      5. 乐观锁冲突（409）：弹窗提示并刷新列表
      6. 批量删除：表格加多选列，工具栏"批量删除"一次确认后并发调用单条 DELETE，
         被引用的记录后端返回 409，前端汇总成"成功 N 条 / 失败 M 条 + 原因"提示
    后端契约：
      - 分页 page 从 0 起，size 默认 20 最大 100
      - 更新必须带 version 字段，不一致返回 409 CONFLICT
      - 删除被引用实体（如作者被论文引用）返回 409 + detail 说明
  -->
  <div class="data-view">
    <el-tabs v-model="activeTab" class="data-tabs" @tab-change="onTabChange">
      <!-- ==================== 论文 Tab ==================== -->
      <el-tab-pane label="论文" name="papers">
        <div class="toolbar">
          <el-input
            v-model="paperQuery.keyword"
            placeholder="搜索标题…"
            clearable
            style="width: 240px"
            @keyup.enter="loadPapers(0)"
            @clear="loadPapers(0)"
          />
          <el-select v-model="paperQuery.paperType" placeholder="类型" clearable style="width: 140px" @change="loadPapers(0)">
            <el-option v-for="t in PAPER_TYPES" :key="t" :label="paperTypeLabel(t)" :value="t" />
          </el-select>
          <el-button type="primary" @click="openPaperDialog('create')">+ 新建论文</el-button>
          <el-button type="danger" plain :disabled="paperSelection.length === 0" :loading="batchDeleting"
            @click="batchDeletePapers">
            批量删除{{ paperSelection.length ? `（${paperSelection.length}）` : '' }}
          </el-button>
        </div>
        <el-table :data="paperList" v-loading="paperLoading" stripe @selection-change="onPaperSelect">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ paperTypeLabel(row.paperType) }}</template>
          </el-table-column>
          <el-table-column prop="publicationYear" label="年份" width="80" />
          <el-table-column label="作者" width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.authors?.map((a: any) => a.displayName).join(', ') || '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="citationCount" label="被引" width="70" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPaperDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deletePaper(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="paperPage"
          :page-size="paperQuery.size"
          :total="paperTotal"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadPapers"
        />
      </el-tab-pane>

      <!-- ==================== 作者 Tab ==================== -->
      <el-tab-pane label="作者" name="authors">
        <div class="toolbar">
          <el-input v-model="authorQuery.keyword" placeholder="搜索姓名…" clearable style="width: 240px"
            @keyup.enter="loadAuthors(0)" @clear="loadAuthors(0)" />
          <el-button type="primary" @click="openAuthorDialog('create')">+ 新建作者</el-button>
          <el-button type="danger" plain :disabled="authorSelection.length === 0" :loading="batchDeleting"
            @click="batchDeleteAuthors">
            批量删除{{ authorSelection.length ? `（${authorSelection.length}）` : '' }}
          </el-button>
        </div>
        <el-table :data="authorList" v-loading="authorLoading" stripe @selection-change="onAuthorSelect">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="displayName" label="姓名" min-width="200" />
          <el-table-column prop="orcid" label="ORCID" width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAuthorDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deleteAuthor(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="authorPage" :page-size="authorQuery.size" :total="authorTotal"
          layout="total, prev, pager, next" class="pager" @current-change="loadAuthors" />
      </el-tab-pane>

      <!-- ==================== 机构 Tab ==================== -->
      <el-tab-pane label="机构" name="institutions">
        <div class="toolbar">
          <el-input v-model="instQuery.keyword" placeholder="搜索机构名…" clearable style="width: 240px"
            @keyup.enter="loadInstitutions(0)" @clear="loadInstitutions(0)" />
          <el-button type="primary" @click="openInstDialog('create')">+ 新建机构</el-button>
          <el-button type="danger" plain :disabled="instSelection.length === 0" :loading="batchDeleting"
            @click="batchDeleteInstitutions">
            批量删除{{ instSelection.length ? `（${instSelection.length}）` : '' }}
          </el-button>
        </div>
        <el-table :data="instList" v-loading="instLoading" stripe @selection-change="onInstSelect">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="displayName" label="名称" min-width="240" />
          <el-table-column prop="countryCode" label="国家" width="100" />
          <el-table-column prop="institutionType" label="类型" width="120" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openInstDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deleteInst(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="instPage" :page-size="instQuery.size" :total="instTotal"
          layout="total, prev, pager, next" class="pager" @current-change="loadInstitutions" />
      </el-tab-pane>

      <!-- ==================== 关键词 Tab ==================== -->
      <el-tab-pane label="关键词" name="keywords">
        <div class="toolbar">
          <el-input v-model="kwQuery.keyword" placeholder="搜索关键词…" clearable style="width: 240px"
            @keyup.enter="loadKeywords(0)" @clear="loadKeywords(0)" />
          <el-button type="primary" @click="openKwDialog('create')">+ 新建关键词</el-button>
          <el-button type="danger" plain :disabled="kwSelection.length === 0" :loading="batchDeleting"
            @click="batchDeleteKeywords">
            批量删除{{ kwSelection.length ? `（${kwSelection.length}）` : '' }}
          </el-button>
        </div>
        <el-table :data="kwList" v-loading="kwLoading" stripe @selection-change="onKwSelect">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="fieldName" label="所属领域" width="200" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openKwDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deleteKw(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="kwPage" :page-size="kwQuery.size" :total="kwTotal"
          layout="total, prev, pager, next" class="pager" @current-change="loadKeywords" />
      </el-tab-pane>

      <!-- ==================== 渠道 Tab ==================== -->
      <el-tab-pane label="渠道" name="venues">
        <div class="toolbar">
          <el-input v-model="venueQuery.keyword" placeholder="搜索渠道名…" clearable style="width: 240px"
            @keyup.enter="loadVenues(0)" @clear="loadVenues(0)" />
          <el-button type="primary" @click="openVenueDialog('create')">+ 新建渠道</el-button>
          <el-button type="danger" plain :disabled="venueSelection.length === 0" :loading="batchDeleting"
            @click="batchDeleteVenues">
            批量删除{{ venueSelection.length ? `（${venueSelection.length}）` : '' }}
          </el-button>
        </div>
        <el-table :data="venueList" v-loading="venueLoading" stripe @selection-change="onVenueSelect">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="displayName" label="名称" min-width="240" />
          <el-table-column prop="issn" label="ISSN" width="140" />
          <el-table-column prop="venueType" label="类型" width="120" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openVenueDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="deleteVenue(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="venuePage" :page-size="venueQuery.size" :total="venueTotal"
          layout="total, prev, pager, next" class="pager" @current-change="loadVenues" />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 论文表单弹窗 ==================== -->
    <!--
      论文表单最复杂：包含作者列表（动态增删行）、关键词多选、引用列表（动态增删行）
      作者行：选择作者 + 位次 + 可选所属机构
      引用行：二选一 —— 引用已有论文（下拉）或 外部 DOI（文本）
    -->
    <el-dialog v-model="paperDialogVisible" :title="paperDialogTitle" width="720px" destroy-on-close>
      <el-form :model="paperForm" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="paperForm.title" placeholder="论文标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="DOI">
              <el-input v-model="paperForm.doi" placeholder="10.xxxx/xxxxx" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="paperForm.paperType" placeholder="选择类型" style="width: 100%">
                <el-option v-for="t in PAPER_TYPES" :key="t" :label="paperTypeLabel(t)" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="语言">
              <el-input v-model="paperForm.language" placeholder="zh / en" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发表日期">
              <el-date-picker v-model="paperForm.publicationDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="摘要">
          <el-input v-model="paperForm.abstractText" type="textarea" :rows="3" placeholder="论文摘要（可选）" />
        </el-form-item>
        <el-form-item label="被引次数">
          <el-input-number v-model="paperForm.citationCount" :min="0" />
        </el-form-item>
        <el-form-item label="发表渠道">
          <el-select v-model="paperForm.venueId" placeholder="选择渠道（可选）" clearable style="width: 100%">
            <el-option v-for="v in venueOptions" :key="v.id" :label="v.displayName" :value="v.id" />
          </el-select>
        </el-form-item>

        <!-- 作者列表：动态增删行 -->
        <el-form-item label="作者">
          <div class="sub-list">
            <div v-for="(a, idx) in paperForm.authors" :key="idx" class="sub-row">
              <el-select v-model="a.authorId" placeholder="选择作者" filterable style="width: 180px">
                <el-option v-for="au in authorOptions" :key="au.id" :label="au.displayName" :value="au.id" />
              </el-select>
              <el-input-number v-model="a.position" :min="1" placeholder="位次" style="width: 90px" />
              <el-select v-model="a.institutionId" placeholder="机构（可选）" clearable filterable style="width: 180px">
                <el-option v-for="inst in instOptions" :key="inst.id" :label="inst.displayName" :value="inst.id" />
              </el-select>
              <el-button link type="danger" @click="paperForm.authors.splice(idx, 1)">移除</el-button>
            </div>
            <el-button size="small" @click="paperForm.authors.push({ authorId: 0, position: paperForm.authors.length + 1, institutionId: null })">+ 添加作者</el-button>
          </div>
        </el-form-item>

        <!-- 关键词多选 -->
        <el-form-item label="关键词">
          <el-select v-model="paperForm.keywordIds" multiple filterable placeholder="选择关键词" style="width: 100%">
            <el-option v-for="k in kwOptions" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
        </el-form-item>

        <!-- 引用列表：动态增删行，二选一（论文 ID / 外部 DOI） -->
        <el-form-item label="引用">
          <div class="sub-list">
            <div v-for="(r, idx) in paperForm.references" :key="idx" class="sub-row">
              <el-select v-model="r.citedPaperId" placeholder="引用已有论文" clearable filterable style="width: 220px">
                <el-option v-for="p in paperOptions" :key="p.id" :label="p.title" :value="p.id" />
              </el-select>
              <span class="muted">或</span>
              <el-input v-model="r.externalCitedDoi" placeholder="外部 DOI" style="width: 180px" />
              <el-button link type="danger" @click="paperForm.references.splice(idx, 1)">移除</el-button>
            </div>
            <el-button size="small" @click="paperForm.references.push({ citedPaperId: null, externalCitedDoi: '' })">+ 添加引用</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paperDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paperSaving" @click="savePaper">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 作者表单弹窗 ==================== -->
    <el-dialog v-model="authorDialogVisible" :title="authorDialogTitle" width="480px" destroy-on-close>
      <el-form :model="authorForm" label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="authorForm.displayName" placeholder="作者姓名" />
        </el-form-item>
        <el-form-item label="ORCID">
          <el-input v-model="authorForm.orcid" placeholder="0000-0000-0000-0000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="authorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="authorSaving" @click="saveAuthor">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 机构表单弹窗 ==================== -->
    <el-dialog v-model="instDialogVisible" :title="instDialogTitle" width="480px" destroy-on-close>
      <el-form :model="instForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="instForm.displayName" placeholder="机构名称" />
        </el-form-item>
        <el-form-item label="国家代码">
          <el-input v-model="instForm.countryCode" placeholder="CN / US / GB …" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="instForm.institutionType" placeholder="选择类型" clearable style="width: 100%">
            <el-option label="大学" value="UNIVERSITY" />
            <el-option label="研究院" value="RESEARCH_INSTITUTE" />
            <el-option label="企业" value="CORPORATE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="instDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="instSaving" @click="saveInst">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 关键词表单弹窗 ==================== -->
    <el-dialog v-model="kwDialogVisible" :title="kwDialogTitle" width="480px" destroy-on-close>
      <el-form :model="kwForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="kwForm.name" placeholder="关键词" />
        </el-form-item>
        <el-form-item label="所属领域">
          <el-input v-model="kwForm.fieldName" placeholder="如：计算机科学、物理学" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kwDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="kwSaving" @click="saveKw">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 渠道表单弹窗 ==================== -->
    <el-dialog v-model="venueDialogVisible" :title="venueDialogTitle" width="480px" destroy-on-close>
      <el-form :model="venueForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="venueForm.displayName" placeholder="期刊/会议名称" />
        </el-form-item>
        <el-form-item label="ISSN">
          <el-input v-model="venueForm.issn" placeholder="1234-5678" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="venueForm.venueType" placeholder="选择类型" clearable style="width: 100%">
            <el-option label="期刊" value="JOURNAL" />
            <el-option label="会议" value="CONFERENCE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="venueDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="venueSaving" @click="saveVenue">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 数据管理页脚本：五大实体 CRUD 逻辑
 * ─────────────────────────────────────
 * 每个实体遵循相同的交互模式：
 *   列表加载 → 搜索/翻页 → 新建/编辑弹窗 → 保存 → 刷新列表 → 删除确认
 * 错误处理：
 *   - 409 CONFLICT（乐观锁冲突）：弹窗提示用户"数据已被他人修改，请刷新后重试"
 *   - 409（删除被引用实体）：弹窗显示后端返回的 detail 说明引用关系
 *   - 其他错误：ElMessage.error 统一提示
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  PAPER_TYPES,
  authorsApi,
  institutionsApi,
  keywordsApi,
  papersApi,
  venuesApi,
  type Author,
  type AuthorUpsert,
  type Institution,
  type InstitutionUpsert,
  type Keyword,
  type KeywordUpsert,
  type Paper,
  type PaperUpsert,
  type Venue,
  type VenueUpsert
} from '../api'
import { ApiError } from '../api/http'

// ====================================================================
// 通用工具
// ====================================================================

/** 论文类型英文枚举 → 中文标签映射（下拉框展示用） */
const PAPER_TYPE_LABELS: Record<string, string> = {
  JOURNAL_ARTICLE: '期刊论文',
  CONFERENCE_PAPER: '会议论文',
  PATENT: '专利',
  OTHER: '其他'
}
function paperTypeLabel(t: string) {
  return PAPER_TYPE_LABELS[t] ?? t
}

/** 从 ApiError 中提取用户可读的错误信息：优先 problem.detail，否则用 fallback */
function extractError(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    return err.problem?.detail ?? err.message ?? fallback
  }
  return fallback
}

// ====================================================================
// 批量删除（五个实体共用一套逻辑）
// ====================================================================
/**
 * 后端只提供单条 DELETE 接口，没有批量端点，因此这里在前端并发调用单条删除。
 * 用 Promise.allSettled 而不是 Promise.all 的原因：
 *   被其他记录引用的实体（如作者已署名论文）后端会返回 409 拒绝删除，
 *   all 会在第一条失败时就中断，剩下的记录既没删也拿不到原因；
 *   allSettled 保证每条都尝试完，最后能汇总出"删掉了哪些、哪些删不掉、为什么"。
 */
interface BatchDeleteOptions<T> {
  entityName: string            // 实体中文名，用于提示语
  rows: T[]                     // 当前勾选的行
  labelOf: (row: T) => string   // 取行显示名，用于失败清单
  remove: (id: number) => Promise<unknown>
  reload: () => void            // 删完后刷新当前页列表
  clearSelection: () => void    // 删完后清空勾选（表格数据已变，旧勾选无意义）
}

const batchDeleting = ref(false)

async function runBatchDelete<T extends { id: number }>(opts: BatchDeleteOptions<T>) {
  const { entityName, rows, labelOf, remove, reload, clearSelection } = opts
  if (rows.length === 0) return
  // 一次二次确认覆盖整批，避免逐条弹窗打断操作
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${rows.length} 条${entityName}？删除后不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户点了取消，什么都不做
  }

  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(rows.map(r => remove(r.id)))
    const failed: string[] = []
    results.forEach((res, i) => {
      if (res.status !== 'rejected') return
      const err = res.reason
      // 409 = 该记录存在关联数据被后端拒绝；其余按通用错误提示
      const reason = err instanceof ApiError
        ? (err.problem?.detail ?? (err.status === 409 ? '存在关联数据，无法删除' : err.message))
        : '网络或服务器错误'
      // 单条原因截断到 60 字：后端兜底 500 会把异常类名和原始 message 拼进来，
      // 不截断的话 5 条叠加会让提示框糊成一片
      const brief = reason.length > 60 ? reason.slice(0, 60) + '…' : reason
      failed.push(`${labelOf(rows[i])}（${brief}）`)
    })

    const okCount = rows.length - failed.length
    if (failed.length === 0) {
      ElMessage.success(`已删除 ${okCount} 条${entityName}`)
    } else {
      // 失败清单可能很长，只展示前 5 条，完整原因可在表格中逐条删除时查看
      const preview = failed.slice(0, 5).join('；')
      const more = failed.length > 5 ? `；另有 ${failed.length - 5} 条` : ''
      ElMessage({
        type: okCount > 0 ? 'warning' : 'error',
        message: `成功 ${okCount} 条，失败 ${failed.length} 条：${preview}${more}`,
        duration: 6000,
        showClose: true
      })
    }
  } finally {
    batchDeleting.value = false
    clearSelection()
    reload()
  }
}

// ---- 各实体的勾选状态与批量删除入口 ----
const paperSelection = ref<Paper[]>([])
const authorSelection = ref<Author[]>([])
const instSelection = ref<Institution[]>([])
const kwSelection = ref<Keyword[]>([])
const venueSelection = ref<Venue[]>([])

function onPaperSelect(rows: Paper[]) { paperSelection.value = rows }
function onAuthorSelect(rows: Author[]) { authorSelection.value = rows }
function onInstSelect(rows: Institution[]) { instSelection.value = rows }
function onKwSelect(rows: Keyword[]) { kwSelection.value = rows }
function onVenueSelect(rows: Venue[]) { venueSelection.value = rows }

function batchDeletePapers() {
  runBatchDelete({
    entityName: '论文',
    rows: paperSelection.value,
    labelOf: r => r.title,
    remove: papersApi.remove,
    reload: () => loadPapers(),
    clearSelection: () => { paperSelection.value = [] }
  })
}

function batchDeleteAuthors() {
  runBatchDelete({
    entityName: '作者',
    rows: authorSelection.value,
    labelOf: r => r.displayName,
    remove: authorsApi.remove,
    reload: () => loadAuthors(),
    clearSelection: () => { authorSelection.value = [] }
  })
}

function batchDeleteInstitutions() {
  runBatchDelete({
    entityName: '机构',
    rows: instSelection.value,
    labelOf: r => r.displayName,
    remove: institutionsApi.remove,
    reload: () => loadInstitutions(),
    clearSelection: () => { instSelection.value = [] }
  })
}

function batchDeleteKeywords() {
  runBatchDelete({
    entityName: '关键词',
    rows: kwSelection.value,
    labelOf: r => r.name,
    remove: keywordsApi.remove,
    reload: () => loadKeywords(),
    clearSelection: () => { kwSelection.value = [] }
  })
}

function batchDeleteVenues() {
  runBatchDelete({
    entityName: '渠道',
    rows: venueSelection.value,
    labelOf: r => r.displayName,
    remove: venuesApi.remove,
    reload: () => loadVenues(),
    clearSelection: () => { venueSelection.value = [] }
  })
}

// ====================================================================
// Tab 切换
// ====================================================================
const activeTab = ref('papers')

/** 切换 tab 时自动加载对应实体的第一页数据（避免用户看到空白） */
function onTabChange(tab: string) {
  if (tab === 'papers') loadPapers(0)
  else if (tab === 'authors') loadAuthors(0)
  else if (tab === 'institutions') loadInstitutions(0)
  else if (tab === 'keywords') loadKeywords(0)
  else if (tab === 'venues') loadVenues(0)
}

// ====================================================================
// 论文 CRUD
// ====================================================================
const paperList = ref<Paper[]>([])
const paperLoading = ref(false)
const paperPage = ref(1) // el-pagination 的 current-page 从 1 起，传给后端时 -1
const paperTotal = ref(0)
const paperQuery = reactive({ keyword: '', paperType: '', size: 20 })

/** 加载论文列表：page 参数从 1 起（el-pagination），传给后端时转为 0 起 */
async function loadPapers(page?: number) {
  if (page !== undefined) paperPage.value = page
  paperLoading.value = true
  try {
    const res = await papersApi.list({
      keyword: paperQuery.keyword || undefined,
      paperType: paperQuery.paperType || undefined,
      page: paperPage.value - 1, // 后端 page 从 0 起
      size: paperQuery.size
    })
    paperList.value = res.items
    paperTotal.value = res.totalElements
  } catch (err) {
    ElMessage.error(extractError(err, '加载论文失败'))
  } finally {
    paperLoading.value = false
  }
}

// 论文弹窗状态
const paperDialogVisible = ref(false)
const paperDialogTitle = ref('')
const paperSaving = ref(false)
const paperForm = reactive<{
  id: number | null
  version: number | null
  title: string
  doi: string
  paperType: string
  language: string
  publicationDate: string
  abstractText: string
  citationCount: number
  venueId: number | null
  authors: { authorId: number; position: number; institutionId: number | null }[]
  keywordIds: number[]
  references: { citedPaperId: number | null; externalCitedDoi: string }[]
}>({
  id: null, version: null, title: '', doi: '', paperType: 'JOURNAL_ARTICLE',
  language: '', publicationDate: '', abstractText: '', citationCount: 0,
  venueId: null, authors: [], keywordIds: [], references: []
})

// 弹窗内的下拉选项（作者/机构/关键词/渠道/论文 用于关联选择）
const authorOptions = ref<Author[]>([])
const instOptions = ref<Institution[]>([])
const kwOptions = ref<Keyword[]>([])
const venueOptions = ref<Venue[]>([])
const paperOptions = ref<Paper[]>([])

/**
 * 打开论文弹窗：
 * - create 模式：重置表单为默认值
 * - edit 模式：用行数据填充表单（含 authors/keywords/references 的拆解）
 */
function openPaperDialog(mode: 'create' | 'edit', row?: Paper) {
  // 先加载下拉选项（并行请求，减少等待时间）
  Promise.all([
    authorsApi.list({ size: 100 }),
    institutionsApi.list({ size: 100 }),
    keywordsApi.list({ size: 100 }),
    venuesApi.list({ size: 100 }),
    papersApi.list({ size: 100 })
  ]).then(([au, inst, kw, ve, pa]) => {
    authorOptions.value = au.items
    instOptions.value = inst.items
    kwOptions.value = kw.items
    venueOptions.value = ve.items
    paperOptions.value = pa.items
  })

  if (mode === 'create') {
    Object.assign(paperForm, {
      id: null, version: null, title: '', doi: '', paperType: 'JOURNAL_ARTICLE',
      language: '', publicationDate: '', abstractText: '', citationCount: 0,
      venueId: null, authors: [], keywordIds: [], references: []
    })
    paperDialogTitle.value = '新建论文'
  } else if (row) {
    Object.assign(paperForm, {
      id: row.id,
      version: row.version,
      title: row.title,
      doi: row.doi ?? '',
      paperType: row.paperType,
      language: row.language ?? '',
      publicationDate: row.publicationDate ?? '',
      abstractText: row.abstractText ?? '',
      citationCount: row.citationCount,
      venueId: row.venue?.id ?? null,
      // 拆解后端返回的 authors 引用为表单行结构
      authors: row.authors.map(a => ({
        authorId: a.authorId,
        position: a.position,
        institutionId: a.institutionId ?? null
      })),
      // 关键词：只取 id 数组
      keywordIds: row.keywords.map(k => k.id),
      // 引用：拆解为表单行结构
      references: row.references.map(r => ({
        citedPaperId: r.citedPaperId,
        externalCitedDoi: r.externalCitedDoi ?? ''
      }))
    })
    paperDialogTitle.value = '编辑论文'
  }
  paperDialogVisible.value = true
}

/** 保存论文：根据有无 id 决定 create 还是 update */
async function savePaper() {
  if (!paperForm.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  paperSaving.value = true
  try {
    const body: PaperUpsert = {
      title: paperForm.title,
      doi: paperForm.doi || undefined,
      paperType: paperForm.paperType,
      language: paperForm.language || undefined,
      publicationDate: paperForm.publicationDate || undefined,
      abstractText: paperForm.abstractText || undefined,
      citationCount: paperForm.citationCount,
      venueId: paperForm.venueId,
      // 过滤掉 authorId 为 0 的无效行
      authors: paperForm.authors.filter(a => a.authorId > 0),
      keywordIds: paperForm.keywordIds,
      // 过滤掉两个字段都为空的无效引用行
      references: paperForm.references.filter(r => r.citedPaperId || r.externalCitedDoi),
      version: paperForm.version // 更新时必带，创建时忽略
    }
    if (paperForm.id) {
      await papersApi.update(paperForm.id, body)
      ElMessage.success('更新成功')
    } else {
      await papersApi.create(body)
      ElMessage.success('创建成功')
    }
    paperDialogVisible.value = false
    loadPapers()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      // 乐观锁冲突：数据已被他人修改，需刷新后重试
      ElMessage.error('数据已被他人修改，请刷新后重试')
      loadPapers()
    } else {
      ElMessage.error(extractError(err, '保存失败'))
    }
  } finally {
    paperSaving.value = false
  }
}

/** 删除论文：二次确认后调用 API */
async function deletePaper(row: Paper) {
  try {
    await ElMessageBox.confirm(`确认删除论文「${row.title}」？`, '删除确认', { type: 'warning' })
    await papersApi.remove(row.id)
    ElMessage.success('已删除')
    loadPapers()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error(err.problem?.detail ?? '该论文被引用中，无法删除')
    } else if (err !== 'cancel') {
      ElMessage.error(extractError(err, '删除失败'))
    }
  }
}

// ====================================================================
// 作者 CRUD
// ====================================================================
const authorList = ref<Author[]>([])
const authorLoading = ref(false)
const authorPage = ref(1)
const authorTotal = ref(0)
const authorQuery = reactive({ keyword: '', size: 20 })

async function loadAuthors(page?: number) {
  if (page !== undefined) authorPage.value = page
  authorLoading.value = true
  try {
    const res = await authorsApi.list({ keyword: authorQuery.keyword || undefined, page: authorPage.value - 1, size: authorQuery.size })
    authorList.value = res.items
    authorTotal.value = res.totalElements
  } catch (err) {
    ElMessage.error(extractError(err, '加载作者失败'))
  } finally {
    authorLoading.value = false
  }
}

const authorDialogVisible = ref(false)
const authorDialogTitle = ref('')
const authorSaving = ref(false)
const authorForm = reactive<{ id: number | null; version: number | null; displayName: string; orcid: string }>({
  id: null, version: null, displayName: '', orcid: ''
})

function openAuthorDialog(mode: 'create' | 'edit', row?: Author) {
  if (mode === 'create') {
    Object.assign(authorForm, { id: null, version: null, displayName: '', orcid: '' })
    authorDialogTitle.value = '新建作者'
  } else if (row) {
    Object.assign(authorForm, { id: row.id, version: row.version, displayName: row.displayName, orcid: row.orcid ?? '' })
    authorDialogTitle.value = '编辑作者'
  }
  authorDialogVisible.value = true
}

async function saveAuthor() {
  if (!authorForm.displayName.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  authorSaving.value = true
  try {
    const body: AuthorUpsert = { displayName: authorForm.displayName, orcid: authorForm.orcid || undefined, version: authorForm.version }
    if (authorForm.id) {
      await authorsApi.update(authorForm.id, body)
      ElMessage.success('更新成功')
    } else {
      await authorsApi.create(body)
      ElMessage.success('创建成功')
    }
    authorDialogVisible.value = false
    loadAuthors()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error('数据已被他人修改，请刷新后重试')
      loadAuthors()
    } else {
      ElMessage.error(extractError(err, '保存失败'))
    }
  } finally {
    authorSaving.value = false
  }
}

async function deleteAuthor(row: Author) {
  try {
    await ElMessageBox.confirm(`确认删除作者「${row.displayName}」？`, '删除确认', { type: 'warning' })
    await authorsApi.remove(row.id)
    ElMessage.success('已删除')
    loadAuthors()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error(err.problem?.detail ?? '该作者有关联论文，无法删除')
    } else if (err !== 'cancel') {
      ElMessage.error(extractError(err, '删除失败'))
    }
  }
}

// ====================================================================
// 机构 CRUD
// ====================================================================
const instList = ref<Institution[]>([])
const instLoading = ref(false)
const instPage = ref(1)
const instTotal = ref(0)
const instQuery = reactive({ keyword: '', size: 20 })

async function loadInstitutions(page?: number) {
  if (page !== undefined) instPage.value = page
  instLoading.value = true
  try {
    const res = await institutionsApi.list({ keyword: instQuery.keyword || undefined, page: instPage.value - 1, size: instQuery.size })
    instList.value = res.items
    instTotal.value = res.totalElements
  } catch (err) {
    ElMessage.error(extractError(err, '加载机构失败'))
  } finally {
    instLoading.value = false
  }
}

const instDialogVisible = ref(false)
const instDialogTitle = ref('')
const instSaving = ref(false)
const instForm = reactive<{ id: number | null; version: number | null; displayName: string; countryCode: string; institutionType: string }>({
  id: null, version: null, displayName: '', countryCode: '', institutionType: ''
})

function openInstDialog(mode: 'create' | 'edit', row?: Institution) {
  if (mode === 'create') {
    Object.assign(instForm, { id: null, version: null, displayName: '', countryCode: '', institutionType: '' })
    instDialogTitle.value = '新建机构'
  } else if (row) {
    Object.assign(instForm, { id: row.id, version: row.version, displayName: row.displayName, countryCode: row.countryCode ?? '', institutionType: row.institutionType ?? '' })
    instDialogTitle.value = '编辑机构'
  }
  instDialogVisible.value = true
}

async function saveInst() {
  if (!instForm.displayName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  instSaving.value = true
  try {
    const body: InstitutionUpsert = {
      displayName: instForm.displayName,
      countryCode: instForm.countryCode || undefined,
      institutionType: instForm.institutionType || undefined,
      version: instForm.version
    }
    if (instForm.id) {
      await institutionsApi.update(instForm.id, body)
      ElMessage.success('更新成功')
    } else {
      await institutionsApi.create(body)
      ElMessage.success('创建成功')
    }
    instDialogVisible.value = false
    loadInstitutions()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error('数据已被他修改，请刷新后重试')
      loadInstitutions()
    } else {
      ElMessage.error(extractError(err, '保存失败'))
    }
  } finally {
    instSaving.value = false
  }
}

async function deleteInst(row: Institution) {
  try {
    await ElMessageBox.confirm(`确认删除机构「${row.displayName}」？`, '删除确认', { type: 'warning' })
    await institutionsApi.remove(row.id)
    ElMessage.success('已删除')
    loadInstitutions()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error(err.problem?.detail ?? '该机构有关联作者，无法删除')
    } else if (err !== 'cancel') {
      ElMessage.error(extractError(err, '删除失败'))
    }
  }
}

// ====================================================================
// 关键词 CRUD
// ====================================================================
const kwList = ref<Keyword[]>([])
const kwLoading = ref(false)
const kwPage = ref(1)
const kwTotal = ref(0)
const kwQuery = reactive({ keyword: '', size: 20 })

async function loadKeywords(page?: number) {
  if (page !== undefined) kwPage.value = page
  kwLoading.value = true
  try {
    const res = await keywordsApi.list({ keyword: kwQuery.keyword || undefined, page: kwPage.value - 1, size: kwQuery.size })
    kwList.value = res.items
    kwTotal.value = res.totalElements
  } catch (err) {
    ElMessage.error(extractError(err, '加载关键词失败'))
  } finally {
    kwLoading.value = false
  }
}

const kwDialogVisible = ref(false)
const kwDialogTitle = ref('')
const kwSaving = ref(false)
const kwForm = reactive<{ id: number | null; name: string; fieldName: string }>({
  id: null, name: '', fieldName: ''
})

function openKwDialog(mode: 'create' | 'edit', row?: Keyword) {
  if (mode === 'create') {
    Object.assign(kwForm, { id: null, name: '', fieldName: '' })
    kwDialogTitle.value = '新建关键词'
  } else if (row) {
    Object.assign(kwForm, { id: row.id, name: row.name, fieldName: row.fieldName ?? '' })
    kwDialogTitle.value = '编辑关键词'
  }
  kwDialogVisible.value = true
}

async function saveKw() {
  if (!kwForm.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  kwSaving.value = true
  try {
    const body: KeywordUpsert = { name: kwForm.name, fieldName: kwForm.fieldName || undefined }
    if (kwForm.id) {
      await keywordsApi.update(kwForm.id, body)
      ElMessage.success('更新成功')
    } else {
      await keywordsApi.create(body)
      ElMessage.success('创建成功')
    }
    kwDialogVisible.value = false
    loadKeywords()
  } catch (err) {
    ElMessage.error(extractError(err, '保存失败'))
  } finally {
    kwSaving.value = false
  }
}

async function deleteKw(row: Keyword) {
  try {
    await ElMessageBox.confirm(`确认删除关键词「${row.name}」？`, '删除确认', { type: 'warning' })
    await keywordsApi.remove(row.id)
    ElMessage.success('已删除')
    loadKeywords()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error(err.problem?.detail ?? '该关键词被论文使用，无法删除')
    } else if (err !== 'cancel') {
      ElMessage.error(extractError(err, '删除失败'))
    }
  }
}

// ====================================================================
// 渠道 CRUD
// ====================================================================
const venueList = ref<Venue[]>([])
const venueLoading = ref(false)
const venuePage = ref(1)
const venueTotal = ref(0)
const venueQuery = reactive({ keyword: '', size: 20 })

async function loadVenues(page?: number) {
  if (page !== undefined) venuePage.value = page
  venueLoading.value = true
  try {
    const res = await venuesApi.list({ keyword: venueQuery.keyword || undefined, page: venuePage.value - 1, size: venueQuery.size })
    venueList.value = res.items
    venueTotal.value = res.totalElements
  } catch (err) {
    ElMessage.error(extractError(err, '加载渠道失败'))
  } finally {
    venueLoading.value = false
  }
}

const venueDialogVisible = ref(false)
const venueDialogTitle = ref('')
const venueSaving = ref(false)
const venueForm = reactive<{ id: number | null; displayName: string; issn: string; venueType: string }>({
  id: null, displayName: '', issn: '', venueType: ''
})

function openVenueDialog(mode: 'create' | 'edit', row?: Venue) {
  if (mode === 'create') {
    Object.assign(venueForm, { id: null, displayName: '', issn: '', venueType: '' })
    venueDialogTitle.value = '新建渠道'
  } else if (row) {
    Object.assign(venueForm, { id: row.id, displayName: row.displayName, issn: row.issn ?? '', venueType: row.venueType ?? '' })
    venueDialogTitle.value = '编辑渠道'
  }
  venueDialogVisible.value = true
}

async function saveVenue() {
  if (!venueForm.displayName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  venueSaving.value = true
  try {
    const body: VenueUpsert = {
      displayName: venueForm.displayName,
      issn: venueForm.issn || undefined,
      venueType: venueForm.venueType || undefined
    }
    if (venueForm.id) {
      await venuesApi.update(venueForm.id, body)
      ElMessage.success('更新成功')
    } else {
      await venuesApi.create(body)
      ElMessage.success('创建成功')
    }
    venueDialogVisible.value = false
    loadVenues()
  } catch (err) {
    ElMessage.error(extractError(err, '保存失败'))
  } finally {
    venueSaving.value = false
  }
}

async function deleteVenue(row: Venue) {
  try {
    await ElMessageBox.confirm(`确认删除渠道「${row.displayName}」？`, '删除确认', { type: 'warning' })
    await venuesApi.remove(row.id)
    ElMessage.success('已删除')
    loadVenues()
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      ElMessage.error(err.problem?.detail ?? '该渠道有关联论文，无法删除')
    } else if (err !== 'cancel') {
      ElMessage.error(extractError(err, '删除失败'))
    }
  }
}

// ====================================================================
// 初始化：页面挂载时加载当前 tab 的数据
// ====================================================================
onMounted(() => {
  loadPapers(0)
})
</script>

<style scoped>
/* 数据管理页布局 */
.data-view {
  padding: var(--space-4);
}

/* 工具栏：搜索框 + 新建按钮，水平排列 */
.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
}

/* 分页器上边距 */
.pager {
  margin-top: var(--space-4);
  justify-content: flex-end;
}

/* 弹窗内动态列表（作者行/引用行）的容器 */
.sub-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
}

/* 动态列表的每一行：水平排列各个控件 */
.sub-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}
</style>
