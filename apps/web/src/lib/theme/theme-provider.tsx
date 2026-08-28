import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from 'react';

import type { ThemeId } from '@/generated/design-system/themes';

import {
  createBrowserThemeController,
  type ThemeSetResult,
  type ThemeSnapshot,
} from './theme-runtime';

export type ThemeContextValue = ThemeSnapshot &
  Readonly<{
    setTheme: (themeId: ThemeId) => ThemeSetResult;
  }>;

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [controller] = useState(() => createBrowserThemeController());
  const snapshot = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );

  useEffect(() => controller.start(), [controller]);

  const value = useMemo<ThemeContextValue>(
    () => ({ ...snapshot, setTheme: controller.setTheme }),
    [controller, snapshot],
  );

  return <ThemeContext value={value}>{children}</ThemeContext>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (context === null) {
    throw new Error('useTheme must be used within ThemeProvider.');
  }
  return context;
}
