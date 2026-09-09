// 后端全链路冒烟测试（Node.js 版）
// 用法：先启动后端（mvnw spring-boot:run -Dspring-boot.run.profiles=local），再执行 node smoke-test.mjs
// 说明：Node 的 fetch 按 UTF-8 原样发送中文 JSON——
//       Windows Git Bash 里的 curl 会把中文参数按 GBK 编码，服务端解析 UTF-8 时抛 500，属于终端编码问题而非后端缺陷。
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

// ---- 极简 Cookie 罐：从 Set-Cookie 头解析 key=value，请求时拼回 Cookie 头 ----
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

async function main() {
  // 1. 未登录访问受保护接口 → 401
  console.log('=== 1. 未登录访问受保护接口 → 401 ===');
  check('GET /papers 未登录', 401, (await req('GET', '/papers')).status);

  // 2. CSRF + 登录
  console.log('=== 2. CSRF + 登录（admin/admin123）===');
  const adminJar = makeJar();
  let csrf = await getCsrf(adminJar);
  check('POST /auth/login', 200, (await req('POST', '/auth/login', {
    body: { username: 'admin', password: 'admin123' }, csrf, jar: adminJar,
  })).status);
  check('GET /auth/me 登录后', 200, (await req('GET', '/auth/me', { jar: adminJar })).status);

  // 3. 登录错误路径
  console.log('=== 3. 登录错误路径 ===');
  check('登录密码错误 → 401', 401, (await req('POST', '/auth/login', {
    body: { username: 'admin', password: 'wrong' },
  })).status);
  check('注册重名 → 400', 400, (await req('POST', '/auth/register', {
    body: { username: 'admin', password: '123456' },
  })).status);

  // 4. 基础实体 CRUD
  console.log('=== 4. 基础实体 CRUD ===');
  csrf = await getCsrf(adminJar);
  const instRes = await req('POST', '/institutions', {
    body: { displayName: '西安石油大学', countryCode: 'CN', institutionType: 'university' }, csrf, jar: adminJar,
  });
  check('创建机构', 201, instRes.status);
  const inst = await instRes.json();
  const instId = inst.id;
  check('更新机构(带version)', 200, (await req('PUT', `/institutions/${instId}`, {
    body: { displayName: '西安石油大学（更新）', countryCode: 'CN', institutionType: 'university', version: inst.version },
    csrf, jar: adminJar,
  })).status);
  check('乐观锁冲突 → 409', 409, (await req('PUT', `/institutions/${instId}`, {
    body: { displayName: 'X', countryCode: 'CN', institutionType: 'university', version: 999 },
    csrf, jar: adminJar,
  })).status);

  const au1 = (await (await req('POST', '/authors', {
    body: { displayName: '叶紫薇', orcid: '0000-0001-2345-6789' }, csrf, jar: adminJar,
  })).json());
  check('创建作者', 201, au1.id ? 201 : 0);
  const au2 = (await (await req('POST', '/authors', {
    body: { displayName: '张三' }, csrf, jar: adminJar,
  })).json());
  check('创建作者2', 201, au2.id ? 201 : 0);

  const kw1 = (await (await req('POST', '/keywords', {
    body: { name: '知识图谱', fieldName: '人工智能' }, csrf, jar: adminJar,
  })).json());
  check('创建关键词', 201, kw1.id ? 201 : 0);
  const kw2 = (await (await req('POST', '/keywords', {
    body: { name: '图神经网络', fieldName: '人工智能' }, csrf, jar: adminJar,
  })).json());
  check('创建关键词2', 201, kw2.id ? 201 : 0);
  check('重复关键词 → 400', 400, (await req('POST', '/keywords', {
    body: { name: '知识图谱' }, csrf, jar: adminJar,
  })).status);

  const ve = (await (await req('POST', '/venues', {
    body: { displayName: '计算机学报', issn: '0254-4164', venueType: 'journal' }, csrf, jar: adminJar,
  })).json());
  check('创建渠道', 201, ve.id ? 201 : 0);
  check('空名称 → 400 校验', 400, (await req('POST', '/institutions', {
    body: { displayName: '' }, csrf, jar: adminJar,
  })).status);

  // 5. 论文（带作者/机构/关键词/引用关系）
  console.log('=== 5. 论文（带作者/机构/关键词/引用关系）===');
  const p1 = await (await req('POST', '/papers', {
    body: {
      title: '基于知识图谱的学术关系重构方法', doi: '10.1000/test.001', paperType: 'JOURNAL_ARTICLE',
      publicationDate: '2025-06-15', abstractText: '提出一种基于知识图谱的学术关系重构方法。',
      citationCount: 12, venueId: ve.id,
      authors: [{ authorId: au1.id, position: 1, institutionId: instId }, { authorId: au2.id, position: 2, institutionId: instId }],
      keywordIds: [kw1.id, kw2.id], references: [],
    }, csrf, jar: adminJar,
  })).json();
  check('创建论文1', 201, p1.id ? 201 : 0);
  console.log(`  论文1 id=${p1.id} 标题=${p1.title} 作者数=${p1.authors?.length}`);
  const p2 = await (await req('POST', '/papers', {
    body: {
      title: '图神经网络在学术网络中的应用', doi: '10.1000/test.002', paperType: 'CONFERENCE_PAPER',
      publicationDate: '2026-03-20', citationCount: 30, venueId: ve.id,
      authors: [{ authorId: au1.id, position: 1, institutionId: instId }],
      keywordIds: [kw2.id], references: [{ citedPaperId: p1.id }],
    }, csrf, jar: adminJar,
  })).json();
  check('创建论文2(引用论文1)', 201, p2.id ? 201 : 0);
  check('重复 DOI → 400', 400, (await req('POST', '/papers', {
    body: { title: '重复DOI', doi: '10.1000/test.001' }, csrf, jar: adminJar,
  })).status);
  // 自引用校验：创建时客户端拿不到自己的 id，所以自引用只可能出现在"更新"里
  const selfCiteRes = await req('PUT', `/papers/${p2.id}`, {
    body: {
      title: p2.title, doi: p2.doi, paperType: p2.paperType,
      publicationDate: p2.publicationDate, citationCount: p2.citationCount,
      venueId: p2.venue?.id ?? null,
      authors: [{ authorId: au1.id, position: 1, institutionId: instId }],
      keywordIds: [kw2.id],
      references: [{ citedPaperId: p2.id }],
      version: p2.version,
    }, csrf, jar: adminJar,
  });
  check('更新时自引用 → 400', 400, selfCiteRes.status);
  const search = await (await req('GET', '/papers?keyword=' + encodeURIComponent('知识图谱'), { jar: adminJar })).json();
  check('论文列表搜索', 200, search.totalElements >= 1 ? 200 : 0);
  check('论文不存在 → 404', 404, (await req('GET', '/papers/99999', { jar: adminJar })).status);

  // 6. 图同步
  console.log('=== 6. 图同步 ===');
  const sync = await (await req('POST', '/admin/sync-graph', { csrf, jar: adminJar })).json();
  check('手动触发图同步', 200, sync.processed !== undefined ? 200 : 0);
  console.log(`  处理事件数: ${sync.processed}`);

  // 7. 分析接口（等待同步器把事件投影进 Neo4j）
  console.log('=== 7. 分析接口 ===');
  const col = await (await req('GET', '/analytics/collaborations', { jar: adminJar })).json();
  check('合作网络', 200, Array.isArray(col) ? 200 : 0);
  console.log(`  合作网络条数: ${col.length}`, col.slice(0, 2));
  const cit = await (await req('GET', '/analytics/citations', { jar: adminJar })).json();
  check('引用统计', 200, Array.isArray(cit) ? 200 : 0);
  console.log(`  引用统计条数: ${cit.length}`, cit.slice(0, 2));
  const top = await (await req('GET', '/analytics/topic-evolution', { jar: adminJar })).json();
  check('主题演化', 200, Array.isArray(top) ? 200 : 0);
  console.log(`  主题演化条数: ${top.length}`, top.slice(0, 3));
  const imp = await (await req('GET', '/analytics/institution-impact', { jar: adminJar })).json();
  check('机构影响力', 200, Array.isArray(imp) ? 200 : 0);
  console.log(`  机构影响力条数: ${imp.length}`, imp.slice(0, 2));

  // 8. 管理员接口
  console.log('=== 8. 管理员接口 ===');
  const ulist = await (await req('GET', '/admin/users', { jar: adminJar })).json();
  check('用户列表', 200, ulist.items ? 200 : 0);
  console.log(`  用户数: ${ulist.totalElements}`);
  const newu = await (await req('POST', '/admin/users', {
    body: { username: 'zhangsan', password: '123456', displayName: '张三', roles: ['ANALYST'] }, csrf, jar: adminJar,
  })).json();
  check('管理员创建用户', 201, newu.id ? 201 : 0);

  // 9. 普通用户(ANALYST)访问管理接口 → 403
  console.log('=== 9. 普通用户(ANALYST)访问管理接口 → 403 ===');
  const analystJar = makeJar();
  const csrf2 = await getCsrf(analystJar);
  check('普通用户登录', 200, (await req('POST', '/auth/login', {
    body: { username: 'zhangsan', password: '123456' }, csrf: csrf2, jar: analystJar,
  })).status);
  check('ANALYST 访问管理接口 → 403', 403, (await req('GET', '/admin/users', { jar: analystJar })).status);
  check('ANALYST 访问论文列表 → 200', 200, (await req('GET', '/papers', { jar: analystJar })).status);

  // 10. 禁用账号后登录 → 401
  console.log('=== 10. 禁用账号后登录 → 401 ===');
  check('禁用账号', 200, (await req('PUT', `/admin/users/${newu.id}/status`, {
    body: { status: 'DISABLED' }, csrf, jar: adminJar,
  })).status);
  check('禁用用户登录 → 401', 401, (await req('POST', '/auth/login', {
    body: { username: 'zhangsan', password: '123456' }, csrf: csrf2, jar: analystJar,
  })).status);
  check('重置角色', 200, (await req('PUT', `/admin/users/${newu.id}/roles`, {
    body: { roles: ['ADMIN'] }, csrf, jar: adminJar,
  })).status);

  // 11. CSRF 防护
  console.log('=== 11. CSRF 防护 ===');
  check('缺 CSRF 头 → 403', 403, (await req('POST', '/keywords', {
    body: { name: '无CSRF头' }, jar: adminJar,
  })).status);

  // 12. 删除链路（顺序敏感：先验证外键占用 409，再删论文解除引用，最后删机构/作者）
  console.log('=== 12. 删除链路 ===');
  check('删除被署名引用机构 → 409', 409, (await req('DELETE', `/institutions/${instId}`, { csrf, jar: adminJar })).status);
  check('删除被署名引用作者 → 409', 409, (await req('DELETE', `/authors/${au1.id}`, { csrf, jar: adminJar })).status);
  check('删除论文2', 204, (await req('DELETE', `/papers/${p2.id}`, { csrf, jar: adminJar })).status);
  check('删除论文1', 204, (await req('DELETE', `/papers/${p1.id}`, { csrf, jar: adminJar })).status);
  check('论文删光后删除机构', 204, (await req('DELETE', `/institutions/${instId}`, { csrf, jar: adminJar })).status);
  check('论文删光后删除作者', 204, (await req('DELETE', `/authors/${au1.id}`, { csrf, jar: adminJar })).status);
  check('删除渠道(论文venue_id置空)', 204, (await req('DELETE', `/venues/${ve.id}`, { csrf, jar: adminJar })).status);
  check('管理员删除用户', 204, (await req('DELETE', `/admin/users/${newu.id}`, { csrf, jar: adminJar })).status);

  console.log('');
  console.log(`======== 结果: PASS=${pass} FAIL=${fail} ========`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('测试执行异常:', e);
  process.exit(2);
});
