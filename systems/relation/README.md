# 学术关系知识图谱系统（XSGXZSTP-KS）

## 项目简介

本项目为学术知识图谱相关系统的协作开发仓库，包含多个子系统，最终在 main 分支整合。

## 分支结构

| 分支 | 开发者 | 负责系统 |
|------|--------|----------|
| main | 整合负责人 | 主分支，整合所有系统，保持可运行 |
| feature/yeziwei/relation-graph | 叶紫薇 | 学术关系知识图谱构建平台 |
| feature/duyunhao/entity-extraction | 杜运昊 | 学术多源实体抽取与学术知识图谱构建 |
| feature/liyu/achievement-graph | 李宇 | 学术成果知识图谱构建平台 |
| feature/luozhen/crawler-visualization | 罗振 | 学术成果爬虫与可视化系统 |

## 分支保护规则

- main 分支已设置保护，必须通过 Pull Request 合并，禁止直接推送
- 合并前需确保代码可运行
- 不允许强制推送和删除 main 分支

## 开发流程

### 1. 克隆仓库并切换到自己的分支

git clone https://github.com/yzwyzw222/XSGXZSTP-KS.git
cd XSGXZSTP-KS
git checkout feature/你的拼音/你的系统名

### 2. 日常开发

git add .
git commit -m "feat: 完成xxx功能"
git push

### 3. 第一版开发完成，合并回 main

git checkout main
git pull origin main
git checkout feature/你的分支
git merge main
git push

然后在 GitHub 上发起 Pull Request，base 选 main，compare 选你的分支。

## 提交规范

- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- refactor: 重构
- merge: 合并分支

## 注意事项

1. 不要直接在 main 分支上提交代码
2. 合并前先同步 main，解决冲突
3. 保持自己的分支干净，只包含自己负责系统的代码
4. 有问题及时沟通

