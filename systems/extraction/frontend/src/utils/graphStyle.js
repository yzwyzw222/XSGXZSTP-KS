function cv(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function nodeStyle(type) {
  const key = type.toLowerCase()
  return {
    shape: type === 'PAPER' ? 'round-rectangle' : type === 'VENUE' ? 'diamond' : 'ellipse',
    'background-color': cv(`--graph-${key}`),
    'border-width': 2,
    'border-color': cv(`--graph-${key}-border`),
    label: 'data(label)',
    'font-size': type === 'AUTHOR' ? 10 : 9,
    color: cv('--text-primary'),
    'text-valign': 'center',
    'text-halign': 'center',
    width: type === 'PAPER' ? 50 : type === 'AUTHOR' ? 40 : 35,
    height: type === 'PAPER' ? 30 : type === 'AUTHOR' ? 40 : 35,
    'text-wrap': 'wrap',
    'text-max-width': '80px',
    'text-overflow-wrap': 'ellipsis'
  }
}

export function buildCytoscapeStyle() {
  const textPrimary = cv('--text-primary')
  const textSecondary = cv('--text-secondary')
  const edgeColor = cv('--graph-edge')
  const selectColor = cv('--graph-select')

  return [
    { selector: 'node[nodeType="AUTHOR"]', style: nodeStyle('AUTHOR') },
    { selector: 'node[nodeType="PAPER"]', style: nodeStyle('PAPER') },
    { selector: 'node[nodeType="VENUE"]', style: nodeStyle('VENUE') },
    { selector: 'node[nodeType="ENTITY"]', style: nodeStyle('ENTITY') },
    {
      selector: 'edge',
      style: {
        width: 1.5,
        'line-color': edgeColor,
        'target-arrow-color': edgeColor,
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        'arrow-scale': 0.8,
        label: 'data(label)',
        'font-size': 8,
        color: textSecondary,
        'text-rotation': 'autorotate',
        'text-margin-y': -8
      }
    },
    {
      selector: 'node:selected',
      style: {
        'border-width': 3,
        'border-color': selectColor,
        'overlay-color': selectColor,
        'overlay-padding': 4,
        'overlay-opacity': 0.15
      }
    },
    {
      selector: 'edge:selected',
      style: {
        width: 3,
        'line-color': selectColor,
        'target-arrow-color': selectColor
      }
    }
  ]
}

export const graphStyles = {
  AUTHOR: { label: '学者' },
  PAPER: { label: '论文' },
  VENUE: { label: '期刊/会议' },
  ENTITY: { label: '实体' }
}

export const cytoscapeLayout = {
  name: 'cose',
  animate: true,
  animationDuration: 500,
  nodeRepulsion: 8000,
  idealEdgeLength: 100,
  edgeElasticity: 100,
  gravity: 0.25,
  numIter: 1000,
  padding: 30
}
