import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { loadConfig, publicConfig, rootDirectory } from '../scripts/lib/config.mjs'
import { createGateway, maintenanceHtml, proxyOptions } from '../scripts/lib/gateway.mjs'

const config = loadConfig()
export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  base: '/',
  plugins: [vue(), {
    name: 'academic-integration-gateway',
    configureServer(server) {
      server.middlewares.use(createGateway({ root: rootDirectory, initialConfig: config, development: true }))
    },
    configurePreviewServer(server) {
      server.middlewares.use(createGateway({ root: rootDirectory, initialConfig: config }))
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'integration.json', source: JSON.stringify(publicConfig(config), null, 2) })
      for (const system of config.systems) {
        this.emitFile({ type: 'asset', fileName: `maintenance/${system.id}.html`, source: maintenanceHtml(system) })
      }
    },
  }],
  server: { host: '127.0.0.1', port: config.portalPort, strictPort: true,
    fs: { strict: true, allow: [fileURLToPath(new URL('.', import.meta.url))] }, proxy: proxyOptions(config, true) },
  preview: { host: '127.0.0.1', port: config.portalPort, strictPort: true, proxy: proxyOptions(config, false) },
})
