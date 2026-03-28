<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Eye, EyeOff } from 'lucide-vue-next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { apiService } from '@/services/api';
import loginBg from '@/assets/branding/BackgroudImage.png';
import tsehayLogo from '@/assets/branding/Tsehay logo11.PNG';

const router = useRouter();
const username = ref('');
const password = ref('');
const loading = ref(false);
const error = ref('');
const showPassword = ref(false);
const capsLockOn = ref(false);

const handlePasswordKeyEvent = (event: KeyboardEvent) => {
  capsLockOn.value = event.getModifierState?.('CapsLock') ?? false;
};

const login = async () => {
  error.value = '';
  if (!username.value.trim() || !password.value.trim()) {
    error.value = 'Username and password are required.';
    return;
  }
  loading.value = true;
  try {
    await apiService.post('/api/auth/login', {
      username: username.value.trim(),
      password: password.value,
    });
    await apiService.get('/api/auth/csrf');
    await router.push('/dashboard');
  } catch (err: any) {
    if (err.response?.status === 403) {
      error.value = 'Security validation failed. Please refresh and try again.';
    } else {
      error.value = err.response?.data?.error || 'Invalid username or password.';
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <section class="background-radial-gradient overflow-hidden min-h-screen relative" :style="{ backgroundImage: `url(${loginBg})` }">
    <div class="login-overlay"></div>
    <div class="container px-6 py-0 mx-auto h-screen">
      <div class="grid lg:grid-cols-2 gap-10 items-center h-full relative z-10">
        <div class="text-left">
          <p class="text-[11px] uppercase tracking-[0.35em] hero-kicker font-black drop-shadow-md">Welcome</p>
          <h1 class="mt-4 text-4xl md:text-5xl font-black brand-yellow hero-title leading-tight drop-shadow-lg">
            Reconciliation System
          </h1>
          <p class="mt-4 text-sm md:text-base hero-subtext max-w-md drop-shadow-md">
            Secure settlement intelligence, cross-session matching, and dispute resolution in one place.
          </p>
        </div>

        <div class="relative flex justify-center lg:justify-end items-center lg:min-h-[70vh] pr-0 lg:pr-20">
          <div class="w-full max-w-xl login-panel">
            <div class="logo-wrap flex justify-center">
              <img :src="tsehayLogo" alt="Tsehay Bank logo" class="logo-image" />
            </div>
            <div class="card bg-glass w-full">
            <div class="card-body px-6 py-10">
              <div class="mb-6">
                <h1 class="text-2xl font-black text-foreground">Sign In</h1>
                <p class="text-sm text-muted-foreground mt-1">Use your settlement account</p>
              </div>

              <div class="space-y-4">
                <div>
                  <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground">Username</label>
                  <Input v-model="username" placeholder="Username" class="h-11 mt-2 focus-visible:ring-2 focus-visible:ring-primary/40" />
                </div>
                <div>
                  <label class="text-[10px] font-black uppercase tracking-widest text-muted-foreground">Password</label>
                  <div class="mt-2 relative">
                    <Input
                      v-model="password"
                      :type="showPassword ? 'text' : 'password'"
                      placeholder="Password"
                      class="h-11 pr-11 focus-visible:ring-2 focus-visible:ring-primary/40"
                      @keydown="handlePasswordKeyEvent"
                      @keyup="handlePasswordKeyEvent"
                      @blur="capsLockOn = false"
                    />
                    <button
                      type="button"
                      class="password-toggle"
                      :aria-label="showPassword ? 'Hide password' : 'Show password'"
                      @click="showPassword = !showPassword"
                    >
                      <EyeOff v-if="showPassword" class="h-4 w-4" />
                      <Eye v-else class="h-4 w-4" />
                    </button>
                  </div>
                  <p v-if="capsLockOn" class="mt-1 text-[10px] font-bold uppercase tracking-widest text-warning/95">
                    Caps Lock is on
                  </p>
                </div>
                <p v-if="error" class="text-xs text-destructive font-bold uppercase tracking-widest">
                  {{ error }} <span class="error-hint">Check credentials and try again.</span>
                </p>
                <Button @click="login" :disabled="loading" class="w-full h-11 btn-primary">
                  <span v-if="loading" class="inline-flex items-center gap-2">
                    <span class="btn-spinner" aria-hidden="true"></span>
                    Signing in...
                  </span>
                  <span v-else>Sign In</span>
                </Button>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.background-radial-gradient {
  background-color: rgb(var(--color-background));
  background-size: cover;
  background-position: top right;
  background-repeat: no-repeat;
}

.login-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    rgb(var(--color-foreground) / 0.34) 0%,
    rgb(var(--color-foreground) / 0.2) 55%,
    rgb(var(--color-foreground) / 0.06) 100%
  );
  pointer-events: none;
}

.bg-glass {
  background-color: rgb(var(--color-card) / 0.9);
  backdrop-filter: saturate(200%) blur(25px);
  border-radius: 16px;
  border: 1px solid rgb(var(--color-border) / 0.55);
  position: relative;
  z-index: 1;
}

.login-panel {
  transform: translateY(0);
}

.logo-wrap {
  margin-bottom: 28px;
}

.logo-image {
  display: block;
  width: 270px;
  max-width: 72vw;
  height: auto;
  object-fit: contain;
  opacity: 0.95;
  filter: drop-shadow(0 4px 14px rgb(var(--color-foreground) / 0.24));
}

.brand-yellow {
  color: rgb(var(--color-warning));
}

.hero-subtext {
  color: rgb(248 252 246 / 0.96);
  text-shadow: 0 2px 8px rgb(0 0 0 / 0.45);
}

.hero-kicker {
  color: rgb(248 252 246 / 0.98);
  text-shadow: 0 2px 8px rgb(0 0 0 / 0.45);
}

.hero-title {
  text-shadow: 0 2px 12px rgb(var(--color-primary) / 0.35), 0 0 1px rgb(var(--color-primary) / 0.5);
}

.error-hint {
  text-transform: none;
  letter-spacing: normal;
  font-weight: 600;
}

.password-toggle {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease;
}

.password-toggle:hover {
  color: hsl(var(--foreground));
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgb(var(--color-primary-foreground) / 0.35);
  border-top-color: rgb(var(--color-primary-foreground));
  border-radius: 999px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (min-width: 1024px) {
  .login-panel {
    transform: translateY(-38px);
  }
}
</style>
