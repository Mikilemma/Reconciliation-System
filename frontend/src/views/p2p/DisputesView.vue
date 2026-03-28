<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { apiService } from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
// Using native HTML select for reliable dropdown functionality
import Skeleton from '@/components/ui/skeleton.vue';
import TransactionDetailsModal from '@/components/p2p/TransactionDetailsModal.vue';
import StatCard from '@/components/common/StatCard.vue';
import {
  Search,
  RefreshCw,
  Download,
  FileText,
  Eye,
  CheckCircle,
  AlertTriangle,
  AlertCircle,
  Clock,
  LayoutDashboard,
  Check,
  X
} from 'lucide-vue-next';

// Aggregated dispute structure from backend
interface Dispute {
  id: string;
  sessionId?: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  disputeStatus: string;
  disputeReason?: string;
  originalStatus?: string;
  discrepancyType?: string;
  details?: string;
  resolutionNotes?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt?: string;
  sourceFiles?: string[]; // Now an array from aggregation
  switchData?: string;
  atmData?: string;
  payableData?: string;
  receivableData?: string;
  underlyingDisputeIds?: string[]; // For bulk resolution
  recordCount?: number;
}

interface AuditLog {
  id: string;
  username: string;
  action: string;
  oldStatus?: string;
  newStatus?: string;
  disputeId: string;
  notes?: string;
  createdAt: string;
}

interface DisputePageResponse {
  items: Dispute[];
  totalItems: number;
  totalPages: number;
  page: number;
  size: number;
  totalConflicts: number;
  pendingAction: number;
  pending: number;
  inInvestigation: number;
  resolutionSet: number;
  availableTransactionTypes: string[];
  availableBanks: string[];
  availableDiscrepancyTypes: string[];
}

const disputes = ref<Dispute[]>([]);
const isLoading = ref<boolean>(true);
const error = ref<string>('');
const searchTerm = ref<string>('');
const statusFilter = ref<string>('all');
const discrepancyFilter = ref<string>('all');
const transactionTypeFilter = ref<string>('all');
const sessionDateFilter = ref<string>('');
const selectedSessionId = ref<string>('all');
const bankFilter = ref<string>('all');
const onUsFilter = ref<'all' | 'on-us' | 'off-us'>('all');
const sessions = ref<Array<{id: string; settlementDate: string; processedAt: string}>>([]);
const currentPage = ref<number>(1);
const itemsPerPage = 10;
const totalItems = ref<number>(0);
const totalPagesServer = ref<number>(1);
const statusTotals = ref({ total: 0, open: 0, pending: 0, inProgress: 0, resolved: 0 });
const availableTransactionTypes = ref<string[]>([]);
const availableBanks = ref<string[]>([]);
const availableDiscrepancyTypes = ref<string[]>([]);
const showDetailsModal = ref<boolean>(false);
const selectedDispute = ref<Dispute | null>(null);
const showResolveDialog = ref<boolean>(false);
const resolveForm = ref({
  status: 'resolved_manually',
  notes: '',
});
const currentUser = ref<string>('');
const auditLogs = ref<AuditLog[]>([]);
const isAuditLoading = ref<boolean>(false);
const isExporting = ref<boolean>(false);
const today = new Date().toISOString().split('T')[0];
const jsonParseCache = new Map<string, any>();

const filteredDisputes = computed(() => disputes.value);
const paginatedDisputes = computed(() => disputes.value);
const totalPages = computed(() => totalPagesServer.value);

const filteredSessionsByDate = computed(() => {
  if (!sessionDateFilter.value) return [];
  return sessions.value.filter((s) =>
    (s.settlementDate || '').toString().startsWith(sessionDateFilter.value)
  );
});

const stats = computed(() => {
  const total = statusTotals.value.total;
  const open = statusTotals.value.open;
  const pending = statusTotals.value.pending;
  const inProgress = statusTotals.value.inProgress;
  const resolved = statusTotals.value.resolved;

  return [
    { label: 'Total Conflicts', value: total, icon: AlertTriangle, variant: 'blue' as const },
    { label: 'Pending Action', value: open, icon: AlertCircle, variant: 'orange' as const },
    { label: 'Pending', value: pending, icon: Clock, variant: 'blue' as const },
    { label: 'In Investigation', value: inProgress, icon: Clock, variant: 'cherry' as const },
    { label: 'Resolution Set', value: resolved, icon: CheckCircle, variant: 'green' as const }
  ];
});

const normalizeName = (value?: string | null) =>
  (value || '').toString().trim().toLowerCase();
const normalizeType = (value?: string | null) =>
  (value || '').toString().trim().toLowerCase();

const getSwitchParties = (d: Dispute) => {
  const data = parseJsonSafe(d.switchData);
  const issuer = normalizeName(data?.issuer || data?.Issuer);
  const acquirer = normalizeName(data?.acquirer || data?.Acquirer);
  return { issuer, acquirer };
};

const getTransactionType = (d: Dispute) => {
  const sources = [d.switchData, d.atmData, d.payableData, d.receivableData, d.details];
  let raw = '';

  for (const src of sources) {
    const data = parseJsonSafe(src);
    if (data) {
      raw =
        data.transactionDescription ||
        data.description ||
        data.TransactionType ||
        data.txnType ||
        data.type ||
        raw;
    }
    if (raw) break;
  }

  const text = normalizeType(raw);
  if (text.includes('balance')) return 'Balance Inquiry';
  if (text.includes('pos') || text.includes('purchase')) return 'POS';
  if (text.includes('atm') || text.includes('withdrawal') || text.includes('cash')) return 'ATM';
  if (text.includes('transfer') || text.includes('account2account')) return 'Transfer';
  if (text.includes('reversal')) return 'Reversal';
  return text ? text.toUpperCase() : 'Other';
};

const transactionTypes = computed(() => availableTransactionTypes.value);

const matchesTransactionType = (d: Dispute, filterType: string) =>
  getTransactionType(d) === filterType;

const banks = computed(() => availableBanks.value);

const discrepancyTypes = computed(() => availableDiscrepancyTypes.value);

const getStatusBadge = (status: string) => {
  switch (status.toLowerCase()) {
    case 'open':
      return { label: 'PENDING ACTION', class: 'bg-destructive/10 text-destructive border-destructive/20' };
    case 'in_progress':
      return { label: 'INVESTIGATING', class: 'bg-warning/10 text-warning border-warning/20' };
    case 'resolved_manually':
      return { label: 'RESOLVED MANUALLY', class: 'bg-success/10 text-success border-success/20' };
    case 'resolved':
    case 'closed':
      return { label: 'SUCCESSFULLY RESOLVED', class: 'bg-success/10 text-success border-success/20' };
    default:
      return { label: 'ARCHIVED', class: 'bg-muted text-muted-foreground' };
  }
};

const isResolvedStatus = (status?: string) => {
  const s = (status || '').toLowerCase();
  return s === 'resolved' || s === 'resolved_manually' || s === 'closed';
};

const matchesStatusFilter = (status: string, filter: string) => {
  if (filter === 'resolved' || filter === 'resolved_manually') {
    return isResolvedStatus(status);
  }
  return status === filter;
};

const getResolvedBy = (dispute: Dispute) => {
  return isResolvedStatus(dispute.disputeStatus) ? (dispute.resolvedBy || 'unknown') : '-';
};

const formatCurrency = (amount?: number) => {
  if (!amount) return 'N/A';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
  }).format(amount);
};

const formatDate = (date?: string) => {
  if (!date) return 'N/A';
  return new Date(date).toLocaleString([], {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const loadDisputes = async () => {
  isLoading.value = true;
  error.value = '';
  try {
    const activeSessionId = selectedSessionId.value !== 'all'
      ? selectedSessionId.value
      : undefined;
    const response = await apiService.get<DisputePageResponse>('/api/p2p/disputes/aggregated/page', {
      search: searchTerm.value || undefined,
      status: statusFilter.value !== 'all' ? statusFilter.value : undefined,
      discrepancyType: discrepancyFilter.value !== 'all' ? discrepancyFilter.value : undefined,
      transactionType: transactionTypeFilter.value !== 'all' ? transactionTypeFilter.value : undefined,
      bank: bankFilter.value !== 'all' ? bankFilter.value : undefined,
      onUs: onUsFilter.value !== 'all' ? onUsFilter.value : undefined,
      sessionId: activeSessionId,
      sessionDate: sessionDateFilter.value || undefined,
      page: Math.max(0, currentPage.value - 1),
      size: itemsPerPage
    });
    disputes.value = response.items || [];
    totalItems.value = response.totalItems || 0;
    totalPagesServer.value = response.totalPages || 1;
    statusTotals.value = {
      total: response.totalConflicts || 0,
      open: response.pendingAction || 0,
      pending: response.pending || 0,
      inProgress: response.inInvestigation || 0,
      resolved: response.resolutionSet || 0
    };
    availableTransactionTypes.value = response.availableTransactionTypes || [];
    availableBanks.value = response.availableBanks || [];
    availableDiscrepancyTypes.value = response.availableDiscrepancyTypes || [];
    jsonParseCache.clear();
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to load disputes';
    console.error('Error loading disputes:', err);
  } finally {
    isLoading.value = false;
  }
};

const loadSessions = async () => {
  try {
    const response = await apiService.get<Array<{id: string; settlementDate: string; processedAt: string}>>('/api/p2p/reconciliation/sessions');
    sessions.value = response.sort((a, b) => new Date(b.processedAt).getTime() - new Date(a.processedAt).getTime());
  } catch (err) {
    sessions.value = [];
  }
};

const loadCurrentUser = async () => {
  try {
    const response = await apiService.get<{ username?: string; role?: string }>('/api/auth/me');
    currentUser.value = response?.username || '';
  } catch (err) {
    currentUser.value = '';
  }
};

const refreshDisputes = () => {
  loadDisputes();
};

const csvEscape = (value: unknown) => {
  if (value === null || value === undefined) return '';
  const text = String(value);
  if (text.includes('"') || text.includes(',') || text.includes('\n')) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

const buildDisputesCsv = (rows: Dispute[]) => {
  const headers = [
    'Dispute ID',
    'Session ID',
    'STAN',
    'Transaction Ref',
    'Amount',
    'Transaction Date',
    'Status',
    'Discrepancy Type',
    'Resolved By',
    'Resolved At',
    'Source Files',
    'Details'
  ];

  const lines = rows.map((d) => [
    d.id,
    d.sessionId || '',
    getEffectiveStan(d),
    getEffectiveRef(d),
    getEffectiveAmount(d),
    getEffectiveDate(d) || '',
    d.disputeStatus || '',
    d.discrepancyType || '',
    getResolvedBy(d),
    d.resolvedAt || '',
    getSourceFiles(d),
    d.details || ''
  ].map(csvEscape).join(','));

  return [headers.map(csvEscape).join(','), ...lines].join('\n');
};

const downloadCsv = (content: string, filename: string) => {
  const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.style.display = 'none';
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

const fetchDisputesForExport = async (filtered: boolean): Promise<Dispute[]> => {
  const activeSessionId = filtered && selectedSessionId.value !== 'all'
    ? selectedSessionId.value
    : undefined;

  const pageSize = 500;
  let page = 0;
  let totalPages = 1;
  const allRows: Dispute[] = [];

  while (page < totalPages) {
    const response = await apiService.get<DisputePageResponse>('/api/p2p/disputes/aggregated/page', {
      search: filtered ? (searchTerm.value || undefined) : undefined,
      status: filtered && statusFilter.value !== 'all' ? statusFilter.value : undefined,
      discrepancyType: filtered && discrepancyFilter.value !== 'all' ? discrepancyFilter.value : undefined,
      transactionType: filtered && transactionTypeFilter.value !== 'all' ? transactionTypeFilter.value : undefined,
      bank: filtered && bankFilter.value !== 'all' ? bankFilter.value : undefined,
      onUs: filtered && onUsFilter.value !== 'all' ? onUsFilter.value : undefined,
      sessionId: activeSessionId,
      sessionDate: filtered ? (sessionDateFilter.value || undefined) : undefined,
      page,
      size: pageSize
    });

    allRows.push(...(response.items || []));
    totalPages = Math.max(1, response.totalPages || 1);
    page += 1;
  }

  return allRows;
};

const exportDisputes = async (filtered: boolean) => {
  isExporting.value = true;
  error.value = '';
  try {
    let rows = await fetchDisputesForExport(filtered);

    // Fallback to currently loaded page if backend pagination limits prevent deep export.
    if (!rows.length && filtered) {
      rows = disputes.value;
    }

    if (!rows.length) {
      error.value = filtered
        ? 'No filtered transactions available for export.'
        : 'No transactions available for export.';
      return;
    }

    const csv = buildDisputesCsv(rows);
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = filtered
      ? `disputes-filtered-${timestamp}.csv`
      : `disputes-all-${timestamp}.csv`;
    downloadCsv(csv, filename);
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to export disputes';
    console.error('Error exporting disputes:', err);
  } finally {
    isExporting.value = false;
  }
};

const viewDisputeDetails = (dispute: Dispute) => {
  selectedDispute.value = dispute;
  showDetailsModal.value = true;
  loadAuditLogs(dispute);
};

const openResolveDialog = (dispute: Dispute) => {
  selectedDispute.value = dispute;
  resolveForm.value = {
    status: 'resolved_manually',
    notes: '',
  };
  showResolveDialog.value = true;
};

const resolveDispute = async () => {
  if (!selectedDispute.value) return;

  try {
    // If this is an aggregated dispute with multiple underlying records, resolve all
    const disputeIds = selectedDispute.value.underlyingDisputeIds?.length 
      ? selectedDispute.value.underlyingDisputeIds 
      : [selectedDispute.value.id];
    
    await apiService.put('/api/p2p/disputes/aggregated/status', {
      disputeIds,
      newStatus: resolveForm.value.status,
      resolutionNotes: resolveForm.value.notes,
    });
    showResolveDialog.value = false;
    await loadDisputes();
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to resolve dispute';
    console.error('Error resolving dispute:', err);
  }
};

const loadAuditLogs = async (dispute: Dispute) => {
  isAuditLoading.value = true;
  auditLogs.value = [];
  try {
    const disputeIds = dispute.underlyingDisputeIds?.length
      ? dispute.underlyingDisputeIds
      : [dispute.id];
    auditLogs.value = await apiService.post('/api/p2p/disputes/audit', { disputeIds });
  } catch (err) {
    auditLogs.value = [];
  } finally {
    isAuditLoading.value = false;
  }
};

// Start Data Extraction Helpers for Disputes
const parseJsonSafe = (jsonString?: string | object) => {
  if (!jsonString) return null;
  if (typeof jsonString === 'object') return jsonString;
  const cached = jsonParseCache.get(jsonString);
  if (cached !== undefined) return cached;
  try {
    const parsed = JSON.parse(jsonString);
    jsonParseCache.set(jsonString, parsed);
    return parsed;
  } catch (e) {
    jsonParseCache.set(jsonString, null);
    return null;
  }
};

const getEffectiveStan = (d: Dispute) => {
  if (d.stan) return d.stan;
  
  // Check transaction source data fields
  const sources = [d.switchData, d.atmData, d.payableData, d.receivableData];
  for (const src of sources) {
    const data = parseJsonSafe(src);
    if (data && (data.stan || data.STAN || data.Stan || data.StanNo || data['STAN.NO'])) {
      return data.stan || data.STAN || data.Stan || data.StanNo || data['STAN.NO'];
    }
  }
  
  // Fallback: Check if Details is a JSON blob
  const detailsObj = parseJsonSafe(d.details);
  if (detailsObj && (detailsObj.stan || detailsObj.STAN)) return detailsObj.stan || detailsObj.STAN;

  return 'N/A';
};

const getEffectiveRef = (d: Dispute) => {
  if (d.transactionRef) return d.transactionRef;
  
  const sources = [d.switchData, d.atmData, d.payableData, d.receivableData];
  for (const src of sources) {
    const data = parseJsonSafe(src);
    if (data && (data.rrn || data.RRN || data.ref || data.REF)) {
      return data.rrn || data.RRN || data.ref || data.REF;
    }
  }
  
  const detailsObj = parseJsonSafe(d.details);
  if (detailsObj && (detailsObj.rrn || detailsObj.RRN || detailsObj.ref)) return detailsObj.rrn || detailsObj.RRN || detailsObj.ref;
  return 'NO_REF';
};

const getEffectiveDate = (d: Dispute) => {
  if (d.transactionDate) return d.transactionDate;
  
  const sources = [d.switchData, d.atmData, d.payableData, d.receivableData];
  for (const src of sources) {
    const data = parseJsonSafe(src);
    if (data) {
      const dateVal = data.transactionDate || data.AuthorizationDate || data.txnDate || data.date || data.valueDate || data['VALUE.DATE'];
      if (dateVal) return dateVal;
    }
  }
  return null;
};

const getEffectiveAmount = (d: Dispute) => {
  if (d.amount !== undefined && d.amount !== null) return d.amount;
  
  const sources = [d.switchData, d.atmData, d.payableData, d.receivableData];
  for (const src of sources) {
    const data = parseJsonSafe(src);
    if (data) {
      const amt = data.amount || data.Amount || data.txnAmount || data['TXN.AMOUNT'];
      if (amt !== undefined) return Number(amt);
    }
  }
  return 0;
};

const getSourceFiles = (d: Dispute) => {
  if (d.sourceFiles) {
    return Array.isArray(d.sourceFiles) ? d.sourceFiles.join(', ') : d.sourceFiles;
  }
  
  // Determine source from which data fields are populated
  const sources: string[] = [];
  if (d.switchData) sources.push('Switch (ETHS)');
  if (d.atmData) sources.push('Third Party');
  if (d.payableData) sources.push('Payable');
  if (d.receivableData) sources.push('Receivable');
  
  return sources.length > 0 ? sources.join(', ') : 'Unknown';
};
// End Data Extraction Helpers

onMounted(() => {
  loadDisputes();
  loadSessions();
  loadCurrentUser();
});

watch(
  [searchTerm, statusFilter, discrepancyFilter, transactionTypeFilter, sessionDateFilter, selectedSessionId, bankFilter, onUsFilter],
  async () => {
    currentPage.value = 1;
    await loadDisputes();
  }
);

watch(sessionDateFilter, async () => {
  selectedSessionId.value = 'all';
  await loadDisputes();
});

watch(currentPage, async () => {
  await loadDisputes();
});
</script>

<template>
  <div class="container mx-auto p-6 max-w-[92rem] space-y-8">
    <!-- Header -->
    <div class="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 animate-fade-in">
      <div>
        <h1 class="text-3xl font-black text-foreground mb-2 flex items-center gap-3">
          <div class="p-2 rounded-xl bg-primary/10 text-primary">
            <LayoutDashboard class="w-8 h-8" />
          </div>
          P2P Dispute Management
        </h1>
        <p class="text-foreground/70 font-medium">Investigate discrepancies and manage transaction resolution workflows</p>
      </div>
      <Button @click="refreshDisputes" variant="outline" class="h-11 px-6 rounded-xl shadow-sm hover:shadow-md transition-all">
        <RefreshCw :class="{ 'animate-spin': isLoading }" class="h-4 w-4 mr-2" />
        Fetch Updates
      </Button>
    </div>

    <!-- Stats Dashboard -->
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-10">
      <StatCard
        v-for="(stat, idx) in stats"
        :key="idx"
        :title="stat.label"
        :value="stat.value"
        :variant="stat.variant"
      >
        <template #icon>
          <component :is="stat.icon" />
        </template>
      </StatCard>
    </div>

    <!-- Main Workspace -->
    <div class="h-8"></div>

    <Card class="banking-card-elevated overflow-hidden mt-2">
          <CardHeader class="border-b bg-muted/5 p-6 flex flex-row items-center justify-between gap-3">
             <div>
               <CardTitle class="text-xl">Conflict Registry</CardTitle>
               <CardDescription>Records identified during the reconciliation sync</CardDescription>
             </div>
             <div class="flex items-center gap-2">
               <Button
                 variant="outline"
                 size="icon"
                 class="h-9 w-9 rounded-full"
                 title="Export filtered disputes"
                 :disabled="isLoading || isExporting"
                 @click="exportDisputes(true)"
               >
                 <FileText class="h-4 w-4" />
               </Button>
               <Button
                 variant="outline"
                 size="icon"
                 class="h-9 w-9 rounded-full"
                 title="Export all disputes"
                 :disabled="isLoading || isExporting"
                 @click="exportDisputes(false)"
               >
                 <Download class="h-4 w-4" />
               </Button>
             </div>
          </CardHeader>
          <div class="px-6 py-4 border-b bg-background/80">
            <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
              <div class="space-y-2 md:col-span-2 xl:col-span-1">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Global Search</label>
                <div class="relative group">
                  <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-muted-foreground group-focus-within:text-primary transition-colors" />
                  <Input
                    v-model="searchTerm"
                    placeholder="STAN, Reference..."
                    class="pl-10 h-11 rounded-xl border-border bg-muted/20 focus:bg-background transition-all"
                  />
                </div>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Resolution Status</label>
                <select
                  v-model="statusFilter"
                  class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="all" class="text-foreground">All Conflicts</option>
                  <option value="open" class="text-foreground">Open - Action Required</option>
                  <option value="pending" class="text-foreground">Pending</option>
                  <option value="in_progress" class="text-foreground">In Investigation</option>
                  <option value="resolved" class="text-foreground">Resolved</option>
                  <option value="resolved_manually" class="text-foreground">Resolved Manually</option>
                  <option value="closed" class="text-foreground">Closed / Archived</option>
                </select>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Discrepancy Type</label>
                <select
                  v-model="discrepancyFilter"
                  class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="all" class="text-foreground">All Types</option>
                  <option v-for="type in discrepancyTypes" :key="type" :value="type" class="text-foreground">
                    {{ type }}
                  </option>
                </select>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Transaction Type</label>
                <select
                  v-model="transactionTypeFilter"
                  class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="all" class="text-foreground">All Types</option>
                  <option v-for="type in transactionTypes" :key="type" :value="type" class="text-foreground">
                    {{ type }}
                  </option>
                </select>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Bank</label>
                <select
                  v-model="bankFilter"
                  class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="all" class="text-foreground">All Banks</option>
                  <option v-for="bank in banks" :key="bank" :value="bank" class="text-foreground">
                    {{ bank }}
                  </option>
                </select>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">On-us / Off-us</label>
                <select
                  v-model="onUsFilter"
                  class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="all" class="text-foreground">All</option>
                  <option value="on-us" class="text-foreground">On-us (Tsehay)</option>
                  <option value="off-us" class="text-foreground">Off-us</option>
                </select>
              </div>

              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground px-1">Session Date</label>
                <div class="flex items-center gap-2">
                  <input
                    v-model="sessionDateFilter"
                    type="date"
                    :max="today"
                    class="flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    class="h-11 px-3"
                    @click="() => { sessionDateFilter = ''; selectedSessionId = 'all'; }"
                  >
                    All
                  </Button>
                </div>
              </div>
            </div>

            <div v-if="sessionDateFilter && filteredSessionsByDate.length > 0" class="mt-3">
              <div class="flex gap-2 flex-wrap">
                <button
                  type="button"
                  @click.stop.prevent="selectedSessionId = 'all'"
                  class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150 cursor-pointer"
                  :class="selectedSessionId === 'all'
                    ? 'bg-warning text-warning-foreground border-warning'
                    : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
                >
                  All Sessions
                </button>
                <button
                  v-for="session in filteredSessionsByDate"
                  :key="session.id"
                  type="button"
                  @click.stop.prevent="selectedSessionId = String(session.id)"
                  class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150 cursor-pointer"
                  :class="selectedSessionId === String(session.id)
                    ? 'bg-warning text-warning-foreground border-warning'
                    : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
                >
                  {{ new Date(session.processedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                </button>
              </div>
            </div>
            <div v-else-if="sessionDateFilter" class="mt-3 text-xs text-muted-foreground">
              No sessions found for selected date.
            </div>
          </div>
          <CardContent class="p-0">
             <div v-if="isLoading" class="p-8 space-y-4">
                <Skeleton v-for="i in 5" :key="i" class="h-16 w-full rounded-xl" />
             </div>
             <div v-else-if="totalItems === 0" class="text-center py-24 bg-muted/5">
                <AlertCircle class="h-12 w-12 mx-auto text-muted-foreground/30 mb-4" />
                <h3 class="font-bold text-foreground/50">No conflicts found</h3>
                <p class="text-xs text-muted-foreground">Adjust your filters or sync new data</p>
             </div>
             <div v-else class="overflow-x-auto">
                <table class="w-full table-fixed table-striped">
                  <thead class="bg-muted/30 border-b">
                    <tr>
                      <th class="w-[17%] text-left px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Trace ID</th>
                      <th class="w-[12%] text-left px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Value</th>
                      <th class="w-[18%] text-left px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Processed At</th>
                      <th class="w-[17%] text-left px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Source</th>
                      <th class="w-[15%] text-center px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Workflow</th>
                      <th class="w-[11%] text-left px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Resolved By</th>
                      <th class="w-[10%] text-right px-3 py-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Control</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-border/50">
                    <tr
                      v-for="dispute in paginatedDisputes"
                      :key="dispute.id"
                      class="hover:bg-primary/[0.02] transition-colors cursor-pointer group"
                      @click="viewDisputeDetails(dispute)"
                    >
                      <td class="px-3 py-3">
                         <div class="flex items-center gap-2 mb-0.5">
                           <p class="text-xs font-black text-foreground">{{ getEffectiveStan(dispute) }}</p>
                           <Badge v-if="dispute.recordCount && dispute.recordCount > 1" variant="outline" class="text-[9px] py-0 px-1 bg-primary/20 text-primary border-primary/30 uppercase font-black">
                             Merged ({{ dispute.recordCount }})
                           </Badge>
                         </div>
                         <p class="text-[10px] text-muted-foreground font-mono truncate max-w-[120px]">{{ getEffectiveRef(dispute) }}</p>
                      </td>
                      <td class="px-3 py-3">
                        <p class="text-sm font-black text-foreground">{{ formatCurrency(getEffectiveAmount(dispute)) }}</p>
                      </td>
                      <td class="px-3 py-3">
                        <p class="text-xs font-medium text-foreground/70 truncate" :title="getEffectiveDate(dispute) ? formatDate(getEffectiveDate(dispute)) : 'N/A'">
                          {{ getEffectiveDate(dispute) ? formatDate(getEffectiveDate(dispute)) : 'N/A' }}
                        </p>
                      </td>
                      <td class="px-3 py-3">
                        <p class="text-xs font-medium text-foreground/70 truncate" :title="getSourceFiles(dispute)">{{ getSourceFiles(dispute) }}</p>
                      </td>
                      <td class="px-3 py-3 text-center">
                        <Badge :class="`px-2 py-0.5 rounded-md text-[9px] font-black border shadow-none truncate max-w-[140px] ${getStatusBadge(dispute.disputeStatus).class}`">
                          {{ getStatusBadge(dispute.disputeStatus).label }}
                        </Badge>
                      </td>
                      <td class="px-3 py-3">
                        <p class="text-xs font-semibold text-foreground/80 truncate" :title="getResolvedBy(dispute)">
                          {{ getResolvedBy(dispute) }}
                        </p>
                      </td>
                      <td class="px-3 py-3">
                        <div class="flex items-center justify-end gap-2 whitespace-nowrap">
                          <Button
                            @click.stop="viewDisputeDetails(dispute)"
                            variant="ghost"
                            size="icon"
                            class="h-8 w-8 text-muted-foreground hover:text-primary hover:bg-primary/10 rounded-lg"
                          >
                            <Eye class="h-4 w-4" />
                          </Button>
                          <Button
                            v-if="dispute.disputeStatus === 'open' || dispute.disputeStatus === 'in_progress'"
                            @click.stop="openResolveDialog(dispute)"
                            variant="ghost"
                            size="icon"
                            class="h-8 w-8 text-muted-foreground hover:text-success hover:bg-success/10 rounded-lg"
                          >
                            <Check class="h-4 w-4" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
             </div>
             
             <!-- Pagination Workspace -->
             <div v-if="totalItems > itemsPerPage" class="p-4 border-t bg-muted/5 flex items-center justify-between">
                <span class="text-[10px] font-black text-muted-foreground uppercase opacity-60">Listing {{ (currentPage - 1) * itemsPerPage + 1 }}—{{ Math.min(currentPage * itemsPerPage, totalItems) }} of {{ totalItems }}</span>
                <div class="flex gap-1">
                  <Button @click="currentPage--" :disabled="currentPage === 1" variant="ghost" size="sm" class="h-8 px-4 rounded-lg">Prev</Button>
                  <Button @click="currentPage++" :disabled="currentPage >= totalPages" variant="ghost" size="sm" class="h-8 px-4 rounded-lg">Next</Button>
                </div>
             </div>
          </CardContent>
       </Card>

    <!-- Modals -->
    <TransactionDetailsModal
      v-if="selectedDispute"
      :show="showDetailsModal"
      :transaction="selectedDispute"
      :audit-logs="auditLogs"
      :audit-loading="isAuditLoading"
      @close="showDetailsModal = false"
    />

    <!-- Resolution Dialogue Flow -->
    <div v-if="showResolveDialog" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/95" @click.self="showResolveDialog = false">
       <Card class="max-w-md w-full border-2 border-green-200 shadow-2xl overflow-hidden relative bg-green-50">
          <Button
            variant="ghost"
            size="icon"
            class="absolute right-3 top-3"
            @click="showResolveDialog = false"
          >
            <X class="h-4 w-4" />
          </Button>
          <CardHeader class="bg-primary text-primary-foreground p-6">
             <CardTitle class="text-xl font-bold flex items-center gap-2">
                <CheckCircle class="w-6 h-6" />
                Finalize Resolution
             </CardTitle>
             <CardDescription class="text-primary-foreground/70">Transaction {{ selectedDispute?.stan }}</CardDescription>
          </CardHeader>
          <CardContent class="p-6 space-y-4">
             <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-muted-foreground tracking-widest">Resolution Outcome</label>
                <select 
                  v-model="resolveForm.status"
                  class="flex h-12 w-full items-center justify-between rounded-xl border border-input bg-background px-3 py-2 text-sm text-foreground ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="open" class="text-foreground">Open - Action Required</option>
                  <option value="pending" class="text-foreground">Pending</option>
                  <option value="in_progress" class="text-foreground">In Investigation</option>
                  <option value="resolved" class="text-foreground">Resolved</option>
                  <option value="resolved_manually" class="text-foreground">Resolved Manually</option>
                  <option value="closed" class="text-foreground">Closed / Archived</option>
                </select>
             </div>
             <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-muted-foreground tracking-widest">Investigation Notes</label>
                <textarea
                  v-model="resolveForm.notes"
                  class="w-full p-4 border border-border rounded-xl min-h-[120px] focus:ring-2 focus:ring-primary/20 outline-none transition-all"
                  placeholder="Detail the root cause and matching logic used..."
                />
             </div>
             <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-muted-foreground tracking-widest">Resolved By</label>
                <Input :model-value="currentUser || 'Authenticated user'" readonly class="h-12 rounded-xl bg-muted/20" />
             </div>
          </CardContent>
          <CardFooter class="p-6 pt-0 flex gap-3">
             <Button @click="resolveDispute" class="flex-1 btn-primary h-12 shadow-lg shadow-primary/20">Apply Resolution</Button>
             <Button @click="showResolveDialog = false" variant="ghost" class="flex-1 h-12 rounded-xl">Discard</Button>
          </CardFooter>
       </Card>
    </div>
  </div>
</template>

<style scoped>
.animate-scale-up {
  animation: scale-up 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes scale-up {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
