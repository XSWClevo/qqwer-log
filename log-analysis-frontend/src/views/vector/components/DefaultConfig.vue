<template>
  <div class="default-config">
    <el-alert type="info" :closable="false" show-icon>
      该组件暂无可视化配置，请在 YAML 中手动编辑
    </el-alert>
  </div>
</template>

<script setup lang="ts">
defineProps<{ modelValue: Record<string, any> }>()
defineEmits<{ (e: 'change'): void }>()
</script>
