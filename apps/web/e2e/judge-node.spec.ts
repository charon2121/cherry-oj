import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { expect, test } from '@playwright/test';

import { themeRegistry, themeStorageKey } from '../src/generated/design-system/themes.js';

// Run against node-e2e.py --keep; all API responses come from the isolated real stack.
test('real node offline, recovery and deployment remain usable in the workbench', async ({
  page,
}, testInfo) => {
  test.skip(!process.env.WORK040_E2E_DIRECTORY, 'requires isolated node-e2e.py --keep stack');
  test.setTimeout(150_000);
  const directory = process.env.WORK040_E2E_DIRECTORY!;
  const evidence = JSON.parse(readFileSync(resolve(directory, 'evidence.json'), 'utf8')) as {
    project: string;
    ports: { gateway: number };
    problemId: string;
    versionId: string;
    workbenchVersionId?: string;
  };
  const environment = JSON.parse(
    readFileSync(resolve(directory, 'compose.env.json'), 'utf8'),
  ) as Record<string, string>;
  const compose = (...args: string[]) =>
    execFileSync(
      'docker',
      ['compose', '-f', resolve('../../compose.yaml'), '-p', evidence.project, ...args],
      { env: { ...process.env, ...environment }, stdio: 'pipe' },
    );
  const gateway = `http://127.0.0.1:${evidence.ports.gateway}`;
  const csrfResponse = (await (await page.request.get(`${gateway}/api/auth/csrf`)).json()) as {
    data: { token: string };
  };
  const csrf = csrfResponse.data.token;
  const login = await page.request.post(`${gateway}/api/auth/login`, {
    headers: { Origin: 'http://localhost:5173', 'X-CSRF-Token': csrf },
    data: { username: 'work040admin', password: 'Work040-Changed-Password' },
  });
  expect(login.ok()).toBeTruthy();
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const response = await route.fetch({
      url: gateway + url.pathname + url.search,
      headers: { ...route.request().headers(), origin: 'http://localhost:5173' },
    });
    await route.fulfill({ response });
  });
  const path = `/admin/problems/${evidence.problemId}/versions/${evidence.workbenchVersionId ?? evidence.versionId}?step=test-and-calibrate`;
  await page.goto(path);
  const deploy = page.getByRole('button', { name: '部署测试数据', exact: true });
  await expect(deploy).toBeEnabled();
  try {
    compose('stop', 'judge');
    await expect(deploy).toBeDisabled({ timeout: 20_000 });
    await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
    for (const theme of themeRegistry) {
      await page.evaluate(({ key, id }) => localStorage.setItem(key, id), {
        key: themeStorageKey,
        id: theme.id,
      });
      await page.reload();
      await expect(deploy).toBeDisabled();
      await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
      await deploy.scrollIntoViewIfNeeded();
      await page.screenshot({
        path: testInfo.outputPath(`offline-${theme.id}.png`),
        fullPage: true,
      });
      await page.setViewportSize({ width: 320, height: 900 });
      await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        320,
      );
      await page.screenshot({
        path: testInfo.outputPath(`narrow-${theme.id}.png`),
        fullPage: true,
      });
      await page.setViewportSize({ width: 1280, height: 900 });
    }
    await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
    await page.evaluate(() => (document.documentElement.style.zoom = '2'));
    await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
    await page.screenshot({ path: testInfo.outputPath('forced-colors-zoom.png'), fullPage: true });
    await page.evaluate(() => (document.documentElement.style.zoom = '1'));
    await page.emulateMedia({ forcedColors: 'none', reducedMotion: 'no-preference' });
  } finally {
    compose('start', 'judge');
  }
  await expect(deploy).toBeEnabled({ timeout: 20_000 });
  await deploy.click();
  await expect(page.getByRole('status').filter({ hasText: '部署可用' })).toBeVisible({
    timeout: 20_000,
  });
});
