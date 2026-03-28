<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import { Button } from '@/components/ui/button';
import SessionChooser from '@/components/p2p/SessionChooser.vue';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Download, FileText, Calendar, TrendingUp, TrendingDown, DollarSign, PieChart, Activity } from 'lucide-vue-next';
import DonutChart from '@/components/p2p/DonutChart.vue';

interface SettlementReport {
  sessionId?: string;
  reportTitle: string;
  settlementDate: string;
  formattedDate: string;
  debitSide: {
    onUsATMCommission: number;
    remoteOnUsATMCommission: number;
    onUsBalanceInquiryCommissionToEthSwitch: number;
    remoteOnUsEposPurchasePlusCommission: number;
    balanceInquiryFee: number;
    remoteOnUsPOSAmount: number;
    outwardP2PAmount: number;
    p2PCommission: number;
    onUsATMFee: number;
    remoteOnUsATMFee: number;
    totalAmount: number;
  };
  creditSide: {
    atmCashWithdrawal: number;
    incomingP2P: number;
    remoteOnUsDisputeChargebackAmountCommission: number;
    onUsDisputeChargebackCommission: number;
    atmWithdrawalFee: number;
    balanceInquiryFee: number;
    totalAmount: number;
  };
  totalDebitAmount: number;
  totalCreditAmount: number;
  netSettlementAmount: number;
}

const selectedDate = ref<string>('');
const report = ref<SettlementReport | null>(null);
const isLoading = ref<boolean>(false);
const error = ref<string>('');
const route = useRoute();
const router = useRouter();

// Session management
const sessions = ref<Array<any>>([]);
const filteredSessions = ref<Array<any>>([]);
const currentSessionId = computed(() => route.query.sessionId as string | undefined);

const today = new Date().toISOString().split('T')[0];

onMounted(async () => {
  // Load sessions first
  await loadSessions();
  
  const sessionId = route.query.sessionId as string;
  if (sessionId) {
    loadReportBySession(sessionId);
  } else {
    selectedDate.value = (sessions.value[0]?.settlementDate || today) as string;
    updateFilteredSessions();
    loadReport(); // Auto-load for today if no session
  }
});

watch(() => route.query.sessionId, (newSessionId) => {
  if (newSessionId) {
    loadReportBySession(newSessionId as string);
  } else if (selectedDate.value) {
    // If session ID is removed but date exists, load by date
    loadReport();
  } else {
    loadAllDatesReport();
  }
});

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount);
};

const loadSessions = async () => {
  try {
    const response = await axios.get('/api/p2p/reconciliation/sessions');
    sessions.value = response.data || [];
    // Sort desc by processedAt
    sessions.value.sort((a, b) => new Date(b.processedAt || 0).getTime() - new Date(a.processedAt || 0).getTime());
    
    // Filter sessions for selected date
    updateFilteredSessions();
  } catch (err: any) {
    console.error('Error loading sessions:', err);
  }
};

const updateFilteredSessions = () => {
  if (!selectedDate.value) {
    filteredSessions.value = [];
    return;
  }
  
  const selectedDateStr = selectedDate.value;
  filteredSessions.value = sessions.value.filter(session => {
    const sessionDate = (session.settlementDate || '').toString().slice(0, 10);
    return sessionDate === selectedDateStr;
  });
};

const handleDateChange = () => {
  updateFilteredSessions();
};

const selectSession = async (sessionId: string) => {
  // Update URL with session ID
  router.replace({ query: { sessionId } });
  await loadReportBySession(sessionId);
};

const loadReport = async () => {
  if (!selectedDate.value) {
    await loadAllDatesReport();
    return;
  }

  // If we have an active session in the URL and the date hasn't changed from the report's date, 
  // prefer reloading that specific session. 
  // If the user CHANGED the date, selectedDate will differ from report.settlementDate (if report exists), 
  // so we should fetch by date.
  const sessionId = route.query.sessionId as string;
  if (sessionId && report.value && report.value.settlementDate === selectedDate.value) {
    await loadReportBySession(sessionId);
    return;
  }
  
  // If query has session but report isn't loaded yet (e.g. manual refresh), try to trust the session if date matches? 
  // Hard to know without fetching session metadata. 
  // Logic: If user clicks "Sync Report", they usually mean "Fetch for this date".
  // Note: We'll stick to date-based fetch unless the above condition is met to preserve session context where obvious.

  isLoading.value = true;
  error.value = '';
  
  // Clear session query param if we are explicitly fetching by date (and date changed)
  if (sessionId && report.value && report.value.settlementDate !== selectedDate.value) {
     router.replace({ query: {} });
  }
  
  try {
    const response = await axios.get(`/api/p2p/reports/settlement/${selectedDate.value}`);
    const data = response.data;
    
    if (data && data.debitSide && data.creditSide) {
      report.value = mapReportData(data);
    } else {
      error.value = 'Invalid report data structure';
      report.value = null;
    }
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to load report';
    report.value = null;
    console.error('Error loading report:', err);
  } finally {
    isLoading.value = false;
  }
};

const mapReportData = (data: any): SettlementReport => {
  return {
    sessionId: data.sessionId,
    reportTitle: data.reportTitle || 'Member Net Position Summary Report',
    settlementDate: data.settlementDate || selectedDate.value,
    formattedDate: data.formattedDate || selectedDate.value,
    debitSide: {
      onUsATMCommission: data.debitSide.onUsATMCommission || 0,
      remoteOnUsATMCommission: data.debitSide.remoteOnUsATMCommission || 0,
      onUsBalanceInquiryCommissionToEthSwitch: data.debitSide.onUsBalanceInquiryCommissionToEthSwitch || 0,
      remoteOnUsEposPurchasePlusCommission: data.debitSide.remoteOnUsEposPurchasePlusCommission || 0,
      balanceInquiryFee: data.debitSide.balanceInquiryFee || 0,
      remoteOnUsPOSAmount: data.debitSide.remoteOnUsPOSAmount || 0,
      outwardP2PAmount: data.debitSide.outwardP2PAmount || 0,
      p2PCommission: data.debitSide.p2PCommission || 0,
      onUsATMFee: data.debitSide.onUsATMFee || 0,
      remoteOnUsATMFee: data.debitSide.remoteOnUsATMFee || 0,
      totalAmount: data.debitSide.totalAmount || 0
    },
    creditSide: {
      atmCashWithdrawal: data.creditSide.atmCashWithdrawal || 0,
      incomingP2P: data.creditSide.incomingP2P || 0,
      remoteOnUsDisputeChargebackAmountCommission: data.creditSide.remoteOnUsDisputeChargebackAmountCommission || 0,
      onUsDisputeChargebackCommission: data.creditSide.onUsDisputeChargebackCommission || 0,
      atmWithdrawalFee: data.creditSide.atmWithdrawalFee || 0,
      balanceInquiryFee: data.creditSide.balanceInquiryFee || 0,
      totalAmount: data.creditSide.totalAmount || 0
    },
    totalDebitAmount: data.totalDebitAmount || 0,
    totalCreditAmount: data.totalCreditAmount || 0,
    netSettlementAmount: data.netSettlementAmount || 0
  };
};

const buildAggregateReport = (reports: SettlementReport[]): SettlementReport => {
  const base: SettlementReport = {
    reportTitle: 'All Sessions Settlement Summary Report',
    settlementDate: 'ALL',
    formattedDate: 'All Dates',
    debitSide: {
      onUsATMCommission: 0,
      remoteOnUsATMCommission: 0,
      onUsBalanceInquiryCommissionToEthSwitch: 0,
      remoteOnUsEposPurchasePlusCommission: 0,
      balanceInquiryFee: 0,
      remoteOnUsPOSAmount: 0,
      outwardP2PAmount: 0,
      p2PCommission: 0,
      onUsATMFee: 0,
      remoteOnUsATMFee: 0,
      totalAmount: 0
    },
    creditSide: {
      atmCashWithdrawal: 0,
      incomingP2P: 0,
      remoteOnUsDisputeChargebackAmountCommission: 0,
      onUsDisputeChargebackCommission: 0,
      atmWithdrawalFee: 0,
      balanceInquiryFee: 0,
      totalAmount: 0
    },
    totalDebitAmount: 0,
    totalCreditAmount: 0,
    netSettlementAmount: 0
  };

  for (const r of reports) {
    base.debitSide.onUsATMCommission += r.debitSide.onUsATMCommission || 0;
    base.debitSide.remoteOnUsATMCommission += r.debitSide.remoteOnUsATMCommission || 0;
    base.debitSide.onUsBalanceInquiryCommissionToEthSwitch += r.debitSide.onUsBalanceInquiryCommissionToEthSwitch || 0;
    base.debitSide.remoteOnUsEposPurchasePlusCommission += r.debitSide.remoteOnUsEposPurchasePlusCommission || 0;
    base.debitSide.balanceInquiryFee += r.debitSide.balanceInquiryFee || 0;
    base.debitSide.remoteOnUsPOSAmount += r.debitSide.remoteOnUsPOSAmount || 0;
    base.debitSide.outwardP2PAmount += r.debitSide.outwardP2PAmount || 0;
    base.debitSide.p2PCommission += r.debitSide.p2PCommission || 0;
    base.debitSide.onUsATMFee += r.debitSide.onUsATMFee || 0;
    base.debitSide.remoteOnUsATMFee += r.debitSide.remoteOnUsATMFee || 0;
    base.debitSide.totalAmount += r.debitSide.totalAmount || 0;

    base.creditSide.atmCashWithdrawal += r.creditSide.atmCashWithdrawal || 0;
    base.creditSide.incomingP2P += r.creditSide.incomingP2P || 0;
    base.creditSide.remoteOnUsDisputeChargebackAmountCommission += r.creditSide.remoteOnUsDisputeChargebackAmountCommission || 0;
    base.creditSide.onUsDisputeChargebackCommission += r.creditSide.onUsDisputeChargebackCommission || 0;
    base.creditSide.atmWithdrawalFee += r.creditSide.atmWithdrawalFee || 0;
    base.creditSide.balanceInquiryFee += r.creditSide.balanceInquiryFee || 0;
    base.creditSide.totalAmount += r.creditSide.totalAmount || 0;

    base.totalDebitAmount += r.totalDebitAmount || 0;
    base.totalCreditAmount += r.totalCreditAmount || 0;
    base.netSettlementAmount += r.netSettlementAmount || 0;
  }

  return base;
};

const loadAllDatesReport = async () => {
  isLoading.value = true;
  error.value = '';
  try {
    if (!sessions.value.length) {
      await loadSessions();
    }
    if (!sessions.value.length) {
      report.value = null;
      error.value = 'No sessions available';
      return;
    }

    const responses = await Promise.all(
      sessions.value.map((session) =>
        axios.get(`/api/p2p/reports/settlement/session/${session.id}`).catch(() => null)
      )
    );
    const validReports = responses
      .filter((resp: any) => !!resp?.data?.debitSide && !!resp?.data?.creditSide)
      .map((resp: any) => mapReportData(resp.data));

    if (!validReports.length) {
      report.value = null;
      error.value = 'No report data available for any session';
      return;
    }

    report.value = buildAggregateReport(validReports);
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to load all-dates report';
    report.value = null;
    console.error('Error loading all-dates report:', err);
  } finally {
    isLoading.value = false;
  }
};

const loadReportBySession = async (sessionId: string) => {
  isLoading.value = true;
  error.value = '';
  
  try {
    const response = await axios.get(`/api/p2p/reports/settlement/session/${sessionId}`);
    const data = response.data;
    
    if (data && data.debitSide && data.creditSide) {
      report.value = mapReportData(data);
      if (data.settlementDate) {
        selectedDate.value = data.settlementDate;
        updateFilteredSessions();
      }
    } else {
      error.value = 'Invalid report data structure';
      report.value = null;
    }
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Failed to load report';
    report.value = null;
    console.error('Error loading report by session:', err);
  } finally {
    isLoading.value = false;
  }
};

const selectAllSessionsForDate = async () => {
  if (!selectedDate.value) return;
  await router.replace({ query: {} });
  await loadReport();
};

const selectAllDates = async () => {
  selectedDate.value = '';
  updateFilteredSessions();
  await router.replace({ query: {} });
  await loadAllDatesReport();
};

const debitChartData = computed(() => {
  if (!report.value) return [];
  const d = report.value.debitSide;
  return [
    { label: 'ATM Commission', value: d.onUsATMCommission + d.remoteOnUsATMCommission, color: 'rgb(37, 91, 48)' },
    { label: 'P2P Amount', value: d.outwardP2PAmount, color: 'rgb(240, 206, 13)' },
    { label: 'POS Amount', value: d.remoteOnUsPOSAmount, color: 'rgb(37, 91, 48)' },
    { label: 'Fees/Comm', value: d.balanceInquiryFee + d.p2PCommission + d.onUsATMFee + d.remoteOnUsATMFee, color: 'rgb(240, 206, 13)' }
  ].filter(i => i.value > 0);
});

const creditChartData = computed(() => {
  if (!report.value) return [];
  const c = report.value.creditSide;
  return [
    { label: 'ATM Withdrawal', value: c.atmCashWithdrawal, color: 'rgb(37, 91, 48)' },
    { label: 'Incoming P2P', value: c.incomingP2P, color: 'rgb(240, 206, 13)' },
    { label: 'Fees', value: c.atmWithdrawalFee + c.balanceInquiryFee, color: 'rgb(37, 91, 48)' }
  ].filter(i => i.value > 0);
});

const netComparisonData = computed(() => {
  if (!report.value) return [];
  return [
    { label: 'Total Debit', value: report.value.totalDebitAmount, color: 'rgb(37, 91, 48)' },
    { label: 'Total Credit', value: report.value.totalCreditAmount, color: 'rgb(240, 206, 13)' }
  ];
});

const sessionsList = ref<Array<any>>([]);
const showSessionChooser = ref<boolean>(false);
const pendingExportType = ref<string>('');

const downloadCSV = async () => {
  const sessionId = route.query.sessionId as string;
  const dateToUse = selectedDate.value;
  
  if (!dateToUse && !sessionId) {
    error.value = 'Please select a date or a specific session to export.';
    return;
  }
  
  try {
    let url = '';
    if (sessionId) {
      url = `/api/p2p/reports/settlement/session/${sessionId}/export/csv`;
    } else {
      url = `/api/p2p/reports/settlement/${dateToUse}/export/csv`;
    }
    
    const response = await axios.get(url, {
      responseType: 'blob'
    });
    
    const urlObj = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = urlObj;
    link.setAttribute('download', `${report.value?.reportTitle || 'Settlement Report'}.csv`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(urlObj);
  } catch (err: any) {
    const sessions = err?.response?.data?.sessions;
    if (sessions && Array.isArray(sessions) && sessions.length > 1) {
      sessionsList.value = sessions;
      pendingExportType.value = 'csv';
      showSessionChooser.value = true;
      return;
    }

    error.value = 'Failed to download CSV: ' + (err.response?.data?.error || err.message);
    console.error('CSV download error:', err);
  }
};

const downloadExcel = async () => {
  const sessionId = route.query.sessionId as string;
  const dateToUse = selectedDate.value;
  
  if (!dateToUse && !sessionId) {
    error.value = 'Please select a date or a specific session to export.';
    return;
  }
  
  try {
    let url = '';
    if (sessionId) {
      url = `/api/p2p/reports/settlement/session/${sessionId}/export/excel`;
    } else {
      url = `/api/p2p/reports/settlement/${dateToUse}/export/excel`;
    }
    
    const response = await axios.get(url, {
      responseType: 'blob'
    });
    
    const urlObj = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = urlObj;
    link.setAttribute('download', `${report.value?.reportTitle || 'Settlement Report'}.xlsx`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(urlObj);
  } catch (err: any) {
    const sessions = err?.response?.data?.sessions;
    if (sessions && Array.isArray(sessions) && sessions.length > 1) {
      sessionsList.value = sessions;
      pendingExportType.value = 'excel';
      showSessionChooser.value = true;
      return;
    }
    error.value = 'Failed to download Excel: ' + (err.response?.data?.error || err.message);
    console.error('Excel download error:', err);
  }
};

const onSessionSelected = async (sessionId: string) => {
  showSessionChooser.value = false;
  try {
    if (pendingExportType.value === 'csv') {
      const response = await axios.get(`/api/p2p/reports/settlement/session/${sessionId}/export/csv`, { responseType: 'blob' });
      const urlObj = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = urlObj;
      link.setAttribute('download', `Settlement Report - ${sessionId}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(urlObj);
    } else if (pendingExportType.value === 'excel') {
      const response = await axios.get(`/api/p2p/reports/settlement/session/${sessionId}/export/excel`, { responseType: 'blob' });
      const urlObj = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = urlObj;
      link.setAttribute('download', `Settlement Report - ${sessionId}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(urlObj);
    }
  } catch (err: any) {
    error.value = 'Failed to download report for session: ' + (err.response?.data?.error || err.message);
  }
};
</script>

<template>
  <div class="container mx-auto p-6 max-w-7xl space-y-8">
    <!-- Header -->
    <div class="animate-fade-in flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <h1 class="text-3xl font-bold text-foreground mb-2">P2P Settlement Reports</h1>
        <p class="text-foreground/70">Analyze settlement performance and download detailed breakdown logs</p>
      </div>
      <div class="flex gap-2">
        <Button 
          v-if="report" 
          @click="downloadCSV" 
          variant="outline" 
          class="h-10 px-4"
        >
          <Download class="h-4 w-4 mr-2" />
          CSV
        </Button>
        <Button 
          v-if="report" 
          @click="downloadExcel" 
          class="btn-primary h-10 px-4 shadow-lg shadow-primary/20"
        >
          <Download class="h-4 w-4 mr-2" />
          Excel
        </Button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="rounded-xl bg-muted/20 p-4 animate-fade-in">
      <div class="flex flex-col lg:flex-row gap-4 items-end">
        <div class="w-full lg:w-72 space-y-2">
          <label for="settlement-date" class="text-xs font-black uppercase tracking-widest text-muted-foreground">Settlement Date</label>
          <div class="flex items-center gap-2">
            <input
              id="settlement-date"
              v-model="selectedDate"
              type="date"
              class="w-full p-3 border border-border rounded-xl bg-background focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all outline-none font-medium text-primary"
              :max="today"
              @change="handleDateChange"
            />
            <button
              type="button"
              class="px-3 py-3 text-xs rounded-xl border transition-colors duration-150"
              :class="!selectedDate
                ? 'bg-warning text-warning-foreground border-warning'
                : 'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground'"
              @click="selectAllDates"
            >
              ALL
            </button>
          </div>
        </div>

        <div class="flex-1 w-full">
          <div v-if="selectedDate && filteredSessions.length > 0">
            <div class="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              Available Sessions
            </div>
            <div class="flex gap-2 flex-wrap">
              <button
                @click="selectAllSessionsForDate"
                class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150"
                :class="{
                  'bg-warning text-warning-foreground border-warning': !currentSessionId,
                  'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground': !!currentSessionId
                }"
              >
                All Sessions
              </button>
              <button
                v-for="session in filteredSessions"
                :key="session.id"
                @click="selectSession(session.id)"
                class="px-3 py-1.5 text-xs rounded-md border transition-colors duration-150"
                :class="{
                  'bg-warning text-warning-foreground border-warning': currentSessionId === session.id,
                  'bg-primary text-primary-foreground border-primary hover:bg-warning hover:text-warning-foreground': currentSessionId !== session.id
                }"
              >
                {{ new Date(session.processedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
              </button>
            </div>
          </div>

          <div v-else-if="selectedDate && filteredSessions.length === 0" class="text-xs text-muted-foreground">
            No sessions found for {{ new Date(selectedDate).toLocaleDateString() }}
          </div>
          <div v-else class="text-xs text-muted-foreground">
            Viewing all dates. Select a date to drill down by session.
          </div>
        </div>

        <div class="w-full lg:w-52">
          <Button 
            @click="loadReport"
            :disabled="isLoading || !selectedDate"
            class="w-full btn-primary h-12 text-base shadow-xl"
          >
            <Activity v-if="!isLoading" class="h-5 w-5 mr-3" />
            <div v-else class="animate-spin h-5 w-5 mr-3 border-2 border-current border-t-transparent rounded-full" />
            {{ isLoading ? 'Processing...' : 'Sync Report' }}
          </Button>
        </div>
      </div>

      <div v-if="error" class="mt-4 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-xs font-bold flex items-center gap-2 animate-shake">
        <TrendingDown class="h-4 w-4" />
        {{ error }}
      </div>
    </div>

    <!-- Main Visual Summary -->
    <Card class="banking-card-elevated overflow-hidden border-0 shadow-none bg-transparent">
         <div v-if="!report && !isLoading" class="h-full flex flex-col items-center justify-center p-12 text-center bg-muted/5">
            <PieChart class="h-16 w-16 text-muted-foreground/20 mb-4" />
            <h3 class="text-xl font-bold mb-2">Ready to sync</h3>
            <p class="text-muted-foreground max-w-xs">Select a date and click 'Sync Report' to visualize your P2P settlement data breakdown.</p>
         </div>
         
         <div v-else-if="isLoading" class="h-full flex flex-col items-center justify-center p-12 space-y-4">
            <div class="animate-spin h-12 w-12 border-4 border-primary border-t-transparent rounded-full shadow-lg shadow-primary/20" />
            <p class="text-primary font-bold animate-pulse uppercase tracking-[0.2em] text-xs">Aggregating session records...</p>
         </div>
         
         <div v-else-if="report" class="animate-fade-in h-full">
            <div class="grid grid-cols-1 md:grid-cols-3 h-full">
               <!-- Net Position -->
               <div class="p-8 bg-primary/5 flex flex-col justify-center">
                  <div class="flex items-center gap-2 mb-4">
                    <div class="p-2 rounded-lg bg-primary/10 text-primary">
                      <DollarSign class="h-5 w-5" />
                    </div>
                    <span class="text-xs font-black uppercase tracking-widest text-primary/60">Net Position</span>
                  </div>
                  <h2 class="text-4xl font-black text-foreground mb-1 tracking-tight">{{ formatCurrency(report.netSettlementAmount) }}</h2>
                  <p class="text-xs text-muted-foreground font-bold">{{ report.formattedDate }} Settlement Cycle</p>
                  
                  <div class="mt-8 space-y-4">
                     <div class="flex justify-between items-end pb-2">
                        <span class="text-xs font-bold text-muted-foreground uppercase">Receivables</span>
                        <span class="text-lg font-black text-success">{{ formatCurrency(report.totalCreditAmount) }}</span>
                     </div>
                     <div class="flex justify-between items-end pb-2">
                        <span class="text-xs font-bold text-muted-foreground uppercase">Payables</span>
                        <span class="text-lg font-black text-destructive">{{ formatCurrency(report.totalDebitAmount) }}</span>
                     </div>
                  </div>
               </div>
               
               <!-- Visual Breakdown -->
               <div class="md:col-span-2 p-8 flex flex-col items-center justify-center relative overflow-hidden">
                  <DonutChart 
                    :data="netComparisonData" 
                    :size="240" 
                    label="Volume Mix"
                    :centerValue="formatCurrency(report.totalDebitAmount + report.totalCreditAmount)"
                  />
                  
                  <div class="grid grid-cols-2 gap-8 w-full mt-8">
                     <div class="flex items-center gap-3">
                        <div class="w-1 h-12 bg-destructive rounded-full"></div>
                        <div>
                          <p class="text-[10px] font-black text-muted-foreground uppercase tracking-wider">Debit Strength</p>
                          <p class="text-lg font-black">{{ ((report.totalDebitAmount / (report.totalDebitAmount + report.totalCreditAmount)) * 100).toFixed(1) }}%</p>
                        </div>
                     </div>
                     <div class="flex items-center gap-3">
                        <div class="w-1 h-12 bg-success rounded-full"></div>
                        <div>
                          <p class="text-[10px] font-black text-muted-foreground uppercase tracking-wider">Credit Strength</p>
                          <p class="text-lg font-black">{{ ((report.totalCreditAmount / (report.totalDebitAmount + report.totalCreditAmount)) * 100).toFixed(1) }}%</p>
                        </div>
                     </div>
                  </div>
               </div>
            </div>
         </div>
      </Card>

    <!-- Detailed Ledger -->
    <Card v-if="report" class="banking-card-elevated overflow-hidden animate-slide-up border-0 shadow-none bg-transparent">
      <div class="grid grid-cols-1 lg:grid-cols-2">
        <!-- DEBIT SIDE ANALYTICS -->
        <section class="p-6">
          <div class="flex items-center gap-2 mb-4">
            <TrendingUp class="w-5 h-5 text-destructive" />
            <h3 class="text-lg font-black uppercase tracking-widest text-foreground">Debit Ledger Breakdown</h3>
          </div>
          <div class="p-5 rounded-xl bg-destructive/5 flex items-center justify-between">
            <DonutChart :data="debitChartData" :size="120" label="DEBIT" />
            <div class="text-right">
              <p class="text-[10px] font-black text-muted-foreground uppercase tracking-wider">Total Payable</p>
              <p class="text-2xl font-black text-destructive">{{ formatCurrency(report.totalDebitAmount) }}</p>
            </div>
          </div>
          <div class="mt-4 divide-y divide-border/50">
            <div v-for="(val, key) in report.debitSide" :key="key" class="p-4 flex justify-between items-center group hover:bg-muted/30 transition-colors">
              <div v-if="key !== 'totalAmount'" class="flex items-center gap-3">
                <div class="w-1.5 h-1.5 rounded-full bg-primary/40 group-hover:bg-primary transition-colors"></div>
                <span class="text-sm font-bold text-foreground/80 capitalize">{{ (key as string).replace(/([A-Z])/g, ' $1').trim() }}</span>
              </div>
              <span v-if="key !== 'totalAmount'" class="text-sm font-black">{{ formatCurrency(val) }}</span>
            </div>
          </div>
        </section>

        <!-- CREDIT SIDE ANALYTICS -->
        <section class="p-6 border-t lg:border-t-0">
          <div class="flex items-center gap-2 mb-4">
            <TrendingDown class="w-5 h-5 text-success" />
            <h3 class="text-lg font-black uppercase tracking-widest text-foreground">Credit Ledger Breakdown</h3>
          </div>
          <div class="p-5 rounded-xl bg-success/5 flex items-center justify-between">
            <DonutChart :data="creditChartData" :size="120" label="CREDIT" />
            <div class="text-right">
              <p class="text-[10px] font-black text-muted-foreground uppercase tracking-wider">Total Receivable</p>
              <p class="text-2xl font-black text-success">{{ formatCurrency(report.totalCreditAmount) }}</p>
            </div>
          </div>
          <div class="mt-4 divide-y divide-border/50">
            <div v-for="(val, key) in report.creditSide" :key="key" class="p-4 flex justify-between items-center group hover:bg-muted/30 transition-colors">
              <div v-if="key !== 'totalAmount'" class="flex items-center gap-3">
                <div class="w-1.5 h-1.5 rounded-full bg-primary/40 group-hover:bg-primary transition-colors"></div>
                <span class="text-sm font-bold text-foreground/80 capitalize">{{ (key as string).replace(/([A-Z])/g, ' $1').trim() }}</span>
              </div>
              <span v-if="key !== 'totalAmount'" class="text-sm font-black">{{ formatCurrency(val) }}</span>
            </div>
          </div>
        </section>
      </div>
    </Card>
    
    <SessionChooser :show="showSessionChooser" :sessions="sessionsList" @close="showSessionChooser = false" @select="onSessionSelected" />
  </div>
</template>
