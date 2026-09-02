import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  colorSchemeAttribute,
  defaultThemeId,
  ThemeProvider,
  themeRegistry,
  themeSelectorAttribute,
  themeStorageKey,
} from '@/lib/theme';

import { getNextTheme, ThemeSwitcher } from './theme-switcher';

const nextTheme = getNextTheme(themeRegistry, defaultThemeId);
if (nextTheme === null) throw new Error('ThemeSwitcher tests require at least two themes.');

function createBrowserStorage({ failWrites = false }: Readonly<{ failWrites?: boolean }> = {}) {
  const values = new Map<string, string>();
  return {
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() {
      return values.size;
    },
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => {
      if (failWrites) throw new DOMException('Storage is full.', 'QuotaExceededError');
      values.set(key, value);
    },
  } satisfies Storage;
}

function installStorage(storage: Storage) {
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: storage,
  });
}

beforeEach(() => {
  installStorage(createBrowserStorage());
  document.documentElement.removeAttribute(themeSelectorAttribute);
  document.documentElement.removeAttribute(colorSchemeAttribute);
});

describe('getNextTheme', () => {
  it('cycles through the generated registry and disables cycling for a single theme', () => {
    for (const [index, theme] of themeRegistry.entries()) {
      const expectedTheme = themeRegistry[(index + 1) % themeRegistry.length];
      expect(getNextTheme(themeRegistry, theme.id)).toBe(expectedTheme);
    }

    expect(getNextTheme([themeRegistry[0]], themeRegistry[0].id)).toBeNull();
    expect(getNextTheme(themeRegistry, 'missing-theme')).toBe(themeRegistry[0]);
  });
});

describe('ThemeSwitcher', () => {
  it('switches to the next registered theme, persists it, and updates its accessible action', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>,
    );

    const button = screen.getByRole('button', { name: `切换到 ${nextTheme.label}` });
    await user.click(button);

    expect(document.documentElement).toHaveAttribute(themeSelectorAttribute, nextTheme.id);
    expect(document.documentElement).toHaveAttribute(colorSchemeAttribute, nextTheme.colorScheme);
    expect(window.localStorage.getItem(themeStorageKey)).toBe(nextTheme.id);
    expect(screen.getByRole('status')).toHaveTextContent(`已切换到 ${nextTheme.label}`);

    const followingTheme = getNextTheme(themeRegistry, nextTheme.id);
    if (followingTheme === null) throw new Error('Expected the generated theme cycle to continue.');
    expect(screen.getByRole('button', { name: `切换到 ${followingTheme.label}` })).toHaveFocus();
  });

  it('still applies the theme and announces when browser persistence fails', async () => {
    installStorage(createBrowserStorage({ failWrites: true }));
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole('button', { name: `切换到 ${nextTheme.label}` }));

    expect(document.documentElement).toHaveAttribute(themeSelectorAttribute, nextTheme.id);
    expect(screen.getByRole('status')).toHaveTextContent(
      `已切换到 ${nextTheme.label}，但浏览器未能记住选择`,
    );
  });
});
