import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const timestamp = '2026-10-18T01:22:51.534471';
const prefix = '#include <bits/stdc++.h>\nusing namespace std;\n';

async function success(route: Route, data: object, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId } }),
  });
}

async function mockAdmin(page: Page) {
  await page.route('**/api/auth/session', (route) =>
    success(route, {
      authenticated: true,
      user: {
        id: 'd0e35399-6487-4ac8-8138-8d5bd60eb003',
        username: 'root-admin',
        role: 'ADMIN',
        status: 'ACTIVE',
        passwordChangeRequired: false,
        createdAt: '2026-08-26T01:00:00Z',
        updatedAt: '2026-08-26T01:00:00Z',
        rowVersion: 0,
      },
    }),
  );
  await page.route('**/api/auth/csrf', (route) =>
    success(route, { token: 'csrf-token-that-is-long-enough', headerName: 'X-CSRF-Token' }),
  );
}

test('an administrator edits a language prefix explicitly and sees the effect before saving', async ({
  page,
}) => {
  let current = { id: 'cpp', displayName: 'C++', solutionPrefix: prefix, updatedAt: timestamp };
  let saved: { solutionPrefix: string; expectedUpdatedAt: string } | undefined;
  await mockAdmin(page);
  await page.route('**/api/admin/languages', (route) => success(route, { items: [current] }));
  await page.route('**/api/admin/languages/cpp', async (route) => {
    saved = route.request().postDataJSON() as typeof saved;
    current = {
      ...current,
      solutionPrefix: saved!.solutionPrefix,
      updatedAt: '2026-10-18T02:00:00.000001',
    };
    await success(route, current);
  });

  await page.goto('/admin');
  await page
    .getByRole('navigation', { name: '管理侧栏导航' })
    .getByRole('link', { name: '语言' })
    .click();
  await expect(page).toHaveURL(/\/admin\/languages$/);
  await expect(page.getByRole('heading', { level: 1, name: '语言' })).toBeVisible();
  const section = page.getByRole('region', { name: 'C++' });
  await expect(section).toContainText('前缀代码 2 行');
  // 没改动时没有保存按钮：前缀对所有核心代码题立即生效，不做自动保存。
  await expect(section.getByRole('button', { name: '保存' })).toHaveCount(0);

  await section.getByRole('textbox', { name: 'C++ 前缀代码' }).click();
  await page.keyboard.press('ControlOrMeta+End');
  await page.keyboard.type('using ll = long long;');
  await expect(section.getByRole('status')).toContainText('已标定的核心代码题会提示重新标定');
  await section.getByRole('button', { name: '保存' }).click();

  // 末尾的换行由页面补上；并发前提是读到的 updatedAt。
  await expect
    .poll(() => saved)
    .toEqual({
      solutionPrefix: `${prefix}using ll = long long;\n`,
      expectedUpdatedAt: timestamp,
    });
  await expect(section).toContainText('前缀代码 3 行');
  await expect(section.getByRole('button', { name: '保存' })).toHaveCount(0);
});

test('a stale window is told to reload instead of overwriting another save', async ({ page }) => {
  await mockAdmin(page);
  await page.route('**/api/admin/languages', (route) =>
    success(route, {
      items: [{ id: 'cpp', displayName: 'C++', solutionPrefix: prefix, updatedAt: timestamp }],
    }),
  );
  await page.route('**/api/admin/languages/cpp', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/problem+json',
      headers: { 'X-Request-Id': requestId },
      body: JSON.stringify({
        type: 'urn:cherry-oj:problem:row-version-conflict',
        title: '资源冲突',
        status: 409,
        detail: '前缀已被其他窗口修改，请重新加载。',
        instance: `urn:cherry-oj:request:${requestId}`,
        code: 'ROW_VERSION_CONFLICT',
        meta: { requestId },
      }),
    }),
  );

  await page.goto('/admin/languages');
  const section = page.getByRole('region', { name: 'C++' });
  await section.getByRole('textbox', { name: 'C++ 前缀代码' }).click();
  await page.keyboard.press('ControlOrMeta+End');
  await page.keyboard.type('#include <ranges>');
  await section.getByRole('button', { name: '保存' }).click();

  await expect(page.getByRole('alert')).toContainText('前缀已被其他窗口修改，请重新加载。');
  await expect(section.getByRole('button', { name: '保存' })).toBeDisabled();
  await section.getByRole('button', { name: '放弃改动并重新加载' }).click();
  await expect(section.getByRole('button', { name: '保存' })).toHaveCount(0);
  await expect(page.getByRole('alert')).toHaveCount(0);
});
