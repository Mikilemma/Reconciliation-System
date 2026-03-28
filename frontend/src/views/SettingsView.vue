<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Label } from '@/components/ui/label';
import { useTheme } from '@/composables/useTheme';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { apiService } from '@/services/api';

const { currentTheme, setTheme, initTheme } = useTheme();
const route = useRoute();
const router = useRouter();
const currentPassword = ref('');
const newPassword = ref('');
const changeError = ref('');
const changeSuccess = ref('');
const currentUserRole = ref('USER');

interface User {
  id: string;
  username: string;
  enabled?: boolean;
  locked?: boolean;
  role?: string;
}

const users = ref<User[]>([]);
const usersLoading = ref(false);
const usersError = ref('');
const newUsername = ref('');
const newUserPassword = ref('');
const userSuccess = ref('');
const activeSection = ref<'appearance' | 'security' | 'users'>('appearance');
const resetUserId = ref<string>('');
const resetPassword = ref<string>('');
const resetError = ref('');
const resetSuccess = ref('');

const isAdmin = computed(() => currentUserRole.value.toUpperCase() === 'ADMIN');

const syncSectionFromRoute = () => {
  const section = (route.query.section || '').toString().toLowerCase();
  if (section === 'users') {
    activeSection.value = isAdmin.value ? 'users' : 'security';
    return;
  }
  if (section === 'security') {
    activeSection.value = 'security';
    return;
  }
  activeSection.value = 'appearance';
};

const setSection = (section: 'appearance' | 'security' | 'users') => {
  if (section === 'users' && !isAdmin.value) {
    activeSection.value = 'security';
    router.replace({ query: { section: 'security' } });
    return;
  }
  activeSection.value = section;
  router.replace({ query: { section } });
};

const loadCurrentUser = async () => {
  try {
    const profile = await apiService.get<{ username: string; role?: string }>('/api/auth/me');
    currentUserRole.value = (profile?.role || 'USER').toUpperCase();
  } catch {
    currentUserRole.value = 'USER';
  }
};

// Initialize theme and role on mount
onMounted(async () => {
  initTheme();
  await loadCurrentUser();
  syncSectionFromRoute();
  if (isAdmin.value) {
    loadUsers();
  }
});

// Watch for theme changes and apply them
watch(currentTheme, (newTheme) => {
  setTheme(newTheme);
});

watch(() => route.query.section, () => {
  syncSectionFromRoute();
});

const changePassword = async () => {
  changeError.value = '';
  changeSuccess.value = '';
  if (!currentPassword.value.trim() || !newPassword.value.trim()) {
    changeError.value = 'Current and new password are required.';
    return;
  }
  try {
    await apiService.post('/api/auth/change-password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    currentPassword.value = '';
    newPassword.value = '';
    changeSuccess.value = 'Password updated.';
  } catch (err: any) {
    changeError.value = err.response?.data?.error || 'Failed to change password.';
  }
};

const loadUsers = async () => {
  if (!isAdmin.value) {
    users.value = [];
    usersLoading.value = false;
    usersError.value = '';
    return;
  }
  usersLoading.value = true;
  usersError.value = '';
  try {
    users.value = await apiService.get('/api/p2p/users');
  } catch (err: any) {
    usersError.value = err.response?.data?.error || 'Failed to load users.';
  } finally {
    usersLoading.value = false;
  }
};

const createUser = async () => {
  if (!isAdmin.value) {
    usersError.value = 'Only admin users can manage users.';
    return;
  }
  usersError.value = '';
  userSuccess.value = '';
  if (!newUsername.value.trim()) {
    usersError.value = 'Username is required.';
    return;
  }
  try {
    await apiService.post('/api/p2p/users', {
      username: newUsername.value.trim(),
      password: newUserPassword.value.trim() || undefined,
    });
    newUsername.value = '';
    newUserPassword.value = '';
    userSuccess.value = 'User created or already exists.';
    await loadUsers();
  } catch (err: any) {
    usersError.value = err.response?.data?.error || 'Failed to create user.';
  }
};

const beginReset = (userId: string) => {
  if (!isAdmin.value) {
    usersError.value = 'Only admin users can manage users.';
    return;
  }
  resetUserId.value = userId;
  resetPassword.value = '';
  resetError.value = '';
  resetSuccess.value = '';
};

const cancelReset = () => {
  resetUserId.value = '';
  resetPassword.value = '';
  resetError.value = '';
  resetSuccess.value = '';
};

const resetUserPassword = async () => {
  if (!isAdmin.value) {
    resetError.value = 'Only admin users can manage users.';
    return;
  }
  resetError.value = '';
  resetSuccess.value = '';
  if (!resetUserId.value || !resetPassword.value.trim()) {
    resetError.value = 'New password is required.';
    return;
  }
  try {
    await apiService.post(`/api/p2p/users/${resetUserId.value}/reset-password`, {
      newPassword: resetPassword.value.trim(),
    });
    resetSuccess.value = 'Password reset.';
    cancelReset();
  } catch (err: any) {
    resetError.value = err.response?.data?.error || 'Failed to reset password.';
  }
};

const updateUserStatus = async (user: User, updates: { enabled?: boolean; locked?: boolean }) => {
  if (!isAdmin.value) {
    usersError.value = 'Only admin users can manage users.';
    return;
  }
  usersError.value = '';
  try {
    await apiService.put(`/api/p2p/users/${user.id}/status`, updates);
    await loadUsers();
  } catch (err: any) {
    usersError.value = err.response?.data?.error || 'Failed to update user.';
  }
};

const deleteUser = async (user: User) => {
  if (!isAdmin.value) {
    usersError.value = 'Only admin users can manage users.';
    return;
  }
  usersError.value = '';
  try {
    await apiService.delete(`/api/p2p/users/${user.id}`);
    await loadUsers();
  } catch (err: any) {
    usersError.value = err.response?.data?.error || 'Failed to delete user.';
  }
};

const userCount = computed(() => users.value.length);
</script>

<template>
  <div class="container mx-auto p-6 space-y-8">
    <h1 class="text-3xl font-bold text-foreground mb-4">Settings</h1>
    <div class="grid grid-cols-1 lg:grid-cols-[220px_1fr] gap-6">
      <aside class="space-y-4">
        <button
          type="button"
          class="settings-nav-btn w-full text-left px-4 py-3 rounded-xl text-sm font-black tracking-wide border shadow-sm"
          :class="activeSection === 'appearance' ? 'settings-nav-btn--active' : ''"
          @click="setSection('appearance')"
        >
          Appearance
        </button>
        <button
          type="button"
          class="settings-nav-btn w-full text-left px-4 py-3 rounded-xl text-sm font-black tracking-wide border shadow-sm"
          :class="activeSection === 'security' ? 'settings-nav-btn--active' : ''"
          @click="setSection('security')"
        >
          Security
        </button>
        <button
          v-if="isAdmin"
          type="button"
          class="settings-nav-btn w-full text-left px-4 py-3 rounded-xl text-sm font-black tracking-wide border shadow-sm"
          :class="activeSection === 'users' ? 'settings-nav-btn--active' : ''"
          @click="setSection('users')"
        >
          User Management
        </button>
      </aside>

      <section class="space-y-6">
        <div v-if="activeSection === 'appearance'" class="space-y-4">
          <div class="space-y-2">
            <Label for="theme-select" class="text-foreground">Application Theme</Label>
            <select
              id="theme-select"
              v-model="currentTheme"
              class="flex h-10 w-48 items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm text-primary"
            >
              <option value="light">Light</option>
              <option value="dark">Dark</option>
              <option value="system">System</option>
              <option value="tsehay-bank-theme">Tsehay Bank Theme</option>
            </select>
          </div>
        </div>

        <div v-if="activeSection === 'security'" class="border border-border rounded-xl p-6 bg-card space-y-4 max-w-lg">
          <h2 class="text-lg font-bold text-foreground">Change Password</h2>
          <div>
            <Label class="text-foreground">Current Password</Label>
            <Input v-model="currentPassword" type="password" class="h-11 mt-2" />
          </div>
          <div>
            <Label class="text-foreground">New Password</Label>
            <Input v-model="newPassword" type="password" class="h-11 mt-2" />
          </div>
          <p v-if="changeError" class="text-xs text-destructive font-bold uppercase tracking-widest">{{ changeError }}</p>
          <p v-if="changeSuccess" class="text-xs text-success font-bold uppercase tracking-widest">{{ changeSuccess }}</p>
          <Button class="btn-primary h-11" @click="changePassword">Update Password</Button>
        </div>

        <div v-if="activeSection === 'users' && isAdmin" class="border border-border rounded-xl p-6 bg-card space-y-6">
          <div class="flex items-center justify-between">
            <div>
              <h2 class="text-lg font-bold text-foreground">User Management</h2>
              <p class="text-xs text-muted-foreground">Create and manage users ({{ userCount }})</p>
            </div>
            <Button variant="outline" class="h-10" @click="loadUsers" :disabled="usersLoading">
              {{ usersLoading ? 'Refreshing...' : 'Refresh' }}
            </Button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <Label class="text-foreground">Username</Label>
              <Input v-model="newUsername" class="h-11 mt-2" placeholder="e.g. auditor01" />
            </div>
            <div>
              <Label class="text-foreground">Password (optional)</Label>
              <Input v-model="newUserPassword" type="password" class="h-11 mt-2" placeholder="leave blank for change_me" />
            </div>
          </div>
          <div class="flex gap-3">
            <Button class="btn-primary h-11" @click="createUser">Add User</Button>
          </div>
          <p v-if="usersError" class="text-xs text-destructive font-bold uppercase tracking-widest">{{ usersError }}</p>
          <p v-if="userSuccess" class="text-xs text-success font-bold uppercase tracking-widest">{{ userSuccess }}</p>

          <div v-if="resetUserId" class="border border-border/60 rounded-lg p-4 bg-muted/20 space-y-3">
            <div class="text-xs font-bold uppercase tracking-widest text-muted-foreground">Reset Password</div>
            <Input v-model="resetPassword" type="password" class="h-11" placeholder="New password" />
            <div class="flex gap-2">
              <Button class="btn-primary h-10" @click="resetUserPassword">Confirm Reset</Button>
              <Button variant="outline" class="h-10" @click="cancelReset">Cancel</Button>
            </div>
            <p v-if="resetError" class="text-xs text-destructive font-bold uppercase tracking-widest">{{ resetError }}</p>
            <p v-if="resetSuccess" class="text-xs text-success font-bold uppercase tracking-widest">{{ resetSuccess }}</p>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-sm border border-border/60 rounded-lg overflow-hidden">
              <thead class="bg-muted/40 border-b border-border/60">
                <tr>
                  <th class="text-left p-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Username</th>
                  <th class="text-left p-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Status</th>
                  <th class="text-left p-3 text-[10px] font-black uppercase tracking-widest text-muted-foreground">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="usersLoading">
                  <td colspan="3" class="p-4 text-xs text-muted-foreground">Loading users...</td>
                </tr>
                <tr v-else-if="users.length === 0">
                  <td colspan="3" class="p-4 text-xs text-muted-foreground">No users found.</td>
                </tr>
                <tr v-else v-for="u in users" :key="u.id" class="border-t border-border/50">
                  <td class="p-3 font-semibold text-foreground">{{ u.username }}</td>
                  <td class="p-3 text-xs text-muted-foreground">
                    <span class="mr-3">Enabled: <strong>{{ u.enabled !== false ? 'Yes' : 'No' }}</strong></span>
                    <span>Locked: <strong>{{ u.locked ? 'Yes' : 'No' }}</strong></span>
                  </td>
                  <td class="p-3">
                    <div class="flex flex-wrap gap-2">
                      <Button variant="outline" class="h-8 px-3" @click="beginReset(u.id)">Reset Password</Button>
                      <Button
                        variant="outline"
                        class="h-8 px-3"
                        @click="updateUserStatus(u, { enabled: !(u.enabled !== false) })"
                      >
                        {{ u.enabled !== false ? 'Disable' : 'Enable' }}
                      </Button>
                      <Button
                        variant="outline"
                        class="h-8 px-3"
                        @click="updateUserStatus(u, { locked: !u.locked })"
                      >
                        {{ u.locked ? 'Unlock' : 'Lock' }}
                      </Button>
                      <Button
                        variant="outline"
                        class="h-8 px-3 text-destructive border-destructive/40 hover:bg-destructive/10"
                        @click="deleteUser(u)"
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings-nav-btn {
  background-color: rgb(var(--color-primary));
  border-color: rgb(var(--color-primary) / 0.6);
  color: rgb(var(--color-primary-foreground));
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
  margin-bottom: 12px;
}

.settings-nav-btn:last-child {
  margin-bottom: 0;
}

.settings-nav-btn:hover {
  background-color: rgb(var(--color-warning));
  border-color: rgb(var(--color-warning) / 0.75);
  color: rgb(var(--color-warning-foreground));
}

.settings-nav-btn--active {
  background-color: rgb(var(--color-accent));
  border-color: rgb(var(--color-accent) / 0.82);
  color: rgb(var(--color-primary-foreground));
}
</style>
