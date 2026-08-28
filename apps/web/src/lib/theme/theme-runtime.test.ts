import { beforeEach, describe, expect, it } from 'vitest';

import {
  colorSchemeAttribute,
  defaultThemeId,
  fallbackThemeId,
  themeRegistry,
  themeSelectorAttribute,
  themeStorageKey,
} from '@/generated/design-system/themes';

import {
  applyTheme,
  createThemeController,
  readStoredTheme,
  resolveTheme,
  type ThemeStorage,
  type ThemeStorageEvent,
  type ThemeStorageListener,
} from './theme-runtime';

const lightTheme = themeRegistry.find((theme) => theme.colorScheme === 'light');
if (lightTheme === undefined) throw new Error('The test manifest must contain a light theme.');
const lightThemeId = lightTheme.id;
const lightColorScheme = lightTheme.colorScheme;

function createMemoryStorage(initialValue: string | null = null) {
  let value = initialValue;
  const removedKeys: string[] = [];
  const storage: ThemeStorage = {
    getItem: (key) => (key === themeStorageKey ? value : null),
    removeItem: (key) => {
      removedKeys.push(key);
      if (key === themeStorageKey) value = null;
    },
    setItem: (key, nextValue) => {
      if (key === themeStorageKey) value = nextValue;
    },
  };
  return { getValue: () => value, removedKeys, storage };
}

function clearRootTheme() {
  document.documentElement.removeAttribute(themeSelectorAttribute);
  document.documentElement.removeAttribute(colorSchemeAttribute);
}

beforeEach(() => {
  clearRootTheme();
});

describe('theme resolver', () => {
  it('uses the default for missing and empty values', () => {
    expect(resolveTheme(undefined)).toBe(defaultThemeId);
    expect(resolveTheme(null)).toBe(defaultThemeId);
    expect(resolveTheme('')).toBe(defaultThemeId);
    expect(resolveTheme('   ')).toBe(defaultThemeId);
  });

  it('accepts every registered theme and rejects unknown values', () => {
    for (const theme of themeRegistry) expect(resolveTheme(theme.id)).toBe(theme.id);
    expect(resolveTheme('removed-theme')).toBe(fallbackThemeId);
  });
});

describe('theme DOM and persistence', () => {
  it('updates the selector and its derived color scheme together', () => {
    const snapshot = applyTheme(document.documentElement, lightThemeId);

    expect(snapshot).toEqual({ themeId: lightThemeId, colorScheme: lightColorScheme });
    expect(document.documentElement).toHaveAttribute(themeSelectorAttribute, lightThemeId);
    expect(document.documentElement).toHaveAttribute(colorSchemeAttribute, lightColorScheme);
  });

  it('cleans invalid stored values and falls back safely', () => {
    const memory = createMemoryStorage('unknown-theme');

    expect(readStoredTheme(() => memory.storage)).toEqual({
      themeId: fallbackThemeId,
      status: 'invalid',
    });
    expect(memory.removedKeys).toEqual([themeStorageKey]);
    expect(memory.getValue()).toBeNull();
  });

  it('survives storage getter and read failures', () => {
    expect(
      readStoredTheme(() => {
        throw new Error('blocked');
      }),
    ).toEqual({ themeId: fallbackThemeId, status: 'unavailable' });

    const unreadableStorage: ThemeStorage = {
      getItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => undefined,
      setItem: () => undefined,
    };
    expect(readStoredTheme(() => unreadableStorage)).toEqual({
      themeId: fallbackThemeId,
      status: 'unavailable',
    });
  });
});

describe('theme controller', () => {
  it('keeps the first-paint DOM theme as its initial snapshot', () => {
    applyTheme(document.documentElement, lightThemeId);
    const storage = createMemoryStorage(defaultThemeId);

    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => storage.storage,
    });

    expect(controller.getSnapshot().themeId).toBe(lightThemeId);
  });

  it('applies a theme even when persistence fails and reports the failure', () => {
    const unwritableStorage: ThemeStorage = {
      getItem: () => null,
      removeItem: () => undefined,
      setItem: () => {
        throw new Error('quota exceeded');
      },
    };
    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => unwritableStorage,
    });

    expect(controller.setTheme(lightThemeId)).toEqual({
      themeId: lightThemeId,
      persisted: false,
    });
    expect(controller.getSnapshot().themeId).toBe(lightThemeId);
    expect(document.documentElement).toHaveAttribute(themeSelectorAttribute, lightThemeId);
    expect(document.documentElement).toHaveAttribute(colorSchemeAttribute, lightColorScheme);
  });

  it('persists successful changes', () => {
    const memory = createMemoryStorage();
    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => memory.storage,
    });

    expect(controller.setTheme(lightThemeId).persisted).toBe(true);
    expect(memory.getValue()).toBe(lightThemeId);
  });

  it('converges on cross-tab changes and cleans unknown values', () => {
    const memory = createMemoryStorage();
    let storageListener: ThemeStorageListener | null = null;
    const emitStorage = (event: ThemeStorageEvent) => {
      if (storageListener === null) throw new Error('Storage listener was not started.');
      storageListener(event);
    };
    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => memory.storage,
      listenToStorage: (listener) => {
        storageListener = listener;
        return () => {
          storageListener = null;
        };
      },
    });
    const stop = controller.start();

    emitStorage({ key: themeStorageKey, newValue: lightThemeId });
    expect(controller.getSnapshot().themeId).toBe(lightThemeId);

    emitStorage({ key: themeStorageKey, newValue: 'unknown-theme' });
    expect(controller.getSnapshot().themeId).toBe(fallbackThemeId);
    expect(memory.removedKeys).toContain(themeStorageKey);

    emitStorage({ key: null, newValue: null });
    expect(controller.getSnapshot().themeId).toBe(defaultThemeId);

    stop();
    expect(() => emitStorage({ key: themeStorageKey, newValue: lightThemeId })).toThrow(
      'Storage listener was not started.',
    );
  });

  it('ignores storage events from a different storage area', () => {
    const localMemory = createMemoryStorage();
    const sessionMemory = createMemoryStorage();
    let storageListener: ThemeStorageListener | null = null;
    const emitStorage = (event: ThemeStorageEvent) => {
      if (storageListener === null) throw new Error('Storage listener was not started.');
      storageListener(event);
    };
    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => localMemory.storage,
      listenToStorage: (listener) => {
        storageListener = listener;
        return () => {
          storageListener = null;
        };
      },
    });
    controller.start();

    emitStorage({
      key: themeStorageKey,
      newValue: lightThemeId,
      storageArea: sessionMemory.storage,
    });
    expect(controller.getSnapshot().themeId).toBe(defaultThemeId);

    emitStorage({
      key: themeStorageKey,
      newValue: lightThemeId,
      storageArea: localMemory.storage,
    });
    expect(controller.getSnapshot().themeId).toBe(lightThemeId);

    emitStorage({ key: null, newValue: null, storageArea: sessionMemory.storage });
    expect(controller.getSnapshot().themeId).toBe(lightThemeId);
  });

  it('notifies subscribers only when the resolved theme changes', () => {
    const memory = createMemoryStorage();
    const controller = createThemeController({
      root: document.documentElement,
      getStorage: () => memory.storage,
    });
    let updateCount = 0;
    const unsubscribe = controller.subscribe(() => {
      updateCount += 1;
    });

    controller.setTheme(defaultThemeId);
    controller.setTheme(lightThemeId);
    controller.setTheme(lightThemeId);
    unsubscribe();
    controller.setTheme(defaultThemeId);

    expect(updateCount).toBe(1);
  });
});
