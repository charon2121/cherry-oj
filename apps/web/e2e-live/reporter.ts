import {
  closeSync,
  constants,
  existsSync,
  fstatSync,
  openSync,
  readSync,
  writeFileSync,
} from 'node:fs';
import { basename, join } from 'node:path';

import type {
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
  TestStep,
} from '@playwright/test/reporter';

const phases = new Set([
  'login',
  'problem',
  'io',
  'ce',
  're',
  'signal',
  'cpu',
  'memory',
  'output',
  'empty',
  'ac',
  'wa',
  'history',
]);
const files = new Set([
  'business.spec.ts',
  'support.ts',
  'schemas.ts',
  'reporter.ts',
  'playwright.live.config.ts',
]);

// Error messages, step titles and call logs may contain credentials. Only locations
// in our fixed test sources and explicitly named phases can leave the private area.
export default class LiveReporter implements Reporter {
  private phase = 'startup';
  private failures: { file: string; line: number; column: number }[] = [];

  private failure(error: TestError, fallback?: TestStep['location']) {
    const location = error.location ?? fallback;
    if (!location || this.failures.length >= 16) return;
    const file = basename(location.file);
    if (
      !files.has(file) ||
      !Number.isSafeInteger(location.line) ||
      !Number.isSafeInteger(location.column) ||
      location.line < 1 ||
      location.line > 100_000 ||
      location.column < 1 ||
      location.column > 100_000
    )
      return;
    const row = { file, line: location.line, column: location.column };
    if (
      !this.failures.some(
        (old) => old.file === file && old.line === row.line && old.column === row.column,
      )
    ) {
      this.failures.push(row);
    }
  }

  onStepBegin(_test: TestCase, _result: TestResult, step: TestStep) {
    if (step.category !== 'test.step' || !phases.has(step.title)) return;
    this.phase = step.title;
    const directory = process.env['CHERRY_LIVE_PRIVATE'];
    if (!directory) throw new Error('Missing private live-test directory');
    writeFileSync(join(directory, 'playwright-step.json'), JSON.stringify({ step: step.title }));
  }
  onStepEnd(_test: TestCase, _result: TestResult, step: TestStep) {
    if (step.error) this.failure(step.error, step.location);
  }
  onTestEnd(_test: TestCase, result: TestResult) {
    for (const error of result.errors) this.failure(error);
  }
  onError(error: TestError) {
    this.failure(error);
  }
  onEnd(result: FullResult) {
    const directory = process.env['CHERRY_LIVE_PRIVATE'];
    if (!directory) throw new Error('Missing private live-test directory');
    writeFileSync(
      join(directory, 'playwright-status.json'),
      JSON.stringify({ status: result.status }),
    );
    let editor: Record<string, unknown> | undefined;
    const path = join(directory, 'editor-state.json');
    if (existsSync(path)) {
      const descriptor = openSync(
        path,
        constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK,
      );
      let raw: string;
      try {
        const info = fstatSync(descriptor);
        if (!info.isFile() || info.nlink !== 1 || info.size > 1024)
          throw new Error('Unsafe editor diagnostic');
        const buffer = Buffer.alloc(1025);
        const size = readSync(descriptor, buffer, 0, buffer.length, 0);
        if (size > 1024) throw new Error('Oversized editor diagnostic');
        raw = buffer.subarray(0, size).toString('utf8');
      } finally {
        closeSync(descriptor);
      }
      const parsed: unknown = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') throw new Error('Invalid editor diagnostic');
      const row = parsed as Record<string, unknown>;
      if (
        Object.keys(row).sort().join(',') !== 'loadError,loading,matches,problemResponse,visible' ||
        typeof row['matches'] !== 'number' ||
        !Number.isInteger(row['matches']) ||
        row['matches'] < 0 ||
        row['matches'] > 16 ||
        typeof row['problemResponse'] !== 'number' ||
        !Number.isInteger(row['problemResponse']) ||
        (row['problemResponse'] !== 0 &&
          (row['problemResponse'] < 100 || row['problemResponse'] > 599)) ||
        ['visible', 'loading', 'loadError'].some((key) => typeof row[key] !== 'boolean')
      )
        throw new Error('Invalid editor diagnostic fields');
      editor = row;
    }
    writeFileSync(
      join(directory, 'playwright-diagnostic.json'),
      JSON.stringify({
        phase: this.phase,
        status: result.status,
        failures: this.failures,
        ...(editor ? { editor } : {}),
      }),
    );
  }
}
