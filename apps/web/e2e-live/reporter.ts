import { writeFileSync } from 'node:fs';
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
    writeFileSync(
      join(directory, 'playwright-diagnostic.json'),
      JSON.stringify({
        phase: this.phase,
        status: result.status,
        failures: this.failures,
      }),
    );
  }
}
