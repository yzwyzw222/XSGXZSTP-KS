<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <h2>AACV</h2>
    </div>
    <el-menu
      :default-active="route.path"
      :router="true"
      class="sidebar-menu"
      background-color="#0a1e38"
      text-color="#8296ae"
      active-text-color="#2d9df5"
    >
      <el-menu-item index="/dashboard">
        <el-icon><DataBoard /></el-icon>
        <span>总览</span>
      </el-menu-item>
      <el-menu-item index="/scholar-profile">
        <el-icon><User /></el-icon>
        <span>学者画像</span>
      </el-menu-item>
      <el-menu-item index="/keyword-search">
        <el-icon><Search /></el-icon>
        <span>关键词检索</span>
      </el-menu-item>
      <el-menu-item index="/author-search">
        <el-icon><Avatar /></el-icon>
        <span>作者检索</span>
      </el-menu-item>
      <el-menu-item index="/institution-search">
        <el-icon><OfficeBuilding /></el-icon>
        <span>机构检索</span>
      </el-menu-item>
      <el-menu-item index="/paper-search">
        <el-icon><Document /></el-icon>
        <span>论文检索</span>
      </el-menu-item>
      <el-menu-item index="/ai-selection">
        <el-icon><MagicStick /></el-icon>
        <span>AI 选文分析</span>
      </el-menu-item>
      <el-menu-item index="/analytics">
        <el-icon><TrendCharts /></el-icon>
        <span>科研分析</span>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { Avatar, DataBoard, Document, MagicStick, OfficeBuilding, Search, TrendCharts, User } from '@element-plus/icons-vue'

const route = useRoute()
</script>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  width: 228px;
  height: 100%;
  background-color: var(--sidebar);
  display: flex;
  flex-direction: column;
  z-index: 100;
  border-right: 1px solid #1e3a5e;
}

.sidebar-logo {
  height: 78px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #1e3a5e;
}

.sidebar-logo h2 {
  color: var(--accent);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}

.sidebar-menu {
  flex: 1;
  padding-top: 8px;
  border-right: none;
}

.el-menu-item {
  font-size: 14px;
}
</style>