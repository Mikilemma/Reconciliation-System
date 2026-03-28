<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import axios from 'axios';
import { Button } from '@/components/ui/button/index';
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card/index';
import { Input } from '@/components/ui/input/index';
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from '@/components/ui/select';
import { UploadCloud, FileText, CheckCircle, AlertCircle, X, Search, ChevronUp, ChevronDown, Eye, Download, LayoutDashboard, RefreshCw } from 'lucide-vue-next';
import SummaryReportPopup from '@/components/p2p/SummaryReportPopup.vue';
import TransactionDetailsModal from '@/components/p2p/TransactionDetailsModal.vue';
import ReconciliationResultsTable from '@/components/p2p/ReconciliationResultsTable.vue';
import CrossSessionMatchNotification from '@/components/p2p/CrossSessionMatchNotification.vue';
import { useCrossSessionMatchingStore } from '@/stores/crossSessionMatching';

interface ReconciliationResult {
  id: string;
  sessionId: string;
  stan?: string;
  transactionRef?: string;
  amount?: number;
  transactionDate?: string;
  terminalId?: string;
  status: 'settled' | 'discrepant' | 'missing' | 'duplicate' | 'pending';
  details?: string;
  discrepancyType?: string;
  sourceFiles?: string;
  switchData?: string;
  atmData?: string;
  payableData?: string;
  receivableData?: string;
}

const selectedFiles = ref<File[]>([]);
const uploadStatus = ref<string>('');
const parsedData = ref<any>(null);
const isLoading = ref<boolean>(false);
const isDragging = ref<boolean>(false);
const uploadProgress = ref<number>(0);
const uploadError = ref<string>('');
const showSummaryPopup = ref<boolean>(false);
const currentSummaryData = ref<any>(null);
// Wizard State
const currentStep = ref<number>(1);
const totalSteps = 3;

// Transaction table state
const transactions = ref<ReconciliationResult[]>([]);
const transactionsLoading = ref<boolean>(false);
const selectedSession = ref<string>('');
const sessions = ref<Array<{id: string, settlementDate: string, processedAt: string}>>([]);
const showTransactionDetailsModal = ref<boolean>(false);
const selectedTransactionForDetails = ref<ReconciliationResult | null>(null);

const resultsTableRef = ref<any>(null);
const fileInput = ref<HTMLInputElement | null>(null);

// Cross-session matching
const crossSessionStore = useCrossSessionMatchingStore();
const enableCrossSessionMatching = ref<boolean>(true); // Default to enabled

const loadSessions = async () => {
  try {
    const response = await axios.get('/api/p2p/reconciliation/sessions');
    sessions.value = response.data.sort((a: any, b: any) => {
      return new Date(b.processedAt).getTime() - new Date(a.processedAt).getTime();
    });
    if (sessions.value.length > 0) {
      selectedSession.value = sessions.value[0]!.id;
      await loadTransactions(selectedSession.value);
    }
  } catch (err: any) {
    console.error('Error loading sessions:', err);
  }
};


const statusType = computed(() => {
  if (uploadStatus.value.includes('failed') || uploadError.value) return 'error';
  if (uploadStatus.value.includes('successfully')) return 'success';
  return 'info';
});

const statusIcon = computed(() => {
  switch (statusType.value) {
    case 'success': return CheckCircle;
    case 'error': return AlertCircle;
    default: return FileText;
  }
});

const transactionStatusCounts = computed(() => {
  const counts = {
    total: transactions.value.length,
    settled: 0,
    discrepant: 0,
    missing: 0,
    duplicate: 0,
    pending: 0
  };
  
  transactions.value.forEach(t => {
    if (t.status in counts) {
      counts[t.status as keyof typeof counts]++;
    }
  });
  
  return counts;
});

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const files = Array.from(target.files);
    files.forEach(file => processFile(file));
  }
};

const processFile = (file: File) => {
  const maxSize = 50 * 1024 * 1024; // 50MB
  
  if (file.size > maxSize) {
    uploadError.value = `File "${file.name}" is too large. Maximum size is 50MB.`;
    return false;
  }

  const validTypes = ['text/csv', 'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'];
  if (!validTypes.includes(file.type) && !file.name.toLowerCase().endsWith('.csv') && !file.name.toLowerCase().endsWith('.xls') && !file.name.toLowerCase().endsWith('.xlsx')) {
    uploadError.value = `File "${file.name}" has an unsupported format. Please upload CSV or Excel files.`;
    return false;
  }

  if (selectedFiles.value.some(f => f.name === file.name && f.size === file.size)) {
    uploadError.value = `File "${file.name}" has already been selected.`;
    return false;
  }

  selectedFiles.value.push(file);
  uploadError.value = '';
  uploadStatus.value = `File selected: ${file.name}`;
};

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const handleDragOver = (event: DragEvent) => {
  event.preventDefault();
  isDragging.value = true;
};

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault();
  isDragging.value = false;
};

const handleDrop = (event: DragEvent) => {
  event.preventDefault();
  isDragging.value = false;
  
  if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
    const files = Array.from(event.dataTransfer.files);
    files.forEach(file => processFile(file));
  }
};

const removeFile = (fileToRemove: File) => {
  const index = selectedFiles.value.findIndex(file => 
    file.name === fileToRemove.name && file.size === fileToRemove.size
  );
  
  if (index > -1) {
    selectedFiles.value.splice(index, 1);
    if (selectedFiles.value.length === 0) {
      uploadStatus.value = '';
      uploadError.value = '';
      parsedData.value = null;
    }
  }
};

const clearAllFiles = () => {
  selectedFiles.value = [];
  uploadStatus.value = '';
  uploadError.value = '';
  parsedData.value = null;
};

const uploadFiles = async () => {
  if (selectedFiles.value.length === 0) {
    uploadError.value = 'Please select files first.';
    return;
  }

  isLoading.value = true;
  uploadError.value = '';
  uploadProgress.value = 0;
  uploadStatus.value = 'Preparing upload...';
  
  try {
    let uploadResult = null;
    
    if (enableCrossSessionMatching.value && selectedFiles.value.length === 1) {
      // Use cross-session matching for single file uploads
      const file = selectedFiles.value[0]!;
      const fileType = getFileType(file.name);
      
      uploadStatus.value = `📤 Uploading ${file.name} with cross-session matching...`;
      uploadProgress.value = 10;
      
      uploadResult = await crossSessionStore.uploadWithCrossSessionMatch(
        file, 
        fileType, 
        (progress) => {
          uploadProgress.value = Math.min(progress, 90);
          uploadStatus.value = `📤 Uploading ${file.name}... ${progress}%`;
        }
      );
      
      uploadProgress.value = 95;
      uploadStatus.value = '🔄 Processing cross-session matches...';
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // Create parsedData structure for cross-session upload result
      parsedData.value = {
        sessionId: uploadResult.sessionId,
        files: [{
          filename: file.name,
          fileType: fileType.toUpperCase(),
          recordCount: uploadResult.totalProcessed || 0,
          records: uploadResult.unresolvedTransactions || [],
          sessionId: uploadResult.sessionId,
          uploadResult: uploadResult
        }]
      };
      
      uploadProgress.value = 100;
      uploadStatus.value = `✅ Upload completed! Found ${uploadResult.crossSessionMatches.length} cross-session matches.`;
    } else {
      // Use traditional upload for multiple files or when cross-session matching is disabled
      const allResponses = [];
      
      for (let i = 0; i < selectedFiles.value.length; i++) {
        const file = selectedFiles.value[i]!;
        const formData = new FormData();
        formData.append('file', file);
        
        const baseProgress = Math.round((i / selectedFiles.value.length) * 80);
        uploadStatus.value = `📤 Uploading file ${i + 1} of ${selectedFiles.value.length}: ${file.name}`;
        uploadProgress.value = baseProgress;
        
        const response = await axios.post('/api/p2p/files/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 300000, // 5 minute timeout for file uploads
          onUploadProgress: (progressEvent) => {
            if (progressEvent.total) {
              const fileProgress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
              const totalProgress = baseProgress + Math.round((fileProgress * 80) / (selectedFiles.value.length * 100));
              uploadProgress.value = Math.min(totalProgress, 90);
              uploadStatus.value = `📤 Uploading ${file.name}... ${fileProgress}%`;
            }
          }
        });
        
        allResponses.push(response.data);
        uploadProgress.value = Math.round(((i + 1) / selectedFiles.value.length) * 80);
      }
      
      uploadProgress.value = 85;
      uploadStatus.value = '🔄 Processing uploaded files...';
      await new Promise(resolve => setTimeout(resolve, 300));
      
      // Process traditional upload results...
      const combinedData = allResponses.reduce((acc, response, index) => {
        const fileType = getFileType(selectedFiles.value[index]!.name);
        
        return {
          ...acc,
          files: [...(acc.files || []), {
            filename: selectedFiles.value[index]?.name,
            fileType: fileType.toUpperCase(),
            recordCount: response.totalRecords || 0,
            records: response.records || [],
            sessionId: response.sessionId,
            parsedData: response
          }]
        };
      }, { files: [] });
      
      parsedData.value = combinedData;
      uploadProgress.value = 100;
      uploadStatus.value = '✅ All files uploaded successfully!';
    }
    
    // Move to next step after successful upload
    if (uploadResult || parsedData.value) {
      currentStep.value = 2;
    }
    
  } catch (error: any) {
    console.error('Upload error:', error);
    uploadError.value = error.response?.data?.message || error.message || 'Upload failed. Please try again.';
    uploadStatus.value = '';
  } finally {
    isLoading.value = false;
  }
};

// Helper function to determine file type based on filename
const getFileType = (filename: string): 'switch' | 'atm' | 'payable' | 'receivable' => {
  const lower = filename.toLowerCase();
  if (lower.includes('switch')) return 'switch';
  if (lower.includes('atm')) return 'atm';
  if (lower.includes('payable')) return 'payable';
  if (lower.includes('receivable')) return 'receivable';
  return 'switch'; // Default fallback
};

let reconciliationStatusTimer: number | null = null;

const confirmAndSave = async () => {
  if (!parsedData.value) {
    uploadError.value = 'No data to save.';
    return;
  }

  isLoading.value = true;
  uploadError.value = '';
  uploadStatus.value = 'Initializing...';

  try {
    const files = parsedData.value.files || [];
    const pickSettlementDate = () => {
      const byType = (type: string) => files.find((f: any) => f.detectedFileType === type && f.settlementDate)?.settlementDate;
      return byType('switch_transaction')
        || byType('summary_report')
        || files.find((f: any) => f.settlementDate)?.settlementDate
        || new Date().toISOString().split('T')[0];
    };
    const settlementDate = pickSettlementDate();
    const fileSessionIds = files
      .filter((f: any) => f.sessionId && (f.recordCount > 0 || f.detectedFileType === 'summary_report'))
      .map((f: any) => f.sessionId);
    
    if (fileSessionIds.length === 0) {
      uploadError.value = 'No valid files with records to process';
      return;
    }
    
    uploadStatus.value = '📋 Creating reconciliation session...';
    const response = await axios.post('/api/p2p/reconciliation/start', { 
      settlementDate,
      fileSessionIds
    }, { timeout: 60000 });
    const session = response.data;
    
    uploadStatus.value = '🔄 Matching Switch & ATM transactions...';
    await new Promise(resolve => setTimeout(resolve, 500)); // Brief pause for UI update
    
    uploadStatus.value = '💰 Matching Payable & Receivable records...';
    await new Promise(resolve => setTimeout(resolve, 500));
    
    uploadStatus.value = '🔍 Running reconciliation (this can take a few minutes)...';

    if (reconciliationStatusTimer) {
      clearTimeout(reconciliationStatusTimer);
    }
    reconciliationStatusTimer = window.setTimeout(() => {
      uploadStatus.value = '⏳ Still running—this may take a while for large datasets. You can check progress in the Session Record Library below.';
    }, 15000);

    // Cross-session matching can take time on large uploads.
    // We use a moderate timeout, but if it exceeds the timeout we still poll for results.
    const performTimeout = 120000; // 2 minutes
    let performTimedOut = false;

    try {
      await axios.post(`/api/p2p/reconciliation/${session.id}/perform`, {}, { timeout: performTimeout });
    } catch (err: any) {
      const isTimeout = err?.code === 'ECONNABORTED' || (err?.message && err.message.toLowerCase().includes('timeout'));
      if (isTimeout) {
        performTimedOut = true;
        uploadStatus.value = '⏳ Reconciliation is still running on the server; polling for results...';
      } else {
        throw err;
      }
    }

    if (reconciliationStatusTimer) {
      clearTimeout(reconciliationStatusTimer);
      reconciliationStatusTimer = null;
    }

    // Always try to load results (even if /perform timed out). Poll until we have data or max retries.
    uploadStatus.value = '📊 Loading reconciliation results...';
    let pollAttempts = 0;
    const maxPollAttempts = 24; // ~2 minutes of polling at 5s interval.
    const pollIntervalMs = 5000;

    while (pollAttempts < maxPollAttempts) {
      await loadTransactions(session.id);
      if (transactions.value.length > 0) {
        break;
      }
      pollAttempts += 1;
      uploadStatus.value = `⏳ Waiting for reconciliation results... (attempt ${pollAttempts}/${maxPollAttempts})`;
      await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
    }

    if (transactions.value.length === 0 && performTimedOut) {
      uploadStatus.value = '⚠️ Reconciliation is still processing, please check the Session Record Library in a few minutes.';
      uploadError.value = 'Reconciliation is still running on the server (request timed out). Please refresh the session list after a few minutes.';
      return;
    }

    uploadStatus.value = '📈 Generating settlement summary...';
    const summaryResponse = await axios.get(`/api/p2p/reconciliation/${session.id}/summary`);
    currentSummaryData.value = summaryResponse.data;
    showSummaryPopup.value = true;
    
    parsedData.value = null;
    selectedFiles.value = [];
    uploadProgress.value = 0;
    uploadStatus.value = '✅ Reconciliation completed successfully!';
    currentStep.value = 3;
  } catch (error: any) {
    const timeoutError = error?.code === 'ECONNABORTED' || (error?.message && error.message.toLowerCase().includes('timeout'));
    const baseMsg = error.response?.data?.message || error.response?.data?.error || error.message || 'Unknown error';
    const errorMsg = timeoutError
      ? 'Reconciliation took too long to complete. The server may still be processing. Please check the Session Record Library after a few minutes.'
      : `Save failed: ${baseMsg}`;

    uploadError.value = errorMsg;
    console.error('Confirm and save failed:', error);
    console.error('Error details:', error.response?.data);
  } finally {
    if (reconciliationStatusTimer) {
      clearTimeout(reconciliationStatusTimer);
      reconciliationStatusTimer = null;
    }
    isLoading.value = false;
  }
};

const loadTransactions = async (sessionId: string) => {
  transactionsLoading.value = true;
  try {
    const response = await axios.get(`/api/p2p/reconciliation/results/${sessionId}/aggregated`);
    transactions.value = response.data;
  } catch (error: any) {
    console.error('Failed to load transactions:', error);
    transactions.value = [];
  } finally {
    transactionsLoading.value = false;
  }
};

const formatRecordPreview = (records: unknown): string => {
  // Debug logging
  console.log('formatRecordPreview called with:', records);
  console.log('Array.isArray(records):', Array.isArray(records));
  console.log('records length:', Array.isArray(records) ? records.length : 'N/A');
  
  if (!records || (Array.isArray(records) && records.length === 0)) return 'No records available';
  try {
    const json = JSON.stringify(records, null, 2);
    return json.length > 500 ? `${json.substring(0, 500)}...` : json;
  } catch (error) {
    return 'Unable to display records';
  }
};

const viewTransactionDetails = (transaction: ReconciliationResult) => {
  selectedTransactionForDetails.value = transaction;
  showTransactionDetailsModal.value = true;
};

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'ETB',
    minimumFractionDigits: 2
  }).format(amount);
};

onMounted(() => {
  loadSessions();
});

// Handler for viewing cross-session matches
const handleViewMatches = () => {
  // Navigate to a dedicated cross-session matching view or show modal
  // For now, we'll just dismiss the notification
  crossSessionStore.dismissMatchNotification();
  // TODO: Navigate to cross-session matching page
  console.log('Navigate to cross-session matching view');
};
</script>

<template>
  <div class="container mx-auto p-6 max-w-6xl">
    <!-- Cross-Session Match Notification -->
    <CrossSessionMatchNotification
      :show="crossSessionStore.showMatchNotification"
      :upload-result="crossSessionStore.currentUploadResult"
      @dismiss="crossSessionStore.dismissMatchNotification"
      @view-matches="handleViewMatches"
    />

    <!-- Header -->
    <div class="mb-8 animate-fade-in flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <h1 class="text-3xl font-bold text-foreground mb-2">P2P File Upload</h1>
        <p class="text-foreground/70">Upload your settlement files to initiate the reconciliation process</p>
      </div>
      
      <!-- Stepper -->
      <div class="flex items-center gap-2 bg-muted/30 p-2 rounded-xl border border-border">
        <div v-for="step in totalSteps" :key="step" class="flex items-center">
          <div 
            class="h-10 w-10 rounded-full flex items-center justify-center text-sm font-bold transition-all duration-300 border-2"
            :class="currentStep >= step ? 'bg-primary border-primary text-primary-foreground shadow-lg' : 'bg-muted border-transparent text-muted-foreground'"
          >
            <CheckCircle v-if="currentStep > step" class="h-6 w-6" />
            <span v-else>{{ step }}</span>
          </div>
          <div v-if="step < totalSteps" class="h-1 w-8 mx-1 bg-muted rounded-full transition-all duration-300 overflow-hidden">
            <div class="h-full bg-primary transition-all duration-500" :style="{ width: currentStep > step ? '100%' : '0%' }"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Step 1: Selection -->
    <div v-if="currentStep === 1" class="animate-slide-up">
      <Card class="banking-card-elevated mb-6">
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-xl">
            <UploadCloud class="h-6 w-6 text-primary" />
            Step 1: Select Settlement Files
          </CardTitle>
          <CardDescription class="text-base">
            Drag and drop your Excel or CSV files here (Switch, ATM, Payable, Receivable)
          </CardDescription>
        </CardHeader>
        <CardContent>
          <!-- Cross-Session Matching Toggle - Moved outside upload zone -->
          <div class="mb-6 p-4 rounded-xl bg-primary/5 border border-primary/20">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg bg-primary/20">
                  <Search class="h-5 w-5 text-primary" />
                </div>
                <div class="text-left">
                  <h4 class="font-semibold text-primary">Cross-Session Matching</h4>
                  <p class="text-sm text-primary/70">Automatically match unresolved transactions from previous sessions</p>
                </div>
              </div>
              <label class="relative inline-flex items-center cursor-pointer" @click.stop>
                <input 
                  type="checkbox" 
                  v-model="enableCrossSessionMatching" 
                  class="sr-only peer"
                  :disabled="selectedFiles.length > 1"
                >
                <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary peer-disabled:opacity-50 peer-disabled:cursor-not-allowed"></div>
              </label>
            </div>
            <div v-if="selectedFiles.length > 1" class="mt-2 text-xs text-warning">
              ⚠️ Cross-session matching is only available for single file uploads
            </div>
          </div>

          <div
            class="upload-zone min-h-[300px] flex flex-col items-center justify-center border-2 border-dashed border-border rounded-2xl transition-all duration-300 cursor-pointer hover:border-primary/50 hover:bg-muted/30"
            :class="{ 'border-primary bg-primary/5': isDragging, 'bg-muted/20': selectedFiles.length > 0 }"
            @drop="handleDrop"
            @dragover="handleDragOver"
            @dragleave="handleDragLeave"
            @click="fileInput?.click()"
          >
            <input
              ref="fileInput"
              type="file"
              class="hidden"
              @change="handleFileChange"
              :disabled="isLoading"
              accept=".xlsx,.xls,.csv"
              multiple
            />
            
            <div v-if="selectedFiles.length === 0" class="upload-content text-center py-12">
              <div class="bg-primary/10 p-6 rounded-full inline-block mb-4 group-hover:scale-110 transition-transform">
                <UploadCloud class="h-12 w-12 text-primary" />
              </div>
              <h3 class="text-xl font-semibold mb-2">Select files for reconciliation</h3>
              <p class="text-muted-foreground mb-6">Drag & Drop or click to browse</p>
              
              <div class="flex flex-wrap justify-center gap-3">
                <div class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-muted border border-border text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  <FileText class="w-3.5 h-3.5" /> Multiple Files
                </div>
                <div class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-muted border border-border text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  <span class="w-1.5 h-1.5 rounded-full bg-primary"></span> Max 50MB Each
                </div>
              </div>
            </div>
            
            <div v-else class="selected-files-container w-full p-6">
              <div class="flex justify-between items-center mb-6">
                <h4 class="text-lg font-semibold flex items-center gap-2">
                  <CheckCircle class="h-5 w-5 text-success" />
                  Files Selected ({{ selectedFiles.length }})
                </h4>
                <Button variant="ghost" size="sm" @click.stop="clearAllFiles" class="text-destructive hover:bg-destructive/10">
                  <X class="h-4 w-4 mr-2" /> Clear All
                </Button>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div 
                  v-for="(file, index) in selectedFiles" 
                  :key="`${file.name}-${index}`"
                  class="flex items-center justify-between p-4 rounded-xl border border-border bg-card/50 shadow-sm group hover:border-primary/50 transition-colors"
                >
                  <div class="flex items-center gap-4 overflow-hidden">
                    <div class="p-2.5 rounded-lg bg-primary/5 text-primary">
                      <FileText class="h-6 w-6" />
                    </div>
                    <div class="overflow-hidden">
                      <p class="font-semibold text-sm truncate">{{ file.name }}</p>
                      <p class="text-xs text-muted-foreground tracking-tight">{{ formatFileSize(file.size) }}</p>
                    </div>
                  </div>
                  <Button variant="ghost" size="icon" @click.stop="removeFile(file)" class="opacity-40 group-hover:opacity-100 transition-opacity hover:text-destructive hover:bg-destructive/5">
                    <X class="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </div>
          </div>
          
          <!-- Errors -->
          <div v-if="uploadError" class="mt-4 p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-center gap-3 animate-shake">
            <AlertCircle class="h-5 w-5 flex-shrink-0" />
            <p class="text-sm font-medium">{{ uploadError }}</p>
          </div>
        </CardContent>
        <CardFooter class="flex justify-end pt-2">
          <Button @click="uploadFiles" :disabled="selectedFiles.length === 0 || isLoading" class="btn-primary min-w-[200px] h-11 text-base shadow-lg shadow-primary/20">
            <div v-if="isLoading" class="animate-spin h-5 w-5 mr-3 border-2 border-current border-t-transparent rounded-full" />
            Next: Preview & Upload
          </Button>
        </CardFooter>
      </Card>
    </div>

    <!-- Step 2: Preview & Confirm -->
    <div v-if="currentStep === 2" class="animate-slide-up">
      <Card class="banking-card-elevated mb-6">
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-xl">
            <CheckCircle class="h-6 w-6 text-primary" />
            Step 2: Preview & Confirm Data
          </CardTitle>
          <CardDescription class="text-base">Review the parsed data before starting reconciliation</CardDescription>
        </CardHeader>
        <CardContent>
          <!-- Upload Progress -->
          <div v-if="isLoading" class="mb-8">
            <div class="flex justify-between text-sm mb-3 font-semibold">
              <span class="flex items-center gap-2">
                <RefreshCw class="h-4 w-4 animate-spin text-primary" />
                {{ uploadStatus || 'Processing files...' }}
              </span>
              <span class="text-primary">{{ uploadProgress }}%</span>
            </div>
            <div class="w-full bg-muted rounded-full h-3 overflow-hidden shadow-inner border border-border">
              <div class="bg-primary h-full rounded-full transition-all duration-300 ease-out shadow-lg shadow-primary/50" :style="{ width: uploadProgress + '%' }" />
            </div>
            <div v-if="uploadProgress > 0" class="text-xs text-muted-foreground mt-2 text-center">
              {{ uploadProgress < 100 ? 'Upload in progress...' : 'Processing complete!' }}
            </div>
          </div>

          <!-- Parsed Data Cards -->
          <div v-if="parsedData && parsedData.files" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div v-for="(fileResponse, index) in parsedData.files" :key="index" class="overflow-hidden rounded-2xl border border-border bg-card/30 flex flex-col">
              <div class="p-4 bg-muted/20 border-b border-border flex justify-between items-center">
                <div class="overflow-hidden">
                  <h4 class="font-bold text-sm truncate" :title="fileResponse.filename">{{ fileResponse.filename }}</h4>
                  <p class="text-[10px] text-muted-foreground uppercase font-bold tracking-widest mt-0.5">{{ fileResponse.fileType }}</p>
                </div>
                <div class="flex-shrink-0 bg-success/10 text-success p-1 rounded-full border border-success/20">
                  <CheckCircle class="h-4 w-4" />
                </div>
              </div>
              <div class="p-4 flex-1 bg-card">
                 <div class="flex gap-4 mb-4">
                   <div class="flex-1 p-2 rounded-lg bg-muted/30 text-center border border-border/50">
                     <p class="text-lg font-bold">{{ fileResponse.recordCount || 0 }}</p>
                     <p class="text-[10px] text-muted-foreground uppercase font-bold">Records</p>
                   </div>
                   <div class="flex-1 p-2 rounded-lg bg-muted/30 text-center border border-border/50">
                     <p class="text-lg font-bold">{{ fileResponse.fileType?.split('_')[0] || 'DATA' }}</p>
                     <p class="text-[10px] text-muted-foreground uppercase font-bold">Category</p>
                   </div>
                 </div>
                 
                 <!-- Cross-Session Match Info (if available) -->
                 <div v-if="fileResponse.uploadResult && fileResponse.uploadResult.crossSessionMatches" class="mb-4 p-3 rounded-lg bg-primary/5 border border-primary/20">
                   <h5 class="text-[11px] font-bold text-primary uppercase mb-2">Cross-Session Matches</h5>
                   <div class="grid grid-cols-2 gap-2 text-xs">
                     <div>
                       <span class="text-muted-foreground">Found:</span>
                       <span class="font-bold text-primary ml-1">{{ fileResponse.uploadResult.crossSessionMatches.length }}</span>
                     </div>
                     <div>
                       <span class="text-muted-foreground">Integrated:</span>
                       <span class="font-bold text-success ml-1">{{ fileResponse.uploadResult.integratedFromPreviousSessions || 0 }}</span>
                     </div>
                   </div>
                 </div>
                 
                 <h5 class="text-[11px] font-bold text-muted-foreground uppercase mb-2">Raw Data Snapshot</h5>
                 <pre class="text-[10px] font-mono bg-muted/50 p-3 rounded-xl max-h-48 overflow-y-auto border border-border text-foreground/80 leading-relaxed">{{ formatRecordPreview(fileResponse.records) }}</pre>
              </div>
            </div>
          </div>
          
          <div v-else-if="!isLoading" class="text-center py-20 bg-muted/10 rounded-2xl border border-dashed border-border">
            <FileText class="mx-auto h-12 w-12 text-muted-foreground/30 mb-4" />
            <p class="text-muted-foreground font-medium">No data preview available.</p>
            <p class="text-sm text-muted-foreground mt-2">Please ensure the backend is returning parsed data from uploaded files.</p>
          </div>
        </CardContent>
        <CardFooter class="flex justify-between pt-6 border-t border-border/50 mt-4">
          <Button variant="ghost" @click="currentStep = 1" :disabled="isLoading" class="h-11">
             <X class="h-4 w-4 mr-2" /> Back to Files
          </Button>
          <Button @click="confirmAndSave" :disabled="!parsedData || isLoading" class="btn-primary min-w-[200px] h-11 text-base shadow-xl">
             <div v-if="isLoading" class="animate-spin h-5 w-5 mr-3 border-2 border-current border-t-transparent rounded-full" />
             Start Settlement Process
          </Button>
        </CardFooter>
      </Card>
    </div>

    <!-- Step 3: Results -->
    <div v-if="currentStep === 3" class="animate-slide-up">
      <!-- Quick Stats -->
      <div class="grid grid-cols-2 md:grid-cols-5 gap-4 mb-8">
        <div class="banking-card p-6 border-b-4 border-b-primary bg-primary/5">
          <p class="text-3xl font-black text-primary">{{ transactionStatusCounts.total }}</p>
          <p class="text-[10px] text-primary/60 font-black uppercase tracking-[0.2em]">Total Txns</p>
        </div>
        <div class="banking-card p-6 border-b-4 border-b-success bg-success/5">
          <p class="text-3xl font-black text-success">{{ transactionStatusCounts.settled }}</p>
          <p class="text-[10px] text-success/60 font-black uppercase tracking-[0.2em]">Settled</p>
        </div>
        <div class="banking-card p-6 border-b-4 border-b-destructive bg-destructive/5">
          <p class="text-3xl font-black text-destructive">{{ transactionStatusCounts.discrepant }}</p>
          <p class="text-[10px] text-destructive/60 font-black uppercase tracking-[0.2em]">Discrepant</p>
        </div>
        <div class="banking-card p-6 border-b-4 border-b-muted bg-muted/5">
          <p class="text-3xl font-black text-foreground/60">{{ transactionStatusCounts.missing }}</p>
          <p class="text-[10px] text-foreground/30 font-black uppercase tracking-[0.2em]">Missing</p>
        </div>
        <div class="banking-card p-6 border-b-4 border-b-muted bg-muted/5">
          <p class="text-3xl font-black text-foreground/60">{{ transactionStatusCounts.duplicate }}</p>
          <p class="text-[10px] text-foreground/30 font-black uppercase tracking-[0.2em]">Duplicates</p>
        </div>
      </div>

      <!-- Results Table -->
      <Card class="banking-card-elevated overflow-hidden border-none shadow-2xl mb-8">
        <CardHeader class="border-b bg-muted/10 p-6 flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle class="text-xl">Reconciliation Results</CardTitle>
            <CardDescription>Comprehensive verification records for the selected session</CardDescription>
          </div>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" @click="currentStep = 1" class="h-9">
              <RefreshCw class="h-4 w-4 mr-2" /> New Upload
            </Button>
            <Button size="sm" @click="showSummaryPopup = true" class="h-9">
              <FileText class="h-4 w-4 mr-2" /> View Report
            </Button>
          </div>
        </CardHeader>
        <CardContent class="p-0">
          <div class="p-6 pt-2">
            <ReconciliationResultsTable 
              ref="resultsTableRef"
              :transactions="transactions" 
              :loading="transactionsLoading"
              @view-details="viewTransactionDetails"
            />
          </div>
        </CardContent>
      </Card>
      
      <!-- Session History Area -->
      <div class="space-y-4 max-w-2xl mx-auto">
        <div class="flex items-center gap-2 px-2">
          <LayoutDashboard class="w-4 h-4 text-primary" />
          <h3 class="text-sm font-black uppercase tracking-widest text-muted-foreground">Session Record Library</h3>
        </div>
        <div class="banking-card p-3 rounded-2xl flex items-center gap-4 border border-border bg-card/30" v-if="sessions.length > 0">
          <Select v-model="selectedSession" @update:modelValue="loadTransactions">
            <SelectTrigger class="border-none bg-transparent shadow-none hover:bg-muted/30 focus:ring-0">
              <SelectValue placeholder="Access archived sessions" />
            </SelectTrigger>
            <SelectContent class="bg-card border-border rounded-xl">
              <SelectItem 
                v-for="session in sessions" 
                :key="session.id" 
                :value="session.id"
                class="hover:bg-primary/10 rounded-lg m-1"
              >
                <div class="flex flex-col">
                  <span class="font-bold">{{ session.settlementDate }}</span>
                  <span class="text-[10px] text-muted-foreground uppercase">{{ new Date(session.processedAt).toLocaleString() }}</span>
                </div>
              </SelectItem>
            </SelectContent>
          </Select>
          <div class="h-8 w-[1px] bg-border mx-2"></div>
          <Button @click="loadTransactions(selectedSession!)" variant="ghost" size="icon" :disabled="!selectedSession" class="h-10 w-10 text-primary">
            <RefreshCw class="h-5 w-5" />
          </Button>
        </div>
      </div>
    </div>

    <!-- Global Modals -->
    <SummaryReportPopup 
      v-if="showSummaryPopup"
      :show="showSummaryPopup"
      :summary-data="currentSummaryData"
      @close="showSummaryPopup = false"
    />

    <TransactionDetailsModal
      v-if="showTransactionDetailsModal && selectedTransactionForDetails"
      :show="showTransactionDetailsModal"
      :transaction="selectedTransactionForDetails"
      @close="showTransactionDetailsModal = false"
    />
  </div>
</template>
