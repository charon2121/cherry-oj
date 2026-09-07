import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  CodeDraftController,
  type CodeDraftIdentity,
  codeDraftKey,
  codeDraftMaxBytes,
  type CodeDraftRecord,
} from './code-draft';

function createStorage() {
  const data = new Map<string, string>();
  return {
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
  } satisfies Storage;
}

let identity: CodeDraftIdentity;
let storage: Storage;
let identitySequence = 0;

function controller(writerId: string, starter = 'starter') {
  return new CodeDraftController(identity, starter, { writerId, getStorage: () => storage });
}

function record(writerId: string) {
  const raw = storage.getItem(codeDraftKey(identity) + writerId);
  if (raw === null) throw new Error('Expected persisted recovery copy');
  return JSON.parse(raw) as CodeDraftRecord;
}

beforeEach(() => {
  identity = {
    userId: `draft-test-user-${++identitySequence}`,
    problemId: 'problem',
    problemVersionId: 'v1',
    languageId: 'cpp',
  };
  storage = createStorage();
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('local code drafts', () => {
  it('reads before saving and preserves an intentionally empty draft', () => {
    const first = controller('first');
    expect(first.getSnapshot().value).toBe('starter');
    expect(storage.length).toBe(0);
    first.setValue('');
    vi.advanceTimersByTime(499);
    expect(storage.length).toBe(0);
    vi.advanceTimersByTime(1);
    expect(first.getSnapshot().status).toBe('saved');
    expect(controller('reload', 'different starter').getSnapshot().value).toBe('');
  });

  it('retains text on quota errors and saves it when storage becomes writable', () => {
    const first = controller('first');
    const denied = vi.spyOn(storage, 'setItem').mockImplementation(() => {
      throw new DOMException('Full', 'QuotaExceededError');
    });
    first.setValue('important source');
    expect(first.flush()).toBe(false);
    expect(first.getSnapshot()).toMatchObject({
      value: 'important source',
      status: 'error',
      hasUnsavedChanges: true,
    });
    denied.mockRestore();
    first.retrySave();
    expect(first.getSnapshot()).toMatchObject({ status: 'saved', hasUnsavedChanges: false });
    expect(record('first').source).toBe('important source');
  });

  it('enforces UTF-8 byte size without truncating and accepts the exact limit', () => {
    const first = controller('first');
    const oversized = '中'.repeat(Math.ceil(codeDraftMaxBytes / 3));
    first.setValue(oversized);
    expect(first.flush()).toBe(false);
    expect(first.getSnapshot().value).toBe(oversized);
    expect(storage.length).toBe(0);
    first.setValue('a'.repeat(codeDraftMaxBytes));
    expect(first.flush()).toBe(true);
    expect(record('first').source.length).toBe(codeDraftMaxBytes);
  });

  it('does not overwrite corrupt data until a confirmed reset for only this identity', () => {
    const badKey = codeDraftKey(identity) + 'broken';
    storage.setItem(badKey, '{broken');
    storage.setItem('unrelated-draft', 'keep');
    const first = controller('first');
    expect(first.getSnapshot().status).toBe('error');
    first.setValue('keep in memory');
    expect(first.flush()).toBe(false);
    expect(storage.getItem(badKey)).toBe('{broken');
    first.resetToStarter();
    expect(first.getSnapshot()).toMatchObject({ value: 'starter', status: 'saved' });
    expect(storage.getItem(badKey)).toBeNull();
    expect(storage.getItem('unrelated-draft')).toBe('keep');
  });

  it('retains both racing writers even when both scan before either write', () => {
    const first = controller('first');
    const second = controller('second');
    first.setValue('first source');
    second.setValue('second source');
    const originalSetItem = storage.setItem.bind(storage);
    let interleaved = false;
    vi.spyOn(storage, 'setItem').mockImplementation((key, value) => {
      if (!interleaved) {
        interleaved = true;
        second.flush();
      }
      originalSetItem(key, value);
    });
    first.flush();
    expect(record('first').source).toBe('first source');
    expect(record('second').source).toBe('second source');
    expect(first.getSnapshot().status).toBe('conflict');
    const reloaded = controller('reload');
    expect(
      reloaded
        .getSnapshot()
        .conflicts.map((draft) => draft.source)
        .sort(),
    ).toEqual(['first source', 'second source']);
  });

  it('saves a conflict recovery branch without replacing the other tab and resolves explicitly', () => {
    const first = controller('first');
    const second = controller('second');
    first.setValue('first source');
    second.setValue('second source');
    first.flush();
    second.refresh();
    expect(second.getSnapshot()).toMatchObject({
      value: 'second source',
      status: 'conflict',
      hasUnsavedChanges: true,
    });
    second.flush();
    expect(second.getSnapshot()).toMatchObject({ status: 'conflict', hasUnsavedChanges: false });
    second.resolveConflict('current');
    expect(second.getSnapshot()).toMatchObject({
      value: 'second source',
      status: 'saved',
      conflicts: [],
    });
    expect(record('first').source).toBe('first source');
    const reloaded = controller('reload');
    expect(reloaded.getSnapshot()).toMatchObject({
      value: 'second source',
      status: 'saved',
      conflicts: [],
    });
  });

  it('retains a clean buffer when another tab saves until the user chooses a copy', () => {
    const first = controller('first');
    first.setValue('shared baseline');
    first.flush();
    const second = controller('second');
    first.setValue('new remote source');
    first.flush();
    second.refresh();
    expect(second.getSnapshot()).toMatchObject({
      value: 'shared baseline',
      status: 'conflict',
    });
    second.resolveConflict(record('first').revision);
    expect(second.getSnapshot()).toMatchObject({ value: 'new remote source', status: 'saved' });
  });

  it('retains the initial buffer if a different page creates the first saved draft', () => {
    const first = controller('first');
    const second = controller('second');
    first.setValue('new remote source');
    first.flush();
    second.refresh();
    expect(second.getSnapshot()).toMatchObject({ value: 'starter', status: 'conflict' });
    second.resolveConflict('current');
    expect(controller('reload').getSnapshot()).toMatchObject({ value: 'starter', status: 'saved' });
  });

  it('keeps an unseen concurrent branch when it races with explicit conflict resolution', () => {
    const first = controller('first');
    const second = controller('second');
    first.setValue('A');
    first.flush();
    second.setValue('B');
    second.flush();
    const third = controller('third');
    third.setValue('C');
    const originalSetItem = storage.setItem.bind(storage);
    let interleaved = false;
    vi.spyOn(storage, 'setItem').mockImplementation((key, value) => {
      if (!interleaved) {
        interleaved = true;
        third.flush();
      }
      originalSetItem(key, value);
    });
    second.resolveConflict('current');
    expect(second.getSnapshot().status).toBe('conflict');
    expect(
      controller('reload')
        .getSnapshot()
        .conflicts.map((draft) => draft.source)
        .sort(),
    ).toEqual(['B', 'C']);
  });

  it('can recover a chosen stored branch while preserving all existing copies', () => {
    const first = controller('first');
    const second = controller('second');
    first.setValue('A');
    first.flush();
    second.setValue('B');
    second.flush();
    const recovery = controller('recovery');
    recovery.resolveConflict(record('first').revision);
    expect(recovery.getSnapshot()).toMatchObject({ value: 'A', status: 'saved', conflicts: [] });
    expect(record('second').source).toBe('B');
    expect(controller('reload').getSnapshot().value).toBe('A');
  });

  it('does not reintroduce resolved ancestors after repeated saves and new pages', () => {
    const first = controller('first');
    first.setValue('one');
    first.flush();
    const second = controller('second');
    for (const value of ['two', 'three', 'four']) {
      second.setValue(value);
      second.flush();
    }
    const third = controller('third');
    third.setValue('five');
    third.flush();
    expect(controller('reload').getSnapshot()).toMatchObject({
      value: 'five',
      status: 'saved',
      conflicts: [],
    });
    expect(record('second').supersedes).toHaveLength(1);
  });

  it('does not trust a previously inaccessible storage to be empty', () => {
    const first = controller('first');
    first.setValue('existing code');
    first.flush();
    let denied = true;
    const second = new CodeDraftController(identity, 'starter', {
      writerId: 'second',
      getStorage: () => {
        if (denied) throw new Error('Denied');
        return storage;
      },
    });
    second.setValue('offline code');
    expect(second.flush()).toBe(false);
    denied = false;
    second.flush();
    expect(second.getSnapshot().status).toBe('conflict');
    expect(record('first').source).toBe('existing code');
    expect(record('second').source).toBe('offline code');
  });

  it('treats deleted persisted records as unsaved and can restore the current buffer', () => {
    const first = controller('first');
    first.setValue('source');
    first.flush();
    storage.clear();
    first.refresh();
    expect(first.getSnapshot()).toMatchObject({
      value: 'source',
      status: 'error',
      hasUnsavedChanges: true,
    });
    first.retrySave();
    expect(controller('reload').getSnapshot().value).toBe('source');
  });

  it('isolates encoded account, problem, version and language identities', () => {
    const prefix = codeDraftKey(identity);
    for (const field of ['userId', 'problemId', 'problemVersionId', 'languageId'] as const) {
      expect(codeDraftKey({ ...identity, [field]: identity[field] + ':other' })).not.toBe(prefix);
    }
    expect(codeDraftKey({ ...identity, userId: 'a:b', problemId: 'c' })).not.toBe(
      codeDraftKey({ ...identity, userId: 'a', problemId: 'b:c' }),
    );
  });
});
