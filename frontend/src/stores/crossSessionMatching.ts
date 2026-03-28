import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { p2pApi, type CrossSessionMatch, type UnresolvedTransaction, type UploadResult } from '@/services/p2pApi';

export const useCrossSessionMatchingStore = defineStore('crossSessionMatching', () => {
  // State
  const crossSessionMatches = ref<CrossSessionMatch[]>([]);
  const unresolvedTransactions = ref<UnresolvedTransaction[]>([]);
  const currentUploadResult = ref<UploadResult | null>(null);
  const loading = ref<boolean>(false);
  const error = ref<string>('');
  const showMatchNotification = ref<boolean>(false);
  const manualMatchingMode = ref<boolean>(false);
  const selectedTransaction = ref<UnresolvedTransaction | null>(null);
  const potentialMatches = ref<CrossSessionMatch[]>([]);

  // Getters
  const matchesByConfidence = computed(() => {
    return [...crossSessionMatches.value].sort((a, b) => b.confidence - a.confidence);
  });

  const matchesByType = computed(() => {
    const grouped = crossSessionMatches.value.reduce((acc, match) => {
      const type = match.matchType || 'unknown';
      if (!acc[type]) {
        acc[type] = [];
      }
      acc[type].push(match);
      return acc;
    }, {} as Record<string, CrossSessionMatch[]>);

    return grouped;
  });

  const recentMatches = computed(() => {
    const oneWeekAgo = new Date();
    oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);

    return crossSessionMatches.value.filter(match => 
      new Date(match.matchedAt) >= oneWeekAgo
    );
  });

  const unresolvedByStatus = computed(() => {
    const grouped = unresolvedTransactions.value.reduce((acc, transaction) => {
      const status = transaction.status || 'unknown';
      if (!acc[status]) {
        acc[status] = [];
      }
      acc[status].push(transaction);
      return acc;
    }, {} as Record<string, UnresolvedTransaction[]>);

    return grouped;
  });

  const stats = computed(() => {
    const totalUnresolved = unresolvedTransactions.value.length;
    const totalMatches = crossSessionMatches.value.length;
    const exactMatches = crossSessionMatches.value.filter(m => m.matchType === 'exact').length;
    const fuzzyMatches = crossSessionMatches.value.filter(m => m.matchType === 'fuzzy').length;
    const manualMatches = crossSessionMatches.value.filter(m => m.matchType === 'manual').length;

    return {
      totalUnresolved,
      totalMatches,
      exactMatches,
      fuzzyMatches,
      manualMatches,
      matchRate: totalUnresolved > 0 ? Math.round((totalMatches / totalUnresolved) * 100) : 0
    };
  });

  // Actions
  async function loadCrossSessionMatches() {
    loading.value = true;
    error.value = '';
    
    try {
      crossSessionMatches.value = await p2pApi.getCrossSessionMatches();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load cross-session matches';
      console.error('Error loading cross-session matches:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadUnresolvedTransactions() {
    loading.value = true;
    error.value = '';
    
    try {
      unresolvedTransactions.value = await p2pApi.getUnresolvedTransactions();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load unresolved transactions';
      console.error('Error loading unresolved transactions:', err);
    } finally {
      loading.value = false;
    }
  }

  async function uploadWithCrossSessionMatch(file: File, fileType: 'switch' | 'atm' | 'payable' | 'receivable', onProgress?: (progress: number) => void) {
    loading.value = true;
    error.value = '';
    currentUploadResult.value = null;
    
    try {
      const result = await p2pApi.uploadWithCrossSessionMatch(file, fileType, onProgress);
      currentUploadResult.value = result;
      
      // Show notification if matches were found
      if (result.crossSessionMatches.length > 0) {
        showMatchNotification.value = true;
        
        // Add new matches to the store
        crossSessionMatches.value.push(...result.crossSessionMatches);
      }
      
      // Update unresolved transactions - convert Transaction to UnresolvedTransaction
      if (result.unresolvedTransactions.length > 0) {
        const unresolvedTx: UnresolvedTransaction[] = result.unresolvedTransactions.map(tx => ({
          id: tx.id,
          stan: tx.stan,
          transactionRef: tx.transactionRef,
          amount: tx.amount,
          transactionDate: tx.transactionDate,
          terminalId: tx.terminalId,
          status: tx.status === 'discrepant' || tx.status === 'missing' || tx.status === 'duplicate' ? tx.status : 'discrepant',
          originalSessionId: tx.sessionId,
          createdAt: new Date().toISOString()
        }));
        unresolvedTransactions.value.push(...unresolvedTx);
      }
      
      return result;
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to upload file with cross-session matching';
      console.error('Error uploading file:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function manualMatchTransactions(originalTransactionId: string, matchedTransactionId: string, notes?: string) {
    loading.value = true;
    error.value = '';
    
    try {
      const match = await p2pApi.manualMatchTransactions(originalTransactionId, matchedTransactionId, notes);
      
      // Add the new match to the store
      crossSessionMatches.value.push(match);
      
      // Remove the unresolved transaction if it was resolved
      const index = unresolvedTransactions.value.findIndex(t => t.id === originalTransactionId);
      if (index !== -1) {
        unresolvedTransactions.value.splice(index, 1);
      }
      
      // Exit manual matching mode
      manualMatchingMode.value = false;
      selectedTransaction.value = null;
      potentialMatches.value = [];
      
      return match;
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to create manual match';
      console.error('Error creating manual match:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function rejectMatch(matchId: string, reason: string) {
    loading.value = true;
    error.value = '';
    
    try {
      await p2pApi.rejectMatch(matchId, reason);
      
      // Remove the match from the store
      const index = crossSessionMatches.value.findIndex(m => m.id === matchId);
      if (index !== -1) {
        crossSessionMatches.value.splice(index, 1);
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to reject match';
      console.error('Error rejecting match:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  function startManualMatching(transaction: UnresolvedTransaction) {
    selectedTransaction.value = transaction;
    manualMatchingMode.value = true;
    
    // Find potential matches for this transaction
    potentialMatches.value = crossSessionMatches.value.filter(match => 
      match.originalTransactionId === transaction.id ||
      match.matchedTransactionId === transaction.id
    );
  }

  function exitManualMatching() {
    manualMatchingMode.value = false;
    selectedTransaction.value = null;
    potentialMatches.value = [];
  }

  function dismissMatchNotification() {
    showMatchNotification.value = false;
  }

  function clearError() {
    error.value = '';
  }

  // Initialize
  async function initialize() {
    await Promise.all([
      loadCrossSessionMatches(),
      loadUnresolvedTransactions()
    ]);
  }

  return {
    // State
    crossSessionMatches,
    unresolvedTransactions,
    currentUploadResult,
    loading,
    error,
    showMatchNotification,
    manualMatchingMode,
    selectedTransaction,
    potentialMatches,
    
    // Getters
    matchesByConfidence,
    matchesByType,
    recentMatches,
    unresolvedByStatus,
    stats,
    
    // Actions
    loadCrossSessionMatches,
    loadUnresolvedTransactions,
    uploadWithCrossSessionMatch,
    manualMatchTransactions,
    rejectMatch,
    startManualMatching,
    exitManualMatching,
    dismissMatchNotification,
    clearError,
    initialize
  };
});
