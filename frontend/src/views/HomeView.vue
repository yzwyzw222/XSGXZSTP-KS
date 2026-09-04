<template>
  <section class="hero-section">
    <div class="hero-copy">
      <p class="eyebrow">工程卷宗 · 01</p>
      <h1>先让每一条连接<br />都可被验证。</h1>
      <p class="hero-lead">
        AACV System 已建立前后端最小工程、关系数据库权威存储与图数据库投影视图的运行边界。
        本阶段只证明基础设施可靠连通，不提前引入业务能力。
      </p>
      <RouterLink class="primary-link" to="/status">
        检查运行状态
        <span aria-hidden="true">→</span>
      </RouterLink>
    </div>

    <aside class="folio" aria-label="阶段说明">
      <span class="folio-number">01</span>
      <dl>
        <div><dt>范围</dt><dd>Foundation only</dd></div>
        <div><dt>运行时</dt><dd>JDK 21 / Node 24</dd></div>
        <div><dt>数据层</dt><dd>MySQL 8.0 / Neo4j 5.26</dd></div>
      </dl>
    </aside>
  </section>

  <section class="foundation-section" aria-labelledby="foundation-title">
    <div class="section-heading">
      <p class="eyebrow">SYSTEM FOUNDATIONS</p>
      <h2 id="foundation-title">三条明确的基础边界</h2>
    </div>

    <div class="foundation-grid">
      <article>
        <span class="index">I</span>
        <h3>MySQL 是权威数据源</h3>
        <p>本机 MySQL 8.0 承载持久数据，Flyway 负责可审计的结构演进；工程不创建 MySQL 容器。</p>
      </article>
      <article>
        <span class="index">II</span>
        <h3>Neo4j 是图投影视图</h3>
        <p>Neo4j 5.26 Community 由 Compose 单独管理，用于验证 Bolt 连接与后续图投影边界。</p>
      </article>
      <article>
        <span class="index">III</span>
        <h3>阶段 1 到此为止</h3>
        <p>页面只提供工程说明和存活检查，不创建阶段 2 的领域模型、业务接口或数据库对象。</p>
      </article>
    </div>
  </section>
</template>
