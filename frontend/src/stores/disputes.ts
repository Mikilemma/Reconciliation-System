import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { p2pApi, type Dispute } from '@/services/p2pApi';

export const useDisputesStore = defineStore('disputes', () => {
  // State
  const disputes = ref<Dispute[]>([]);
  const selectedDispute = ref<Dispute | null>(null);
  const statusFilter = ref<string>('all');
  const searchTerm = ref<string>('');
  const currentPage = ref<number>(1);
  const itemsPerPage = 10;
  const loading = ref<boolean>(false);
  const error = ref<string>('');
  const showResolveDialog = ref<boolean>(false);
  const resolveForm = ref({
    status: 'resolved',
    notes: '',
    resolvedBy: '',
  });

  const isResolvedStatus = (status?: string) => {
    const s = (status || '').toLowerCase();
    return s === 'resolved' || s === 'resolved_manually' || s === 'closed';
  };

  // Getters
  const filteredDisputes = computed(() => {
    let filtered = disputes.value;

    // Apply search filter
    if (searchTerm.value) {
      const term = searchTerm.value.toLowerCase();
      filtered = filtered.filter(
        (d) =>
          d.stan?.toLowerCase().includes(term) ||
          d.transactionRef?.toLowerCase().includes(term) ||
          d.amount?.toString().includes(term)
      );
    }

    // Apply status filter
    if (statusFilter.value !== 'all') {
      filtered = filtered.filter((d) => {
        if (statusFilter.value === 'resolved' || statusFilter.value === 'resolved_manually') {
          return isResolvedStatus(d.disputeStatus);
        }
        return d.disputeStatus === statusFilter.value;
      });
    }

    return filtered;
  });

  const paginatedDisputes = computed(() => {
    const start = (currentPage.value - 1) * itemsPerPage;
    return filteredDisputes.value.slice(start, start + itemsPerPage);
  });

  const totalPages = computed(() => Math.ceil(filteredDisputes.value.length / itemsPerPage));

  const stats = computed(() => {
    const total = disputes.value.length;
    const open = disputes.value.filter(d => d.disputeStatus === 'open').length;
    const inProgress = disputes.value.filter(d => d.disputeStatus === 'in_progress').length;
    const resolved = disputes.value.filter(d => isResolvedStatus(d.disputeStatus)).length;
    
    return [
      { label: 'Total Conflicts', value: total, icon: 'AlertTriangle', color: 'text-primary', bg: 'bg-primary/5' },
      { label: 'Pending Action', value: open, icon: 'AlertCircle', color: 'text-destructive', bg: 'bg-destructive/5' },
      { label: 'In Investigation', value: inProgress, icon: 'Clock', color: 'text-warning', bg: 'bg-warning/5' },
      { label: 'Resolution Set', value: resolved, icon: 'CheckCircle', color: 'text-success', bg: 'bg-success/5' }
    ];
  });

  // Actions
  async function loadDisputes() {
    loading.value = true;
    error.value = '';
    
    try {
      disputes.value = await p2pApi.getDisputes();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load disputes';
      console.error('Error loading disputes:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadDisputeById(disputeId: string) {
    loading.value = true;
    error.value = '';
    
    try {
      selectedDispute.value = await p2pApi.getDisputeById(disputeId);
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load dispute';
      console.error('Error loading dispute:', err);
    } finally {
      loading.value = false;
    }
  }

  async function resolveDispute(disputeId: string) {
    loading.value = true;
    error.value = '';
    
    try {
      const updatedDispute = await p2pApi.resolveDispute(disputeId, resolveForm.value);
      
      // Update local state
      const index = disputes.value.findIndex(d => d.id === disputeId);
      if (index !== -1) {
        disputes.value[index] = updatedDispute;
      }
      
      // Close dialog and reset form
      showResolveDialog.value = false;
      resetResolveForm();
      
      return updatedDispute;
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to resolve dispute';
      console.error('Error resolving dispute:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  function selectDispute(dispute: Dispute) {
    selectedDispute.value = dispute;
  }

  function openResolveDialog(dispute: Dispute) {
    selectedDispute.value = dispute;
    showResolveDialog.value = true;
    resolveForm.value.resolvedBy = '';
    resolveForm.value.notes = '';
  }

  function closeResolveDialog() {
    showResolveDialog.value = false;
    selectedDispute.value = null;
    resetResolveForm();
  }

  function resetResolveForm() {
    resolveForm.value = {
      status: 'resolved',
      notes: '',
      resolvedBy: '',
    };
  }

  function setStatusFilter(status: string) {
    statusFilter.value = status;
    currentPage.value = 1; // Reset to first page
  }

  function setSearchTerm(term: string) {
    searchTerm.value = term;
    currentPage.value = 1; // Reset to first page
  }

  function setCurrentPage(page: number) {
    if (page >= 1 && page <= totalPages.value) {
      currentPage.value = page;
    }
  }

  function clearError() {
    error.value = '';
  }

  // Initialize
  async function initialize() {
    await loadDisputes();
  }

  return {
    // State
    disputes,
    selectedDispute,
    statusFilter,
    searchTerm,
    currentPage,
    loading,
    error,
    showResolveDialog,
    resolveForm,
    
    // Getters
    filteredDisputes,
    paginatedDisputes,
    totalPages,
    stats,
    
    // Actions
    loadDisputes,
    loadDisputeById,
    resolveDispute,
    selectDispute,
    openResolveDialog,
    closeResolveDialog,
    resetResolveForm,
    setStatusFilter,
    setSearchTerm,
    setCurrentPage,
    clearError,
    initialize
  };
});
