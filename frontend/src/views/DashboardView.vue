<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useReconciliationData } from '@/composables/useReconciliationData'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  AlertTriangle,
  CheckCircle2,
  Download,
  FileClock,
  GitMerge,
  PlayCircle,
  Scale,
  TrendingUp
} from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import StatCard from '@/components/common/StatCard.vue'

const {
  isLoading,
  error,
  sessions,
  selectedSessionId,
  transactions,
  loadSessions,
  loadTransactions,
  loadTransactionsForSessions
} = useReconciliationData()

const currentSession = computed(() => sessions.value.find((s) => s.id === selectedSessionId.value) || null)
const selectedDate = ref<string>('')
const today = new Date().toISOString().split('T')[0]

const filteredSessions = computed(() => {
  if (!selectedDate.value) return sessions.value
  return sessions.value.filter((s) => (s.settlementDate || '').toString().startsWith(selectedDate.value))
})

const activeScopeSessions = computed(() => {
  if (selectedSessionId.value === 'all') return filteredSessions.value
  return sessions.value.filter((s) => s.id === selectedSessionId.value)
})

const latestScopeSession = computed(() => {
  return activeScopeSessions.value.length ? activeScopeSessions.value[0] : null
})

const summary = computed(() => {
  const total = transactions.value.length
  const settled = transactions.value.filter((t) => t.status === 'settled').length
  const discrepant = transactions.value.filter((t) => t.status === 'discrepant').length
  const pending = transactions.value.filter((t) => t.status === 'pending').length
  const totalAmount = transactions.value.reduce((sum, t) => sum + (t.amount || 0), 0)
  const settledAmount = transactions.value.filter((t) => t.status === 'settled').reduce((sum, t) => sum + (t.amount || 0), 0)
  const exceptionAmount = transactions.value.filter((t) => t.status !== 'settled').reduce((sum, t) => sum + (t.amount || 0), 0)
  const reconciliationRate = total === 0 ? 0 : (settled / total) * 100
  const unresolvedRate = total === 0 ? 0 : ((discrepant + pending) / total) * 100

  return {
    total,
    settled,
    discrepant,
    pending,
    totalAmount,
    settledAmount,
    exceptionAmount,
    reconciliationRate,
    unresolvedRate
  }
})

const statusDistribution = computed(() => {
  const total = summary.value.total || 1
  return [
    { label: 'Settled', count: summary.value.settled, pct: Math.round((summary.value.settled / total) * 100), color: 'bg-emerald-500' },
    { label: 'Discrepant', count: summary.value.discrepant, pct: Math.round((summary.value.discrepant / total) * 100), color: 'bg-amber-500' },
    { label: 'Pending', count: summary.value.pending, pct: Math.round((summary.value.pending / total) * 100), color: 'bg-slate-500' }
  ]
})

const sourceCoverage = computed(() => {
  const total = transactions.value.length || 1
  const switchLinked = transactions.value.filter((t) => !!t.switchData).length
  const atmLinked = transactions.value.filter((t) => !!t.atmData).length
  const payableLinked = transactions.value.filter((t) => !!t.payableData).length
  const receivableLinked = transactions.value.filter((t) => !!t.receivableData).length

  return [
    { label: 'Switch', count: switchLinked, pct: Math.round((switchLinked / total) * 100), color: 'bg-blue-500' },
    { label: 'ATM', count: atmLinked, pct: Math.round((atmLinked / total) * 100), color: 'bg-indigo-500' },
    { label: 'Payable', count: payableLinked, pct: Math.round((payableLinked / total) * 100), color: 'bg-green-500' },
    { label: 'Receivable', count: receivableLinked, pct: Math.round((receivableLinked / total) * 100), color: 'bg-yellow-500' }
  ]
})

const analyticsView = ref<'status' | 'source'>('status')
const analyticsItems = computed(() =>
  analyticsView.value === 'status' ? statusDistribution.value : sourceCoverage.value
)

const dataQuality = computed(() => {
  const missingStan = transactions.value.filter((t) => !t.stan).length
  const missingRef = transactions.value.filter((t) => !t.transactionRef).length
  const missingDate = transactions.value.filter((t) => !t.transactionDate).length
  const missingTerminal = transactions.value.filter((t) => !t.terminalId).length
  return { missingStan, missingRef, missingDate, missingTerminal }
})

const topDiscrepancies = computed(() => {
  const map = new Map<string, number>()
  transactions.value.forEach((t) => {
    const key = t.discrepancyType || (t.status !== 'settled' ? 'unspecified' : '')
    if (!key) return
    map.set(key, (map.get(key) || 0) + 1)
  })
  return Array.from(map.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([type, count]) => ({ type, count }))
})

const alerts = computed(() => {
  const items: string[] = []
  if (summary.value.unresolvedRate > 20) items.push('High unresolved ratio detected.')
  if (dataQuality.value.missingStan > 0) items.push('Some records are missing STAN values.')
  if (dataQuality.value.missingRef > 0) items.push('Some records are missing transaction references.')
  if (!sessions.value.length) items.push('No session selected.')
  return items.slice(0, 3)
})

const matchRingStyle = computed(() => {
  const rate = Math.max(0, Math.min(100, summary.value.reconciliationRate))
  return {
    background: `conic-gradient(rgb(var(--color-primary)) ${rate}%, rgb(var(--color-warning)) ${rate}% 100%)`
  }
})

const matchRateValue = computed(() => Math.max(0, Math.min(100, summary.value.reconciliationRate)))
const matchRingCircumference = 2 * Math.PI * 42
const matchRingOffset = computed(() => matchRingCircumference * (1 - matchRateValue.value / 100))
const matchRateTone = computed(() => {
  if (matchRateValue.value >= 95) return 'text-emerald-600'
  if (matchRateValue.value >= 80) return 'text-primary'
  if (matchRateValue.value >= 60) return 'text-amber-600'
  return 'text-red-600'
})

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount)

const formatDateTime = (date?: string) => (date ? new Date(date).toLocaleString() : 'N/A')

const exportCurrentSession = () => {
  if (transactions.value.length === 0) return

  const headers = ['STAN', 'Transaction Ref', 'Amount', 'Date', 'Terminal ID', 'Status', 'Details', 'Source Files']
  const rows = transactions.value.map((t) => [
    t.stan || '',
    t.transactionRef || '',
    t.amount?.toString() || '',
    t.transactionDate || '',
    t.terminalId || '',
    t.status,
    t.details || '',
    t.sourceFiles || ''
  ])

  const csv = [headers.join(','), ...rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `dashboard-settlements-${new Date().toISOString().split('T')[0]}.csv`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

const applySessionSelection = async () => {
  if (selectedSessionId.value === 'all') {
    await loadTransactionsForSessions(activeScopeSessions.value.map((s) => s.id))
    return
  }
  await loadTransactions()
}

const showAllDates = async () => {
  const alreadyAllDates = !selectedDate.value
  selectedDate.value = ''
  selectedSessionId.value = 'all'
  if (alreadyAllDates) {
    await applySessionSelection()
  }
}

onMounted(async () => {
  isLoading.value = true
  await loadSessions()
  if (sessions.value.length > 0) {
    selectedDate.value = (sessions.value[0]?.settlementDate || '').toString().slice(0, 10)
    selectedSessionId.value = 'all'
    await applySessionSelection()
  }
  isLoading.value = false
})

watch(selectedDate, async () => {
  selectedSessionId.value = 'all'
  await applySessionSelection()
})
</script>

<template>
  <div class="w-full max-w-7xl space-y-5 xl:space-y-6">
    <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
      <div>
        <p class="text-xs uppercase tracking-widest text-muted-foreground">Financial Ops</p>
        <h1 class="mt-1 text-[1.65rem] font-black text-foreground md:text-3xl">Settlement Command Center</h1>
        <p class="mt-1 text-xs text-muted-foreground md:text-sm">Reconciliation status, risks, and action signals in one view.</p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label for="dashboard-session-date" class="text-xs font-black uppercase tracking-widest text-muted-foreground">Session Date</label>
        <input
          id="dashboard-session-date"
          v-model="selectedDate"
          type="date"
          :max="today"
          class="w-36 max-w-full p-2 border border-input rounded-md bg-background focus:ring-2 focus:ring-ring focus:border-primary transition-all outline-none text-sm text-primary"
        />
        <div class="flex gap-1 flex-wrap">
          <button
            type="button"
            class="px-3 py-2 text-xs rounded-md border transition-colors duration-150"
            :class="!selectedDate
              ? 'bg-warning text-warning-foreground border-warning'
              : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
            @click="showAllDates"
          >
            ALL
          </button>
          <button
            type="button"
            class="px-3 py-2 text-xs rounded-md border transition-colors duration-150"
            :class="selectedSessionId === 'all'
              ? 'bg-warning text-warning-foreground border-warning'
              : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
            @click="selectedSessionId = 'all'; applySessionSelection()"
          >
            All Sessions
          </button>
          <button
            v-if="selectedDate"
            v-for="s in filteredSessions"
            :key="s.id"
            type="button"
            class="px-3 py-2 text-xs rounded-md border transition-colors duration-150"
            :class="selectedSessionId === s.id
              ? 'bg-warning text-warning-foreground border-warning'
              : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
            @click="selectedSessionId = s.id; applySessionSelection()"
          >
            {{ new Date(s.processedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
          </button>
        </div>
        <Button size="sm" class="h-10 gap-2" :disabled="isLoading || transactions.length === 0" @click="exportCurrentSession">
          <Download class="h-4 w-4" />
          Export
        </Button>
      </div>
    </div>

    <Card class="overflow-hidden border-0 shadow-none bg-transparent">
      <CardContent class="p-0">
        <div class="grid grid-cols-1 gap-0 lg:grid-cols-3">
          <div class="bg-gradient-to-br from-primary/10 to-primary/5 p-4 lg:col-span-2 xl:p-5">
            <p class="text-xs uppercase tracking-widest text-muted-foreground">Current Session</p>
            <h2 class="mt-1 font-mono text-sm text-foreground">
              {{ selectedSessionId === 'all' ? 'All Sessions' : (currentSession?.id || 'N/A') }}
            </h2>
            <p class="mt-2 text-xs text-muted-foreground">
              Processed: {{ formatDateTime(latestScopeSession?.processedAt) }}
            </p>
            <div class="mt-3 grid grid-cols-2 gap-2">
              <div class="rounded-lg bg-background/80 p-2.5 border-0">
                <p class="text-[11px] text-muted-foreground">Total Value</p>
                <p class="text-sm font-bold text-foreground">{{ formatCurrency(summary.totalAmount) }}</p>
              </div>
              <div class="rounded-lg bg-background/80 p-2.5 border-0">
                <p class="text-[11px] text-muted-foreground">Exception Value</p>
                <p class="text-sm font-bold text-amber-700">{{ formatCurrency(summary.exceptionAmount) }}</p>
              </div>
            </div>
          </div>
          <div class="flex items-center justify-center bg-muted/20 p-3.5 xl:p-4">
            <div class="text-center">
              <div class="relative mx-auto h-28 w-28">
                <svg viewBox="0 0 100 100" class="h-full w-full -rotate-90">
                  <circle cx="50" cy="50" r="42" class="fill-none stroke-muted/50" stroke-width="8" />
                  <circle
                    cx="50"
                    cy="50"
                    r="42"
                    class="fill-none stroke-current transition-all duration-700 ease-out"
                    :class="matchRateTone"
                    stroke-width="8"
                    stroke-linecap="round"
                    :stroke-dasharray="matchRingCircumference"
                    :stroke-dashoffset="matchRingOffset"
                  />
                </svg>
                <div class="absolute inset-0 flex flex-col items-center justify-center">
                  <span class="text-xl font-black text-foreground">{{ summary.reconciliationRate.toFixed(0) }}%</span>
                  <span class="text-[10px] font-bold uppercase tracking-wide" :class="matchRateTone">
                    {{ summary.reconciliationRate >= 95 ? 'Excellent' : summary.reconciliationRate >= 80 ? 'Stable' : summary.reconciliationRate >= 60 ? 'Watch' : 'Risk' }}
                  </span>
                </div>
              </div>
              <p class="mt-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Match Rate</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>

    <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4 xl:gap-4">
      <StatCard compact title="Settled" :value="summary.settled" subtitle="Reconciled transactions" variant="green">
        <template #icon><CheckCircle2 /></template>
      </StatCard>
      <StatCard compact title="Discrepant" :value="summary.discrepant" subtitle="Needs investigation" variant="orange">
        <template #icon><AlertTriangle /></template>
      </StatCard>
      <StatCard compact title="Pending" :value="summary.pending" subtitle="Awaiting completion" variant="blue">
        <template #icon><FileClock /></template>
      </StatCard>
      <StatCard compact title="Net Position" :value="formatCurrency(summary.settledAmount - summary.exceptionAmount)" subtitle="Dashboard estimate" variant="cherry">
        <template #icon><Scale /></template>
      </StatCard>
    </div>

    <div class="grid grid-cols-1 gap-3 xl:grid-cols-3 xl:gap-4">
      <Card class="xl:col-span-2 border-0 shadow-none bg-transparent">
        <CardHeader class="pb-2">
          <div class="flex items-center justify-between gap-3">
            <CardTitle class="text-sm">Coverage Snapshot</CardTitle>
            <div class="inline-flex rounded-lg border border-border/70 p-1">
              <button
                type="button"
                class="rounded-md px-3 py-1 text-[11px] font-bold uppercase tracking-wide transition-colors"
                :class="analyticsView === 'status' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'"
                @click="analyticsView = 'status'"
              >
                Status
              </button>
              <button
                type="button"
                class="rounded-md px-3 py-1 text-[11px] font-bold uppercase tracking-wide transition-colors"
                :class="analyticsView === 'source' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'"
                @click="analyticsView = 'source'"
              >
                Source
              </button>
            </div>
          </div>
        </CardHeader>
        <CardContent class="space-y-2 pt-0">
          <div v-for="item in analyticsItems" :key="item.label">
            <div class="mb-1 flex items-center justify-between text-xs">
              <span class="font-semibold">{{ item.label }}</span>
              <span class="text-muted-foreground">{{ item.count }} ({{ item.pct }}%)</span>
            </div>
            <div class="h-2 rounded-full bg-muted">
              <div class="h-2 rounded-full" :class="item.color" :style="{ width: `${item.pct}%` }"></div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card class="border-0 shadow-none bg-transparent">
        <CardHeader><CardTitle class="text-base">Alerts & Actions</CardTitle></CardHeader>
        <CardContent class="space-y-3">
          <div class="space-y-2">
            <div v-if="error" class="rounded-md border border-red-300/40 bg-red-50 px-3 py-2 text-xs text-red-700">
              {{ error }}
            </div>
            <div v-for="a in alerts" :key="a" class="rounded-md border border-amber-300/40 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              {{ a }}
            </div>
            <div v-if="!error && alerts.length === 0" class="rounded-md border border-emerald-300/40 bg-emerald-50 px-3 py-2 text-xs text-emerald-800">
              Session is healthy. No active alerts.
            </div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <RouterLink to="/p2p/upload"><Button variant="outline" size="sm" class="w-full gap-1"><PlayCircle class="h-3.5 w-3.5" /> Upload</Button></RouterLink>
            <RouterLink to="/p2p/reconciliation"><Button variant="outline" size="sm" class="w-full gap-1"><GitMerge class="h-3.5 w-3.5" /> Reconcile</Button></RouterLink>
            <RouterLink to="/p2p/disputes"><Button variant="outline" size="sm" class="w-full gap-1"><AlertTriangle class="h-3.5 w-3.5" /> Disputes</Button></RouterLink>
            <RouterLink to="/p2p/reports"><Button variant="outline" size="sm" class="w-full gap-1"><TrendingUp class="h-3.5 w-3.5" /> Reports</Button></RouterLink>
          </div>
        </CardContent>
      </Card>
    </div>

    <div class="grid grid-cols-1 gap-3 xl:gap-4">
      <Card class="border-0 shadow-none bg-transparent">
        <CardHeader><CardTitle class="text-base">Risk & Data Quality</CardTitle></CardHeader>
        <CardContent class="space-y-4">
          <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
            <div class="rounded-lg border border-border p-3">
              <p class="text-[11px] text-muted-foreground">Unresolved Ratio</p>
              <p class="text-lg font-black text-amber-700">{{ summary.unresolvedRate.toFixed(1) }}%</p>
            </div>
            <div class="rounded-lg border border-border p-3">
              <p class="text-[11px] text-muted-foreground">Missing STAN / REF</p>
              <p class="text-lg font-black">{{ dataQuality.missingStan }} / {{ dataQuality.missingRef }}</p>
            </div>
            <div class="rounded-lg border border-border p-3">
              <p class="text-[11px] text-muted-foreground">Missing Date / Terminal</p>
              <p class="text-lg font-black">{{ dataQuality.missingDate }} / {{ dataQuality.missingTerminal }}</p>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Top Discrepancies</p>
            <div v-if="topDiscrepancies.length === 0" class="text-xs text-muted-foreground">No discrepancy categories detected.</div>
            <div v-for="d in topDiscrepancies" :key="d.type" class="mb-2 flex items-center justify-between rounded-md border border-border px-3 py-2 text-xs">
              <span class="truncate pr-3">{{ d.type }}</span>
              <Badge variant="outline">{{ d.count }}</Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
