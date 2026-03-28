<template>
  <div
    class="animate-pulse rounded-md bg-muted"
    :class="[customClass]"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
interface Props {
  customClass?: string
}

defineProps<Props>()
</script>
