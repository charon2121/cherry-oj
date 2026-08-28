import {
  colorSchemeAttribute,
  defaultThemeId,
  fallbackThemeId,
  type ThemeColorScheme,
  type ThemeDefinition,
  type ThemeId,
  themeRegistry,
  themeSelectorAttribute,
  themeStorageKey,
} from '../../generated/design-system/themes.js';

export type ThemeRoot = Pick<Element, 'getAttribute' | 'setAttribute'>;
export type ThemeStorage = Pick<Storage, 'getItem' | 'removeItem' | 'setItem'>;
export type ThemeStorageProvider = () => ThemeStorage | null;
export type ThemeStorageEvent = Pick<StorageEvent, 'key' | 'newValue'> &
  Readonly<{ storageArea?: ThemeStorage | null }>;
export type ThemeStorageListener = (event: ThemeStorageEvent) => void;

export type ThemeSnapshot = Readonly<{
  themeId: ThemeId;
  colorScheme: ThemeColorScheme;
}>;

export type ThemeSetResult = Readonly<{
  themeId: ThemeId;
  persisted: boolean;
}>;

export type StoredThemeRead = Readonly<{
  themeId: ThemeId;
  status: 'missing' | 'valid' | 'invalid' | 'unavailable';
}>;

export type ThemeController = Readonly<{
  getSnapshot: () => ThemeSnapshot;
  setTheme: (themeId: ThemeId) => ThemeSetResult;
  start: () => () => void;
  subscribe: (listener: () => void) => () => void;
}>;

export type ThemeControllerOptions = Readonly<{
  root: ThemeRoot;
  getStorage: ThemeStorageProvider;
  listenToStorage?: (listener: ThemeStorageListener) => () => void;
}>;

export function isThemeId(value: unknown): value is ThemeId {
  return typeof value === 'string' && themeRegistry.some((theme) => theme.id === value);
}

export function resolveTheme(value: unknown): ThemeId {
  if (value === null || value === undefined) return defaultThemeId;
  if (typeof value === 'string' && value.trim().length === 0) return defaultThemeId;
  return isThemeId(value) ? value : fallbackThemeId;
}

export function getThemeDefinition(themeId: ThemeId): ThemeDefinition {
  const definition = themeRegistry.find((theme) => theme.id === themeId);
  if (definition === undefined) {
    throw new Error('Theme is missing from the generated registry: ' + themeId);
  }
  return definition;
}

export function applyTheme(root: ThemeRoot, themeId: ThemeId): ThemeSnapshot {
  const definition = getThemeDefinition(themeId);
  root.setAttribute(themeSelectorAttribute, definition.id);
  root.setAttribute(colorSchemeAttribute, definition.colorScheme);
  return Object.freeze({ themeId: definition.id, colorScheme: definition.colorScheme });
}

function getStorage(provider: ThemeStorageProvider): ThemeStorage | null {
  try {
    return provider();
  } catch {
    return null;
  }
}

function removeStoredTheme(storage: ThemeStorage | null): void {
  if (storage === null) return;
  try {
    storage.removeItem(themeStorageKey);
  } catch {
    // Invalid preference cleanup is best-effort.
  }
}

export function readStoredTheme(provider: ThemeStorageProvider): StoredThemeRead {
  const storage = getStorage(provider);
  if (storage === null) {
    return Object.freeze({ themeId: fallbackThemeId, status: 'unavailable' });
  }

  let storedTheme: string | null;
  try {
    storedTheme = storage.getItem(themeStorageKey);
  } catch {
    return Object.freeze({ themeId: fallbackThemeId, status: 'unavailable' });
  }

  if (storedTheme === null) {
    return Object.freeze({ themeId: defaultThemeId, status: 'missing' });
  }
  if (isThemeId(storedTheme)) {
    return Object.freeze({ themeId: storedTheme, status: 'valid' });
  }

  removeStoredTheme(storage);
  return Object.freeze({ themeId: resolveTheme(storedTheme), status: 'invalid' });
}

export function persistTheme(provider: ThemeStorageProvider, themeId: ThemeId): boolean {
  const storage = getStorage(provider);
  if (storage === null) return false;
  try {
    storage.setItem(themeStorageKey, themeId);
    return true;
  } catch {
    return false;
  }
}

export function createThemeController(options: ThemeControllerOptions): ThemeController {
  const storedTheme = readStoredTheme(options.getStorage);
  const rootTheme = options.root.getAttribute(themeSelectorAttribute);
  const initialTheme = isThemeId(rootTheme) ? rootTheme : storedTheme.themeId;
  let snapshot = applyTheme(options.root, initialTheme);
  let stopListening: (() => void) | null = null;
  const listeners = new Set<() => void>();

  const commit = (themeId: ThemeId) => {
    const nextSnapshot = applyTheme(options.root, themeId);
    if (
      snapshot.themeId === nextSnapshot.themeId &&
      snapshot.colorScheme === nextSnapshot.colorScheme
    ) {
      return;
    }
    snapshot = nextSnapshot;
    for (const listener of listeners) listener();
  };

  const handleStorage = (event: ThemeStorageEvent) => {
    if (event.key !== null && event.key !== themeStorageKey) return;
    const storage = getStorage(options.getStorage);
    if (event.storageArea !== undefined && event.storageArea !== storage) return;
    const nextTheme = event.key === null ? defaultThemeId : resolveTheme(event.newValue);
    if (event.key === themeStorageKey && event.newValue !== null && !isThemeId(event.newValue)) {
      removeStoredTheme(storage);
    }
    commit(nextTheme);
  };

  const controller: ThemeController = Object.freeze({
    getSnapshot: () => snapshot,
    setTheme: (themeId) => {
      const resolvedTheme = resolveTheme(themeId);
      commit(resolvedTheme);
      return Object.freeze({
        themeId: resolvedTheme,
        persisted: persistTheme(options.getStorage, resolvedTheme),
      });
    },
    start: () => {
      if (stopListening !== null) return stopListening;
      const stop = options.listenToStorage?.(handleStorage) ?? (() => undefined);
      const cleanup = () => {
        if (stopListening !== cleanup) return;
        stop();
        stopListening = null;
      };
      stopListening = cleanup;
      return cleanup;
    },
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  });

  return controller;
}

export function createBrowserThemeController(): ThemeController {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    throw new Error('The browser theme controller requires window and document.');
  }
  return createThemeController({
    root: document.documentElement,
    getStorage: () => window.localStorage,
    listenToStorage: (listener) => {
      const browserListener = (event: StorageEvent) => listener(event);
      window.addEventListener('storage', browserListener);
      return () => window.removeEventListener('storage', browserListener);
    },
  });
}
