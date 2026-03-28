<template>
  <div
    class="inline-flex items-center justify-center"
    :class="[sizeClass, customClass]"
  >
    <div
      class="animate-spin rounded-full border-2 border-current border-t-transparent"
      :class="[borderWidthClass]"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: 'sm' | 'md' | 'lg' | 'xl'
  customClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md'
})

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'h-4 w-4'
    case 'md':
      return 'h-6 w-6'
    case 'lg':
      return 'h-8 w-8'
    case 'xl':
      return 'h-12 w-12'
    default:
      return 'h-6 w-6'
  }
})

const borderWidthClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'border-2'
    case 'md':
      return 'border-2'
    case 'lg':
      return 'border-3'
    case 'xl':
      return 'border-4'
    default:
      return 'border-2'
  }
})
</script>
