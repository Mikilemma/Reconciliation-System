import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import axios from 'axios'
import router from './router'
import { useTheme } from './composables/useTheme'; // Import useTheme

axios.defaults.baseURL = '' // Use empty string to leverage Vite API proxy
axios.defaults.timeout = 10000; // 10s timeout
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
const SESSION_COOKIE_NAME = 'JSESSIONID';
let lastKnownSessionId: string | null = null;

const getCookie = (name: string): string | null => {
  const escaped = name.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  const value = match?.[1];
  return value ? decodeURIComponent(value) : null;
};

const ensureCsrfToken = async () => {
  const existing = getCookie('XSRF-TOKEN');
  const currentSessionId = getCookie(SESSION_COOKIE_NAME);
  const sessionChanged = currentSessionId && lastKnownSessionId && currentSessionId !== lastKnownSessionId;
  const mustRefresh = !existing || sessionChanged;

  if (!mustRefresh) {
    return existing;
  }
  await axios.get('/api/auth/csrf');
  lastKnownSessionId = getCookie(SESSION_COOKIE_NAME);
  return getCookie('XSRF-TOKEN');
};

axios.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  const isMutatingMethod = method === 'post' || method === 'put' || method === 'patch' || method === 'delete';
  const requestUrl = config.url || '';
  const isCsrfBootstrapCall = requestUrl.includes('/api/auth/csrf') && method === 'get';

  if (isMutatingMethod && !isCsrfBootstrapCall) {
    const token = await ensureCsrfToken();
    if (token) {
      config.headers = config.headers || {};
      (config.headers as any)['X-XSRF-TOKEN'] = token;
    }
  }
  return config;
});

const { initTheme } = useTheme(); // Get initTheme from the composable
initTheme(); // Initialize theme before mounting the app

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')
