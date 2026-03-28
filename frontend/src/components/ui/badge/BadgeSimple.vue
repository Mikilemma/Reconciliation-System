<template>
  <span :class="badgeClasses">
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

interface BadgeProps {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline';
  size?: 'default' | 'sm' | 'lg';
  class?: string;
}

const props = withDefaults(defineProps<BadgeProps>(), {
  variant: 'default',
  size: 'default',
});

const badgeClasses = computed(() => {
  const baseClasses = 'inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors';
  
  const variantClasses = {
    default: 'border-transparent bg-primary text-primary-foreground shadow hover:bg-warning hover:text-warning-foreground',
    secondary: 'border-transparent bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground',
    destructive: 'border-transparent bg-primary text-primary-foreground shadow hover:bg-warning hover:text-warning-foreground',
    outline: 'border-primary bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground',
  };
  
  const sizeClasses = {
    default: 'px-2.5 py-0.5 text-xs',
    sm: 'px-2 py-0.5 text-xs',
    lg: 'px-3 py-1 text-sm',
  };
  
  return `${baseClasses} ${variantClasses[props.variant]} ${sizeClasses[props.size]} ${props.class || ''}`;
});
</script>
