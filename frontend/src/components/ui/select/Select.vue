<template>
  <SelectRoot v-bind="forwardedProps" v-model="modelValue">
    <SelectTrigger
      :class="cn(
        'flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        props.class
      )"
      v-bind="$attrs"
    >
      <slot name="trigger">
        <SelectValue :placeholder="placeholder" />
      </slot>
      <SelectIcon as-child>
        <ChevronDown class="h-4 w-4 opacity-50" />
      </SelectIcon>
    </SelectTrigger>
    <SelectContent>
      <slot />
    </SelectContent>
  </SelectRoot>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { ChevronDown } from "lucide-vue-next";
import {
  SelectContent,
  SelectIcon,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  useForwardProps, // Import useForwardProps from radix-vue
} from "radix-vue";
import { cn } from "@/lib/utils";

interface SelectProps {
  modelValue?: string;
  placeholder?: string;
  class?: string;
}

const props = withDefaults(defineProps<SelectProps>(), {
  placeholder: "Select an option",
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const modelValue = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value === undefined ? '' : value),
});

const forwardedProps = useForwardProps(props);
</script>
