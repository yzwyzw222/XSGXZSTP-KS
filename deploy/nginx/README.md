# Nginx 本地入口配置

唯一状态来源是 `../systems.json`；生成逻辑位于 `scripts/lib/nginx.mjs`，不手工维护第二份启用清单。

在仓库根目录：

```powershell
npm --prefix portal run build
npm --prefix portal run generate:nginx
nginx -t -c F:/Program/Java/course_design/.local/nginx/nginx.conf
```

最后一条必须替换为当前仓库的绝对路径。本轮未安装 Nginx，未执行 `nginx -t` 或启动 Nginx；只验证配置生成及维护模式无 `proxy_pass`。生成文件含机器路径，因此保存在已忽略的 `.local/nginx`。

维护页面内联在代理配置中，不依赖任何子系统前端；API location 在页面匹配之前返回 JSON 503。已启用系统前端构建目录、后端端口从同一配置生成。代理仅绑定 127.0.0.1，禁止与门户开发/预览服务同时使用同一端口。

状态变更后必须重新构建、生成、`nginx -t` 并重新加载 Nginx。生成器拒绝门户构建摘要与当前配置不一致的情况。Nginx 启停及其进程归属尚未纳入第一阶段总脚本；总脚本的 Demo 使用 Vite 本地预览。正式采用 Nginx 前，应补充其精确进程管理与实际代理验收。
