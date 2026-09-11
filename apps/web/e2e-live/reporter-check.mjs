import assert from 'node:assert/strict';
import console from 'node:console';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import process from 'node:process';

import LiveReporter from './reporter.ts';

// No browser or business environment: exercise the actual export boundary with
// credentials in every free-text field a Playwright failure could supply.
const directory = mkdtempSync(join(tmpdir(), 'cherry-live-reporter-'));
const previous = process.env['CHERRY_LIVE_PRIVATE'];
process.env['CHERRY_LIVE_PRIVATE'] = directory;
try {
  const reporter = new LiveReporter();
  const secret = 'private-password-and-cookie';
  const location = { file: '/private/' + secret + '/support.ts', line: 52, column: 7 };
  const error = { message: secret, stack: secret, snippet: secret, value: secret };
  reporter.onStepBegin({}, {}, { category: 'test.step', title: 'login' });
  reporter.onStepBegin({}, {}, { category: 'test.step', title: secret });
  reporter.onStepEnd({}, {}, { error, location });
  reporter.onTestEnd({}, { errors: [{ ...error, location }] });
  reporter.onError({ ...error, location: { ...location, file: '/' + secret } });
  reporter.onError({ ...error, location: { ...location, line: Number.NaN } });
  reporter.onEnd({ status: 'failed' });
  const raw = readFileSync(join(directory, 'playwright-diagnostic.json'), 'utf8');
  assert.ok(!raw.includes(secret));
  assert.deepEqual(JSON.parse(raw), {
    phase: 'login',
    status: 'failed',
    failures: [{ file: 'support.ts', line: 52, column: 7 }],
  });
  assert.deepEqual(JSON.parse(readFileSync(join(directory, 'playwright-step.json'), 'utf8')), {
    step: 'login',
  });
  for (let line = 1; line <= 30; line++)
    reporter.onError({ ...error, location: { ...location, line } });
  reporter.onEnd({ status: 'failed' });
  assert.equal(
    JSON.parse(readFileSync(join(directory, 'playwright-diagnostic.json'), 'utf8')).failures.length,
    16,
  );
  console.log('PASS: browser diagnostic excludes private text and bounds source locations');
} finally {
  if (previous === undefined) delete process.env['CHERRY_LIVE_PRIVATE'];
  else process.env['CHERRY_LIVE_PRIVATE'] = previous;
  rmSync(directory, { recursive: true, force: true });
}
