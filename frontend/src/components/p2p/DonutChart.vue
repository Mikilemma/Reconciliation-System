<template>
  <div class="relative flex items-center justify-center">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" class="transform -rotate-90">
      <!-- Background Circle -->
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="transparent"
        stroke="currentColor"
        stroke-width="12"
        class="text-muted/20"
      />
      
      <!-- Data Segments -->
      <circle
        v-for="(segment, index) in segments"
        :key="index"
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="transparent"
        :stroke="segment.color"
        stroke-width="12"
        :stroke-dasharray="strokeDasharray"
        :stroke-dashoffset="getOffset(index)"
        stroke-linecap="round"
        class="transition-all duration-1000 ease-out"
      />
    </svg>
    
    <!-- Center Content -->
    <div class="absolute flex flex-col items-center justify-center text-center">
      <slot name="center">
        <p class="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">{{ label }}</p>
        <p class="text-xl font-black text-foreground">{{ centerValue }}</p>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface DataItem {
  label: string;
  value: number;
  color: string;
}

const props = defineProps<{
  data: DataItem[];
  size?: number;
  label?: string;
  centerValue?: string;
}>();

const size = props.size || 200;
const radius = (size / 2) - 10;
const circumference = 2 * Math.PI * radius;
const strokeDasharray = circumference;

const total = computed(() => props.data.reduce((sum, item) => sum + item.value, 0));

const segments = computed(() => {
  if (total.value === 0) return [];
  return props.data.map(item => ({
    ...item,
    percentage: (item.value / total.value) * 100
  }));
});

const getOffset = (index: number) => {
  let previousTotal = 0;
  for (let i = 0; i < index; i++) {
    const segment = segments.value[i];
    if (segment && segment.value !== undefined) {
      previousTotal += segment.value;
    }
  }
  const percentage = total.value > 0 ? (previousTotal / total.value) : 0;
  return circumference * (1 - percentage);
};
</script>

<style scoped>
circle {
  transition: stroke-dashoffset 1s ease-in-out;
}
</style>
