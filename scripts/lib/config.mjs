import { createHash } from 'node:crypto'
import { existsSync, readFileSync, realpathSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const rootDirectory = fileURLToPath(new URL('../../', import.meta.url))
export const systemIds = ['relation', 'crawler']
const statuses = ['maintenance', 'enabled']

function requireValue(condition, message) {
  if (!condition) throw new Error(`接入配置错误：${message}`)
}

export function workspacePath(root, relative, prefix = '', allowBoundary = false) {
  requireValue(typeof relative === 'string' && relative.length > 0, '路径不能为空')
  requireValue(!path.isAbsolute(relative) && !relative.includes('\\'), '只接受仓库相对路径')
  const absolute = path.resolve(root, relative)
  const boundary = path.resolve(root, prefix)
  requireValue(absolute.startsWith(`${boundary}${path.sep}`) || (allowBoundary && absolute === boundary), '路径必须位于所属目录内')
  if (existsSync(absolute)) {
    const resolved = realpathSync(absolute)
    const resolvedBoundary = realpathSync(boundary)
    requireValue(resolved.startsWith(`${resolvedBoundary}${path.sep}`) || (allowBoundary && resolved === resolvedBoundary), '不允许路径链接越界')
  }
  return absolute
}

/** 启用必须同时提供运行配置和验收记录，拼写错误不能意外开放后端。 */
export function validateConfig(config) {
  requireValue(config?.version === 1, '不支持的版本')
  const ports = new Set()
  const port = value => {
    requireValue(Number.isInteger(value) && value >= 1024 && value <= 65535, '端口必须为 1024–65535')
    requireValue(!ports.has(value), '端口冲突')
    ports.add(value)
  }
  port(config.portalPort)
  requireValue(Array.isArray(config.systems) && config.systems.length === systemIds.length, '必须配置两个系统')
  for (const id of systemIds) {
    const matches = config.systems.filter(system => system?.id === id)
    requireValue(matches.length === 1, `${id} 缺失或重复`)
    const system = matches[0]
    for (const field of ['name', 'description', 'message']) {
      requireValue(typeof system[field] === 'string' && system[field].trim().length > 0, `${id}.${field} 不能为空`)
    }
    requireValue(Array.isArray(system.capabilities) && system.capabilities.length === 3 &&
      system.capabilities.every(value => typeof value === 'string' && value.trim()), `${id} 功能说明无效`)
    requireValue(statuses.includes(system.status), `${id} 状态无效`)
    requireValue(/^(?:feature\/)?[A-Za-z]+$/.test(system.sourceBranch) && /^[0-9a-f]{40}$/.test(system.sourceSha), `${id} 来源无效`)
    if (system.status === 'maintenance') continue
    const runtime = system.runtime
    requireValue(runtime && typeof runtime === 'object', `${id} 缺少接入运行配置`)
    requireValue(runtime.acceptanceSha === system.sourceSha, `${id} 验收来源与同步来源不一致`)
    requireValue(typeof runtime.acceptance === 'string' && /^docs\/[\w/-]+\.md$/.test(runtime.acceptance), `${id} 缺少验收记录路径`)
    requireValue(runtime.dist === `systems/${id}/frontend/dist`, `${id} 构建目录无效`)
    requireValue(runtime.contextPath === undefined || runtime.contextPath === `/${id}`, `${id} 后端上下文路径无效`)
    requireValue(typeof runtime.readinessPath === 'string' && /^\/[\w/-]+$/.test(runtime.readinessPath), `${id} 就绪路径无效`)
    port(runtime.backendPort)
    port(runtime.frontendPort)
    for (const component of ['backend', 'frontend']) {
      const command = runtime[component]
      requireValue(command && ['node', 'java'].includes(command.executable), `${id}.${component} 必须为直接运行的 node 或 java 进程`)
      requireValue(typeof command.cwd === 'string' && (command.cwd === `systems/${id}` || command.cwd.startsWith(`systems/${id}/`)) && !command.cwd.includes('..'), `${id} 工作目录越界`)
      requireValue(Array.isArray(command.args) && command.args.length > 0 && command.args.every(arg =>
        typeof arg === 'string' && !/[\r\n\0"]/.test(arg)), `${id} 进程参数无效`)
    }
  }
  return config
}

export function loadConfig(root = rootDirectory) {
  const content = readFileSync(path.join(root, 'deploy/systems.json'), 'utf8')
  const config = validateConfig(JSON.parse(content))
  for (const system of config.systems.filter(entry => entry.status === 'enabled')) {
    requireValue(existsSync(workspacePath(root, system.runtime.acceptance, 'docs')), `${system.id} 验收记录不存在`)
    for (const component of ['backend', 'frontend']) {
      requireValue(existsSync(workspacePath(root, system.runtime[component].cwd, `systems/${system.id}`, true)), `${system.id} 工作目录不存在`)
    }
  }
  return { ...config, revision: createHash('sha256').update(content).digest('hex') }
}

export function publicConfig(config) {
  return {
    revision: config.revision,
    systems: config.systems.map(({ id, name, description, capabilities, status, message }) =>
      ({ id, name, description, capabilities, status, message, path: `/${id}/` })),
  }
}

export function startupPlan(config, selected = 'all', mode = 'Demo') {
  requireValue(['all', 'portal', ...systemIds].includes(selected), '未知启动对象')
  requireValue(['Development', 'Demo'].includes(mode), '未知运行模式')
  const enabled = config.systems.filter(system => system.status === 'enabled' &&
    (selected === 'all' || selected === system.id))
  return {
    portalPort: config.portalPort,
    revision: config.revision,
    skipped: config.systems.filter(system => system.status === 'maintenance').map(system => system.id),
    processes: enabled.flatMap(system => {
      const runtime = system.runtime
      const backend = { ...runtime.backend, id: `${system.id}-backend`, system: system.id,
        args: [...runtime.backend.args, ...(system.id !== 'crawler' ? [`--integration.portal-url=http://127.0.0.1:${config.portalPort}`] : [])],
        port: runtime.backendPort, readinessUrl: `http://127.0.0.1:${runtime.backendPort}${runtime.readinessPath}` }
      return mode === 'Demo' ? [backend] : [backend, { ...runtime.frontend, id: `${system.id}-frontend`,
        system: system.id, port: runtime.frontendPort, readinessUrl: `http://127.0.0.1:${runtime.frontendPort}/${system.id}/` }]
    }),
  }
}
