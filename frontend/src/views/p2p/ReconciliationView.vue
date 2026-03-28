<template>
  <div class="container mx-auto p-6 space-y-6">
    <!-- Header -->
    <div class="animate-fade-in">
      <h1 class="text-3xl font-bold text-foreground mb-2">P2P Reconciliation</h1>
      <p class="text-foreground">Monitor and manage your settlement reconciliation process</p>
    </div>

    <!-- KPI Cards -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <div v-for="i in 4" :key="i" class="p-6 rounded-xl border border-border/50 bg-card">
        <Skeleton class="h-4 w-24 mb-4" />
        <Skeleton class="h-8 w-32 mb-2" />
        <Skeleton class="h-4 w-16" />
      </div>
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-10">
      <button
        v-for="(kpi, index) in kpiData"
        :key="kpi.title"
        class="text-left rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        :class="{'ring-2 ring-primary': activeFilter === (kpi as any).id}"
        @click="filterByKPI((kpi as any).id)"
      >
        <StatCard
          :title="kpi.title"
          :value="kpi.value"
          :subtitle="kpi.change || 'Stable'"
          :variant="getKpiVariant((kpi as any).id)"
        >
          <template #icon>
            <component :is="kpi.icon" />
          </template>
        </StatCard>
      </button>
    </div>

    <!-- Main Content Grid -->
    <div class="h-8"></div>

    <div class="grid grid-cols-1 gap-6 mt-2">
      <!-- Reconciliation Status -->
      <div>
        <Card class="banking-card animate-slide-up border-0 shadow-none bg-transparent" style="animation-delay: 400ms">
          <CardHeader>
            <CardTitle class="flex items-center justify-between">
              <div class="flex items-center gap-2 font-bold">
                <Activity class="h-5 w-5 text-primary" />
                Process Summary
              </div>
              <div class="flex items-center gap-2" v-if="sessions.length > 0">
                <div class="flex items-center gap-2">
                  <label for="reconciliation-date" class="text-xs font-black uppercase tracking-widest text-muted-foreground">Session Date</label>
                  <input
                    id="reconciliation-date"
                    v-model="selectedDate"
                    type="date"
                    class="w-48 p-2 border border-input rounded-md bg-background focus:ring-2 focus:ring-ring focus:border-primary transition-all outline-none text-sm text-primary"
                    :max="today"
                    @change="handleDateChange"
                  />
                  <button
                    type="button"
                    class="px-3 py-2 text-xs rounded-md border transition-colors duration-150"
                    :class="!selectedDate
                      ? 'bg-warning text-warning-foreground border-warning'
                      : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
                    @click="handleAllDates"
                  >
                    ALL
                  </button>
                </div>
              </div>
              
              <!-- Sessions for selected date -->
              <div v-if="selectedDate && filteredSessions.length > 0" class="mt-3">
                <div class="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
                  Sessions on {{ new Date(selectedDate).toLocaleDateString() }}
                </div>
                <div class="flex gap-2 flex-wrap">
                  <button
                    @click="handleSessionSelect('all')"
                    class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150"
                    :class="{
                      'bg-warning text-warning-foreground border-warning': selectedSessionId === 'all',
                      'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground': selectedSessionId !== 'all'
                    }"
                  >
                    All Sessions
                  </button>
                  <button
                    v-for="session in filteredSessions"
                    :key="session.id"
                    @click="handleSessionSelect(session.id)"
                    class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150"
                    :class="{
                      'bg-warning text-warning-foreground border-warning': selectedSessionId === session.id,
                      'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground': selectedSessionId !== session.id
                    }"
                  >
                    {{ new Date(session.processedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                  </button>
                </div>
              </div>
              
              <div v-else-if="selectedDate && filteredSessions.length === 0" class="mt-3">
                <div class="text-xs text-muted-foreground">
                  No sessions found for {{ new Date(selectedDate).toLocaleDateString() }}
                </div>
              </div>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="loading" class="space-y-4">
              <Skeleton class="h-12 w-full" />
              <Skeleton class="h-24 w-full" />
            </div>
            
            <div v-else class="space-y-6">
              <div v-if="sessions.length > 0" class="flex flex-col md:flex-row gap-6 p-6 rounded-2xl bg-muted/20">
                <div class="flex-1">
                  <h4 class="text-sm font-bold text-muted-foreground uppercase tracking-widest mb-2">Reconciliation Health</h4>
                  <div class="flex items-center gap-4">
                    <div class="text-4xl font-black" :class="stats.settlementRate > 95 ? 'text-success' : stats.settlementRate > 80 ? 'text-warning' : 'text-destructive'">
                      {{ stats.settlementRate.toFixed(1) }}%
                    </div>
                    <div>
                      <p class="text-xs font-bold text-foreground/60 uppercase">System Match Rate</p>
                      <p class="text-xs text-muted-foreground">Updated {{ formatTimeAgo(sessions[0]?.processedAt) }}</p>
                    </div>
                  </div>
                </div>
                
                <div class="flex gap-4">
                   <div class="text-center px-6 py-3 rounded-xl bg-card">
                     <p class="text-xl font-black text-success">{{ stats.settled || 0 }}</p>
                     <p class="text-[10px] font-bold text-muted-foreground uppercase">Matched</p>
                   </div>
                   <div class="text-center px-6 py-3 rounded-xl bg-card">
                     <p class="text-xl font-black text-destructive">{{ stats.discrepant || 0 }}</p>
                     <p class="text-[10px] font-bold text-muted-foreground uppercase">Discrepant</p>
                   </div>
                </div>
              </div>

              <div v-else class="text-center py-12 bg-muted/10 rounded-2xl">
                <Activity class="h-10 w-10 text-muted-foreground/30 mx-auto mb-4" />
                <p class="text-muted-foreground font-medium">No reconciliation sessions found.</p>
                <Button @click="startNewReconciliation" variant="link" class="mt-2">Upload files to get started</Button>
              </div>
              
              <div class="flex gap-3">
                <Button @click="startNewReconciliation" class="btn-primary flex-1 h-11 shadow-lg shadow-primary/20">
                  <UploadCloud class="h-4 w-4 mr-2" />
                  New Upload & Sync
                </Button>
                <Button @click="router.push({ path: '/p2p/reports', query: { sessionId: selectedSessionId } })" variant="outline" class="h-11 px-6">
                  <BarChart class="h-4 w-4 mr-2" />
                  Reports
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Results Table Section -->
    <Card class="banking-card-elevated animate-slide-up mt-4 border-0 shadow-none bg-transparent" style="animation-delay: 600ms">
      <CardHeader class="border-b bg-muted/5 p-6">
        <div class="flex justify-between items-center">
          <div>
            <CardTitle class="text-xl">Transaction Breakdown</CardTitle>
            <p class="text-sm text-muted-foreground">Deep dive into the latest reconciliation results</p>
          </div>
          <div class="flex gap-2">
             <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-muted text-xs font-bold">
               <span class="w-2 h-2 rounded-full bg-primary animate-pulse"></span> Live Data
             </div>
          </div>
        </div>
      </CardHeader>
      <CardContent class="p-6">
        <ReconciliationResultsTable 
          ref="resultsTableRef"
          :transactions="transactions" 
          :loading="loading"
          :initial-status-filter="activeFilter"
          @view-details="viewTransactionDetails"
        />
      </CardContent>
    </Card>

    <!-- Detailed Modal -->
    <TransactionDetailsModal
      v-if="showTransactionDetailsModal && selectedTransactionForDetails"
      :show="showTransactionDetailsModal"
      :transaction="selectedTransactionForDetails"
      @close="showTransactionDetailsModal = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import Skeleton from '@/components/ui/skeleton.vue';
import { Button } from '@/components/ui/button';
import { TrendingUp, AlertTriangle, Users, DollarSign, Activity, UploadCloud, BarChart, Settings, CheckCircle, Search } from 'lucide-vue-next';
import ReconciliationResultsTable from '@/components/p2p/ReconciliationResultsTable.vue';
import StatCard from '@/components/common/StatCard.vue';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import TransactionDetailsModal from '@/components/p2p/TransactionDetailsModal.vue';
import { useReconciliationStore } from '@/stores/reconciliation';
import { storeToRefs } from 'pinia';

const router = useRouter();
const reconciliationStore = useReconciliationStore();

// Extract reactive state from store using storeToRefs
const {
  sessions,
  transactions,
  selectedSessionId,
  selectedDate,
  loading,
  error,
  filteredSessions,
  stats
} = storeToRefs(reconciliationStore);

// Extract actions from store
const {
  initialize,
  selectSession,
  selectDate: setDate,
  clearError
} = reconciliationStore;

// Local state
const activeFilter = ref<string>('all');
const showTransactionDetailsModal = ref<boolean>(false);
const selectedTransactionForDetails = ref<any>(null);
const resultsTableRef = ref<any>(null);

// Computed KPI data from store stats
const kpiData = computed(() => {
  if (!stats.value) return [];

  return [
    {
      id: 'all',
      title: 'Session Total',
      value: stats.value.total.toLocaleString(),
      change: 'Transactions',
      trend: 'up' as const,
      icon: Activity,
      color: 'text-primary'
    },
    {
      id: 'settled',
      title: 'Settled',
      value: stats.value.settled.toLocaleString(),
      change: 'Matched',
      trend: 'up' as const,
      icon: CheckCircle,
      color: 'text-success'
    },
    {
      id: 'discrepant',
      title: 'Discrepancies',
      value: stats.value.discrepant.toString(),
      change: 'Issues',
      trend: stats.value.discrepant > 0 ? 'down' as const : 'up' as const,
      icon: AlertTriangle,
      color: 'text-destructive'
    },
    {
      id: 'matchRate',
      title: 'Match Rate',
      value: stats.value.settlementRate.toFixed(1) + '%',
      change: 'Accuracy',
      trend: stats.value.settlementRate > 95 ? 'up' as const : 'down' as const,
      icon: TrendingUp,
      color: 'text-purple-600'
    }
  ];
});

const getKpiVariant = (id: string) => {
  switch (id) {
    case 'settled':
      return 'green';
    case 'discrepant':
      return 'orange';
    case 'matchRate':
      return 'cherry';
    case 'all':
    default:
      return 'blue';
  }
};

const formatTimeAgo = (dateStr?: string) => {
  if (!dateStr) return 'Unknown';
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);
  
  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${diffDays}d ago`;
};

// Helper to get today's date in YYYY-MM-DD format
const today = new Date().toISOString().split('T')[0];

// Event handlers
const handleDateChange = () => {
  setDate(selectedDate.value);
};

const handleSessionSelect = (sessionId: string) => {
  selectSession(sessionId);
};

const handleAllDates = () => {
  setDate('');
  selectSession('all');
};

const viewTransactionDetails = (transaction: any) => {
  selectedTransactionForDetails.value = transaction;
  showTransactionDetailsModal.value = true;
};

const filterByKPI = (kpiId: string) => {
  activeFilter.value = activeFilter.value === kpiId ? 'all' : kpiId;
};

// Initialize on mount
onMounted(async () => {
  await initialize();
  
  // Check for query param sessionId
  const routeSessionId = router.currentRoute.value.query.sessionId as string;
  if (routeSessionId) {
    selectSession(routeSessionId);
  }
});

const startNewReconciliation = () => {
  // Navigate to file upload page to start new reconciliation
  router.push('/p2p/upload');
};

const viewDetails = () => {
  // Navigate to detailed reconciliation view
  console.log('View reconciliation details');
  // TODO: Implement detailed view
};

const uploadFile = () => {
  // Navigate to file upload page
  router.push('/p2p/upload');
};

const generateReport = () => {
  // Navigate to reports page
  router.push('/p2p/reports');
};

const viewDisputes = () => {
  // Navigate to disputes page
  router.push('/p2p/disputes');
};

const openSettings = () => {
  // Navigate to settings page
  router.push('/settings');
};
</script>
