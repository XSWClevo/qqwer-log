<template>
  <el-breadcrumb v-if="breadcrumbs.length > 1" separator="/" class="app-breadcrumb">
    <el-breadcrumb-item
      v-for="(item, index) in breadcrumbs"
      :key="item.path"
      :to="index < breadcrumbs.length - 1 ? { path: item.path } : undefined"
    >
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

interface BreadcrumbItem {
  title: string
  path: string
}

// 路由到面包屑的映射
const routeMap: Record<string, { title: string; parent?: string }> = {
  '/': { title: '监控大屏' },
  '/log-search': { title: '日志搜索' },
  '/agent': { title: '智能助手' },
  '/trend-analysis': { title: '趋势分析', parent: '日志分析' },
  '/trace-analysis': { title: '链路分析', parent: '日志分析' },
  '/alert/rules': { title: '告警规则', parent: '告警管理' },
  '/alert/history': { title: '告警历史', parent: '告警管理' },
  '/vector/machines': { title: '主机管理', parent: 'Vector 管理' },
  '/vector/configs': { title: '配置管理', parent: 'Vector 管理' },
  '/vector/components': { title: '组件库', parent: 'Vector 管理' },
  '/vector/visual-configs': { title: '可视化配置', parent: 'Vector 管理' },
  '/vector/packages': { title: '安装包管理', parent: 'Vector 管理' },
  '/vector/logs': { title: '日志监控', parent: 'Vector 管理' },
  '/datasources': { title: '数据源管理' },
  '/log-source': { title: '日志源管理' },
  '/settings': { title: '系统设置' },
  '/settings/database-config': { title: '数据库配置', parent: '系统设置' },
}

const route = useRoute()

const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const path = route.path
  const info = routeMap[path]
  if (!info) return []

  const crumbs: BreadcrumbItem[] = [{ title: '首页', path: '/' }]

  if (info.parent) {
    crumbs.push({ title: info.parent, path: '' })
  }

  if (path !== '/') {
    crumbs.push({ title: info.title, path })
  }

  return crumbs
})
</script>

<style scoped lang="scss">
.app-breadcrumb {
  padding: 12px 20px 0;

  :deep(.el-breadcrumb__item) {
    .el-breadcrumb__inner {
      font-size: 13px;
      color: var(--macos-text-tertiary);
      font-weight: 400;

      &.is-link:hover {
        color: var(--macos-blue);
      }
    }

    &:last-child .el-breadcrumb__inner {
      color: var(--macos-text-secondary);
      font-weight: 500;
    }
  }
}
</style>
