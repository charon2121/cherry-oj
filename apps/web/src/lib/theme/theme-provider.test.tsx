import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it } from 'vitest';

import {
  colorSchemeAttribute,
  defaultThemeId,
  themeRegistry,
  themeSelectorAttribute,
  themeStorageKey,
} from '@/generated/design-system/themes';

import { ThemeProvider, useTheme } from './theme-provider';

const lightTheme = themeRegistry.find((theme) => theme.colorScheme === 'light');
if (lightTheme === undefined) throw new Error('The test manifest must contain a light theme.');
const lightThemeId = lightTheme.id;
const lightColorScheme = lightTheme.colorScheme;

function createBrowserStorage() {
  const values = new Map<string, string>();
  return {
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() {
      return values.size;
    },
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => values.set(key, value),
  } satisfies Storage;
}

function ThemeProbe() {
  const theme = useTheme();
  return (
    <button type="button" onClick={() => theme.setTheme(lightThemeId)}>
      当前主题：{theme.themeId}
    </button>
  );
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: createBrowserStorage(),
  });
  document.documentElement.removeAttribute(themeSelectorAttribute);
  document.documentElement.removeAttribute(colorSchemeAttribute);
});

it('exposes the current theme and a persistent setter through React context', async () => {
  const user = userEvent.setup();
  render(
    <ThemeProvider>
      <ThemeProbe />
    </ThemeProvider>,
  );

  expect(screen.getByRole('button')).toHaveTextContent('当前主题：' + defaultThemeId);
  await user.click(screen.getByRole('button'));

  expect(screen.getByRole('button')).toHaveTextContent('当前主题：' + lightThemeId);
  expect(window.localStorage.getItem(themeStorageKey)).toBe(lightThemeId);
  expect(document.documentElement).toHaveAttribute(themeSelectorAttribute, lightThemeId);
  expect(document.documentElement).toHaveAttribute(colorSchemeAttribute, lightColorScheme);
});
