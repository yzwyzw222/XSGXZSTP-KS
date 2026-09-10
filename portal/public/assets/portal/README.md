# 门户视觉素材

本目录只服务统一入口，资源 URL 使用 `/assets/portal/`，沿用现有网关白名单。运行时不请求外部图片、字体或图标服务。

## 图片

以下五张图片使用内置 `image_gen`，以用户提供的学术智能平台参考图为视觉依据分别生成。没有把整张界面截图铺作页面；标题、状态、按钮与统计均由页面渲染。输出转为无损 WebP，尺寸不变，透明度及所有可见像素已逐像素核对。

| 文件 | 用途 | 生成提示词摘要 |
| --- | --- | --- |
| `images/portal-background.webp` | 全幅背景 | 移除参考界面上的卡片和文字，保留右上蓝色地球、左侧城市、光点网络和底部全息圆台，中央留出深蓝空间，无文字 |
| `images/relation-network.webp` | 关系平台插图 | 居中的青蓝全息学术关系球状网络，细连接线、人物和文档节点，深海军蓝背景，无边框或文字 |
| `images/crawler-analytics.webp` | 信息采集平台插图 | 青色机器人位于透明平台左下，右上两块全息屏显示折线和柱图，等距 3D，无标题或文字 |
| `images/cooperation-map.webp` | 合作网络示意 | 蓝色点阵世界大陆、青色跨洲连接弧线、欧美与东亚连接节点，深蓝背景，无文字或图例 |

图片是概念插画；合作地图不表达真实机构合作关系。PNG 原始生成件保留在生成工具输出与本地忽略目录中，不作为网站依赖。

## 图标

`icons/` 收录 [Bootstrap Icons v1.13.1](https://github.com/twbs/icons/tree/v1.13.1/icons) 的 19 个原始 SVG 文件，采用 [MIT 许可证](https://github.com/twbs/icons/blob/v1.13.1/LICENSE)，完整许可证随附于 `icons/LICENSE`。图标通过 `PortalIcon.vue` 的 CSS 遮罩统一着色，未手写或改动 SVG 路径。

该子集是本地静态素材，不引入 Bootstrap 样式、JavaScript 框架或 npm 运行依赖。
