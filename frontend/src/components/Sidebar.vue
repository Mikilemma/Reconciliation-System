<script setup lang="ts">
import { ref, computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  Home,
  Settings,
  BarChart,
  GitMerge,
  FileUp,
  AlertTriangle,
  TrendingUp,
  Users,
  LayoutDashboard,
  Menu,
  X,
  Sun,
  Moon
} from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme';
import tsehayLogoUrl from '@/assets/branding/Tsehay logo side bar.png';

const route = useRoute()
const isCollapsed = ref(false)
const { currentTheme, setTheme } = useTheme(); // Use the theme composable

const p2pMenu = [
  {
    name: 'Dashboard',
    href: '/dashboard',
    icon: LayoutDashboard,
    description: 'Executive overview and recent settlements'
  },
  {
    name: 'File Upload',
    href: '/p2p/upload',
    icon: FileUp,
    description: 'Upload settlement files'
  },
  {
    name: 'Reconciliation',
    href: '/p2p/reconciliation',
    icon: GitMerge,
    description: 'Manage reconciliation process'
  },
  {
    name: 'Reports',
    href: '/p2p/reports',
    icon: BarChart,
    description: 'View reports and analytics'
  },
  {
    name: 'Disputes',
    href: '/p2p/disputes',
    icon: AlertTriangle,
    description: 'Handle disputed transactions'
  }
]

const ipsMenu = [
  {
    name: 'Transactions',
    href: '/ips/transactions',
    icon: TrendingUp,
    description: 'IPS transaction management'
  },
  {
    name: 'Participants',
    href: '/ips/participants',
    icon: Users,
    description: 'Manage IPS participants'
  }
]

const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
}


// Determine if the current theme is dark for the icon
// const isCurrentlyDark = computed(() => {
//   return currentTheme.value === 'dark' || (currentTheme.value === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
// });

const isActiveRoute = (href: string) => {
  return route.path === href
}
</script>
<template>
  <aside 
    class="app-sidebar flex-shrink-0 border-r border-primary/20 bg-primary transition-all duration-300"
    :class="isCollapsed ? 'w-20' : 'w-60'"
  >
    <div class="flex h-full flex-col">
      <!-- Header -->
      <div class="app-sidebar-header flex items-center gap-3 p-4 border-b border-primary/20 bg-primary">
        <div v-if="!isCollapsed" class="flex items-center min-w-0 flex-1">
          <img
            :src="tsehayLogoUrl"
            alt="Tsehay Bank"
            class="h-10 w-full max-w-[180px] object-contain"
          />
        </div>
        
        <div v-else class="flex justify-center w-full">
          <img
            :src="tsehayLogoUrl"
            alt="Tsehay Bank"
            class="h-8 w-8 object-contain"
          />
        </div>

        <div class="ml-auto flex items-center gap-2">
          <button
            type="button"
            @click="isCollapsed = !isCollapsed"
            class="h-8 w-8 p-0 bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground transition-colors duration-150 rounded-md"
          >
            <Menu v-if="isCollapsed" class="h-4 w-4" />
            <X v-else class="h-4 w-4" />
          </button>
        </div>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 overflow-y-auto p-4 space-y-6">
        <!-- P2P Section -->
        <div>
          <h3 v-if="!isCollapsed" class="mb-3 px-3 text-[10px] font-black uppercase tracking-widest text-warning/90">
            P2P Settlement
          </h3>
          <div v-else class="mb-3 flex justify-center">
            <div class="h-px w-8 bg-border/50"></div>
          </div>
          
          <ul class="space-y-1">
            <li v-for="item in p2pMenu" :key="item.name">
              <RouterLink 
                :to="item.href"
                class="group flex items-center rounded-xl px-3 py-2.5 text-sm font-semibold transition-colors duration-150 bg-primary text-primary-foreground"
                :class="{
                  'bg-warning text-warning-foreground ring-1 ring-warning/30': isActiveRoute(item.href),
                  'hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground': !isActiveRoute(item.href)
                }"
                :title="isCollapsed ? item.name : undefined"
              >
                <component 
                  :is="item.icon" 
                  class="h-4 w-4 flex-shrink-0 transition-transform group-hover:scale-110"
                  :class="{
                    'mr-3': !isCollapsed,
                    'mx-auto': isCollapsed,
                    'text-warning-foreground': isActiveRoute(item.href),
                    'text-primary-foreground group-hover:text-warning-foreground': !isActiveRoute(item.href)
                  }"
                />
                <span v-if="!isCollapsed" class="truncate">{{ item.name }}</span>
                
                <!-- Tooltip for collapsed state -->
                <div 
                  v-if="isCollapsed"
                  class="absolute left-full ml-4 hidden rounded-lg bg-popover px-3 py-2 text-xs font-bold text-popover-foreground shadow-xl border border-border group-hover:block z-50 whitespace-nowrap animate-in fade-in slide-in-from-left-2"
                >
                  <div class="text-foreground">{{ item.name }}</div>
                  <div class="text-[10px] text-muted-foreground font-medium">{{ item.description }}</div>
                </div>
              </RouterLink>
            </li>
          </ul>
        </div>

        <!-- IPS Section -->
        <div>
          <h3 v-if="!isCollapsed" class="mb-3 px-3 text-[10px] font-black uppercase tracking-widest text-warning/90">
            IPS System
          </h3>
          <div v-else class="mb-3 flex justify-center">
            <div class="h-px w-8 bg-border/50"></div>
          </div>
          
          <ul class="space-y-1">
            <li v-for="item in ipsMenu" :key="item.name">
              <RouterLink 
                :to="item.href"
                class="group flex items-center rounded-xl px-3 py-2.5 text-sm font-semibold transition-colors duration-150 bg-primary text-primary-foreground"
                :class="{
                  'bg-warning text-warning-foreground ring-1 ring-warning/30': isActiveRoute(item.href),
                  'hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground': !isActiveRoute(item.href)
                }"
                :title="isCollapsed ? item.name : undefined"
              >
                <component 
                  :is="item.icon" 
                  class="h-4 w-4 flex-shrink-0 transition-transform group-hover:scale-110"
                  :class="{
                    'mr-3': !isCollapsed,
                    'mx-auto': isCollapsed,
                    'text-warning-foreground': isActiveRoute(item.href),
                    'text-primary-foreground group-hover:text-warning-foreground': !isActiveRoute(item.href)
                  }"
                />
                <span v-if="!isCollapsed" class="truncate">{{ item.name }}</span>
                
                <!-- Tooltip for collapsed state -->
                <div 
                  v-if="isCollapsed"
                  class="absolute left-full ml-4 hidden rounded-lg bg-popover px-3 py-2 text-xs font-bold text-popover-foreground shadow-xl border border-border group-hover:block z-50 whitespace-nowrap animate-in fade-in slide-in-from-left-2"
                >
                  <div class="text-foreground">{{ item.name }}</div>
                  <div class="text-[10px] text-muted-foreground font-medium">{{ item.description }}</div>
                </div>
              </RouterLink>
            </li>
          </ul>
          
          <div v-if="!isCollapsed" class="mt-4 px-3">
            <div class="rounded-xl bg-primary/20 border border-warning/30 p-3">
              <p class="text-[10px] font-bold text-warning leading-tight uppercase tracking-wider">IPS Features</p>
              <p class="text-[9px] text-primary-foreground/80 mt-1">Expanding soon with real-time settlement rails.</p>
            </div>
          </div>
        </div>
      </nav>

      <!-- Footer -->
      <div class="app-sidebar-footer border-t border-primary/20 p-4 bg-primary">
        <div class="flex items-center justify-between gap-2">
          <RouterLink 
            to="/settings"
            class="group flex flex-1 items-center rounded-xl px-3 py-2.5 text-sm font-semibold transition-colors duration-150 bg-primary text-primary-foreground hover:bg-warning hover:text-warning-foreground focus-visible:bg-warning focus-visible:text-warning-foreground"
            :class="{'justify-center': isCollapsed}"
            :title="isCollapsed ? 'Settings' : undefined"
          >
            <Settings 
              class="h-4 w-4 flex-shrink-0 transition-transform group-hover:rotate-45"
              :class="{
                'mr-3': !isCollapsed,
                'mx-auto': isCollapsed
              }"
            />
            <span v-if="!isCollapsed">Settings</span>
          </RouterLink>
          

        </div>
      </div>
    </div>
  </aside>
</template>
