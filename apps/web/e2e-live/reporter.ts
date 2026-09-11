import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

import type {
  FullResult,
  Reporter,
  TestCase,
  TestResult,
  TestStep,
} from '@playwright/test/reporter';

// Playwright call logs can include login arguments. Export only the final outcome.
export default class LiveReporter implements Reporter {
  onStepBegin(_test: TestCase, _result: TestResult, step: TestStep) {
    if (step.category !== 'test.step') return;
    const directory = process.env['CHERRY_LIVE_PRIVATE'];
    if (!directory) throw new Error('Missing private live-test directory');
    writeFileSync(join(directory, 'playwright-step.json'), JSON.stringify({ step: step.title }));
  }
  onEnd(result: FullResult) {
    const directory = process.env['CHERRY_LIVE_PRIVATE'];
    if (!directory) throw new Error('Missing private live-test directory');
    writeFileSync(
      join(directory, 'playwright-status.json'),
      JSON.stringify({ status: result.status }),
    );
  }
}
