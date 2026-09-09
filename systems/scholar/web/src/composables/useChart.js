import * as echarts from 'echarts'
import { ref, onMounted, onBeforeUnmount } from 'vue'

/**
 * ECharts 生命周期封装：初始化、resize 监听、销毁。
 * 用法：const { chartRef, setOption } = useChart()
 */
export function useChart() {
  const chartRef = ref(null)
  let chart = null

  onMounted(() => {
    if (chartRef.value) {
      chart = echarts.init(chartRef.value)
      window.addEventListener('resize', resize)
    }
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    if (chart) {
      chart.dispose()
      chart = null
    }
  })

  function resize() {
    if (chart) chart.resize()
  }

  function setOption(option, notMerge = false) {
    if (chart) chart.setOption(option, notMerge)
  }

  return { chartRef, setOption, resize }
}
