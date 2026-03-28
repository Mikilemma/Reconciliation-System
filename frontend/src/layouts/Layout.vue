<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import { apiService } from '@/services/api'
import { ChevronDown, LogOut, KeyRound, User } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const currentUser = ref<string>('')
const menuOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const showProfileMenu = computed(() => route.path === '/dashboard')
type AuthMeResponse = { username?: string; role?: string }

const loadCurrentUser = async () => {
  try {
    const response = await apiService.get<AuthMeResponse>('/api/auth/me')
    currentUser.value = response?.username || ''
  } catch {
    currentUser.value = ''
  }
}

const logout = async () => {
  try {
    await apiService.post('/api/auth/logout')
  } finally {
    router.push('/login')
  }
}

const handleDocumentClick = (event: MouseEvent) => {
  const target = event.target as Node | null
  if (menuRef.value && target && !menuRef.value.contains(target)) {
    menuOpen.value = false
  }
}

onMounted(() => {
  loadCurrentUser()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <div class="flex h-screen bg-background text-foreground">
    <Sidebar />
    <main class="flex-1 overflow-y-auto">
      <div class="relative p-8 isolate">
        <div v-if="showProfileMenu" ref="menuRef" class="absolute right-8 top-6 z-50">
          <button
            type="button"
            class="flex items-center gap-2 rounded-full border border-warning/30 bg-primary text-primary-foreground px-2 py-1 text-xs font-bold shadow-md hover:bg-warning hover:text-warning-foreground transition-colors"
            @click="menuOpen = !menuOpen"
          >
            <span class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-warning/90 text-warning-foreground">
              <User class="h-3.5 w-3.5" />
            </span>
            <span class="max-w-[120px] truncate">{{ currentUser || 'User' }}</span>
            <ChevronDown class="h-3.5 w-3.5" />
          </button>

          <div
            v-if="menuOpen"
            class="app-sidebar absolute right-0 mt-2 w-56 rounded-xl border border-warning/30 text-primary-foreground shadow-2xl overflow-hidden z-50"
            :style="{ backgroundColor: 'rgb(var(--color-primary))', transform: 'translateX(8px)' }"
          >
            <div
              class="app-sidebar px-4 py-3 border-b border-warning/30"
              :style="{ backgroundColor: 'rgb(var(--color-primary))' }"
            >
              <div class="text-[10px] uppercase tracking-widest text-warning">Signed In</div>
              <div class="text-sm font-black truncate text-primary-foreground">
                {{ currentUser || 'Unknown User' }}
              </div>
            </div>
            <button
              type="button"
              class="w-full flex items-center gap-2 px-4 py-3 text-sm font-semibold text-primary-foreground hover:bg-warning hover:text-warning-foreground"
              @click="menuOpen = false; router.push('/settings')"
            >
              <KeyRound class="h-4 w-4" />
              Change Password
            </button>
            <button
              type="button"
              class="w-full flex items-center gap-2 px-4 py-3 text-sm font-semibold text-primary-foreground hover:bg-warning hover:text-warning-foreground"
              @click="logout"
            >
              <LogOut class="h-4 w-4" />
              Logout
            </button>
          </div>
        </div>

        <RouterView />
      </div>
    </main>
  </div>
</template>
