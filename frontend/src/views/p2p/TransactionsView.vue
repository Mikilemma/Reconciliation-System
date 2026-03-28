<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from '@/components/ui/select';
import { 
  Search, 
  ChevronUp, 
  ChevronDown, 
  Eye, 
  Download, 
  Filter,
  RefreshCw 
} from 'lucide-vue-next';
import TransactionDetailsModal from '@/components/p2p/TransactionDetailsModal.vue';
import { useReconciliationData } from '@/composables/useReconciliationData';

interface ReconciliationResult {
  id: string;
  sessionId: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  status: 'settled' | 'discrepant' | 'missing' | 'duplicate' | 'pending' | 'reversal' | 'Transfer In' | 'Transfer Out';
  details?: string;
  discrepancyType?: string;
  sourceFiles?: string;
  switchData?: string;
  atmData?: string;
  payableData?: string;
  receivableData?: string;
}

const {
  isLoading,
  error,
  sessions,
  selectedSessionId: selectedSession,
  transactions,
  loadSessions,
  loadTransactions,
} = useReconciliationData()

const searchTerm = ref<string>('');
const statusFilter = ref<string>('all');
const typeFilter = ref<string>('all');
const sortField = ref<'stan' | 'amount' | 'date' | 'status'>('date');
const sortDirection = ref<'asc' | 'desc'>('desc');
const currentPage = ref<number>(1);
const selectedTransactions = ref<Set<string>>(new Set());
const itemsPerPage = 25;
const showTransactionDetailsModal = ref<boolean>(false);
const selectedTransactionForDetails = ref<ReconciliationResult | null>(null);
const isExporting = ref<boolean>(false);

const parseJsonSafe = (jsonString?: string | object) => {
  if (!jsonString) return null;
  if (typeof jsonString === 'object') return jsonString;
  try {
    return JSON.parse(jsonString);
  } catch {
    return null;
  }
};

const normalizeType = (value?: string | null) =>
  (value || '').toString().trim().toLowerCase();

const getTransactionType = (t: ReconciliationResult) => {
  const sources = [t.switchData, t.atmData, t.payableData, t.receivableData, t.details];
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
  if (text.includes('transfer')) return 'Transfer';
  if (text.includes('reversal')) return 'Reversal';
  return text ? text.toUpperCase() : 'Other';
};

const transactionTypes = computed(() => {
  const types = new Set<string>();
  transactions.value.forEach(t => {
    types.add(getTransactionType(t));
  });
  return Array.from(types).sort();
});

// Computed properties
const filteredAndSortedTransactions = computed(() => {
  let filtered = transactions.value.filter(t => {
    const matchesSearch = 
      (t.stan?.toLowerCase().includes(searchTerm.value.toLowerCase())) ||
      (t.transactionRef?.toLowerCase().includes(searchTerm.value.toLowerCase())) ||
      (t.terminalId?.toLowerCase().includes(searchTerm.value.toLowerCase()));
    
    const matchesStatus = statusFilter.value === 'all' || t.status === statusFilter.value;
    const matchesType = typeFilter.value === 'all' || getTransactionType(t) === typeFilter.value;
    
    return matchesSearch && matchesStatus && matchesType;
  });

  // Sort
  filtered.sort((a, b) => {
    let comparison = 0;
    
    switch (sortField.value) {
      case 'stan':
        comparison = (a.stan || '').localeCompare(b.stan || '');
        break;
      case 'amount':
        comparison = (a.amount || 0) - (b.amount || 0);
        break;
      case 'date':
        comparison = (a.transactionDate || '').localeCompare(b.transactionDate || '');
        break;
      case 'status':
        comparison = a.status.localeCompare(b.status);
        break;
    }
    
    return sortDirection.value === 'asc' ? comparison : -comparison;
  });

  return filtered;
});

const totalPages = computed(() => 
  Math.ceil(filteredAndSortedTransactions.value.length / itemsPerPage)
);

const paginatedTransactions = computed(() => 
  filteredAndSortedTransactions.value.slice(
    (currentPage.value - 1) * itemsPerPage,
    currentPage.value * itemsPerPage
  )
);

const statusCounts = computed(() => {
  const counts = {
    total: transactions.value.length,
    settled: 0,
    discrepant: 0,
    missing: 0,
    duplicate: 0,
    pending: 0,
    'Transfer In': 0,
    'Transfer Out': 0
  };
  
  transactions.value.forEach(t => {
    if (t.status in counts) {
      counts[t.status as keyof typeof counts]++;
    }
  });
  
  return counts;
});

onMounted(() => {
  loadSessions();
});

onMounted(() => {
  loadSessions();
});

const handleSort = (field: typeof sortField.value) => {
  if (sortField.value === field) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortField.value = field;
    sortDirection.value = 'asc';
  }
};

const getStatusLabel = (status: string) => {
  return status.charAt(0).toUpperCase() + status.slice(1);
};

const getStatusClass = (status: string) => {
  const baseClass = 'px-2 py-1 rounded-md text-xs font-black border uppercase tracking-widest';
  const variants: Record<string, string> = {
    settled: `${baseClass} text-success-foreground bg-success border-success`,
    discrepant: `${baseClass} text-destructive-foreground bg-destructive border-destructive`,
    missing: `${baseClass} text-muted-foreground bg-muted border-muted`,
    duplicate: `${baseClass} text-muted-foreground bg-muted border-muted`,
    pending: `${baseClass} text-warning-foreground bg-warning border-warning`,
    'Transfer In': `${baseClass} text-blue-100 bg-blue-600 border-blue-700`,
    'Transfer Out': `${baseClass} text-indigo-100 bg-indigo-600 border-indigo-700`
  };
  return variants[status] || `${baseClass} text-muted-foreground bg-muted border-muted`;
};

const handleSelectAll = () => {
  if (selectedTransactions.value.size === paginatedTransactions.value.length) {
    selectedTransactions.value = new Set();
  } else {
    selectedTransactions.value = new Set(paginatedTransactions.value.map(t => t.id));
  }
};

const handleSelectTransaction = (id: string) => {
  const newSelected = new Set(selectedTransactions.value);
  if (newSelected.has(id)) {
    newSelected.delete(id);
  } else {
    newSelected.add(id);
  }
  selectedTransactions.value = newSelected;
};

const csvEscape = (value: unknown) => {
  if (value === null || value === undefined) return '';
  const text = String(value);
  if (text.includes('"') || text.includes(',') || text.includes('\n')) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

const convertToCSV = (data: ReconciliationResult[]) => {
  const headers = ['STAN', 'Transaction Ref', 'Amount', 'Date', 'Terminal ID', 'Status', 'Details', 'Source Files'];
  const rows = data.map(t => [
    t.stan || '',
    t.transactionRef || '',
    t.amount?.toString() || '',
    t.transactionDate || '',
    t.terminalId || '',
    t.status,
    t.details || '',
    t.sourceFiles || ''
  ]);
  
  return [headers.map(csvEscape).join(','), ...rows.map(row => row.map(csvEscape).join(','))].join('\n');
};

const downloadCSV = (csv: string, filename: string) => {
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
};

const exportTransactions = (filtered: boolean) => {
  isExporting.value = true;
  try {
    const data = filtered
      ? filteredAndSortedTransactions.value
      : transactions.value;

    if (!data.length) return;

    const csv = convertToCSV(data);
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = filtered
      ? `reconciliation-filtered-${timestamp}.csv`
      : `reconciliation-all-${timestamp}.csv`;
    downloadCSV(csv, filename);
  } finally {
    isExporting.value = false;
  }
};

const viewDetails = (transaction: ReconciliationResult) => {
  selectedTransactionForDetails.value = transaction;
  showTransactionDetailsModal.value = true;
};

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount);
};

onMounted(() => {
  loadSessions();
});
</script>

<template>
  <div class="container mx-auto p-6 max-w-7xl">
    <!-- Header -->
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-foreground mb-2">Transaction Reconciliation</h1>
      <p class="text-foreground">View and manage transaction reconciliation results</p>
    </div>

    <!-- Session Selection and Summary -->
    <div class="grid grid-cols-1 lg:grid-cols-4 gap-6 mb-6">
      <!-- Session Selector -->
      <Card class="lg:col-span-1">
        <CardHeader>
          <CardTitle class="text-lg">Reconciliation Session</CardTitle>
        </CardHeader>
        <CardContent>
          <Select v-model="selectedSession" @update:modelValue="loadTransactions">
            <SelectTrigger>
              <SelectValue placeholder="Select session" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem 
                v-for="session in sessions" 
                :key="session.id" 
                :value="session.id"
              >
                {{ session.settlementDate }} - {{ new Date(session.processedAt).toLocaleDateString() }}
              </SelectItem>
            </SelectContent>
          </Select>
          <Button 
            @click="loadTransactions" 
            :disabled="isLoading || !selectedSession"
            variant="outline" 
            size="sm" 
            class="w-full mt-2"
          >
            <RefreshCw v-if="!isLoading" class="h-4 w-4 mr-2" />
            <div v-else class="animate-spin h-4 w-4 mr-2 border-2 border-current border-t-transparent rounded-full" />
            Refresh
          </Button>
        </CardContent>
      </Card>

      <!-- Summary Cards -->
      <Card class="lg:col-span-3">
        <CardHeader>
          <CardTitle class="text-lg">Transaction Summary</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div class="text-center p-3 rounded-lg bg-muted/50">
              <p class="text-xl font-bold text-foreground">{{ statusCounts.total }}</p>
              <p class="text-xs text-foreground">Total</p>
            </div>
            <div class="text-center p-3 rounded-lg bg-green-50 border border-green-200">
              <p class="text-xl font-bold text-green-700">{{ statusCounts.settled }}</p>
              <p class="text-xs text-green-600">Settled</p>
            </div>
            <div class="text-center p-3 rounded-lg bg-red-50 border border-red-200">
              <p class="text-xl font-bold text-red-700">{{ statusCounts.discrepant }}</p>
              <p class="text-xs text-red-600">Discrepant</p>
            </div>
            <div class="text-center p-3 rounded-lg bg-gray-50 border border-gray-200">
              <p class="text-xl font-bold text-gray-700">{{ statusCounts.missing }}</p>
              <p class="text-xs text-gray-600">Missing</p>
            </div>
            <div class="text-center p-3 rounded-lg bg-primary/10 border border-primary/20">
              <p class="text-xl font-bold text-primary">{{ statusCounts.duplicate }}</p>
              <p class="text-xs text-primary/80">Duplicate</p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Error Message -->
    <div v-if="error" class="mb-6 p-4 bg-destructive/10 border border-destructive/20 rounded-lg">
      <p class="text-destructive">{{ error }}</p>
    </div>

    <!-- Filters and Search -->
    <Card class="mb-6">
      <CardContent class="p-4">
        <div class="flex flex-col lg:flex-row gap-4 items-center">
          <div class="flex-1">
            <div class="relative">
              <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Search by STAN, reference, or terminal ID..."
                v-model="searchTerm"
                class="pl-10"
              />
            </div>
          </div>
          <div class="flex gap-2 flex-wrap items-center">
            <Select v-model="statusFilter">
              <SelectTrigger class="w-40">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Statuses</SelectItem>
                <SelectItem value="settled">Settled</SelectItem>
                <SelectItem value="discrepant">Discrepant</SelectItem>
                <SelectItem value="missing">Missing</SelectItem>
                <SelectItem value="duplicate">Duplicate</SelectItem>
                <SelectItem value="Transfer In">Transfer In</SelectItem>
                <SelectItem value="Transfer Out">Transfer Out</SelectItem>
                <SelectItem value="pending">Pending</SelectItem>
              </SelectContent>
            </Select>
            <Select v-model="typeFilter">
              <SelectTrigger class="w-48">
                <SelectValue placeholder="Transaction Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Types</SelectItem>
                <SelectItem v-for="t in transactionTypes" :key="t" :value="t">
                  {{ t }}
                </SelectItem>
              </SelectContent>
            </Select>
            <Button
              @click="exportTransactions(true)"
              variant="outline"
              size="icon"
              class="h-9 w-9 rounded-full"
              title="Export filtered reconciliation transactions"
              :disabled="isExporting || isLoading"
            >
              <Filter class="w-4 h-4" />
            </Button>
            <Button
              @click="exportTransactions(false)"
              variant="outline"
              size="icon"
              class="h-9 w-9 rounded-full"
              title="Export all reconciliation transactions"
              :disabled="isExporting || isLoading"
            >
              <Download class="w-4 h-4" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Transaction Table -->
    <Card>
      <CardContent class="p-0">
        <div class="overflow-x-auto">
          <table class="w-full border border-border rounded-lg">
            <thead class="bg-primary text-primary-foreground">
              <tr>
                <th class="w-12 p-4">
                  <input
                    type="checkbox"
                    :checked="selectedTransactions.size === paginatedTransactions.length && paginatedTransactions.length > 0"
                    @change="handleSelectAll"
                    class="cursor-pointer accent-primary-foreground"
                  />
                </th>
                <th 
                  class="p-4 text-left cursor-pointer hover:opacity-90 font-semibold"
                  @click="handleSort('stan')"
                >
                  STAN 
                  <ChevronUp v-if="sortField === 'stan' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
                  <ChevronDown v-if="sortField === 'stan' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
                </th>
                <th class="p-4 text-left">Transaction Ref</th>
                <th 
                  class="p-4 text-left cursor-pointer hover:opacity-90 font-semibold"
                  @click="handleSort('date')"
                >
                  Date/Time
                  <ChevronUp v-if="sortField === 'date' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
                  <ChevronDown v-if="sortField === 'date' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
                </th>
                <th 
                  class="p-4 text-right cursor-pointer hover:opacity-90 font-semibold"
                  @click="handleSort('amount')"
                >
                  Amount
                  <ChevronUp v-if="sortField === 'amount' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
                  <ChevronDown v-if="sortField === 'amount' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
                </th>
                <th class="p-4 text-left">Terminal ID</th>
                <th 
                  class="p-4 text-left cursor-pointer hover:opacity-90 font-semibold"
                  @click="handleSort('status')"
                >
                  Status
                  <ChevronUp v-if="sortField === 'status' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
                  <ChevronDown v-if="sortField === 'status' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
                </th>
                <th class="p-4 text-left">Details</th>
                <th class="p-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="isLoading">
                <td colspan="9" class="text-center py-12">
                  <div class="animate-spin h-6 w-6 border-2 border-primary border-t-transparent rounded-full mx-auto"></div>
                  <p class="text-foreground mt-2">Loading transactions...</p>
                </td>
              </tr>
              <tr v-else-if="paginatedTransactions.length === 0">
                <td colspan="9" class="text-center py-12 text-foreground">
                  No transactions found
                </td>
              </tr>
              <tr 
                v-else 
                v-for="(transaction, index) in paginatedTransactions" 
                :key="transaction.id"
                class="border-b transition-colors hover:bg-muted/50"
                :class="index % 2 === 0 ? 'bg-card' : 'bg-muted/20'"
              >
                <td class="p-4">
                  <input
                    type="checkbox"
                    :checked="selectedTransactions.has(transaction.id)"
                    @change="handleSelectTransaction(transaction.id)"
                    class="cursor-pointer"
                  />
                </td>
                <td class="p-4 font-mono text-sm">
                  {{ transaction.stan || '-' }}
                </td>
                <td class="p-4 font-mono text-sm">
                  {{ transaction.transactionRef || '-' }}
                </td>
                <td class="p-4 text-sm">
                  {{ transaction.transactionDate ? new Date(transaction.transactionDate).toLocaleString() : '-' }}
                </td>
                <td class="p-4 text-right font-mono font-medium">
                  {{ transaction.amount ? formatCurrency(transaction.amount) : '-' }}
                </td>
                <td class="p-4 font-mono text-sm">
                  {{ transaction.terminalId || '-' }}
                </td>
                <td class="p-4">
                  <Badge :class="getStatusClass(transaction.status)">
                    {{ getStatusLabel(transaction.status) }}
                  </Badge>
                </td>
                <td class="p-4 text-sm max-w-xs truncate">
                  {{ transaction.details || '-' }}
                </td>
                <td class="p-4 text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    @click="viewDetails(transaction)"
                  >
                    <Eye class="w-4 h-4 mr-1" />
                    Details
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="flex items-center justify-between p-4 border-t">
          <p class="text-sm text-foreground">
            Showing {{ ((currentPage - 1) * itemsPerPage) + 1 }} to {{ Math.min(currentPage * itemsPerPage, filteredAndSortedTransactions.length) }} of {{ filteredAndSortedTransactions.length }} transactions
          </p>
          <div class="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              @click="currentPage = Math.max(1, currentPage - 1)"
              :disabled="currentPage === 1"
            >
              Previous
            </Button>
            <span class="text-sm text-foreground">
              Page {{ currentPage }} of {{ totalPages }}
            </span>
            <Button
              variant="outline"
              size="sm"
              @click="currentPage = Math.min(totalPages, currentPage + 1)"
              :disabled="currentPage === totalPages"
            >
              Next
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Transaction Details Modal -->
    <TransactionDetailsModal
      :show="showTransactionDetailsModal"
      :transaction="selectedTransactionForDetails"
      @close="showTransactionDetailsModal = false"
    />
  </div>
</template>
