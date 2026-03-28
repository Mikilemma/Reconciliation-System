<script setup lang="ts">
import { ref, computed } from 'vue';
import {
  Search,
  ChevronUp,
  ChevronDown,
  Eye,
  Download,
  FileText
} from 'lucide-vue-next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import Pagination from '@/components/common/Pagination.vue';

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
  switchData?: any;
  atmData?: any;
  payableData?: any;
  receivableData?: any;
  recordCount?: number;
}

const getEffectiveStan = (t: ReconciliationResult) => {
  if (t.stan) return t.stan;
  const sources = [t.switchData, t.atmData, t.payableData, t.receivableData];
  for (const src of sources) {
    const data = typeof src === 'string' ? parseJsonSafe(src) : src;
    if (data && (data.stan || data.STAN || data.Stan || data.StanNo || data['STAN.NO'])) return data.stan || data.STAN || data.Stan || data.StanNo || data['STAN.NO'];
  }
  return null;
};

const getEffectiveDate = (t: ReconciliationResult) => {
  if (t.transactionDate) return t.transactionDate;
  const sources = [t.switchData, t.atmData, t.payableData, t.receivableData];
  for (const src of sources) {
    const data = typeof src === 'string' ? parseJsonSafe(src) : src;
    if (data) {
       const dateVal = data.transactionDate || data.AuthorizationDate || data.txnDate || data.date || data.valueDate || data['VALUE.DATE'];
       if (dateVal) return dateVal;
    }
  }
  return null;
};

const getEffectiveAmount = (t: ReconciliationResult) => {
  if (t.amount !== undefined && t.amount !== null) return t.amount;
  const sources = [t.switchData, t.atmData, t.payableData, t.receivableData];
  for (const src of sources) {
    const data = typeof src === 'string' ? parseJsonSafe(src) : src;
    if (data) {
       const amt = data.amount || data.Amount || data.txnAmount || data['TXN.AMOUNT'];
       if (amt !== undefined) return Number(amt);
    }
  }
  return 0;
};

const props = defineProps<{
  transactions: ReconciliationResult[];
  loading: boolean;
  initialStatusFilter?: string;
}>();

const emit = defineEmits<{
  (e: 'view-details', transaction: ReconciliationResult): void;
}>();

// Table state
const searchTerm = ref<string>('');
const statusFilter = ref<string>(props.initialStatusFilter || 'all');
const bankFilter = ref<string>('all');
const onUsFilter = ref<'all' | 'on-us' | 'off-us'>('all');
const typeFilter = ref<string>('all');
const sortField = ref<'stan' | 'amount' | 'date' | 'status'>('date');
const sortDirection = ref<'asc' | 'desc'>('desc');
const currentPage = ref<number>(1);
const selectedTransactions = ref<Set<string>>(new Set());
const itemsPerPage = 10;
const isExporting = ref<boolean>(false);

const normalizeName = (value?: string | null) =>
  (value || '').toString().trim().toLowerCase();

const normalizeType = (value?: string | null) =>
  (value || '').toString().trim().toLowerCase();

const getSwitchParties = (t: ReconciliationResult) => {
  const src = t.switchData;
  const data = typeof src === 'string' ? parseJsonSafe(src) : src;
  const issuer = normalizeName(data?.issuer || data?.Issuer);
  const acquirer = normalizeName(data?.acquirer || data?.Acquirer);
  return { issuer, acquirer };
};

const banks = computed(() => {
  const names = new Set<string>();
  props.transactions.forEach(t => {
    const { issuer, acquirer } = getSwitchParties(t);
    if (issuer) names.add(issuer);
    if (acquirer) names.add(acquirer);
  });
  return Array.from(names)
    .sort()
    .map(n => n.replace(/\b\w/g, c => c.toUpperCase()));
});

const parseJsonSafeAny = (input?: any) => {
  if (!input) return null;
  if (typeof input === 'object') return input;
  try {
    return JSON.parse(input);
  } catch {
    return null;
  }
};

const getTransactionType = (t: ReconciliationResult) => {
  const sources = [t.switchData, t.atmData, t.payableData, t.receivableData, t.details];
  let raw = '';
  for (const src of sources) {
    const data = parseJsonSafeAny(src);
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
  props.transactions.forEach(t => {
    types.add(getTransactionType(t));
  });
  return Array.from(types).sort();
});

// Computed properties
const filteredAndSortedTransactions = computed(() => {
  let filtered = props.transactions.filter(t => {
    const { issuer, acquirer } = getSwitchParties(t);
    const matchesSearch = 
      (t.stan?.toLowerCase().includes(searchTerm.value.toLowerCase())) ||
      (t.transactionRef?.toLowerCase().includes(searchTerm.value.toLowerCase())) ||
      (t.terminalId?.toLowerCase().includes(searchTerm.value.toLowerCase()));
    
    const matchesStatus = statusFilter.value === 'all' || t.status === statusFilter.value;
    const matchesType = typeFilter.value === 'all' || getTransactionType(t) === typeFilter.value;

    const normalizedBankFilter = normalizeName(bankFilter.value);
    const matchesBank = normalizedBankFilter === 'all'
      || issuer === normalizedBankFilter
      || acquirer === normalizedBankFilter;

    const isOnUs = issuer === 'tsehay' || acquirer === 'tsehay';
    const hasParty = !!issuer || !!acquirer;
    const matchesOnUs =
      onUsFilter.value === 'all' ||
      (onUsFilter.value === 'on-us' && isOnUs) ||
      (onUsFilter.value === 'off-us' && hasParty && !isOnUs);
    
    return matchesSearch && matchesStatus && matchesType && matchesBank && matchesOnUs;
  });

  filtered.sort((a, b) => {
    let comparison = 0;
    switch (sortField.value) {
      case 'stan':
        comparison = (getEffectiveStan(a) || '').localeCompare(getEffectiveStan(b) || '');
        break;
      case 'amount':
        comparison = (getEffectiveAmount(a) || 0) - (getEffectiveAmount(b) || 0);
        break;
      case 'date':
        comparison = (getEffectiveDate(a) || '').localeCompare(getEffectiveDate(b) || '');
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

// Methods
const handleSort = (field: typeof sortField.value) => {
  if (sortField.value === field) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortField.value = field;
    sortDirection.value = 'asc';
  }
};

const getStatusBadgeClass = (status: string) => {
  const variants: Record<string, string> = {
    settled: 'bg-green-600 text-white',
    discrepant: 'bg-red-600 text-white',
    pending: 'bg-yellow-500 text-foreground',
    missing: 'bg-gray-400 text-foreground',
    duplicate: 'bg-gray-400 text-foreground',
    'Transfer In': 'bg-blue-600 text-white',
    'Transfer Out': 'bg-indigo-600 text-white'
  };
  return `px-2 py-1 rounded-md text-xs font-medium ${variants[status] || 'bg-gray-400 text-foreground'}`;
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
    getEffectiveStan(t) || '',
    getEffectiveRef(t) || '',
    getEffectiveAmount(t)?.toString() || '',
    getEffectiveDate(t) || '',
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
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
};

const exportTransactions = (filtered: boolean) => {
  isExporting.value = true;
  try {
    const data = filtered ? filteredAndSortedTransactions.value : props.transactions;
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

const handleViewDetails = (transaction: ReconciliationResult) => {
  console.log('ResultsTable: View details clicked for:', transaction.id);
  emit('view-details', transaction);
};

// Helper Methods
const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount);
};

// Data Extraction Helpers
const parseJsonSafe = (jsonString?: string) => {
  if (!jsonString) return null;
  try {
    return JSON.parse(jsonString);
  } catch (e) {
    return null;
  }
};

// getEffectiveStan moved up
// getEffectiveDate moved up

const getEffectiveRef = (t: ReconciliationResult) => {
  if (t.transactionRef) return t.transactionRef;
   const sources = [t.switchData, t.atmData, t.payableData, t.receivableData];
  for (const src of sources) {
    const data = typeof src === 'string' ? parseJsonSafe(src) : src;
    if (data && (data.rrn || data.RRN || data.ref || data.REF)) return data.rrn || data.RRN || data.ref || data.REF;
  }
  return null;
};

defineExpose({
  setStatusFilter: (status: string) => {
    statusFilter.value = status;
    currentPage.value = 1;
  }
});
</script>

<template>
  <div class="space-y-4">
    <!-- Filters and Search -->
    <div class="flex flex-col lg:flex-row gap-4">
      <div class="flex-1">
        <div class="relative">
          <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input
            placeholder="Search transactions..."
            v-model="searchTerm"
            class="pl-10"
          />
        </div>
      </div>
      <div class="flex gap-2 flex-wrap items-center">
        <select
          v-model="bankFilter"
          class="flex h-10 w-44 items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        >
          <option class="text-foreground" value="all">All Banks</option>
          <option
            v-for="bank in banks"
            :key="bank"
            class="text-foreground"
            :value="bank"
          >
            {{ bank }}
          </option>
        </select>
        <select
          v-model="onUsFilter"
          class="flex h-10 w-40 items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        >
          <option class="text-foreground" value="all">On-us/Off-us</option>
          <option class="text-foreground" value="on-us">On-us</option>
          <option class="text-foreground" value="off-us">Off-us</option>
        </select>
        <select
          v-model="statusFilter"
          class="flex h-10 w-40 items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        >
          <option class="text-foreground" value="all">All Statuses</option>
          <option class="text-foreground" value="settled">Settled</option>
          <option class="text-foreground" value="discrepant">Discrepant</option>
          <option class="text-foreground" value="missing">Missing</option>
          <option class="text-foreground" value="duplicate">Duplicate</option>
          <option class="text-foreground" value="reversal">Reversal</option>
          <option class="text-foreground" value="Transfer In">Transfer In</option>
          <option class="text-foreground" value="Transfer Out">Transfer Out</option>
          <option class="text-foreground" value="pending">Pending</option>
        </select>
        <select
          v-model="typeFilter"
          class="flex h-10 w-48 items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
        >
          <option class="text-foreground" value="all">All Types</option>
          <option
            v-for="t in transactionTypes"
            :key="t"
            class="text-foreground"
            :value="t"
          >
            {{ t }}
          </option>
        </select>
        <Button
          @click="exportTransactions(true)"
          variant="outline"
          size="icon"
          class="h-9 w-9 rounded-full"
          title="Export filtered reconciliation transactions"
          :disabled="isExporting || loading"
        >
          <FileText class="w-4 h-4" />
        </Button>
        <Button
          @click="exportTransactions(false)"
          variant="outline"
          size="icon"
          class="h-9 w-9 rounded-full"
          title="Export all reconciliation transactions"
          :disabled="isExporting || loading"
        >
          <Download class="w-4 h-4" />
        </Button>
      </div>
    </div>

    <!-- Table -->
    <div class="border border-border rounded-lg">
      <table class="w-full table-fixed table-striped">
        <thead class="bg-primary hover:bg-primary/95 transition-colors">
          <tr>
            <th class="w-12 p-4">
              <input
                type="checkbox"
                :checked="selectedTransactions.size === paginatedTransactions.length && paginatedTransactions.length > 0"
                @change="handleSelectAll"
                class="cursor-pointer"
              />
            </th>
            <th
              class="p-4 text-left cursor-pointer font-semibold text-primary-foreground"
              @click="handleSort('stan')"
            >
              STAN
              <ChevronUp v-if="sortField === 'stan' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
              <ChevronDown v-if="sortField === 'stan' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
            </th>
            <th class="p-4 text-left text-primary-foreground">Transaction Ref</th>
            <th
              class="p-4 text-left cursor-pointer font-semibold text-primary-foreground"
              @click="handleSort('date')"
            >
              Date/Time
              <ChevronUp v-if="sortField === 'date' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
              <ChevronDown v-if="sortField === 'date' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
            </th>
            <th
              class="p-4 text-right cursor-pointer font-semibold text-primary-foreground"
              @click="handleSort('amount')"
            >
              Amount
              <ChevronUp v-if="sortField === 'amount' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
              <ChevronDown v-if="sortField === 'amount' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
            </th>
            <th
              class="p-4 text-center cursor-pointer font-semibold text-primary-foreground"
              @click="handleSort('status')"
            >
              Status
              <ChevronUp v-if="sortField === 'status' && sortDirection === 'asc'" class="inline w-4 h-4 ml-1" />
              <ChevronDown v-if="sortField === 'status' && sortDirection === 'desc'" class="inline w-4 h-4 ml-1" />
            </th>
            <th class="p-4 text-left text-primary-foreground">Source</th>
            <th class="p-4 text-left text-primary-foreground">Issue</th>
            <th class="p-4 text-center text-primary-foreground">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="p-8 text-center text-muted-foreground italic">
              Loading transactions...
            </td>
          </tr>
          <tr v-else-if="paginatedTransactions.length === 0">
            <td colspan="7" class="p-8 text-center text-muted-foreground italic">
              No transactions found matching your criteria.
            </td>
          </tr>
          <tr
            v-else
            v-for="transaction in paginatedTransactions"
            :key="transaction.id"
            class="border-b transition-colors hover:bg-muted/30 group"
          >
            <td class="p-4 text-center">
              <input
                type="checkbox"
                :checked="selectedTransactions.has(transaction.id)"
                @change="handleSelectTransaction(transaction.id)"
                class="cursor-pointer"
              />
            </td>
            <td class="p-4 font-mono text-sm">
              <div class="flex items-center gap-2">
                {{ getEffectiveStan(transaction) || 'N/A' }}
                <Badge v-if="transaction.recordCount && transaction.recordCount > 1" variant="outline" class="text-[10px] py-0 px-1 bg-primary/20 text-primary border-primary/30">
                  Merged ({{ transaction.recordCount }})
                </Badge>
              </div>
            </td>
            <td class="p-4 truncate max-w-[150px] font-mono text-xs" :title="getEffectiveRef(transaction) || ''">{{ getEffectiveRef(transaction) || 'N/A' }}</td>
            <td class="p-4 text-sm">{{ getEffectiveDate(transaction) || 'N/A' }}</td>
            <td class="p-4 text-right font-medium">{{ formatCurrency(getEffectiveAmount(transaction)) }}</td>
            <td class="p-4 text-center">
              <span :class="getStatusBadgeClass(transaction.status)">
                {{ transaction.status.toUpperCase() }}
              </span>
            </td>
            <td class="p-4 text-xs text-muted-foreground truncate max-w-[150px]" :title="transaction.sourceFiles">
              {{ transaction.sourceFiles || '-' }}
            </td>
            <td class="p-4 text-xs text-destructive truncate max-w-[150px]" :title="transaction.discrepancyType || transaction.details">
              {{ transaction.status === 'settled' ? '-' : (transaction.discrepancyType || 'Mismatch') }}
            </td>
            <td class="p-4 text-center">
              <Button variant="ghost" size="sm" @click="handleViewDetails(transaction)">
                <Eye class="h-4 w-4" />
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <Pagination
      :current-page="currentPage"
      :total-pages="totalPages"
      :total-items="filteredAndSortedTransactions.length"
      :items-per-page="itemsPerPage"
      @update:current-page="currentPage = $event"
    />
  </div>
</template>

