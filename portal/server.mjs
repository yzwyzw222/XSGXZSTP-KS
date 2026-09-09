import { existsSync } from 'node:fs'
import path from 'node:path'
import { createServer, preview } from 'vite'
import config from './vite.config.mjs'
import { rootDirectory } from '../scripts/lib/config.mjs'

const mode = process.argv[2] ?? 'Demo'
if (!['Development', 'Demo'].includes(mode)) throw new Error('运行模式必须为 Development 或 Demo')
if (mode === 'Demo' && !existsSync(path.join(rootDirectory, 'portal/dist/index.html'))) {
  throw new Error('门户尚未构建，请先执行 npm --prefix portal run build')
}
const server = mode === 'Development' ? await createServer({ ...config, configFile: false }) :
  await preview({ ...config, configFile: false })
if (mode === 'Development') await server.listen()
server.printUrls()

let closing = false
async function close() {
  if (closing) return
  closing = true
  if (mode === 'Development') await server.close()
  else await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
}
process.once('SIGINT', () => { close().catch(() => { process.exitCode = 1 }) })
process.once('SIGTERM', () => { close().catch(() => { process.exitCode = 1 }) })
