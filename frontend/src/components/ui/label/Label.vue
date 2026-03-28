<template>
  <label :class="labelClasses">
    <slot />
  </label>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface LabelProps {
  class?: string;
  for?: string;
}

const props = withDefaults(defineProps<LabelProps>(), {});

const labelClasses = computed(() => {
  const baseClasses = 'text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70';
  
  return `${baseClasses} ${props.class || ''}`;
});
</script>
