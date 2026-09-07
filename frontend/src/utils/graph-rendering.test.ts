import cytoscape, { type Core, type ElementDefinition } from 'cytoscape'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { reconcileGraphElements } from '@/utils/graph-rendering'

const nodes: ElementDefinition[] = [{ data: { id: 'a', label: '成果' } }, { data: { id: 'b', label: '作者' } }]
const edge = { data: { id: 'e', source: 'a', target: 'b' } }
let cy: Core
beforeEach(() => { cy = cytoscape({ headless: true }) })
afterEach(() => cy.destroy())

describe('图形增量同步', () => {
  it('初始加载和空结果使用明确的布局信号', () => {
    expect(reconcileGraphElements(cy, [...nodes, edge]).initial).toBe(true)
    reconcileGraphElements(cy, [])
    expect(cy.elements()).toHaveLength(0)
    expect(reconcileGraphElements(cy, nodes).initial).toBe(true)
  })

  it('刷新标签时保留元素实例、拖拽位置、选择和镜头', () => {
    reconcileGraphElements(cy, [...nodes, edge])
    const original = cy.getElementById('a')
    original.position({ x: 125, y: 240 }).select()
    cy.viewport({ zoom: .8, pan: { x: 100, y: 200 } })
    const result = reconcileGraphElements(cy, [{ data: { id: 'a', label: '更新题名' } }, nodes[1]!, edge])
    expect(result.addedIds).toEqual([])
    expect(cy.getElementById('a')[0]).toBe(original[0])
    expect(original.position()).toEqual({ x: 125, y: 240 })
    expect(original.selected()).toBe(true)
    expect(original.data('label')).toBe('更新题名')
    expect(cy.pan()).toEqual({ x: 100, y: 200 })
    expect(cy.zoom()).toBe(.8)
  })

  it('展开只为新增节点分配有限坐标，连续刷新不重新排布', () => {
    reconcileGraphElements(cy, [...nodes, edge])
    cy.getElementById('a').position({ x: 200, y: 200 })
    cy.getElementById('b').position({ x: 100, y: 100 })
    const expanded = [...nodes, edge, { data: { id: 'c' } }, { data: { id: 'f', source: 'b', target: 'c' } }]
    const result = reconcileGraphElements(cy, expanded, 'a')
    expect(result.initial).toBe(false)
    expect([...result.targets.keys()]).toEqual(['c'])
    const target = result.targets.get('c')!
    expect(Number.isFinite(target.x) && Number.isFinite(target.y)).toBe(true)
    expect(cy.getElementById('a').position()).toEqual({ x: 200, y: 200 })
    expect(cy.getElementById('b').position()).toEqual({ x: 100, y: 100 })
    expect(reconcileGraphElements(cy, expanded).targets.size).toBe(0)
  })

  it('删除不存在的节点和悬空关系，并按标识去重', () => {
    reconcileGraphElements(cy, [...nodes, edge])
    reconcileGraphElements(cy, [nodes[0]!, nodes[0]!, edge])
    expect(cy.nodes().map(node => node.id())).toEqual(['a'])
    expect(cy.edges()).toHaveLength(0)
  })

  it('同一关系标识的端点更新时不保留旧连线', () => {
    reconcileGraphElements(cy, [...nodes, edge])
    reconcileGraphElements(cy, [...nodes, { data: { id: 'e', source: 'b', target: 'a' } }])
    expect(cy.getElementById('e').source().id()).toBe('b')
    expect(cy.getElementById('e').target().id()).toBe('a')
  })
})
