import { defineConfig } from '@playwright/test';

// The Linux CI driver owns the real stack and writes only private runtime files.
export default defineConfig({
  testDir: './e2e-live',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 600_000,
  expect: { timeout: 10_000 },
  reporter: [['./e2e-live/reporter.ts']],
  outputDir: process.env['CHERRY_LIVE_PRIVATE'] + '/playwright-output',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    viewport: { width: 1440, height: 950 },
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    actionTimeout: 15_000,
  },
});
