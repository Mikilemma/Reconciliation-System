import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { p2pApi, type Report, type Session } from '@/services/p2pApi';

export const useReportsStore = defineStore('reports', () => {
  // State
  const reports = ref<Report[]>([]);
  const sessions = ref<Session[]>([]);
  const selectedDate = ref<string>('');
  const filteredSessions = ref<Session[]>([]);
  const selectedReport = ref<Report | null>(null);
  const loading = ref<boolean>(false);
  const error = ref<string>('');

  // Getters
  const reportsByDate = computed(() => {
    if (!selectedDate.value) return reports.value;
    
    return reports.value.filter(report => {
      const reportDate = new Date(report.settlementDate).toISOString().split('T')[0];
      return reportDate === selectedDate.value;
    });
  });

  const stats = computed(() => {
    const total = reports.value.length;
    const generated = reports.value.filter(r => r.status === 'generated').length;
    const pending = reports.value.filter(r => r.status === 'pending').length;
    const failed = reports.value.filter(r => r.status === 'failed').length;
    
    return {
      total,
      generated,
      pending,
      failed,
      successRate: total > 0 ? Math.round((generated / total) * 100) : 0
    };
  });

  // Actions
  async function loadReports() {
    loading.value = true;
    error.value = '';
    
    try {
      reports.value = await p2pApi.getReports();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load reports';
      console.error('Error loading reports:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadSessions() {
    loading.value = true;
    error.value = '';
    
    try {
      sessions.value = await p2pApi.getSessions();
      updateFilteredSessions();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load sessions';
      console.error('Error loading sessions:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadReportById(reportId: string) {
    loading.value = true;
    error.value = '';
    
    try {
      selectedReport.value = await p2pApi.getReportById(reportId);
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load report';
      console.error('Error loading report:', err);
    } finally {
      loading.value = false;
    }
  }

  async function loadReportBySession(sessionId: string) {
    loading.value = true;
    error.value = '';
    
    try {
      selectedReport.value = await p2pApi.getReportBySession(sessionId);
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load report for session';
      console.error('Error loading report by session:', err);
    } finally {
      loading.value = false;
    }
  }

  async function generateReport(sessionId: string) {
    loading.value = true;
    error.value = '';
    
    try {
      const newReport = await p2pApi.generateReport(sessionId);
      reports.value.push(newReport);
      return newReport;
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to generate report';
      console.error('Error generating report:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  function updateFilteredSessions() {
    if (!selectedDate.value) {
      filteredSessions.value = sessions.value;
      return;
    }
    
    filteredSessions.value = sessions.value.filter(session => {
      const sessionDate = new Date(session.processedAt).toISOString().split('T')[0];
      return sessionDate === selectedDate.value;
    });
  }

  function selectDate(date: string) {
    selectedDate.value = date;
    updateFilteredSessions();
  }

  function selectSession(sessionId: string) {
    // Load report for the selected session
    loadReportBySession(sessionId);
  }

  function clearError() {
    error.value = '';
  }

  // Initialize
  async function initialize() {
    await Promise.all([
      loadReports(),
      loadSessions()
    ]);
  }

  return {
    // State
    reports,
    sessions,
    selectedDate,
    filteredSessions,
    selectedReport,
    loading,
    error,
    
    // Getters
    reportsByDate,
    stats,
    
    // Actions
    loadReports,
    loadSessions,
    loadReportById,
    loadReportBySession,
    generateReport,
    updateFilteredSessions,
    selectDate,
    selectSession,
    clearError,
    initialize
  };
});
