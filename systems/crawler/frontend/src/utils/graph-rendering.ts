import type { Core, ElementDefinition, Position } from 'cytoscape'

/** 按业务标识增量同步图元素，已有节点的拖拽位置和选择上下文保持不变。 */
export function reconcileGraphElements(cy: Core, elements: ElementDefinition[], rootNodeId?: string) {
  const nodes = new Map(elements.filter(element => !element.data.source).map(element => [String(element.data.id), element]))
  const edges = elements.filter(element => element.data.source
    && nodes.has(String(element.data.source)) && nodes.has(String(element.data.target)))
  const next = new Map([...nodes.values(), ...edges].map(element => [String(element.data.id), element]))
  const retained = cy.nodes().filter(node => nodes.has(node.id()))
  const initial = retained.empty()
  const addedIds = [...nodes.keys()].filter(id => cy.getElementById(id).empty())
  const addedSet = new Set(addedIds)

  cy.batch(() => {
    cy.elements().filter(element => !next.has(element.id())).remove()
    for (const [id, definition] of next) {
      const current = cy.getElementById(id)
      if (current.empty()) cy.add(definition)
      else if (current.group() === 'edges' && (current.source().id() !== definition.data.source || current.target().id() !== definition.data.target)) {
        current.remove()
        cy.add(definition)
      } else current.data(definition.data)
    }
  })

  const targets = new Map<string, Position>()
  if (!initial) {
    const placed = retained.nodes().map(node => ({ ...node.position() }))
    const root = cy.getElementById(rootNodeId ?? '')
    for (const [index, id] of addedIds.entries()) {
      const node = cy.getElementById(id)
      const neighbor = node.neighborhood().nodes().filter(item => !addedSet.has(item.id())).nodes().first()
      const origin = neighbor.nonempty() ? neighbor.position() : root.nonempty() ? root.position() : { x: 0, y: 0 }
      let target = { ...origin }
      // 有界的确定性放置只生成坐标，不引入随机业务数据或重新打散既有图形。
      for (let attempt = 0; attempt < 32; attempt++) {
        const angle = (index + attempt) * 2.399963 - Math.PI / 2
        const radius = 140 + Math.floor(attempt / 6) * 70
        target = { x: origin.x + Math.cos(angle) * radius, y: origin.y + Math.sin(angle) * radius }
        if (placed.every(position => Math.hypot(target.x - position.x, target.y - position.y) >= 110)) break
      }
      node.position({ x: origin.x + (target.x - origin.x) * .75, y: origin.y + (target.y - origin.y) * .75 })
      targets.set(id, target)
      placed.push(target)
    }
  }
  return { initial, addedIds, targets }
}
