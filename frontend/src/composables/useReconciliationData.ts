
import { ref } from 'vue'
import axios from 'axios'

interface ReconciliationResult {
  id: string
  sessionId: string
  stan?: string
  transactionRef?: string
  amount?: number
  transactionDate?: string
  terminalId?: string
  status: 'settled' | 'discrepant' | 'missing' | 'duplicate' | 'pending'
  details?: string
  discrepancyType?: string
  sourceFiles?: string
  switchData?: string
  atmData?: string
  payableData?: string
  receivableData?: string
}

export function useReconciliationData() {
  const isLoading = ref(true)
  const error = ref('')
  const sessions = ref<Array<{ id: string; settlementDate: string; processedAt: string }>>([])
  const selectedSessionId = ref<string>('')
  const transactions = ref<ReconciliationResult[]>([])

  const loadTransactions = async () => {
    if (!selectedSessionId.value) return

    isLoading.value = true
    error.value = ''

    try {
      const response = await axios.get(`/api/p2p/reconciliation/results/${selectedSessionId.value}/aggregated`)
      transactions.value = response.data
    } catch (err: any) {
      error.value = err.response?.data?.error || 'Failed to load transactions'
      transactions.value = []
    } finally {
      isLoading.value = false
    }
  }

  const loadTransactionsForSessions = async (sessionIds: string[]) => {
    if (!sessionIds.length) {
      transactions.value = []
      return
    }

    isLoading.value = true
    error.value = ''

    try {
      const responses = await Promise.all(
        sessionIds.map((sessionId) => axios.get(`/api/p2p/reconciliation/results/${sessionId}/aggregated`))
      )
      transactions.value = responses.flatMap((response) => response.data || [])
    } catch (err: any) {
      error.value = err.response?.data?.error || 'Failed to load transactions'
      transactions.value = []
    } finally {
      isLoading.value = false
    }
  }

  const loadSessions = async () => {
    try {
      const response = await axios.get('/api/p2p/reconciliation/sessions')
      sessions.value = response.data.sort((a: any, b: any) => {
        return new Date(b.processedAt).getTime() - new Date(a.processedAt).getTime()
      })

      if (sessions.value.length > 0) {
        selectedSessionId.value = sessions.value[0]!.id
        await loadTransactions()
      }
    } catch (err: any) {
      error.value = 'Failed to load dashboard data'
      console.error('Error loading sessions:', err)
    }
  }

  return {
    isLoading,
    error,
    sessions,
    selectedSessionId,
    transactions,
    loadSessions,
    loadTransactions,
    loadTransactionsForSessions,
  }
}
