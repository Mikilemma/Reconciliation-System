<template>
  <div v-if="show" class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-40 z-50">
    <div class="bg-card rounded-lg shadow-lg w-11/12 max-w-2xl p-6 text-foreground">
      <h3 class="text-lg font-bold mb-4">Multiple Sessions Found</h3>
      <p class="text-sm text-muted-foreground mb-4">Select the session you want to export:</p>
      <div class="space-y-2 max-h-64 overflow-auto">
        <div v-for="s in sessions" :key="s.id" class="p-3 border rounded flex items-center justify-between">
          <div>
            <div class="font-medium">Session: {{ s.id }}</div>
            <div class="text-xs text-muted-foreground">Date: {{ s.settlementDate }} • Created: {{ s.createdAt }}</div>
            <div class="text-xs text-muted-foreground">Txns: {{ s.totalTransactions }} • Settled: {{ s.settledCount }} • Discrepant: {{ s.discrepantCount }}</div>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-outline" @click="$emit('select', s.id)">Export</button>
          </div>
        </div>
      </div>
      <div class="mt-4 text-right">
        <button class="btn btn-ghost" @click="$emit('close')">Close</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps({
  sessions: { type: Array as any, default: () => [] },
  show: { type: Boolean, default: false }
})
</script>

<style scoped>
.btn { padding: 0.4rem 0.75rem; border-radius: 0.375rem; }
.btn-outline { border: 1px solid #ddd; background: white; }
.btn-ghost { background: transparent; }
</style>