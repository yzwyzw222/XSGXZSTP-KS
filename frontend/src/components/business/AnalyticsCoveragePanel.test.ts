import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsCoveragePanel from './AnalyticsCoveragePanel.vue'

const coverage = {
  withDoiCount: 1, withPublicationYearCount: 2, withAbstractCount: 1,
  withCitationCount: 0, withOpenAccessStatusCount: 1, withRetractionStatusCount: 0,
  authorshipsMayBeIncompleteCount: 1,
}
describe('本地字段覆盖率', () => {
  it('使用当前规范成果数作为分母并展示未提供指标', () => {
    const wrapper = mount(AnalyticsCoveragePanel, { props: { total: 2, coverage } })
    expect(wrapper.findAll('strong').map((item) => item.text())).toEqual(['50.0%', '100.0%', '50.0%', '0.0%', '50.0%', '0.0%'])
    expect(wrapper.text()).toContain('不表示全球学术成果的采集覆盖率')
    expect(wrapper.text()).toContain('1 项成果的作者署名可能不完整')
  })
  it('空范围无比例，不输出NaN或虚构100%覆盖', () => {
    const wrapper = mount(AnalyticsCoveragePanel, { props: { total: 0, coverage: {
      withDoiCount: 0, withPublicationYearCount: 0, withAbstractCount: 0,
      withCitationCount: 0, withOpenAccessStatusCount: 0, withRetractionStatusCount: 0,
      authorshipsMayBeIncompleteCount: 0,
    } } })
    expect(wrapper.findAll('strong').every((item) => item.text() === '—')).toBe(true)
    expect(wrapper.text()).not.toContain('NaN')
  })
})
