<script setup lang="ts">
import { useSessionActivity } from '@/composables/useSessionActivity'

// 启动会话活跃监听，监听用户操作并定期发送心跳
useSessionActivity()
</script>

<template>
  <router-view />
</template>

<style lang="scss">
@use './assets/styles/macos.scss';

#app {
  width: 100%;
  min-height: 100vh;
}
</style>
