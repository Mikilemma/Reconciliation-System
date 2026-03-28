import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { p2pApi, type Session, type Transaction } from '@/services/p2pApi';

export const useReconciliationStore = defineStore('reconciliation', () => {
  // State
  const sessions = ref<Session[]>([]);
  const transactions = ref<Transaction[]>([]);
  const selectedSessionId = ref<string>('');
  const selectedDate = ref<string>('');
  const loading = ref<boolean>(false);
  const error = ref<string>('');

  // Getters
  const selectedSession = computed(() =>
    sessions.value.find(session => session.id === selectedSessionId.value)
  );

  const filteredSessions = computed(() => {
    if (!selectedDate.value) return sessions.value;

    return sessions.value.filter(session => {
      const sessionDate = new Date(session.processedAt).toISOString().split('T')[0] ?? '';
      return sessionDate === selectedDate.value;
    });
  });

  const stats = computed(() => {
    const total = transactions.value.length;
    const reversal = transactions.value.filter(t => t.status === 'reversal').length;
    const settled = transactions.value.filter(t =>
      t.status === 'settled' ||
      t.status === 'reversal' ||
      t.status === 'Transfer In' ||
      t.status === 'Transfer Out'
    ).length;
    const discrepant = transactions.value.filter(t => t.status === 'discrepant').length;
    const pending = transactions.value.filter(t => t.status === 'pending').length;

    return {
      total,
      reversal,
      settled,
      discrepant,
      pending,
      settlementRate: total > 0 ? Math.round((settled / total) * 100) : 0
    };
  });

  // Actions
  async function loadSessions() {
    loading.value = true;
    error.value = '';

    try {
      sessions.value = await p2pApi.getSessions();

      // Auto-select first session if none selected
      if (!selectedSessionId.value && sessions.value.length > 0 && sessions.value[0]) {
        selectedSessionId.value = sessions.value[0].id;
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load sessions';
      console.error('Error loading sessions:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadTransactions(sessionId?: string) {
    const id = sessionId || selectedSessionId.value;
    if (!id) return;

    loading.value = true;
    error.value = '';

    try {
      if (id === 'all') {
        if (sessions.value.length === 0) {
          await loadSessions();
        }

        const sessionsToLoad = selectedDate.value
          ? sessions.value.filter(session => {
            const sessionDate = new Date(session.processedAt).toISOString().split('T')[0] ?? '';
            return sessionDate === selectedDate.value;
          })
          : sessions.value;

        if (sessionsToLoad.length === 0) {
          transactions.value = [];
          return;
        }

        const results = await Promise.all(
          sessionsToLoad.map(session => p2pApi.getAggregatedResults(session.id))
        );
        transactions.value = results.flat();
      } else {
        transactions.value = await p2pApi.getAggregatedResults(id);
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load transactions';
      console.error('Error loading transactions:', err);
    } finally {
      loading.value = false;
    }
  }

  function selectSession(sessionId: string) {
    selectedSessionId.value = sessionId;

    if (sessionId !== 'all') {
      // Auto-set date to match selected session
      const session = sessions.value.find(s => s.id === sessionId);
      if (session && session.processedAt) {
        const dateStr = new Date(session.processedAt).toISOString().split('T')[0] ?? '';
        selectedDate.value = dateStr || '';
      }
    }

    // Load transactions for selected session
    loadTransactions(sessionId);
  }

  function selectDate(date: string) {
    selectedDate.value = date;
  }

  function clearError() {
    error.value = '';
  }

  // Initialize
  async function initialize() {
    await loadSessions();
    if (selectedSessionId.value) {
      // Auto-set date to match first session
      const session = sessions.value.find(s => s.id === selectedSessionId.value);
      if (session && session.processedAt) {
        selectedDate.value = new Date(session.processedAt).toISOString().split('T')[0] ?? '';
      }
      await loadTransactions(selectedSessionId.value);
    }
  }

  return {
    // State
    sessions,
    transactions,
    selectedSessionId,
    selectedDate,
    loading,
    error,

    // Getters
    selectedSession,
    filteredSessions,
    stats,

    // Actions
    loadSessions,
    loadTransactions,
    selectSession,
    selectDate,
    clearError,
    initialize
  };
});
