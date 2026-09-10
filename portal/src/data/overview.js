// 以下统计仅用于入口视觉演示；系统接入状态仍由现有服务独立读取。
export const metrics = [
  { label: '论文数量', value: '8,429,617', change: '+12.3%', icon: 'file-earmark-text' },
  { label: '作者数量', value: '2,356,781', change: '+8.7%', icon: 'person' },
  { label: '机构数量', value: '98,421', change: '+6.1%', icon: 'bank' },
  { label: '知识图谱节点数', value: '12,843,506', change: '+15.2%', icon: 'share' },
  { label: '数据源平台', value: '32', change: '+3', icon: 'database' },
  { label: '可视化分析', value: '50+', change: '', icon: 'graph-up-arrow' },
]

export const fields = [
  { label: '计算机科学', value: 24, color: '#4b90ff' },
  { label: '材料科学', value: 18, color: '#60dce9' },
  { label: '生物医学', value: 15, color: '#65e6b0' },
  { label: '工程技术', value: 12, color: '#ffcd72' },
  { label: '社会科学', value: 10, color: '#d78472' },
  { label: '其他领域', value: 21, color: '#819bc7' },
]

export const trend = [
  { label: '2020', value: 8 }, { label: '2021', value: 10 }, { label: '2022', value: 17 },
  { label: '2023', value: 21 }, { label: '2024', value: 30 },
]

export const researchTopics = ['人工智能', '大语言模型', '知识图谱', '深度学习', '数据挖掘', '科学计算', '生物信息学', '区块链', '碳中和', '智能制造', '精准医疗', '教育信息化', '学术合作', '实体抽取', '可视化']

export const systemVisuals = {
  relation: {
    number: '01', image: '/assets/portal/images/relation-network.webp', caption: 'KNOWLEDGE\nGRAPH\nRELATIONSHIP\nNETWORK',
    description: '构建学者、论文、机构等多维学术关系网络，挖掘学术合作与知识演化脉络',
    keywords: ['人工智能', '知识图谱', '数据挖掘', '科学计算', '生物信息学', '碳中和', '学术合作'],
  },
  crawler: {
    number: '02', image: '/assets/portal/images/crawler-analytics.webp', caption: 'INFORMATION\nCOLLECTION\nANALYTICS\nVISUALIZATION',
    description: '面向多平台的学术成果信息采集、数据整合与可视化分析，助力科研态势洞察',
    keywords: ['人工智能', '数据挖掘', '科学计算', '区块链', '碳中和', '智能制造', '教育信息化', '可视化'],
  },
}
