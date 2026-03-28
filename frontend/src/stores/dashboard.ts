import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { p2pApi, type Transaction } from '@/services/p2pApi';

export const useDashboardStore = defineStore('dashboard', () => {
  // State
  const stats = ref<any>(null);
  const recentSettlements = ref<Transaction[]>([]);
  const loading = ref<boolean>(false);
  const error = ref<string>('');

  // Getters
  const summaryCards = computed(() => {
    if (!stats.value) return [];
    
    return [
      {
        label: 'Total Sessions',
        value: stats.value.totalSessions || 0,
        icon: 'LayoutDashboard',
        color: 'text-primary',
        bg: 'bg-primary/5',
        trend: stats.value.sessionTrend || 0
      },
      {
        label: 'Settlement Rate',
        value: `${stats.value.settlementRate || 0}%`,
        icon: 'CheckCircle',
        color: 'text-success',
        bg: 'bg-success/5',
        trend: stats.value.settlementTrend || 0
      },
      {
        label: 'Total Volume',
        value: formatCurrency(stats.value.totalVolume || 0),
        icon: 'TrendingUp',
        color: 'text-warning',
        bg: 'bg-warning/5',
        trend: stats.value.volumeTrend || 0
      },
      {
        label: 'Active Disputes',
        value: stats.value.activeDisputes || 0,
        icon: 'AlertTriangle',
        color: 'text-destructive',
        bg: 'bg-destructive/5',
        trend: stats.value.disputeTrend || 0
      }
    ];
  });

  // Actions
  async function loadDashboardStats() {
    loading.value = true;
    error.value = '';
    
    try {
      stats.value = await p2pApi.getDashboardStats();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load dashboard stats';
      console.error('Error loading dashboard stats:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadRecentSettlements(limit = 10) {
    loading.value = true;
    error.value = '';
    
    try {
      recentSettlements.value = await p2pApi.getRecentSettlements(limit);
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load recent settlements';
      console.error('Error loading recent settlements:', err);
    } finally {
      loading.value = false;
    }
  }

  function formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  function exportToCSV(data: Transaction[], filename: string) {
    if (!data || data.length === 0) {
      throw new Error('No data to export');
    }

    const headers = [
      'ID', 'STAN', 'Transaction Ref', 'Amount', 
      'Transaction Date', 'Terminal ID', 'Status', 'Details', 'Source Files'
    ];

    const rows = data.map(t => [
      t.id,
      t.stan || '',
      t.transactionRef || '',
      t.amount?.toString() || '',
      t.transactionDate || '',
      t.terminalId || '',
      t.status,
      t.details || '',
      t.sourceFiles || '',
    ]);

    const csv = [
      headers.join(','),
      ...rows.map(row => 
        row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')
      )
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${filename}-${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }

  function clearError() {
    error.value = '';
  }

  // Initialize
  async function initialize() {
    await Promise.all([
      loadDashboardStats(),
      loadRecentSettlements()
    ]);
  }

  return {
    // State
    stats,
    recentSettlements,
    loading,
    error,
    
    // Getters
    summaryCards,
    
    // Actions
    loadDashboardStats,
    loadRecentSettlements,
    formatCurrency,
    exportToCSV,
    clearError,
    initialize
  };
});
