import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { loadConfig, rootDirectory } from './lib/config.mjs'
import { renderNginx } from './lib/nginx.mjs'

const config = loadConfig()
const built = JSON.parse(readFileSync(path.join(rootDirectory, 'portal/dist/integration.json'), 'utf8'))
if (built.revision !== config.revision) throw new Error('门户构建状态与配置不一致，请重新构建门户')
const directory = path.join(rootDirectory, '.local/nginx')
mkdirSync(directory, { recursive: true })
writeFileSync(path.join(directory, 'nginx.conf'), renderNginx(config, rootDirectory))
process.stdout.write('已生成 .local/nginx/nginx.conf；使用前必须执行 nginx -t。\n')
