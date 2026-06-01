<template>
  <section class="empty-dataset-state">
    <EmptyState :title="title" :description="description">
      <template #action>
        <div class="action-row">
          <router-link v-if="actionRoute" :to="actionRoute">
            <el-button type="primary">{{ actionLabel || '去处理' }}</el-button>
          </router-link>
          <el-button v-else type="primary" plain>{{ actionLabel || '已知晓' }}</el-button>
        </div>
      </template>
    </EmptyState>
  </section>
</template>

<script setup lang="ts">
import EmptyState from '@/components/common/EmptyState.vue'

defineProps<{
  title: string
  description: string
  actionLabel?: string
  actionRoute?: string
}>()
</script>

<style scoped lang="scss">
.empty-dataset-state {
  padding: 20px;
  border: 1px dashed color-mix(in srgb, var(--macos-border) 82%, transparent);
  border-radius: 18px;
  background: color-mix(in srgb, var(--macos-bg-secondary) 96%, transparent);
}

.action-row {
  display: flex;
  justify-content: center;
}
</style>
