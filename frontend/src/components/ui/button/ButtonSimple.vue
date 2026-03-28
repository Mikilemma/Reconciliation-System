<template>
  <button 
    :class="buttonClasses"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface ButtonProps {
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link';
  size?: 'default' | 'sm' | 'lg' | 'icon';
  class?: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<ButtonProps>(), {
  variant: 'default',
  size: 'default',
  disabled: false,
});

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();

const buttonClasses = computed(() => {
  const baseClasses = 'inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50';
  
  const variantClasses = {
    default: 'bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground',
    destructive: 'bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground',
    outline: 'border border-primary bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground',
    secondary: 'bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground',
    ghost: 'bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground',
    link: 'bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground active:bg-warning active:text-warning-foreground underline-offset-4 hover:underline',
  };
  
  const sizeClasses = {
    default: 'h-10 px-4 py-2',
    sm: 'h-9 rounded-md px-3',
    lg: 'h-11 rounded-md px-8',
    icon: 'h-10 w-10',
  };
  
  return `${baseClasses} ${variantClasses[props.variant]} ${sizeClasses[props.size]} ${props.class || ''}`;
});
</script>
