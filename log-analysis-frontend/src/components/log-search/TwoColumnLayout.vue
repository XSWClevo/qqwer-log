<template>
  <div class="two-column-layout">
    <aside class="left-sidebar">
      <slot name="sidebar"></slot>
    </aside>
    <main class="main-content">
      <slot name="content"></slot>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * TwoColumnLayout - Splunk/Kibana style two-column layout
 * Left sidebar: 25% width (field facets)
 * Main content: 75% width (charts and log list)
 */
</script>

<style scoped lang="scss">
.two-column-layout {
  display: flex;
  gap: 16px;
  width: 100%;
  min-height: calc(100vh - 200px);
}

.left-sidebar {
  flex: 0 0 25%;
  min-width: 280px;
  max-width: 360px;
  background: var(--macos-bg-secondary);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  min-width: 0; // Prevent flex item from overflowing
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// Responsive adjustments
@media (max-width: 1200px) {
  .left-sidebar {
    flex: 0 0 30%;
    min-width: 260px;
  }
}

@media (max-width: 992px) {
  .two-column-layout {
    flex-direction: column;
  }
  
  .left-sidebar {
    flex: none;
    width: 100%;
    max-width: none;
    min-width: auto;
    max-height: 400px;
  }
  
  .main-content {
    width: 100%;
  }
}
</style>
