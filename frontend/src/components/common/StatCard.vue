<template>
  <div :class="['stat-card', variantClass, { 'stat-card--compact': compact }]">
    <div class="stat-icon">
      <slot name="icon" />
    </div>
    <div class="stat-content">
      <div class="stat-title">{{ title }}</div>
      <div class="stat-value">{{ value }}</div>
      <div v-if="subtitle" class="stat-subtitle">{{ subtitle }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  title: string;
  value: string | number;
  subtitle?: string;
  variant?: 'cherry' | 'blue' | 'green' | 'orange';
  compact?: boolean;
}>();

const variantClass = `stat-${props.variant || 'blue'}`;
</script>

<style scoped>
.stat-card {
  position: relative;
  isolation: isolate;
  padding: 1.5rem;
  border-radius: 10px;
  color: rgb(var(--color-foreground));
  min-height: 120px;
  height: 120px;
  background: rgb(var(--color-card));
  border: 1px solid rgb(var(--color-border) / 0.8);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.stat-card--compact {
  min-height: 112px;
  height: 112px;
  padding: 1.1rem;
}

.stat-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.stat-icon {
  position: absolute;
  right: -6px;
  top: 16px;
  z-index: 1;
  pointer-events: none;
  opacity: 0.12;
  font-size: 110px;
  line-height: 1;
  color: rgb(var(--color-primary) / 0.25);
}

.stat-icon :deep(svg) {
  width: 110px;
  height: 110px;
}

.stat-card--compact .stat-icon {
  right: -10px;
  top: 10px;
}

.stat-card--compact .stat-icon :deep(svg) {
  width: 88px;
  height: 88px;
}

.stat-title {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-weight: 800;
  color: rgb(var(--color-muted-foreground));
  opacity: 0.95;
}

.stat-value {
  font-size: 1.875rem;
  font-weight: 900;
  margin-top: 0.25rem;
  color: rgb(var(--color-foreground));
}

.stat-card--compact .stat-value {
  font-size: 1.45rem;
}

.stat-subtitle {
  font-size: 0.75rem;
  margin-top: auto;
  color: rgb(var(--color-muted-foreground));
  opacity: 0.95;
  white-space: normal;
  line-height: 1.15;
}

.stat-cherry {
  background: linear-gradient(135deg, rgb(var(--color-card)), rgb(var(--color-card)));
  border-color: rgb(var(--color-accent) / 0.55);
  box-shadow: inset 0 4px 0 rgb(var(--color-accent) / 0.95), var(--shadow-card);
}

.stat-blue {
  background: linear-gradient(135deg, rgb(var(--color-card)), rgb(var(--color-card)));
  border-color: rgb(var(--color-primary) / 0.45);
  box-shadow: inset 0 4px 0 rgb(var(--color-primary) / 0.9), var(--shadow-card);
}

.stat-green {
  background: linear-gradient(135deg, rgb(var(--color-card)), rgb(var(--color-card)));
  border-color: rgb(var(--color-success) / 0.5);
  box-shadow: inset 0 4px 0 rgb(var(--color-success) / 0.95), var(--shadow-card);
}

.stat-orange {
  background: linear-gradient(135deg, rgb(var(--color-card)), rgb(var(--color-card)));
  border-color: rgb(var(--color-warning) / 0.6);
  box-shadow: inset 0 4px 0 rgb(var(--color-warning) / 0.95), var(--shadow-card);
}
</style>
