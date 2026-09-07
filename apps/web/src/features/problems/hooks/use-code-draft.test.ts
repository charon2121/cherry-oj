import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

import { CodeDraftController, type CodeDraftIdentity, codeDraftKey } from '../lib/code-draft';
import { useCodeDraft } from './use-code-draft';

let options: CodeDraftIdentity & { starterCode: string };
let identitySequence = 0;

beforeEach(() => {
  const data = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      clear: () => data.clear(),
      getItem: (key: string) => data.get(key) ?? null,
      key: (index: number) => [...data.keys()][index] ?? null,
      get length() {
        return data.size;
      },
      removeItem: (key: string) => {
        data.delete(key);
      },
      setItem: (key: string, value: string) => {
        data.set(key, value);
      },
    } satisfies Storage,
  });
  options = {
    userId: `draft-hook-user-${++identitySequence}`,
    problemId: 'problem',
    problemVersionId: 'v1',
    languageId: 'cpp',
    starterCode: 'starter',
  };
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

it('flushes the old identity on unmount without a delayed write to a new account/version', () => {
  const first = renderHook(() => useCodeDraft(options));
  act(() => {
    first.result.current.setValue('old account source');
  });
  first.unmount();
  const changed = { ...options, userId: 'next-account', problemVersionId: 'v2' };
  const second = renderHook(() => useCodeDraft(changed));
  expect(second.result.current.value).toBe('starter');
  act(() => {
    vi.advanceTimersByTime(1000);
  });
  expect(new CodeDraftController(options, '').getSnapshot().value).toBe('old account source');
  expect(new CodeDraftController(changed, '').getSnapshot().value).toBe('');
});

it('flushes before pagehide and retains a deliberately empty draft on reload', () => {
  const first = renderHook(() => useCodeDraft(options));
  act(() => {
    first.result.current.setValue('');
  });
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  expect(first.result.current.hasUnsavedChanges).toBe(false);
  first.unmount();
  const second = renderHook(() => useCodeDraft(options));
  expect(second.result.current.value).toBe('');
});

it('does not replace edited content when a background response changes starterCode', () => {
  const draft = renderHook((props) => useCodeDraft(props), { initialProps: options });
  act(() => {
    draft.result.current.setValue('my source');
  });
  draft.rerender({ ...options, starterCode: 'background update' });
  expect(draft.result.current.value).toBe('my source');
  act(() => {
    draft.result.current.resetToStarter();
  });
  expect(draft.result.current.value).toBe('starter');
});

it('uses storage events as notifications and keeps pending local edits during conflict', () => {
  const local = renderHook(() => useCodeDraft(options));
  const other = new CodeDraftController(options, 'starter');
  act(() => {
    local.result.current.setValue('local');
  });
  other.setValue('other');
  other.flush();
  act(() => {
    window.dispatchEvent(new StorageEvent('storage', { key: codeDraftKey(options) + 'other' }));
  });
  expect(local.result.current.value).toBe('local');
  expect(local.result.current.status).toBe('conflict');
  local.unmount();
  expect(
    new CodeDraftController(options, '')
      .getSnapshot()
      .conflicts.map((draft) => draft.source)
      .sort(),
  ).toEqual(['local', 'other']);
});

it('protects page exit if storage is blocked, then restores memory only for the original account', () => {
  const denied = vi.spyOn(window.localStorage, 'setItem').mockImplementation(() => {
    throw new Error('Denied');
  });
  const first = renderHook(() => useCodeDraft(options));
  act(() => {
    first.result.current.setValue('private unsaved source');
  });
  const event = new Event('beforeunload', { cancelable: true });
  act(() => {
    window.dispatchEvent(event);
  });
  expect(event.defaultPrevented).toBe(true);
  first.unmount();
  const differentAccount = renderHook(() => useCodeDraft({ ...options, userId: 'someone-else' }));
  expect(differentAccount.result.current.value).toBe('starter');
  differentAccount.unmount();
  const originalAccount = renderHook(() => useCodeDraft(options));
  expect(originalAccount.result.current).toMatchObject({
    value: 'private unsaved source',
    hasUnsavedChanges: true,
  });
  denied.mockRestore();
  act(() => {
    originalAccount.result.current.retrySave();
  });
  expect(originalAccount.result.current.status).toBe('saved');
});

it('does not prevent page exit once the pending edit has been saved', () => {
  const draft = renderHook(() => useCodeDraft(options));
  act(() => {
    draft.result.current.setValue('source');
  });
  const event = new Event('beforeunload', { cancelable: true });
  act(() => {
    window.dispatchEvent(event);
  });
  expect(event.defaultPrevented).toBe(false);
  expect(draft.result.current.status).toBe('saved');
});
