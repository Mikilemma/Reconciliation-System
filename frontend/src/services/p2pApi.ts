import { apiService } from './api';

// Types
export interface Session {
  id: string;
  processedAt: string;
  status?: string;
  totalTransactions?: number;
  settledTransactions?: number;
  discrepantTransactions?: number;
}

export interface Transaction {
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
}

export interface Dispute {
  id: string;
  transactionId: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  disputeStatus: string;
  disputeReason: string;
  originalStatus: string;
  discrepancyType?: string;
  details?: string;
  resolutionNotes?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt?: string;
}

// Aggregated dispute - consolidates multiple records for same transaction
export interface AggregatedDispute {
  id: string;
  transactionRef?: string;
  stan?: string;
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
  switchData?: string;
  atmData?: string;
  payableData?: string;
  receivableData?: string;
  sourceFiles: string[];
  underlyingDisputeIds: string[];
  recordCount: number;
}

export interface Report {
  id: string;
  sessionId: string;
  settlementDate: string;
  totalAmount: number;
  settledAmount: number;
  status: string;
  generatedAt: string;
}

// Cross-session matching types
export interface CrossSessionMatch {
  id: string;
  originalTransactionId: string;
  originalSessionId: string;
  matchedTransactionId: string;
  matchedSessionId: string;
  matchType: 'exact' | 'fuzzy' | 'manual';
  confidence: number; // 0-100
  matchedAt: string;
  resolvedBy: 'system' | 'user';
}

export interface UploadResult {
  sessionId: string;
  totalProcessed: number;
  settled: number;
  discrepant: number;
  missing: number;
  duplicate: number;
  crossSessionMatches: CrossSessionMatch[];
  unresolvedTransactions: Transaction[];

  // Cross-session integration statistics
  integratedFromPreviousSessions?: number;
  exactMatchesIntegrated?: number;
  fuzzyMatchesIntegrated?: number;
  manualMatchesIntegrated?: number;
  averageMatchConfidence?: number;
  integratedTransactions?: IntegratedTransaction[];
}

export interface IntegratedTransaction {
  id: string;
  stan?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  originalSessionId: string;
  matchId: string;
  matchType: 'exact' | 'fuzzy' | 'manual';
  confidence: number;
  integrationType: string;
  integratedAt: string;
  status: string;
  notes: string;
}

export interface UnresolvedTransaction {
  id: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  status: 'discrepant' | 'missing' | 'duplicate';
  originalSessionId: string;
  createdAt: string;
  resolvedAt?: string;
  resolvedBySessionId?: string;
}

// P2P API Service
export class P2pApiService {
  // Sessions
  async getSessions() {
    return apiService.get<Session[]>('/api/p2p/reconciliation/sessions');
  }

  async getSessionById(sessionId: string) {
    return apiService.get<Session>(`/api/p2p/reconciliation/sessions/${sessionId}`);
  }

  // Transactions
  async getTransactions(sessionId: string) {
    return apiService.get<Transaction[]>(`/api/p2p/reconciliation/transactions/${sessionId}`);
  }

  async getAggregatedResults(sessionId: string) {
    return apiService.get<Transaction[]>(`/api/p2p/reconciliation/results/${sessionId}/aggregated`);
  }

  async getTransactionById(transactionId: string) {
    return apiService.get<Transaction>(`/api/p2p/reconciliation/transaction/${transactionId}`);
  }

  // File Upload
  async uploadSwitchFile(file: File, onProgress?: (progress: number) => void) {
    return apiService.uploadFile('/api/p2p/upload/switch', file, onProgress);
  }

  async uploadAtmFile(file: File, onProgress?: (progress: number) => void) {
    return apiService.uploadFile('/api/p2p/upload/atm', file, onProgress);
  }

  async uploadPayableFile(file: File, onProgress?: (progress: number) => void) {
    return apiService.uploadFile('/api/p2p/upload/payable', file, onProgress);
  }

  async uploadReceivableFile(file: File, onProgress?: (progress: number) => void) {
    return apiService.uploadFile('/api/p2p/upload/receivable', file, onProgress);
  }

  // Cross-Session Upload with Matching
  async uploadWithCrossSessionMatch(
    file: File,
    fileType: 'switch' | 'atm' | 'payable' | 'receivable',
    onProgress?: (progress: number) => void
  ) {
    return apiService.uploadFile<UploadResult>(
      `/api/p2p/cross-session-matches/upload/${fileType}/with-cross-session-match`,
      file,
      onProgress,
      {
        fileType,
        enableCrossSessionMatch: 'true'
      }
    );
  }

  // Cross-Session Matching
  async getCrossSessionMatches() {
    return apiService.get<CrossSessionMatch[]>('/api/p2p/cross-session-matches');
  }

  async getCrossSessionMatchesBySession(sessionId: string) {
    return apiService.get<CrossSessionMatch[]>(`/api/p2p/cross-session-matches/session/${sessionId}`);
  }

  async getUnresolvedTransactions() {
    return apiService.get<UnresolvedTransaction[]>('/api/p2p/unresolved-transactions');
  }

  async manualMatchTransactions(originalTransactionId: string, matchedTransactionId: string, notes?: string) {
    return apiService.post<CrossSessionMatch>('/api/p2p/cross-session-matches/manual', {
      originalTransactionId,
      matchedTransactionId,
      notes
    });
  }

  async rejectMatch(matchId: string, reason: string) {
    return apiService.put(`/api/p2p/cross-session-matches/${matchId}/reject`, { reason });
  }

  // Disputes
  async getDisputes() {
    return apiService.get<Dispute[]>('/api/p2p/disputes');
  }

  // Aggregated Disputes - consolidates duplicates by transactionRef
  async getAggregatedDisputes() {
    return apiService.get<AggregatedDispute[]>('/api/p2p/disputes/aggregated');
  }

  async getDisputeById(disputeId: string) {
    return apiService.get<Dispute>(`/api/p2p/disputes/${disputeId}`);
  }

  async resolveDispute(disputeId: string, resolutionData: {
    status: string;
    notes: string;
    resolvedBy: string;
  }) {
    return apiService.put<Dispute>(`/api/p2p/disputes/${disputeId}/resolve`, resolutionData);
  }

  // Resolve all underlying disputes in an aggregated dispute
  async resolveAggregatedDispute(disputeIds: string[], resolutionData: {
    newStatus: string;
    resolutionNotes: string;
    resolvedBy: string;
  }) {
    return apiService.put('/api/p2p/disputes/aggregated/status', {
      disputeIds,
      ...resolutionData
    });
  }

  // Reports
  async getReports() {
    return apiService.get<Report[]>('/api/p2p/reports');
  }

  async getReportById(reportId: string) {
    return apiService.get<Report>(`/api/p2p/reports/${reportId}`);
  }

  async getReportBySession(sessionId: string) {
    return apiService.get<Report>(`/api/p2p/reports/session/${sessionId}`);
  }

  async generateReport(sessionId: string) {
    return apiService.post<Report>(`/api/p2p/reports/generate/${sessionId}`);
  }

  // Dashboard
  async getDashboardStats() {
    return apiService.get<any>('/api/p2p/dashboard/stats');
  }

  async getRecentSettlements(limit = 10) {
    return apiService.get<Transaction[]>('/api/p2p/dashboard/recent-settlements', { limit });
  }
}

export const p2pApi = new P2pApiService();
