<template>
  <div class="not-found-page">
    <div class="error-content">
      <div class="error-icon">
        <svg width="120" height="120" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="60" cy="60" r="56" stroke="var(--macos-border)" stroke-width="2" fill="var(--macos-bg-tertiary)"/>
          <text x="60" y="72" text-anchor="middle" font-size="36" font-weight="700" fill="var(--macos-text-secondary)">404</text>
        </svg>
      </div>
      <h1 class="error-title">页面未找到</h1>
      <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
      <div class="error-actions">
        <el-button type="primary" size="large" @click="goHome">
          <el-icon><HomeFilled /></el-icon>
          返回首页
        </el-button>
        <el-button size="large" @click="goBack">
          <el-icon><Back /></el-icon>
          返回上页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled, Back } from '@element-plus/icons-vue'

const router = useRouter()

const goHome = () => router.push('/')
const goBack = () => router.back()
</script>

<style scoped lang="scss">
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--macos-bg-secondary);
}

.error-content {
  text-align: center;
  padding: 40px;
}

.error-icon {
  margin-bottom: 24px;
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--macos-text-primary);
  margin: 0 0 12px;
}

.error-desc {
  font-size: 15px;
  color: var(--macos-text-secondary);
  margin: 0 0 32px;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
