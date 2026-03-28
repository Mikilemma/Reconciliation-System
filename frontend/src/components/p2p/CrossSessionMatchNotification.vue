<template>
  <div v-if="show && uploadResult" class="fixed top-4 right-4 z-50 max-w-md">
    <div class="bg-white border-2 border-primary rounded-xl shadow-2xl overflow-hidden animate-slide-in">
      <!-- Header -->
      <div class="bg-primary text-primary-foreground p-4">
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-lg bg-primary-foreground/20">
            <CheckCircle class="h-6 w-6" />
          </div>
          <div class="flex-1">
            <h3 class="font-bold text-lg">Cross-Session Matches Found!</h3>
            <p class="text-sm opacity-90">{{ uploadResult.crossSessionMatches.length }} transactions resolved automatically</p>
          </div>
          <button @click="dismiss" class="p-1 rounded-lg hover:bg-primary-foreground/20 transition-colors">
            <X class="h-5 w-5" />
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="p-4 space-y-3">
        <!-- Summary Stats -->
        <div class="grid grid-cols-2 gap-3">
          <div class="bg-success/10 border border-success/20 rounded-lg p-3">
            <div class="flex items-center gap-2">
              <CheckCircle class="h-4 w-4 text-success" />
              <span class="text-sm font-medium text-success">Exact Matches</span>
            </div>
            <p class="text-lg font-bold text-success mt-1">
              {{ exactMatchesCount }}
            </p>
          </div>
          <div class="bg-warning/10 border border-warning/20 rounded-lg p-3">
            <div class="flex items-center gap-2">
              <Search class="h-4 w-4 text-warning" />
              <span class="text-sm font-medium text-warning">Fuzzy Matches</span>
            </div>
            <p class="text-lg font-bold text-warning mt-1">
              {{ fuzzyMatchesCount }}
            </p>
          </div>
        </div>

        <!-- Match Details -->
        <div v-if="showDetails" class="space-y-2 max-h-60 overflow-y-auto">
          <div 
            v-for="match in uploadResult.crossSessionMatches.slice(0, 5)" 
            :key="match.id"
            class="border border-border rounded-lg p-3 hover:bg-muted/50 transition-colors"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="text-xs font-medium px-2 py-1 rounded-full"
                :class="getMatchTypeClass(match.matchType)">
                {{ match.matchType.toUpperCase() }}
              </span>
              <div class="text-sm text-muted-foreground">
                {{ uploadResult.crossSessionMatches.length }} matches found
                <span v-if="uploadResult.integratedFromPreviousSessions && uploadResult.integratedFromPreviousSessions > 0" class="text-success">
                  • {{ uploadResult.integratedFromPreviousSessions }} integrated into report
                </span>
              </div>
            </div>
            <div class="text-sm">
              <p class="font-medium">Session {{ match.originalSessionId.slice(0, 8) }} → Session {{ match.matchedSessionId.slice(0, 8) }}</p>
              <p class="text-muted-foreground text-xs">Matched {{ formatTimeAgo(match.matchedAt) }}</p>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex gap-2 pt-2">
          <button 
            @click="toggleDetails"
            class="flex-1 px-3 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors text-sm font-medium"
          >
            {{ showDetails ? 'Hide Details' : 'Show Details' }}
          </button>
          <button 
            @click="viewMatches"
            class="flex-1 px-3 py-2 border border-primary text-primary rounded-lg hover:bg-primary/10 transition-colors text-sm font-medium"
          >
            View All Matches
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { CheckCircle, Search, X } from 'lucide-vue-next';
import type { UploadResult, CrossSessionMatch } from '@/services/p2pApi';

interface Props {
  show: boolean;
  uploadResult?: UploadResult | null;
}

interface Emits {
  (e: 'dismiss'): void;
  (e: 'view-matches'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const showDetails = ref(false);

const exactMatchesCount = computed(() => {
  if (!props.uploadResult) return 0;
  return props.uploadResult.crossSessionMatches.filter(m => m.matchType === 'exact').length;
});

const fuzzyMatchesCount = computed(() => {
  if (!props.uploadResult) return 0;
  return props.uploadResult.crossSessionMatches.filter(m => m.matchType === 'fuzzy').length;
});

function getMatchTypeClass(type: string) {
  switch (type) {
    case 'exact':
      return 'bg-success/10 text-success border border-success/20';
    case 'fuzzy':
      return 'bg-warning/10 text-warning border border-warning/20';
    case 'manual':
      return 'bg-primary/10 text-primary border border-primary/20';
    default:
      return 'bg-muted text-muted-foreground border border-border';
  }
}

function formatConfidence(confidence: number) {
  return Math.round(confidence);
}

function formatTimeAgo(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);
  
  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${diffDays}d ago`;
}

function toggleDetails() {
  showDetails.value = !showDetails.value;
}

function dismiss() {
  emit('dismiss');
}

function viewMatches() {
  emit('view-matches');
}
</script>

<style scoped>
.animate-slide-in {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
</style>
