import { ref } from 'vue';

type Theme = 'system' | 'light' | 'dark' | 'tsehay-bank-theme';

const currentTheme = ref<Theme>('system');

let systemThemeListenerRegistered = false;

export function useTheme() {
  const ensureSystemThemeListener = () => {
    if (systemThemeListenerRegistered) return;
    if (typeof window === 'undefined') return;

    const media = window.matchMedia('(prefers-color-scheme: dark)');
    media.addEventListener('change', () => {
      if (currentTheme.value === 'system') {
        applyThemeToHtml('system');
      }
    });

    systemThemeListenerRegistered = true;
  };

  const applyThemeToHtml = (theme: Theme) => {
    const html = document.documentElement;
    html.classList.remove('light', 'dark', 'tsehay-bank-theme'); // Remove all theme classes

    if (theme === 'system') {
      if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        html.classList.add('dark');
      }
    } else if (theme === 'light') {
      // No specific class needed for light theme, just remove others
    } else {
      html.classList.add(theme);
    }
  };

  const initTheme = () => {
    ensureSystemThemeListener();
    const savedTheme = localStorage.getItem('theme') as Theme;
    if (savedTheme) {
      currentTheme.value = savedTheme;
    } else {
      currentTheme.value = 'system';
    }
    applyThemeToHtml(currentTheme.value);
  };

  const setTheme = (theme: Theme) => {
    ensureSystemThemeListener();
    currentTheme.value = theme;
    localStorage.setItem('theme', theme);
    applyThemeToHtml(theme);
  };
  return {
    currentTheme,
    initTheme,
    setTheme,
  };
}