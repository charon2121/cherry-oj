import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { expect, test } from '@playwright/test';

import { themeRegistry, themeStorageKey } from '../src/generated/design-system/themes.js';

// Run against `scripts/work-002-e2e.py --keep`; all API responses come from the isolated real stack.
test('real node offline and recovery are shown in the workbench', async ({ page }, testInfo) => {
  test.skip(!process.env.WORK002_E2E_DIRECTORY, 'requires isolated work-002-e2e.py --keep stack');
  test.setTimeout(150_000);
  const directory = process.env.WORK002_E2E_DIRECTORY!;
  const evidence = JSON.parse(readFileSync(resolve(directory, 'evidence.json'), 'utf8')) as {
    project: string;
    ports: { gateway: number };
    problemId: string;
    webOrigin: string;
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
    headers: { Origin: evidence.webOrigin, 'X-CSRF-Token': csrf },
    data: { username: 'work002admin', password: 'Work002-Changed-Password' },
  });
  expect(login.ok()).toBeTruthy();
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const response = await route.fetch({
      url: gateway + url.pathname + url.search,
      headers: { ...route.request().headers(), origin: evidence.webOrigin },
    });
    await route.fulfill({ response });
  });
  // 测试数据由节点按地址读取，没有部署动作：节点离线时，校准区显示“没有在线判题节点”并禁用校准。
  await page.goto(`/admin/problems/${evidence.problemId}?step=test-and-calibrate`);
  const calibrate = page.getByRole('button', { name: '运行参考程序校准', exact: true });
  const offline = page.getByRole('status').filter({ hasText: '在线判题节点' });
  await expect(offline).toHaveCount(0);
  try {
    compose('stop', 'judge');
    await expect(offline).toBeVisible({ timeout: 20_000 });
    await expect(calibrate).toBeDisabled();
    await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
    for (const theme of themeRegistry) {
      await page.evaluate(({ key, id }) => localStorage.setItem(key, id), {
        key: themeStorageKey,
        id: theme.id,
      });
      await page.reload();
      await expect(calibrate).toBeDisabled();
      await expect(page.getByRole('status').filter({ hasText: '在线判题节点' })).toBeVisible();
      await calibrate.scrollIntoViewIfNeeded();
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
  // 节点恢复后，提示消失；不需要重新部署任何东西。
  await expect(offline).toHaveCount(0, { timeout: 20_000 });
});
