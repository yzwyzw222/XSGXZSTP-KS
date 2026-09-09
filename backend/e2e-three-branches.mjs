// 三分支整合端到端验收脚本（Node.js 版）
// 链路：登录 → OpenAlex 在线爬取 → 手动同步图谱 → graph-data 抽取图 →
//       无 LLM key 触发抽取 → 轮询得 FAILED（失败路径）→ 重复爬取全 SKIPPED（幂等）
// 用法：后端启动后执行 node e2e-three-branches.mjs
const BASE = 'http://localhost:8080/api/v1';

let pass = 0;
let fail = 0;

function check(name, expected, actual) {
  if (expected === actual) {
    pass += 1;
    console.log(`  [PASS] ${name} (${actual})`);
  } else {
    fail += 1;
    console.log(`  [FAIL] ${name} 期望 ${expected} 实际 ${actual}`);
  }
}

function makeJar() {
  const jar = new Map();
  return {
    eat(res) {
      const headers = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
      for (const sc of headers) {
        const pair = sc.split(';')[0];
        const i = pair.indexOf('=');
        if (i > 0) jar.set(pair.slice(0, i).trim(), pair.slice(i + 1).trim());
      }
    },
    header() {
      return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
    },
  };
}

async function req(method, path, { body, csrf, jar } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (csrf) headers['X-CSRF-TOKEN'] = csrf;
  const cookie = jar ? jar.header() : '';
  if (cookie) headers['Cookie'] = cookie;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (jar) jar.eat(res);
  return res;
}

async function getCsrf(jar) {
  const res = await req('GET', '/auth/csrf', { jar });
  return (await res.json()).token;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  // 1. 登录（admin）
  console.log('=== 1. 管理员登录 ===');
  const jar = makeJar();
  const csrf = await getCsrf(jar);
  check('POST /auth/login', 200, (await req('POST', '/auth/login', {
    body: { username: 'admin', password: 'admin123' }, csrf, jar,
  })).status);

  // 2. 运行中的服务是否已含 extractionStatus 字段（验证后端为最新代码）
  console.log('=== 2. /papers 响应含 extractionStatus（服务为最新代码）===');
  const papersPage = await (await req('GET', '/papers?size=100', { jar })).json();
  const hasField = papersPage.items.length > 0
    ? typeof papersPage.items[0].extractionStatus === 'string'
    : true; // 空库不算失败
  check('/papers 含 extractionStatus 字段', true, hasField);

  // 3. OpenAlex 在线爬取（联网）
  console.log('=== 3. OpenAlex 在线爬取 "knowledge graph"（10 条）===');
  const crawlCsrf = await getCsrf(jar);
  const crawlRes = await req('POST', '/admin/crawl/openalex', {
    body: { keyword: 'knowledge graph', maxRecords: 10 }, csrf: crawlCsrf, jar,
  });
  check('POST /admin/crawl/openalex', 200, crawlRes.status);
  const summary = await crawlRes.json();
  console.log(`  导入摘要: imported=${summary.imported} skipped=${summary.skipped} failed=${summary.failed}`);
  console.log(`  新建对象: ${JSON.stringify(summary.created ?? summary)}`);
  check('导入摘要含 imported 字段', true, typeof summary.imported === 'number');

  // 4. 重复爬取同关键词 → 全部 SKIPPED（DOI/标题判重幂等）
  console.log('=== 4. 重复爬取同关键词 → 全部 SKIPPED（幂等）===');
  const againCsrf = await getCsrf(jar);
  const again = await (await req('POST', '/admin/crawl/openalex', {
    body: { keyword: 'knowledge graph', maxRecords: 10 }, csrf: againCsrf, jar,
  })).json();
  console.log(`  第二次: imported=${again.imported} skipped=${again.skipped} failed=${again.failed}`);
  check('第二次爬取 imported=0', 0, again.imported);
  check('第二次爬取 skipped>0', true, again.skipped > 0);

  // 5. 手动同步图谱（outbox 事件投影 Neo4j）
  console.log('=== 5. 手动同步图谱 ===');
  const syncCsrf = await getCsrf(jar);
  const sync = await (await req('POST', '/admin/sync-graph', { csrf: syncCsrf, jar })).json();
  console.log(`  处理事件数: ${sync.processed}`);
  check('POST /admin/sync-graph', true, typeof sync.processed === 'number');

  // 6. 抽取图谱数据（graph-data：当前为空台账时应返回空节点集）
  console.log('=== 6. GET /extraction/graph-data ===');
  const gd = await (await req('GET', '/extraction/graph-data', { jar })).json();
  console.log(`  nodes=${gd.nodes?.length} edges=${gd.edges?.length}`);
  check('graph-data 返回 nodes 数组', true, Array.isArray(gd.nodes));
  check('graph-data 返回 edges 数组', true, Array.isArray(gd.edges));

  // 7. 无 LLM key 触发抽取 → 轮询得 FAILED（失败路径）
  console.log('=== 7. 无 LLM key 触发抽取 → FAILED（预期失败路径）===');
  const q = await (await req('GET', '/papers?keyword=' + encodeURIComponent('knowledge graph') + '&size=10', { jar })).json();
  const withAbstract = q.items.find((p) => p.abstractText && p.abstractText.length > 10);
  if (!withAbstract) {
    console.log('  [SKIP] 没有带摘要的论文可抽取（可先导入样例数据）');
  } else {
    console.log(`  选中论文 id=${withAbstract.id} 标题=${withAbstract.title}`);
    const trigCsrf = await getCsrf(jar);
    const trig = await req('POST', '/admin/extraction/trigger', {
      body: { paperIds: [withAbstract.id] }, csrf: trigCsrf, jar,
    });
    check('POST /admin/extraction/trigger', 202, trig.status);
    const trigBody = await trig.json();
    console.log(`  ${trigBody.message} queuedCount=${trigBody.queuedCount}`);
    check('queuedCount=1', 1, trigBody.queuedCount);

    let status = 'PENDING';
    for (let i = 0; i < 20; i++) {
      await sleep(2000);
      const st = await (await req('GET', `/admin/extraction/status/${withAbstract.id}`, { jar })).json();
      status = st.status;
      console.log(`  轮询第 ${i + 1} 次: status=${status}`);
      if (status === 'FAILED' || status === 'COMPLETED') break;
    }
    check('无 key 时抽取最终 FAILED', 'FAILED', status);
  }

  // 8. 权限边界：ANALYST 不能触发抽取，匿名不能读 graph-data
  console.log('=== 8. 权限边界 ===');
  const anon = await req('GET', '/extraction/graph-data');
  check('匿名 GET graph-data → 401', 401, anon.status);
  check('匿名 POST crawl → 403(CSRF)/401', [401, 403].includes(
    (await req('POST', '/admin/crawl/openalex', { body: { keyword: 'x', maxRecords: 1 } })).status,
  ), true);
  const analystJar = makeJar();
  const aCsrf = await getCsrf(analystJar);
  const analystLogin = await req('POST', '/auth/login', {
    body: { username: 'zhangsan', password: '123456' }, csrf: aCsrf, jar: analystJar,
  });
  if (analystLogin.status === 200) {
    const bCsrf = await getCsrf(analystJar);
    check('ANALYST POST extraction/trigger → 403', 403, (await req('POST', '/admin/extraction/trigger', {
      body: { paperIds: [1] }, csrf: bCsrf, jar: analystJar,
    })).status);
    check('ANALYST GET graph-data → 200', 200, (await req('GET', '/extraction/graph-data', { jar: analystJar })).status);
  } else {
    console.log('  [SKIP] zhangsan 账号不存在（冒烟测试已删除），跳过 ANALYST 边界检查');
  }

  console.log('');
  console.log(`======== 结果: PASS=${pass} FAIL=${fail} ========`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('测试执行异常:', e);
  process.exit(2);
});
