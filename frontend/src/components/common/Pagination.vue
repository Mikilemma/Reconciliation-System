
<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  currentPage: number
  totalPages: number
  totalItems: number
  itemsPerPage: number
}>()

const emit = defineEmits<{
  (e: 'update:currentPage', page: number): void
}>()

const visiblePages = computed(() => {
  const total = props.totalPages
  const current = props.currentPage
  const delta = 2 // Number of pages to show on each side

  const range: (number | string)[] = []
  const left = Math.max(2, current - delta)
  const right = Math.min(total - 1, current + delta)

  range.push(1) // Always show first page

  if (left > 2) range.push('...')

  for (let i = left; i <= right; i++) {
    range.push(i)
  }

  if (right < total - 1) range.push('...')

  if (total > 1) range.push(total) // Always show last page

  return range
})

const goToPage = (page: number | string) => {
  if (typeof page === 'number' && page !== props.currentPage) {
    emit('update:currentPage', page)
  }
}
</script>

<template>
  <div v-if="totalPages > 1" class="flex items-center justify-between mt-4">
    <p class="text-sm text-foreground">
      Showing {{ (currentPage - 1) * itemsPerPage + 1 }} to {{ Math.min(currentPage * itemsPerPage, totalItems) }} of {{ totalItems }} results
    </p>
    <div class="flex items-center gap-2">
      <Button
        variant="outline"
        size="sm"
        :disabled="currentPage === 1"
        @click="goToPage(currentPage - 1)"
      >
        Previous
      </Button>
      <div class="flex items-center gap-1">
        <template v-for="(page, index) in visiblePages" :key="index">
          <Button
            v-if="typeof page === 'number'"
            variant="outline"
            size="sm"
            :class="currentPage === page ? 'bg-primary text-primary-foreground' : ''"
            @click="goToPage(page)"
          >
            {{ page }}
          </Button>
          <span v-else class="px-2 text-muted-foreground">...</span>
        </template>
      </div>
      <Button
        variant="outline"
        size="sm"
        :disabled="currentPage === totalPages"
        @click="goToPage(currentPage + 1)"
      >
        Next
      </Button>
    </div>
  </div>
</template>
