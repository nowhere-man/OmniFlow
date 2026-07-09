import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ThemeColor = 'mint' | 'blue' | 'purple' | 'orange' | 'rose';

interface SyncConfig {
  url: string;
  user: string;
  pass: string;
  encryptKey: string;
}

interface SettingsState {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
  themeColor: ThemeColor;
  setThemeColor: (color: ThemeColor) => void;
  syncConfig: SyncConfig;
  setSyncConfig: (config: Partial<SyncConfig>) => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'system',
      themeColor: 'mint',
      setTheme: (theme) => {
        set({ theme });
        applyTheme(theme, undefined);
      },
      setThemeColor: (themeColor) => {
        set({ themeColor });
        applyTheme(undefined, themeColor);
      },
      syncConfig: {
        url: '',
        user: '',
        pass: '',
        encryptKey: '',
      },
      setSyncConfig: (config) =>
        set((state) => ({
          syncConfig: { ...state.syncConfig, ...config },
        })),
    }),
    {
      name: 'omniflow-settings',
    }
  )
);

export function applyTheme(newTheme?: ThemeMode, newColor?: ThemeColor) {
  const root = window.document.documentElement;
  
  if (newTheme) {
    root.classList.remove('light', 'dark');
    if (newTheme === 'system') {
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light';
      root.classList.add(systemTheme);
    } else {
      root.classList.add(newTheme);
    }
  }

  if (newColor) {
    root.classList.remove('theme-mint', 'theme-blue', 'theme-purple', 'theme-orange', 'theme-rose');
    root.classList.add(`theme-${newColor}`);
  }
}
