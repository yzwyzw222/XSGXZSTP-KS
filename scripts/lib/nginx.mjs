import path from 'node:path'
import { maintenanceHtml } from './gateway.mjs'
import { publicConfig } from './config.mjs'

const quote = value => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\r?\n/g, '\\n')}'`

/** 由同一份接入配置生成完整 Nginx 配置，维护位置不包含 proxy_pass。 */
export function renderNginx(config, root) {
  const absolute = relative => quote(path.resolve(root, relative).replaceAll('\\', '/'))
  const systems = config.systems.map(system => {
    const prefix = `/${system.id}`
    const canonical = `location = ${prefix} { return 308 ${prefix}/$is_args$args; }`
    if (system.status === 'maintenance') {
      const body = JSON.stringify({ status: 503, code: 'SYSTEM_MAINTENANCE', system: system.id, message: '系统维护中' })
      return `${canonical}
    location ~* ^${prefix}/(?:api|actuator)(?:/|$) {
      default_type application/json;
      add_header Cache-Control no-store always;
      add_header Retry-After 300 always;
      return 503 ${quote(body)};
    }
    location ${prefix}/ {
      default_type text/html;
      add_header Cache-Control no-store always;
      add_header Retry-After 300 always;
      return 503 ${quote(maintenanceHtml(system))};
    }`
    }
    return `${canonical}
    location ~* ^${prefix}/(?:api|actuator)(?:/|$) {
      ${system.runtime.contextPath ? '# 后端已配置所属系统的上下文路径。' : `rewrite ^${prefix}(/.*)$ $1 break;`}
      proxy_pass http://127.0.0.1:${system.runtime.backendPort};
      proxy_set_header Host $http_host;
      proxy_connect_timeout 5s;
      proxy_read_timeout 15s;
      proxy_send_timeout 15s;
    }
    location ${prefix}/ {
      alias ${quote(path.resolve(root, system.runtime.dist).replaceAll('\\', '/') + '/')};
      try_files $uri $uri/ ${prefix}/index.html;
    }`
  }).join('\n    ')
  return `# 由 scripts/generate-nginx.mjs 生成；变更状态后重新构建、生成并验证。
worker_processes 1;
pid ${absolute('.local/nginx/nginx.pid')};
error_log ${absolute('.local/nginx/error.log')};
events { worker_connections 256; }
http {
  types { text/html html; text/css css; application/javascript js mjs; application/json json; image/svg+xml svg; image/png png; image/jpeg jpg jpeg; image/webp webp; font/woff2 woff2; }
  default_type application/octet-stream;
  access_log off;
  server_tokens off;
  server {
    listen 127.0.0.1:${config.portalPort};
    server_name 127.0.0.1 localhost;
    charset utf-8;
    root ${absolute('portal/dist')};
    add_header X-Content-Type-Options nosniff always;
    location = /integration.json {
      default_type application/json;
      add_header Cache-Control no-store always;
      return 200 ${quote(JSON.stringify(publicConfig(config)))};
    }
    ${systems}
    location ~* ^/api(?:/|$) { default_type application/json; return 404 '{"status":404,"code":"UNKNOWN_API"}'; }
    location / { try_files $uri $uri/ =404; }
  }
}
`
}
