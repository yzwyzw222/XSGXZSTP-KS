/** 状态来自当前入口服务；请求失败时不推测任何系统已启用。 */
export async function getSystems(signal) {
  const response = await fetch('/integration.json', { cache: 'no-store', signal })
  if (!response.ok) throw new Error('暂时无法读取接入状态，请稍后重试。')
  const data = await response.json()
  const ids = ['relation', 'crawler']
  if (!Array.isArray(data?.systems) || data.systems.length !== ids.length || ids.some(id =>
    data.systems.filter(system => system.id === id && system.path === `/${id}/` &&
      ['maintenance', 'enabled'].includes(system.status)).length !== 1)) {
    throw new Error('接入状态配置有误，业务入口暂未开放。')
  }
  return data.systems
}
