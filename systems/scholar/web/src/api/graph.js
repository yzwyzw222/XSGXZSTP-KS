import { get } from './request'

// 初始学术成果知识图谱（最新论文为中心的局部子图）
export function fetchInitialGraph() {
  return get('/scholar-graph/initial')
}

// 按节点动态扩展子图
export function fetchExpandGraph(nodeId) {
  return get('/scholar-graph/expand', { params: { nodeId } })
}

// 图谱统计（节点构成 + Top 排行）
export function fetchGraphStats() {
  return get('/scholar-graph/stats')
}