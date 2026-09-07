import { z } from 'zod';

export const codeDraftMaxBytes = 256 * 1024;
export const codeDraftSaveDelayMs = 500;

export interface CodeDraftIdentity {
  userId: string;
  problemId: string;
  problemVersionId: string;
  languageId: string;
}

export function codeDraftKey(identity: CodeDraftIdentity) {
  return `cherry-oj.code-draft.v1:${[identity.userId, identity.problemId, identity.problemVersionId, identity.languageId].map(encodeURIComponent).join(':')}:`;
}

const revisionSchema = z.string().regex(/^[\w-]+:[1-9]\d*$/);
const draftRecordSchema = z.object({
  schemaVersion: z.literal(1),
  writerId: z.string().regex(/^[\w-]+$/),
  revision: revisionSchema,
  parentRevision: revisionSchema.nullable(),
  supersedes: z.array(revisionSchema),
  source: z
    .string()
    .refine((source) => new TextEncoder().encode(source).length <= codeDraftMaxBytes),
  updatedAt: z.number().int().nonnegative(),
});

export type CodeDraftRecord = z.infer<typeof draftRecordSchema>;
export type CodeDraftStatus = 'clean' | 'dirty' | 'saving' | 'saved' | 'error' | 'conflict';

export interface CodeDraftSnapshot {
  value: string;
  status: CodeDraftStatus;
  message: string;
  hasUnsavedChanges: boolean;
  conflicts: CodeDraftRecord[];
}

interface DraftScan {
  heads: CodeDraftRecord[];
  corruptKeys: string[];
}

// Memory-only recovery survives an auth-related unmount in this page. It is never
// exposed before the caller confirms the same account, and disappears on page close.
const interruptedDrafts = new Map<string, { source: string; baseline: CodeDraftRecord | null }>();

function createWriterId() {
  // getRandomValues also works on a plain-HTTP development origin where randomUUID
  // is not exposed; each page still gets an independent 128-bit recovery key.
  return [...crypto.getRandomValues(new Uint8Array(16))]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function revisionWriter(revision: string) {
  return revision.slice(0, revision.lastIndexOf(':'));
}

// One causal revision per writer suffices: a page only replaces its own recovery copy.
// This remains bounded by the number of pages, rather than by every keystroke/save.
function compactRevisions(revisions: string[], currentWriter: string) {
  const latest = new Map<string, string>();
  for (const revision of revisions) {
    const writer = revisionWriter(revision);
    if (writer === currentWriter) continue;
    const previous = latest.get(writer);
    if (!previous || Number(revision.split(':').at(-1)) > Number(previous.split(':').at(-1))) {
      latest.set(writer, revision);
    }
  }
  return [...latest.values()];
}

function ancestry(records: CodeDraftRecord[], writerId: string) {
  return compactRevisions(
    records.flatMap((record) => [
      record.revision,
      ...record.supersedes,
      ...(record.parentRevision ? [record.parentRevision] : []),
    ]),
    writerId,
  );
}

function scanDrafts(storage: Storage, prefix: string): DraftScan {
  const records: CodeDraftRecord[] = [];
  const corruptKeys: string[] = [];
  // Snapshot keys first; other tabs may add a new writer while this scan is running.
  const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index));
  for (const key of keys) {
    if (!key?.startsWith(prefix)) continue;
    const raw = storage.getItem(key);
    if (raw === null) continue;
    try {
      const parsed = draftRecordSchema.safeParse(JSON.parse(raw) as unknown);
      if (
        !parsed.success ||
        key !== prefix + parsed.data.writerId ||
        revisionWriter(parsed.data.revision) !== parsed.data.writerId
      ) {
        corruptKeys.push(key);
      } else {
        records.push(parsed.data);
      }
    } catch {
      corruptKeys.push(key);
    }
  }
  const superseded = new Set(records.flatMap((record) => ancestry([record], record.writerId)));
  const heads = records
    .filter((record) => !superseded.has(record.revision))
    .sort(
      (left, right) =>
        right.updatedAt - left.updatedAt || right.revision.localeCompare(left.revision),
    );
  // Cycles or otherwise inconsistent records must not turn into an empty, writable draft.
  if (records.length && !heads.length)
    corruptKeys.push(...records.map((record) => prefix + record.writerId));
  return { heads, corruptKeys };
}

const failedMessage = '保存失败，请复制备份';
const corruptMessage = '本机草稿格式损坏，未覆盖原记录；请复制备份后恢复起始代码。';
const conflictMessage = '检测到其他标签页的草稿，请选择保留的副本；当前代码仍可复制。';

/**
 * A page owns a separate recovery key. Reading before writing is only conflict detection,
 * never a localStorage compare-and-swap: even simultaneous writes retain both sources.
 */
export class CodeDraftController {
  readonly key: string;
  private readonly writerId: string;
  private readonly getStorage: () => Storage;
  private readonly starterCode: string;
  private readonly listeners = new Set<() => void>();
  private baseline: CodeDraftRecord | null = null;
  private sequence = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private dirty = false;
  private initialReadFailed = false;
  private snapshot: CodeDraftSnapshot;

  constructor(
    identity: CodeDraftIdentity,
    starterCode: string,
    options: { writerId?: string; getStorage?: () => Storage } = {},
  ) {
    this.key = codeDraftKey(identity);
    this.writerId = options.writerId ?? createWriterId();
    this.getStorage = options.getStorage ?? (() => window.localStorage);
    this.starterCode = starterCode;
    this.snapshot = {
      value: starterCode,
      status: 'clean',
      message: '起始代码 · 草稿仅保存在当前浏览器',
      hasUnsavedChanges: false,
      conflicts: [],
    };
    try {
      const scan = scanDrafts(this.getStorage(), this.key);
      const first = scan.heads[0];
      if (first) this.adopt(first);
      if (scan.heads.length > 1) this.showConflict(scan.heads);
      if (scan.corruptKeys.length) this.fail(corruptMessage);
    } catch {
      this.initialReadFailed = true;
      this.fail('无法读取本机草稿；仍可编辑，请复制备份。');
    }
    const interrupted = interruptedDrafts.get(this.key);
    if (interrupted) {
      this.baseline = interrupted.baseline;
      this.dirty = true;
      this.update({
        value: interrupted.source,
        status: 'error',
        message: '已恢复当前页面内存中的代码，尚未保存到本机；请重试保存或复制备份。',
        hasUnsavedChanges: true,
      });
    }
  }

  getSnapshot = () => this.snapshot;

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  private update(next: Partial<CodeDraftSnapshot>) {
    this.snapshot = { ...this.snapshot, ...next };
    this.listeners.forEach((listener) => listener());
  }

  private adopt(record: CodeDraftRecord) {
    this.baseline = record;
    this.dirty = false;
    this.update({
      value: record.source,
      status: 'saved',
      message: '已保存到本机',
      hasUnsavedChanges: false,
      conflicts: [],
    });
  }

  private fail(message = failedMessage) {
    this.update({ status: 'error', message, hasUnsavedChanges: this.dirty });
  }

  private showConflict(heads: CodeDraftRecord[]) {
    this.update({
      status: 'conflict',
      message: conflictMessage,
      conflicts: heads,
      hasUnsavedChanges: this.dirty,
    });
  }

  setValue = (value: string) => {
    if (value === this.snapshot.value) return;
    this.dirty = true;
    this.update({ value, status: 'dirty', message: '保存中', hasUnsavedChanges: true });
    clearTimeout(this.timer);
    this.timer = setTimeout(this.flush, codeDraftSaveDelayMs);
  };

  flush = (): boolean => {
    clearTimeout(this.timer);
    if (!this.dirty) return true;
    if (new TextEncoder().encode(this.snapshot.value).length > codeDraftMaxBytes) {
      this.fail('代码超过本机草稿 256 KiB 上限，未截断内容；请复制备份。');
      return false;
    }
    try {
      const storage = this.getStorage();
      const scan = scanDrafts(storage, this.key);
      if (scan.corruptKeys.length) {
        this.fail(corruptMessage);
        return false;
      }
      this.update({ status: 'saving', message: '保存中' });
      this.persist(storage, this.baseline ? [this.baseline] : []);
      this.initialReadFailed = false;
      const after = scanDrafts(storage, this.key);
      if (after.corruptKeys.length) this.fail(corruptMessage);
      else if (after.heads.length > 1) this.showConflict(after.heads);
      else
        this.update({
          status: 'saved',
          message: '已保存到本机',
          conflicts: [],
          hasUnsavedChanges: false,
        });
    } catch {
      this.fail();
    }
    return !this.dirty;
  };

  private persist(storage: Storage, parents: CodeDraftRecord[]) {
    const record: CodeDraftRecord = {
      schemaVersion: 1,
      writerId: this.writerId,
      revision: `${this.writerId}:${++this.sequence}`,
      parentRevision: this.baseline?.revision ?? null,
      supersedes: ancestry(parents, this.writerId),
      source: this.snapshot.value,
      updatedAt: Date.now(),
    };
    storage.setItem(this.key + this.writerId, JSON.stringify(record));
    this.baseline = record;
    this.dirty = false;
    interruptedDrafts.delete(this.key);
    this.update({ hasUnsavedChanges: false });
  }

  /** Re-scan on notifications and before each write; events are not the data source. */
  refresh = () => {
    try {
      const scan = scanDrafts(this.getStorage(), this.key);
      if (scan.corruptKeys.length) {
        this.fail(corruptMessage);
        return;
      }
      const first = scan.heads[0];
      if (scan.heads.length > 1 || (first && first.revision !== this.baseline?.revision)) {
        this.showConflict(scan.heads);
      } else if (!this.dirty && first) {
        this.adopt(first);
      } else if (!this.dirty && this.baseline) {
        this.dirty = true;
        this.fail('本机草稿记录已被删除，当前代码仍保留在页面中；请重试保存或复制备份。');
      } else if (!this.dirty && this.initialReadFailed) {
        this.initialReadFailed = false;
        this.update({ status: 'clean', message: '起始代码 · 草稿仅保存在当前浏览器' });
      }
    } catch {
      this.fail();
    }
  };

  retrySave = () => {
    if (this.dirty) this.flush();
    else this.refresh();
  };

  detach = () => {
    this.flush();
    if (this.dirty) {
      interruptedDrafts.set(this.key, { source: this.snapshot.value, baseline: this.baseline });
    }
  };

  resolveConflict = (revision: string) => {
    clearTimeout(this.timer);
    try {
      const storage = this.getStorage();
      const scan = scanDrafts(storage, this.key);
      if (scan.corruptKeys.length) {
        this.fail(corruptMessage);
        return;
      }
      const selected =
        revision === 'current'
          ? undefined
          : scan.heads.find((record) => record.revision === revision);
      if (revision !== 'current' && !selected) {
        this.showConflict(scan.heads);
        return;
      }
      if (selected) this.update({ value: selected.source });
      this.dirty = true;
      if (new TextEncoder().encode(this.snapshot.value).length > codeDraftMaxBytes) {
        this.fail('代码超过本机草稿 256 KiB 上限，未截断内容；请复制备份。');
        return;
      }
      // Explicit choice joins observed branches. A concurrent, unseen write stays a conflict.
      this.persist(storage, [...scan.heads, ...(this.baseline ? [this.baseline] : [])]);
      this.refresh();
    } catch {
      this.fail();
    }
  };

  /** The caller must obtain explicit confirmation before invoking this operation. */
  resetToStarter = () => {
    clearTimeout(this.timer);
    this.dirty = true;
    this.update({ value: this.starterCode, hasUnsavedChanges: true });
    if (new TextEncoder().encode(this.starterCode).length > codeDraftMaxBytes) {
      this.fail('代码超过本机草稿 256 KiB 上限，未截断内容；请复制备份。');
      return;
    }
    try {
      const storage = this.getStorage();
      const scan = scanDrafts(storage, this.key);
      // Only confirmed reset removes unreadable records, and only in this exact identity.
      for (const key of scan.corruptKeys) storage.removeItem(key);
      this.persist(storage, [...scan.heads, ...(this.baseline ? [this.baseline] : [])]);
      this.refresh();
    } catch {
      this.fail();
    }
  };
}
