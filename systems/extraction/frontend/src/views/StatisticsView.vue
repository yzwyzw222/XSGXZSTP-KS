<template>
  <div class="statistics-view">
    <h2 class="page-title">数据统计</h2>

    <div class="statistics-view__cards">
      <div class="stat-card" v-for="s in summaryCards" :key="s.label">
        <span class="stat-card__value">{{ s.value }}</span>
        <span class="stat-card__label">{{ s.label }}</span>
      </div>
    </div>

    <div class="statistics-view__charts">
      <div class="statistics-view__chart">
        <h3>论文年份分布</h3>
        <YearDistributionChart :data="yearData" />
      </div>
      <div class="statistics-view__chart">
        <h3>引用排行 Top 10</h3>
        <CitationChart :data="citationData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getStatistics } from '../api/graph.js'
import { formatNumber } from '../utils/formatters.js'
import YearDistributionChart from '../components/charts/YearDistributionChart.vue'
import CitationChart from '../components/charts/CitationChart.vue'

const stats = ref(null)

const summaryCards = ref([])
const yearData = ref([])
const citationData = ref([])

onMounted(async () => {
  try {
    stats.value = await getStatistics()
    const s = stats.value

    summaryCards.value = [
      { label: '论文总数', value: formatNumber(s.paperCount) },
      { label: '学者总数', value: formatNumber(s.authorCount) },
      { label: '实体总数', value: formatNumber(s.entityCount) },
      { label: '关系总数', value: formatNumber(s.relationshipCount) }
    ]

    yearData.value = (s.yearDistribution || []).map(d => ({ label: d.year, value: d.count }))
    citationData.value = (s.topCitedPapers || []).map(d => ({ label: d.title?.slice(0, 20) || '', value: d.citationCount }))
  } catch {
    // ignore
  }
})
</script>

<style lang="scss" scoped>
.statistics-view {
  &__cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: var(--spacing-md);
    margin-bottom: var(--spacing-lg);
  }

  &__charts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
    gap: var(--spacing-md);
  }

  &__chart {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: var(--spacing-md);

    h3 {
      color: var(--text-primary);
      font-size: 0.95rem;
      margin: 0 0 var(--spacing-sm);
    }
  }
}

.stat-card {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  text-align: center;

  &__value {
    display: block;
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--accent);
  }

  &__label {
    font-size: 0.8rem;
    color: var(--text-tertiary);
  }
}

.page-title {
  color: var(--text-primary);
  font-size: 1.25rem;
  margin: 0 0 var(--spacing-md);
}
</style>
