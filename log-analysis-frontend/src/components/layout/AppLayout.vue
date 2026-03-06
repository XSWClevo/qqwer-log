<template>
  <el-container class="app-layout">
    <!-- 左侧菜单 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo-section">
        <img src="@/assets/vue.svg" alt="Logo" class="logo-icon" />
        <span v-show="!isCollapsed" class="logo-text">qqwer</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        :collapse="isCollapsed"
        :collapse-transition="false"
        class="side-menu"
        router
        :unique-opened="false"
      >
        <el-menu-item index="/">
          <el-icon><DataBoard /></el-icon>
          <template #title>监控大屏</template>
        </el-menu-item>
        <el-menu-item index="/log-search">
          <el-icon><Search /></el-icon>
          <template #title>日志搜索</template>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><MagicStick /></el-icon>
          <template #title>智能助手</template>
        </el-menu-item>
        <el-sub-menu index="analysis">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>日志分析</span>
          </template>
          <el-menu-item index="/trend-analysis">趋势分析</el-menu-item>
          <el-menu-item index="/trace-analysis">链路分析</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="alert">
          <template #title>
            <el-icon><Bell /></el-icon>
            <span>告警管理</span>
          </template>
          <el-menu-item index="/alert/rules">告警规则</el-menu-item>
          <el-menu-item index="/alert/history">告警历史</el-menu-item>
        </el-sub-menu>

        <!-- Vector 日志收集管理平台 -->
        <el-sub-menu index="vector">
          <template #title>
            <el-icon><Connection /></el-icon>
            <span>Vector 管理</span>
          </template>
          <el-menu-item index="/vector/machines">主机管理</el-menu-item>
          <el-menu-item index="/vector/configs">配置管理</el-menu-item>
          <el-menu-item index="/vector/components">组件库</el-menu-item>
          <el-menu-item index="/vector/visual-configs">可视化配置</el-menu-item>
          <el-menu-item index="/vector/packages">安装包管理</el-menu-item>
          <el-menu-item index="/vector/logs">日志监控</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/datasources">
          <el-icon><DataLine /></el-icon>
          <template #title>数据源管理</template>
        </el-menu-item>

        <el-menu-item index="/log-source">
          <el-icon><Connection /></el-icon>
          <template #title>日志源管理</template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
      
      <div class="bottom-actions">
        <!-- 主题切换 -->
        <div class="action-btn" @click="toggleDark()" :title="isDark ? '切换到亮色模式' : '切换到暗色模式'">
          <el-icon v-if="isDark"><Moon /></el-icon>
          <el-icon v-else><Sunny /></el-icon>
          <span v-show="!isCollapsed" class="btn-text">{{ isDark ? '暗色模式' : '亮色模式' }}</span>
        </div>
        
        <!-- 折叠按钮 -->
        <div class="action-btn" @click="isCollapsed = !isCollapsed" :title="isCollapsed ? '展开菜单' : '收起菜单'">
          <el-icon><component :is="isCollapsed ? Expand : Fold" /></el-icon>
          <span v-show="!isCollapsed" class="btn-text">收起菜单</span>
        </div>
      </div>
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container class="main-container">
      <el-main class="app-main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useDark, useToggle } from '@vueuse/core'
import {
  DataBoard,
  Search,
  MagicStick,
  TrendCharts,
  Bell,
  Setting,
  Expand,
  Fold,
  Connection,
  Moon,
  Sunny,
  DataLine
} from '@element-plus/icons-vue'

const route = useRoute()
const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)

// 根据当前路由自动展开对应的子菜单
const defaultOpeneds = computed(() => {
  const path = route.path
  const openeds: string[] = []

  // 日志分析子菜单
  if (path.startsWith('/trend-analysis') || path.startsWith('/trace-analysis')) {
    openeds.push('analysis')
  }

  // 告警管理子菜单
  if (path.startsWith('/alert')) {
    openeds.push('alert')
  }

  // Vector 管理子菜单
  if (path.startsWith('/vector')) {
    openeds.push('vector')
  }

  return openeds
})

// Dark mode
const isDark = useDark({
  selector: 'html',
  attribute: 'class',
  valueDark: 'dark',
  valueLight: 'light'
})
const toggleDark = useToggle(isDark)
</script>

<style scoped lang="scss">
// 引入 macOS 样式变量，以便在 <style> 块中使用
@use '@/assets/styles/macos.scss' as macos;

.app-layout {
  height: 100vh;
  overflow: hidden;
  background: var(--macos-bg-secondary); // 使用主题背景色
  color: var(--macos-text-primary);
}

.app-aside {
  @include macos.macos-glass(0.7); // 应用毛玻璃效果
  border-right: 1px solid var(--macos-border);
  display: flex;
  flex-direction: column;
  transition: width 0.25s var(--macos-transition);
  overflow: hidden;
}

.logo-section {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 16px;
  flex-shrink: 0; // 防止 logo 区域被压缩
  border-bottom: 1px solid transparent; // 占位
  transition: var(--macos-transition);
  
  .logo-icon {
    width: 32px;
    height: 32px;
  }
  
  .logo-text {
    font-size: 17px;
    font-weight: 600;
    color: var(--macos-text-primary);
    white-space: nowrap;
  }
}

.side-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 0 8px; // 给菜单一些呼吸空间
  overflow-y: auto;
  @include macos.macos-scrollbar;
  
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    color: var(--macos-text-secondary);
    height: 44px;
    line-height: 44px;
    border-radius: var(--macos-radius-sm);
    transition: var(--macos-transition-fast);
    
    &:hover {
      color: var(--macos-text-primary);
      background: var(--macos-blue-light);
    }
    
    .el-icon {
      color: inherit;
      font-size: 18px;
    }
  }
  
  :deep(.el-menu-item.is-active) {
    color: white;
    background: var(--macos-blue);
    font-weight: 500;
    
    &:hover {
      background: var(--macos-blue-hover);
    }
  }
  
  :deep(.el-sub-menu.is-active) {
    .el-sub-menu__title {
        color: var(--macos-text-primary);
    }
  }

  :deep(.el-sub-menu .el-menu) {
    background: transparent;
  }
  
  :deep(.el-sub-menu .el-menu-item) {
    padding-left: 48px !important;
    height: 40px;
    line-height: 40px;

    &.is-active {
        background: var(--macos-blue-light);
        color: var(--macos-blue);
    }
  }
}

.bottom-actions {
  padding: 8px;
  border-top: 1px solid var(--macos-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-btn {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--macos-text-secondary);
  cursor: pointer;
  border-radius: var(--macos-radius-sm);
  transition: all 0.2s;
  
  &:hover {
    color: var(--macos-text-primary);
    background: var(--macos-bg-tertiary);
  }
  
  .el-icon {
    font-size: 18px;
  }
}

.btn-text {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.main-container {
  flex-direction: column;
  overflow: hidden;
}

.app-main {
  padding: 0;
  overflow: auto;
  @include macos.macos-scrollbar;
}
</style>
