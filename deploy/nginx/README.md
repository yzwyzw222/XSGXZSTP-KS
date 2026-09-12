# Nginx 本地入口配置

唯一状态来源是 `../systems.json`；生成逻辑位于 `scripts/lib/nginx.mjs`，不手工维护第二份启用清单。

在仓库根目录：

```powershell
npm --prefix systems/crawler/frontend run build -- --base=/crawler/
node scripts/generate-nginx.mjs
nginx -t -c E:/Program/Java/course_design/XSGXZSTP-KS/.local/nginx/nginx.conf
```

最后一条必须替换为当前仓库的绝对路径。本轮未安装 Nginx，未执行 `nginx -t` 或启动 Nginx；只验证配置生成及维护模式无 `proxy_pass`。生成文件含机器路径，因此保存在已忽略的 `.local/nginx`。

维护页面内联在代理配置中，不依赖业务前端；API location 在页面匹配之前返回 JSON 503。只生成 crawler 的页面与后端代理，根路径跳转 `/crawler/`，登录跳转 `/crawler/login`。代理仅绑定 127.0.0.1，禁止与 Node 网关同时使用同一端口。

状态变更后必须重新生成、执行 `nginx -t` 并重新加载 Nginx。生成器要求 crawler 前端构建存在。该模板未接入 Node 网关的兼容认证接口，不能作为本次单系统运行验收结果；总脚本仍使用 Node/Vite 网关。请求日志功能已移除，操作与登录日志由后端审计接口提供。本轮只验证配置生成，未安装、启动或修改 Nginx。
