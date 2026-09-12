import { existsSync } from 'node:fs'
import path from 'node:path'
import { createServer, preview } from '../systems/crawler/frontend/node_modules/vite/dist/node/index.js'
import { loadConfig, rootDirectory } from './lib/config.mjs'
import { createGateway, proxyOptions } from './lib/gateway.mjs'

const mode = process.argv[2] ?? 'Demo'
if (!['Development', 'Demo'].includes(mode)) throw new Error('运行模式必须为 Development 或 Demo')
const config = loadConfig()
const root = path.join(rootDirectory, 'systems/crawler/frontend')
if (mode === 'Demo' && !existsSync(path.join(root, 'dist/index.html'))) {
  throw new Error('成果系统尚未构建，请先执行 scripts/Build-Integration.ps1')
}
const development = mode === 'Development'
const options = {
  root, configFile: false,
  plugins: [{
    name: 'academic-system-gateway',
    configureServer(server) { server.middlewares.use(createGateway({ root: rootDirectory, initialConfig: config, development: true })) },
    configurePreviewServer(server) { server.middlewares.use(createGateway({ root: rootDirectory, initialConfig: config })) },
  }],
  server: { host: '127.0.0.1', port: config.portalPort, strictPort: true, proxy: proxyOptions(config, true) },
  preview: { host: '127.0.0.1', port: config.portalPort, strictPort: true, proxy: proxyOptions(config, false) },
}
const server = development ? await createServer(options) : await preview(options)
if (development) await server.listen()
server.printUrls()

let closing = false
async function close() {
  if (closing) return
  closing = true
  if (development) await server.close()
  else await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
}
process.once('SIGINT', () => { close().catch(() => { process.exitCode = 1 }) })
process.once('SIGTERM', () => { close().catch(() => { process.exitCode = 1 }) })
