import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ScholarlySourcePanel from './ScholarlySourcePanel.vue'
import type { AchievementDetail } from '@/types/api'

const source: AchievementDetail['sources'][number] = {
  sourceRecordId: 1, rawRecordId: 1, sourceCode: 'OPENALEX', externalRecordId: 'W1',
  sourceUrl: null, firstSeenAt: '', lastSeenAt: '', parserVersion: 'openalex-v2',
}

describe('来源学术指标', () => {
  it('区分零、未标记和未知，不把来源计数相加', () => {
    const wrapper = mount(ScholarlySourcePanel, { props: { sources: [
      { ...source, scholarlyMetadata: { observedAt: '2026-09-05T00:00:00Z', citedByCount: 0,
        retracted: false, openAccess: true, openAccessStatus: 'green', versionRelations: [] } },
      { ...source, sourceRecordId: 2, sourceCode: 'CROSSREF' },
    ] } })
    expect(wrapper.findAll('article')).toHaveLength(2)
    expect(wrapper.findAll('article')[0]!.text()).toContain('未标记撤稿')
    expect(wrapper.findAll('article')[0]!.findAll('dd')[0]!.text()).toBe('0')
    expect(wrapper.findAll('article')[1]!.findAll('dd')[0]!.text()).toBe('未知')
    expect(wrapper.text()).toContain('尚未采集')
  })

  it('撤稿标记突出显示，版本链接固定到DOI解析站并编码路径', () => {
    const wrapper = mount(ScholarlySourcePanel, { props: { sources: [
      { ...source, scholarlyMetadata: { observedAt: '2026-09-05T00:00:00Z', citedByCount: 7,
        retracted: true, openAccess: null, openAccessStatus: null,
        versionRelations: [{ relationType: 'is-preprint-of', targetDoi: '10.1000/part?x=1' }] } },
    ] } })
    expect(wrapper.text()).toContain('来源标记已撤稿')
    expect(wrapper.get('a').attributes('href')).toBe('https://doi.org/10.1000%2Fpart%3Fx%3D1')
    expect(wrapper.get('a').attributes('rel')).toBe('noopener noreferrer')
  })
})
