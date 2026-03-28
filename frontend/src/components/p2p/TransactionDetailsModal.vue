<template>
  <Teleport to="body">
  <div v-if="show" class="fixed inset-0 z-[100] flex items-center justify-center bg-gray-900/95">
    <div class="bg-green-50 text-gray-900 rounded-lg shadow-2xl max-w-4xl w-full p-6 max-h-[90vh] overflow-y-auto relative border-2 border-green-200">
      <h2 class="text-2xl font-bold mb-4">Transaction Details</h2>
      <Button variant="ghost" size="sm" class="absolute top-4 right-4" @click="$emit('close')">
        <X class="h-5 w-5" />
      </Button>

      <div v-if="transaction" class="space-y-6">
        <!-- Summary Header -->
        <div class="flex items-start justify-between p-4 rounded-xl bg-muted/30 border border-border/50">
          <div class="space-y-1">
            <p class="text-[10px] font-black uppercase tracking-widest text-muted-foreground">Transaction Lifecycle</p>
            <div class="flex items-center gap-3">
              <h3 class="text-xl font-bold font-mono">{{ transaction.transactionRef || transaction.stan || 'NO_REF' }}</h3>
              <Badge :class="getTransactionStatusBadgeClass(resolvedStatus)">
                {{ getTransactionStatusText(resolvedStatus) }}
              </Badge>
            </div>
            <p class="text-xs text-muted-foreground">
              {{ transaction.recordCount && transaction.recordCount > 1 ? `Merged from ${transaction.recordCount} matched records` : 'Single matched record' }}
            </p>
            <p v-if="isResolvedStatus && transaction.resolvedBy" class="text-xs text-muted-foreground">
              Resolved by <span class="font-semibold text-foreground/80">{{ transaction.resolvedBy }}</span>
              <span v-if="transaction.resolvedAt"> on {{ new Date(transaction.resolvedAt).toLocaleString() }}</span>
            </p>
          </div>
          <div class="text-right">
            <p class="text-2xl font-black text-primary">{{ formatCurrency(transaction.amount || 0) }}</p>
            <p class="text-xs text-muted-foreground">{{ getEffectiveDate }}</p>
          </div>
        </div>

        <!-- 4-Source Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          <!-- Switch Data -->
          <Card class="border-t-4 border-t-primary shadow-sm bg-card transition-all" :class="!transaction.switchData ? 'opacity-60 grayscale' : ''">
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-bold flex items-center justify-between">
                Switch Data
                <Badge v-if="transaction.switchData" variant="outline" class="text-[10px] bg-primary/5 text-primary border-primary/20 uppercase">Available</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="transaction.switchData" class="space-y-3">
                <div v-if="getValue({data: safeParse(transaction.switchData)}, ['issuer', 'issuerName']) !== '-'">
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Issuer</p>
                  <p class="text-sm font-semibold">{{ getValue({data: safeParse(transaction.switchData)}, ['issuer', 'issuerName']) }}</p>
                </div>
                <div v-if="getValue({data: safeParse(transaction.switchData)}, ['acquirer', 'acquirerName']) !== '-'">
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Acquirer</p>
                  <p class="text-sm font-semibold">{{ getValue({data: safeParse(transaction.switchData)}, ['acquirer', 'acquirerName']) }}</p>
                </div>
                <div class="grid grid-cols-2 gap-2">
                  <div v-if="getValue({data: safeParse(transaction.switchData)}, ['mtiCode', 'mti', 'MessageTypeIdentifier']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">MTI</p>
                    <p class="text-sm font-mono">{{ getValue({data: safeParse(transaction.switchData)}, ['mtiCode', 'mti', 'MessageTypeIdentifier']) }}</p>
                  </div>
                  <div v-if="getValue({data: safeParse(transaction.switchData)}, ['stanNo', 'stan', 'SystemTraceAuditNumber']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">STAN</p>
                    <p class="text-sm font-mono">{{ getValue({data: safeParse(transaction.switchData)}, ['stanNo', 'stan', 'SystemTraceAuditNumber']) }}</p>
                  </div>
                </div>
                <div v-if="getValue({data: safeParse(transaction.switchData)}, ['transactionDescription', 'description', 'TransactionType']) !== '-'">
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Description</p>
                  <p class="text-xs">{{ getValue({data: safeParse(transaction.switchData)}, ['transactionDescription', 'description', 'TransactionType']) }}</p>
                </div>
              </div>
              <div v-else class="py-12 text-center text-muted-foreground">
                <AlertCircle class="h-6 w-6 mx-auto mb-2 opacity-20" />
                <p class="text-xs font-bold uppercase tracking-widest opacity-40">Missing Switch Activity</p>
              </div>
            </CardContent>
          </Card>

          <!-- ATM Activity Data -->
          <Card class="border-t-4 border-t-purple-500 shadow-sm bg-card transition-all" :class="!transaction.atmData ? 'opacity-60 grayscale' : ''">
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-bold flex items-center justify-between">
                ATM Activity Data
                <Badge v-if="transaction.atmData" variant="outline" class="text-[10px] bg-purple-500/5 text-purple-600 border-purple-500/20 uppercase">Available</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="transaction.atmData" class="space-y-3">
                <div>
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Txn Amount</p>
                  <p class="text-sm font-bold text-purple-600">
                    {{ formatCurrency(getValue({data: safeParse(transaction.atmData)}, ['txnAmount', 'amount', 'Amount'])) }}
                  </p>
                </div>
                <div class="grid grid-cols-2 gap-2">
                  <div v-if="getValue({data: safeParse(transaction.atmData)}, ['authCode', 'authorizationCode']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">Auth Code</p>
                    <p class="text-sm font-mono">{{ getValue({data: safeParse(transaction.atmData)}, ['authCode', 'authorizationCode']) }}</p>
                  </div>
                  <div v-if="getValue({data: safeParse(transaction.atmData)}, ['stanNo', 'stan', 'SystemTraceAuditNumber']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">STAN No</p>
                    <p class="text-sm font-mono">{{ getValue({data: safeParse(transaction.atmData)}, ['stanNo', 'stan', 'SystemTraceAuditNumber']) }}</p>
                  </div>
                </div>
                <div v-if="getValue({data: safeParse(transaction.atmData)}, ['transRef', 'transactionRef', 'RetrievalReferenceNumber', 'rrn']) !== '-'">
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Trans Ref (FT)</p>
                  <p class="text-sm font-mono text-primary font-bold">{{ getValue({data: safeParse(transaction.atmData)}, ['transRef', 'transactionRef', 'RetrievalReferenceNumber', 'rrn']) }}</p>
                </div>
                <div v-if="getValue({data: safeParse(transaction.atmData)}, ['terminalId', 'TerminalID']) !== '-'">
                  <p class="text-[10px] font-bold text-muted-foreground uppercase">Terminal ID</p>
                  <p class="text-sm font-mono">{{ getValue({data: safeParse(transaction.atmData)}, ['terminalId', 'TerminalID']) }}</p>
                </div>
              </div>
              <div v-else class="py-12 text-center text-muted-foreground">
                <AlertCircle class="h-6 w-6 mx-auto mb-2 opacity-20" />
                <p class="text-xs font-bold uppercase tracking-widest opacity-40">Missing ATM Activity</p>
              </div>
            </CardContent>
          </Card>

          <!-- Payable Data -->
          <Card class="border-t-4 border-t-orange-500 shadow-sm bg-card transition-all" :class="payableEntries.length === 0 ? 'opacity-60 grayscale' : ''">
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-bold flex items-center justify-between">
                Payable Data (Bank)
                <Badge v-if="payableEntries.length > 0" variant="outline" class="text-[10px] bg-orange-500/5 text-orange-600 border-orange-500/20 uppercase">Available</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="payableEntries.length > 0" class="space-y-4">
                <div
                  v-for="(entry, idx) in payableEntries"
                  :key="`payable-${idx}`"
                  class="space-y-3 pb-3 border-b border-border/40 last:border-b-0 last:pb-0"
                >
                  <div class="grid grid-cols-2 gap-2">
                    <div>
                      <p class="text-[10px] font-bold text-muted-foreground uppercase">Credit</p>
                      <p class="text-sm font-bold text-orange-600">{{ formatCurrency(getValue({data: entry}, ['credit', 'CreditAmount']) || 0) }}</p>
                    </div>
                    <div>
                      <p class="text-[10px] font-bold text-muted-foreground uppercase">Debit</p>
                      <p class="text-sm font-bold text-gray-500">{{ formatCurrency(getValue({data: entry}, ['debit', 'DebitAmount']) || 0) }}</p>
                    </div>
                  </div>
                  <div v-if="getValue({data: entry}, ['bookingDate', 'date', 'ValueDate']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">Booking Date</p>
                    <p class="text-sm font-mono">{{ new Date(getValue({data: entry}, ['bookingDate', 'date', 'ValueDate'])).toLocaleString() }}</p>
                  </div>
                  <div v-if="getValue({data: entry}, ['transRef', 'transactionRef', 'Reference']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">Trans Ref (FT)</p>
                    <p class="text-sm font-mono">{{ getValue({data: entry}, ['transRef', 'transactionRef', 'Reference']) }}</p>
                  </div>
                </div>
              </div>
              <div v-else class="py-12 text-center text-muted-foreground">
                <AlertCircle class="h-6 w-6 mx-auto mb-2 opacity-20" />
                <p class="text-xs font-bold uppercase tracking-widest opacity-40">Missing Payable Entry</p>
              </div>
            </CardContent>
          </Card>

          <!-- Receivable Data -->
          <Card class="border-t-4 border-t-blue-500 shadow-sm bg-card transition-all" :class="receivableEntries.length === 0 ? 'opacity-60 grayscale' : ''">
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-bold flex items-center justify-between">
                Receivable Data (Bank)
                <Badge v-if="receivableEntries.length > 0" variant="outline" class="text-[10px] bg-blue-500/5 text-blue-600 border-blue-500/20 uppercase">Available</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="receivableEntries.length > 0" class="space-y-4">
                <div
                  v-for="(entry, idx) in receivableEntries"
                  :key="`receivable-${idx}`"
                  class="space-y-3 pb-3 border-b border-border/40 last:border-b-0 last:pb-0"
                >
                  <div class="grid grid-cols-2 gap-2">
                    <div>
                      <p class="text-[10px] font-bold text-muted-foreground uppercase">Credit</p>
                      <p class="text-sm font-bold text-blue-600">{{ formatCurrency(getValue({data: entry}, ['credit', 'CreditAmount']) || 0) }}</p>
                    </div>
                    <div>
                      <p class="text-[10px] font-bold text-muted-foreground uppercase">Debit</p>
                      <p class="text-sm font-bold text-gray-500">{{ formatCurrency(getValue({data: entry}, ['debit', 'DebitAmount']) || 0) }}</p>
                    </div>
                  </div>
                  <div v-if="getValue({data: entry}, ['bookingDate', 'date', 'ValueDate']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">Booking Date</p>
                    <p class="text-sm font-mono">{{ new Date(getValue({data: entry}, ['bookingDate', 'date', 'ValueDate'])).toLocaleString() }}</p>
                  </div>
                  <div v-if="getValue({data: entry}, ['transRef', 'transactionRef', 'Reference']) !== '-'">
                    <p class="text-[10px] font-bold text-muted-foreground uppercase">Trans Ref (FT)</p>
                    <p class="text-sm font-mono">{{ getValue({data: entry}, ['transRef', 'transactionRef', 'Reference']) }}</p>
                  </div>
                </div>
              </div>
              <div v-else class="py-12 text-center text-muted-foreground">
                <AlertCircle class="h-6 w-6 mx-auto mb-2 opacity-20" />
                <p class="text-xs font-bold uppercase tracking-widest opacity-40">Missing Receivable Entry</p>
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- Discrepancy Info Area -->
        <div v-if="resolvedStatus === 'discrepant'" class="mt-6 p-4 rounded-xl bg-destructive/5 border border-destructive/20">
          <h4 class="text-sm font-bold text-destructive uppercase tracking-widest flex items-center gap-2 mb-2">
            <AlertTriangle class="h-4 w-4" />
            Discrepancy Detected
          </h4>
          <p class="text-sm font-medium">{{ transaction.discrepancyType || 'Data mismatch identified between sources.' }}</p>
          <p class="text-xs text-muted-foreground mt-1">{{ transaction.details }}</p>
        </div>

        <!-- Audit Trail -->
        <div v-if="showAuditTrail" class="mt-6 p-4 rounded-xl bg-muted/30 border border-border/50">
          <h4 class="text-sm font-bold uppercase tracking-widest mb-3">Resolution Audit Trail</h4>
          <div v-if="auditLoading" class="text-xs text-muted-foreground">Loading audit history...</div>
          <div v-else-if="!auditLogs || auditLogs.length === 0" class="text-xs text-muted-foreground">
            No audit entries found for this dispute.
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="log in auditLogs"
              :key="log.id"
              class="p-3 rounded-lg border border-border/60 bg-background"
            >
              <div class="flex items-center justify-between gap-4">
                <div class="text-xs font-bold text-foreground">{{ log.username }}</div>
                <div class="text-[10px] text-muted-foreground">{{ formatAuditDate(log.createdAt) }}</div>
              </div>
              <div class="text-[11px] text-muted-foreground mt-1">
                {{ log.action }}
                <span v-if="log.oldStatus || log.newStatus">
                  · {{ (log.oldStatus || 'N/A') }} → {{ (log.newStatus || 'N/A') }}
                </span>
              </div>
              <div v-if="log.notes" class="text-xs text-foreground mt-2">
                {{ log.notes }}
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-center text-foreground py-8">
        No transaction selected.
      </div>
    </div>
  </div>
  </Teleport>
</template>

<script setup lang="ts">

import { computed } from 'vue';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { X, Activity, AlertCircle, AlertTriangle } from 'lucide-vue-next';

interface ReconciliationResult {
  id: string;
  sessionId?: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  status?: 'settled' | 'discrepant' | 'missing' | 'duplicate' | 'pending' | string;
  disputeStatus?: string;
  details?: string;
  discrepancyType?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  sourceFiles?: string | string[];
  switchData?: string;
  atmData?: string;
  payableData?: string;
  receivableData?: string;
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

const props = defineProps<{
  show: boolean;
  transaction: ReconciliationResult | null;
  auditLogs?: AuditLog[];
  auditLoading?: boolean;
}>();

const emit = defineEmits(['close']);

const resolvedStatus = computed(() => {
  const t = props.transaction as (ReconciliationResult & { disputeStatus?: string }) | null;
  return (t?.status || t?.disputeStatus || 'unknown').toString().toLowerCase();
});

const isResolvedStatus = computed(() => {
  const s = resolvedStatus.value;
  return s === 'resolved' || s === 'resolved_manually' || s === 'closed' || s === 'settled';
});

const getComparables = computed(() => {
  if (!props.transaction) return [];
  
  const sources = [
    { name: 'Switch (ETHS)', data: safeParse(props.transaction.switchData), color: 'bg-primary/10 border-primary/20' },
    { name: 'Third Party (ATM)', data: safeParse(props.transaction.atmData), color: 'bg-purple-50 border-purple-200' },
    { name: 'Payable', data: safeParse(props.transaction.payableData), color: 'bg-green-50 border-green-200' },
    { name: 'Receivable', data: safeParse(props.transaction.receivableData), color: 'bg-blue-50 border-blue-200' }
  ];

  // Filter out empty sources
  return sources.filter(s => s.data && Object.keys(s.data).length > 0);
});

const safeParse = (data: string | object | undefined) => {
  if (!data) return null;
  if (typeof data === 'object') {
    const obj = data as Record<string, any>;
    if (obj.data && typeof obj.data === 'object') return obj.data;
    if (obj.payload && typeof obj.payload === 'object') return obj.payload;
    return obj;
  }
  try {
    const parsed = JSON.parse(data);
    if (parsed && typeof parsed === 'object') {
      const obj = parsed as Record<string, any>;
      if (obj.data && typeof obj.data === 'object') return obj.data;
      if (obj.payload && typeof obj.payload === 'object') return obj.payload;
    }
    return parsed;
  } catch (e) {
    return null;
  }
};

const normalizeEntries = (data: string | object | undefined) => {
  const parsed = safeParse(data);
  if (!parsed) return [];
  return Array.isArray(parsed) ? parsed : [parsed];
};

const getValue = (source: any, fields: string | string[]) => {
  if (!source || !source.data) return '-';
  const data = source.data;
  const fieldList = Array.isArray(fields) ? fields : [fields];
  
  for (const field of fieldList) {
    // Exact match
    if (data[field] !== undefined && data[field] !== null) return data[field];
    
    // Case-insensitive match
    const key = Object.keys(data).find(k => k.toLowerCase() === field.toLowerCase());
    if (key !== undefined && data[key] !== null) return data[key];
  }
  
  return '-';
};

const formatCurrency = (amount: number | string): string => {
  const num = Number(amount);
  if (isNaN(num)) return amount?.toString() || '-';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(num);
};

const getTransactionStatusBadgeClass = (status: string | undefined) => {
  if (!status) return 'bg-gray-400 text-foreground px-2 py-1 rounded-md text-xs font-medium';
  
  const variants: Record<string, string> = {
    settled: 'bg-green-600 text-white',
    discrepant: 'bg-red-600 text-white',
    pending: 'bg-yellow-500 text-foreground',
    missing: 'bg-gray-400 text-foreground text-xs',
    duplicate: 'bg-gray-400 text-foreground text-xs',
    unreconciled: 'bg-blue-400 text-white text-[10px]'
  };
  return `px-2 py-1 rounded-md text-xs font-medium ${variants[status.toLowerCase()] || 'bg-gray-400 text-foreground'}`;
};

const getTransactionStatusText = (status: string | undefined) => {
  if (!status) return 'UNKNOWN';
  if (status.toLowerCase() === 'unreconciled') return 'SINGLE SOURCE / UNMATCHED';
  return status.toUpperCase();
};

const getEffectiveDate = computed(() => {
  if (props.transaction?.transactionDate) return new Date(props.transaction.transactionDate).toLocaleString();
  
  // Try to find it in source data
  const sources = [
    props.transaction?.switchData,
    props.transaction?.atmData,
    props.transaction?.payableData,
    props.transaction?.receivableData
  ];
  
  for (const s of sources) {
    const data = safeParse(s);
    if (!data) continue;
    const date = data.valueDate || data.bookingDate || data.transactionDate || data.date || data.requestTime;
    if (date) return new Date(date).toLocaleString();
  }
  
  return 'N/A';
});

const payableEntries = computed(() => normalizeEntries(props.transaction?.payableData));
const receivableEntries = computed(() => normalizeEntries(props.transaction?.receivableData));

const getMatchKeyInfo = (t: ReconciliationResult) => {
  if (t.status !== 'settled') return null;
  
  if (t.switchData && t.atmData) {
    return 'STAN';
  }
  if (t.payableData && t.receivableData) {
    return 'Transaction Ref';
  }
  return null;
};

const showAuditTrail = computed(() => props.auditLogs !== undefined);
const auditLogs = computed(() => props.auditLogs || []);
const auditLoading = computed(() => !!props.auditLoading);

const formatAuditDate = (date?: string) => {
  if (!date) return 'N/A';
  return new Date(date).toLocaleString();
};
</script>
