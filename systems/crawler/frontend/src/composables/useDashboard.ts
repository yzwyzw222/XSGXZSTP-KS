import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { analyticsApi } from '@/services/business'
import { authorImportApi, type ImportSummary } from '@/services/author-import'
import { getAudits } from '@/services/audits'
import { toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { AnalyticsCollaborationResponse, AnalyticsDistributionResponse, AnalyticsOverview, AnalyticsTrendResponse, AuditLog, PageResponse, Permission } from '@/types/api'

export interface DashboardRegion<T> { data: T | null; loading: boolean; error: string; allowed: boolean; loadedAt: string }

/** 聚合页独立降级；请求序号防止快速筛选或卸载后的迟到响应覆盖当前视图。 */
export function useDashboard() {
  const session = useSessionStore()
  const yearRange = ref('all')
  const topicId = ref('ALL')
  const region = <T>(permission: Permission): DashboardRegion<T> => ({ data: null, loading: false, error: '', allowed: session.hasPermission(permission), loadedAt: '' })
  const state = reactive({
    overview: region<AnalyticsOverview>('ANALYTICS_READ'),
    trends: region<AnalyticsTrendResponse>('ANALYTICS_READ'),
    distributions: region<AnalyticsDistributionResponse>('ANALYTICS_READ'),
    collaboration: region<AnalyticsCollaborationResponse>('ANALYTICS_READ'),
    imports: region<ImportSummary[]>('AUTHOR_IMPORT'),
    audits: region<PageResponse<AuditLog>>('AUDIT_READ'),
  })
  let sequence = 0
  const loading = computed(() => Object.values(state).some(item => item.loading))
  async function refresh(): Promise<void> {
    const current = ++sequence
    const filters = {
      ...(yearRange.value !== 'all' ? { publicationYearFrom: new Date().getFullYear() - Number(yearRange.value) + 1 } : {}),
      ...(topicId.value !== 'ALL' ? { topicId: Number(topicId.value) } : {}),
    }
    async function read<T>(target: DashboardRegion<T>, fetch: () => Promise<T>): Promise<void> {
      if (!target.allowed) return
      target.loading = true
      target.error = ''
      try {
        const data = await fetch()
        if (current !== sequence) return
        target.data = data
        target.loadedAt = new Date().toISOString()
      } catch (error) {
        if (current === sequence) target.error = toErrorMessage(error)
      } finally {
        if (current === sequence) target.loading = false
      }
    }
    await Promise.allSettled([
      read(state.overview, () => analyticsApi.overview(filters)),
      read(state.trends, () => analyticsApi.trends(filters)),
      read(state.distributions, () => analyticsApi.distributions(filters)),
      read(state.collaboration, () => analyticsApi.collaboration(filters, 20)),
      read(state.imports, () => authorImportApi.recent()),
      read(state.audits, () => getAudits({ category: 'OPERATION' }, 0, 5)),
    ])
  }
  onMounted(refresh)
  onBeforeUnmount(() => { sequence++ })
  return { state, loading, yearRange, topicId, refresh }
}
