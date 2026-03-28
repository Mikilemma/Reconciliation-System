<script setup lang="ts">
import { ref } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { Button } from '@/components/ui/button/index';
import { X, Download, Printer, CheckCircle, FileText } from 'lucide-vue-next';

const props = defineProps<{
  show: boolean;
  summaryData: any;
}>();

const emit = defineEmits(['close', 'generateReport']);
const router = useRouter();
const isGeneratingReport = ref(false);

const close = () => {
  emit('close');
};

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount);
};

const printReport = () => {
  window.print();
};

const generateFullReport = async () => {
  if (!props.summaryData?.sessionId) {
    return;
  }

  isGeneratingReport.value = true;
  try {
    // Navigate to reports page with session ID
    await router.push({
      path: '/p2p/reports',
      query: { sessionId: props.summaryData.sessionId }
    });
    emit('close');
  } catch (error) {
    console.error('Error generating report:', error);
  } finally {
    isGeneratingReport.value = false;
  }
};

const exportCSV = async () => {
  if (!props.summaryData?.sessionId) {
    return;
  }

  try {
    // Get settlement date from summary data
    // Prefer session-scoped endpoint to avoid ambiguity if multiple sessions exist for a date
    const sessionId = props.summaryData.sessionId;
    const response = await axios.get(`/api/p2p/reports/settlement/session/${sessionId}/export/csv`, {
      responseType: 'blob'
    });
    
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `Settlement Report ${sessionId}.csv`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Error exporting CSV:', error);
  }
};

const viewDetails = () => {
  if (!props.summaryData?.sessionId) return;
  router.push({
    path: '/p2p/reconciliation',
    query: { sessionId: props.summaryData.sessionId }
  });
  emit('close');
};
</script>

<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/95 backdrop-blur-sm animate-fade-in">
    <div class="bg-card border border-border shadow-2xl rounded-xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col animate-scale-up">
      <!-- Header -->
      <div class="p-6 border-b border-border flex items-center justify-between bg-muted/30">
        <div>
          <h2 class="text-2xl font-bold text-foreground">Member Net Position Summery Report</h2>
          <p class="text-sm text-foreground mt-1">Reconciliation Session: {{ summaryData.sessionId }}</p>
        </div>
        <Button variant="ghost" size="icon" @click="close" class="rounded-full">
          <X class="h-5 w-5" />
        </Button>
      </div>

      <!-- Content -->
      <div class="p-8 overflow-y-auto flex-1 h-full print:p-0">
        <div class="print-content space-y-8">
          <!-- Summary Header Information -->
          <div class="grid grid-cols-2 gap-4 text-sm mb-8 border-l-4 border-primary pl-4">
            <div>
              <p class="font-semibold text-foreground uppercase text-xs tracking-wider">Settlement</p>
              <p class="text-lg font-medium">3165</p>
            </div>
            <div>
              <p class="font-semibold text-foreground uppercase text-xs tracking-wider">Currency</p>
              <p class="text-lg font-medium">ETB (230)</p>
            </div>
            <div>
               <p class="font-semibold text-foreground uppercase text-xs tracking-wider">Institution</p>
               <p class="text-lg font-medium">0017 - Tsehay Bank</p>
            </div>
             <div>
               <p class="font-semibold text-foreground uppercase text-xs tracking-wider">Total Transactions</p>
               <p class="text-lg font-medium">{{ summaryData.summary.totalTransactions }}</p>
            </div>
          </div>

          <!-- Report Table -->
          <div class="border border-border rounded-lg overflow-hidden banking-card">
            <table class="w-full text-sm table-striped">
              <thead class="bg-muted/50 border-b border-border">
                <tr>
                  <th class="p-4 text-left font-semibold uppercase tracking-wider text-xs">Category</th>
                  <th class="p-4 text-right font-semibold uppercase tracking-wider text-xs">Debit Value</th>
                  <th class="p-4 text-right font-semibold uppercase tracking-wider text-xs">Credit Value</th>
                  <th class="p-4 text-right font-semibold uppercase tracking-wider text-xs">Net Value</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border">
                <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">ATM CW Transaction Amount</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.atmCwAmount) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.atmCwAmount) }})</td>
                </tr>
                <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">POS PUR THEM-ON-THEM</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.posPurAmount) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.posPurAmount) }})</td>
                </tr>
                <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">On-Us Balance Inquiry Commission to EthSwitch</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.onUsBalanceInquiryCommissionToEthSwitch) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.onUsBalanceInquiryCommissionToEthSwitch) }})</td>
                </tr>
                <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">Remote On-Us EPOS Purchase + Commission</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.remoteOnUsEposPurchasePlusCommission) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.remoteOnUsEposPurchasePlusCommission) }})</td>
                </tr>
                 <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">ATM Withdrawal FEE</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.atmWithdrawalFee) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.atmWithdrawalFee) }})</td>
                </tr>
                 <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">POS Purchase Acq. FEE</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.posPurchaseFee) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.posPurchaseFee) }})</td>
                </tr>
                 <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">ATM Balance Inquiry FEE</td>
                  <td class="p-4 text-right text-destructive">{{ formatCurrency(summaryData.summary.atmBalanceInquiryFee) }}</td>
                  <td class="p-4 text-right text-success">-</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.atmBalanceInquiryFee) }})</td>
                </tr>
                <tr class="hover:bg-muted/30 transition-colors">
                  <td class="p-4 font-medium">Remote On-Us Dispute Chargeback Amount &amp; Commission</td>
                  <td class="p-4 text-right text-destructive">-</td>
                  <td class="p-4 text-right text-success">{{ formatCurrency(summaryData.summary.remoteOnUsDisputeChargebackAmountCommission) }}</td>
                  <td class="p-4 text-right font-mono">({{ formatCurrency(summaryData.summary.remoteOnUsDisputeChargebackAmountCommission) }})</td>
                </tr>
              </tbody>
              <tfoot class="bg-muted/20 font-bold border-t border-border">
                <tr>
                   <td class="p-4">TOTALS</td>
                   <td class="p-4 text-right text-destructive">
                     {{ formatCurrency(
                     summaryData.summary.atmCwAmount +
                     summaryData.summary.posPurAmount +
                     summaryData.summary.onUsBalanceInquiryCommissionToEthSwitch +
                     summaryData.summary.remoteOnUsEposPurchasePlusCommission +
                     summaryData.summary.atmWithdrawalFee +
                     summaryData.summary.posPurchaseFee +
                     summaryData.summary.atmBalanceInquiryFee
                   ) }}
                   </td>
                   <td class="p-4 text-right text-success">-</td>
                   <td class="p-4 text-right font-mono">
                     ({{ formatCurrency(
                     summaryData.summary.atmCwAmount +
                     summaryData.summary.posPurAmount +
                     summaryData.summary.onUsBalanceInquiryCommissionToEthSwitch +
                     summaryData.summary.remoteOnUsEposPurchasePlusCommission +
                     summaryData.summary.atmWithdrawalFee +
                     summaryData.summary.posPurchaseFee +
                     summaryData.summary.atmBalanceInquiryFee
                   ) }})
                   </td>
                </tr>
              </tfoot>
            </table>
          </div>

          <!-- Bottom Summary -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mt-8">
            <div class="p-6 rounded-xl bg-primary/5 border border-primary/10">
              <h4 class="text-sm font-semibold text-primary uppercase tracking-wider mb-4">Payment & Settlement - Net</h4>
              <div class="space-y-3">
                <div class="flex justify-between items-center pb-2 border-b border-primary/10">
                  <span class="text-foreground">Amount</span>
                  <span class="font-medium">-</span>
                </div>
              </div>
            </div>
            <div class="p-6 rounded-xl bg-success/5 border border-success/10 flex flex-col justify-center items-center text-center">
               <CheckCircle class="h-12 w-12 text-success mb-3 opacity-50" />
               <p class="text-sm text-foreground">Ethswitch Receivable-Total</p>
               <p class="text-3xl font-bold text-success mt-1">{{ formatCurrency(summaryData.summary.atmCwAmount) }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="p-6 border-t border-border flex justify-between items-center bg-muted/30">
        <Button 
          variant="outline" 
          @click="generateFullReport" 
          :disabled="isGeneratingReport || !summaryData?.sessionId"
          class="flex items-center gap-2"
        >
          <FileText v-if="!isGeneratingReport" class="h-4 w-4" />
          <div v-else class="animate-spin h-4 w-4 border-2 border-current border-t-transparent rounded-full" />
          {{ isGeneratingReport ? 'Generating...' : 'Generate Full Report' }}
        </Button>
        
        <div class="flex gap-3">
          <Button variant="outline" @click="printReport" class="flex items-center gap-2">
            <Printer class="h-4 w-4" />
            Print
          </Button>
          <Button variant="outline" @click="exportCSV" class="flex items-center gap-2">
            <Download class="h-4 w-4" />
            Export CSV
          </Button>
          <Button @click="close" class="px-8">
            Done
          </Button>
          <Button @click="viewDetails" variant="secondary" class="px-6">
            View Details
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@media print {
  .fixed {
    position: relative !important;
    background: white !important;
    backdrop-filter: none !important;
  }
  .bg-card {
    box-shadow: none !important;
    border: none !important;
    max-height: none !important;
  }
  .print-hide {
    display: none !important;
  }
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}

.animate-scale-up {
  animation: scaleUp 0.3s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes scaleUp {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.banking-card {
  border: 1px solid rgb(var(--color-border));
  box-shadow: var(--shadow-card);
  background: linear-gradient(to bottom right, rgb(var(--color-card)), rgb(var(--color-muted) / 0.3));
}
</style>
