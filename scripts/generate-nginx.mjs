import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { loadConfig, rootDirectory } from './lib/config.mjs'
import { renderNginx } from './lib/nginx.mjs'

const config = loadConfig()
if (!existsSync(path.join(rootDirectory, 'systems/crawler/frontend/dist/index.html'))) throw new Error('请先构建成果系统前端')
const directory = path.join(rootDirectory, '.local/nginx')
mkdirSync(directory, { recursive: true })
writeFileSync(path.join(directory, 'nginx.conf'), renderNginx(config, rootDirectory))
process.stdout.write('已生成 .local/nginx/nginx.conf；使用前必须执行 nginx -t。\n')
