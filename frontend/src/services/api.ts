import axios from 'axios';

const API_BASE_URL = '';
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const SESSION_COOKIE_NAME = 'JSESSIONID';

class ApiService {
  private axiosInstance;
  private lastKnownSessionId: string | null;

  constructor() {
    this.lastKnownSessionId = null;
    this.axiosInstance = axios.create({
      baseURL: API_BASE_URL,
      timeout: 120000, // 2 minutes timeout for file uploads
      withCredentials: true,
      xsrfCookieName: CSRF_COOKIE_NAME,
      xsrfHeaderName: CSRF_HEADER_NAME,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const getCookie = (name: string): string | null => {
      const escaped = name.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
      const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
      const value = match?.[1];
      return value ? decodeURIComponent(value) : null;
    };

    const ensureCsrfToken = async () => {
      const existing = getCookie(CSRF_COOKIE_NAME);
      const currentSessionId = getCookie(SESSION_COOKIE_NAME);
      const sessionChanged = currentSessionId && this.lastKnownSessionId && currentSessionId !== this.lastKnownSessionId;
      const mustRefresh = !existing || sessionChanged;

      if (!mustRefresh) {
        return existing;
      }
      await this.axiosInstance.get('/api/auth/csrf');
      this.lastKnownSessionId = getCookie(SESSION_COOKIE_NAME);
      return getCookie(CSRF_COOKIE_NAME);
    };

    // Request interceptor
    this.axiosInstance.interceptors.request.use(
      async (config) => {
        const method = (config.method || 'get').toLowerCase();
        const isMutatingMethod = method === 'post' || method === 'put' || method === 'patch' || method === 'delete';
        const requestUrl = config.url || '';
        const isCsrfBootstrapCall = requestUrl.includes('/api/auth/csrf') && method === 'get';

        if (isMutatingMethod && !isCsrfBootstrapCall) {
          const token = await ensureCsrfToken();
          if (token) {
            config.headers = config.headers || {};
            (config.headers as any)[CSRF_HEADER_NAME] = token;
          }
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      (error) => {
        // Handle common errors
        if (error.response?.status === 401) {
          const requestUrl = error.config?.url || '';
          const isAuthMe = requestUrl.includes('/api/auth/me');
          const isOnLogin = window.location.pathname === '/login';
          // Avoid infinite reload loop on the login page or auth check
          if (!isAuthMe && !isOnLogin) {
            window.location.href = '/login';
          }
        }
        return Promise.reject(error);
      }
    );
  }

  // Generic GET request
  async get<T>(url: string, params?: any): Promise<T> {
    const response = await this.axiosInstance.get(url, { params });
    return response.data;
  }

  // Generic POST request
  async post<T>(url: string, data?: any): Promise<T> {
    const response = await this.axiosInstance.post(url, data);
    return response.data;
  }

  // Generic PUT request
  async put<T>(url: string, data?: any): Promise<T> {
    const response = await this.axiosInstance.put(url, data);
    return response.data;
  }

  // Generic DELETE request
  async delete<T>(url: string): Promise<T> {
    const response = await this.axiosInstance.delete(url);
    return response.data;
  }

  // File upload
  async uploadFile<T>(
    url: string,
    file: File,
    onProgress?: (progress: number) => void,
    extraFields?: Record<string, string | number | boolean>,
    timeout: number = 300000 // Default 5 minute timeout for file uploads
  ): Promise<T> {
    const formData = new FormData();
    formData.append('file', file);
    if (extraFields) {
      Object.entries(extraFields).forEach(([key, value]) => {
        formData.append(key, String(value));
      });
    }

    const response = await this.axiosInstance.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: timeout, // Use custom timeout for uploads
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(progress);
        }
      },
    });

    return response.data;
  }
}

export const apiService = new ApiService();
