import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const canary = 's3://private-bucket/reference-source.cpp';

async function success(route: Route, data: object, meta: object = {}) {
  await route.fulfill({
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId, ...meta } }),
  });
}

async function anonymous(page: Page) {
  await page.route('**/api/auth/session', (route) => success(route, { authenticated: false }));
}

const summary = {
  problemId,
  slug: 'two-sum',
  currentVersionId: versionId,
  versionNo: 1,
  title: '两数之和：一段很长但仍然能够换行显示的中文题目标题',
  difficulty: 'EASY',
  tags: ['数组', '哈希表'],
  codeMode: 'ACM',
  allowedLanguages: [{ id: 'cpp', displayName: 'C++' }],
  storageRef: canary,
};

test('anonymous URL filters restore and a real detail link stays safe at 320px', async ({
  page,
}) => {
  await anonymous(page);
  await page.route('**/api/problems?**', (route) =>
    success(
      route,
      { items: [summary] },
      { pagination: { kind: 'cursor', nextCursor: null, hasMore: false } },
    ),
  );
  await page.route('**/api/problems/two-sum', (route) =>
    success(route, {
      problemId,
      problemVersionId: versionId,
      versionNo: 1,
      slug: 'two-sum',
      codeMode: 'ACM',
      title: summary.title,
      difficulty: 'EASY',
      tags: ['数组'],
      statementMarkdown: '# 题意\n\n`a + b`\n\n<img src=x onerror="alert(1)">',
      inputDescriptionMarkdown: '两个整数。',
      outputDescriptionMarkdown: '输出和。',
      constraintsMarkdown: null,
      hintMarkdown: null,
      samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: null }],
      allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: 'int main() {}' }],
      storageRef: canary,
    }),
  );

  await page.goto('/problems?q=%E4%B8%A4%E6%95%B0&difficulty=EASY&sort=TITLE_ASC&size=20');
  await expect(page.getByLabel('关键词')).toBeVisible();
  await expect(page.getByLabel('关键词')).toHaveValue('两数');
  // 难度筛选器已从原生 <select> 换成 Base UI Select：trigger 是 button，
  // 当前值读 trigger 的可见文本，不再是表单控件的 value。
  await expect(page.getByRole('combobox', { name: '难度' })).toContainText('简单');
  await expect(page.getByRole('link', { name: /两数之和/ })).toHaveAttribute(
    'href',
    '/problems/two-sum',
  );
  await page.getByRole('link', { name: /两数之和/ }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/problems\/two-sum(?:\?|$)/);
  await expect(page.getByRole('heading', { name: '题意' })).toBeVisible();
  await expect(page.locator('img')).toHaveCount(0);
  await expect(page.getByText(canary)).toHaveCount(0);
  expect(
    await page.evaluate(
      (secret) => ({
        dom: document.documentElement.innerHTML.includes(secret),
        local: JSON.stringify(localStorage).includes(secret),
        session: JSON.stringify(sessionStorage).includes(secret),
        url: location.href.includes(secret),
      }),
      canary,
    ),
  ).toEqual({ dom: false, local: false, session: false, url: false });

  await page.setViewportSize({ width: 320, height: 720 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
});

test('invalid cursor is rendered as an error instead of an empty library', async ({ page }) => {
  await anonymous(page);
  await page.route('**/api/problems?**', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/problem+json',
      headers: { 'X-Request-Id': requestId },
      body: JSON.stringify({
        type: 'urn:cherry-oj:problem:invalid-cursor',
        title: '游标无效',
        status: 400,
        code: 'INVALID_CURSOR',
        instance: `urn:cherry-oj:request:${requestId}`,
        meta: { requestId },
      }),
    }),
  );
  await page.goto('/problems?cursor=bad&sort=UPDATED_DESC&size=20');
  await expect(page.getByText('题库游标已失效')).toBeVisible();
  await expect(page.getByText('题库还没有公开题目')).toHaveCount(0);
});

test('an admin creates a draft and restores the complete version workbench', async ({ page }) => {
  let adminListRequestUrl: string | undefined;
  const timestamp = '2026-08-30T01:00:00Z';
  const versionSummary = {
    id: versionId,
    versionNo: 1,
    status: 'DRAFT',
    title: '两数之和',
    updatedAt: timestamp,
    publishedAt: null,
    rowVersion: 0,
  };
  const problem = {
    id: problemId,
    slug: 'two-sum',
    visibility: 'PRIVATE',
    status: 'ACTIVE',
    currentPublishedVersionId: null,
    versions: [versionSummary],
    createdAt: timestamp,
    updatedAt: timestamp,
    rowVersion: 0,
  };
  const version = {
    id: versionId,
    problemId,
    versionNo: 1,
    status: 'DRAFT',
    codeMode: 'ACM',
    title: '两数之和',
    statementMarkdown: '# 题意',
    inputDescriptionMarkdown: '两个整数。',
    outputDescriptionMarkdown: '输出和。',
    constraintsMarkdown: null,
    hintMarkdown: null,
    difficulty: 'EASY',
    tags: ['数组'],
    samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: null }],
    allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: 'int main() {}' }],
    testDataVersion: null,
    changeSummary: null,
    createdAt: timestamp,
    updatedAt: timestamp,
    publishedAt: null,
    rowVersion: 0,
  };
  await page.route('**/api/auth/session', (route) =>
    success(route, {
      authenticated: true,
      user: {
        id: 'd0e35399-6487-4ac8-8138-8d5bd60eb003',
        username: 'root-admin',
        role: 'ADMIN',
        status: 'ACTIVE',
        passwordChangeRequired: false,
        createdAt: timestamp,
        updatedAt: timestamp,
        rowVersion: 0,
      },
    }),
  );
  await page.route('**/api/auth/csrf', (route) =>
    success(route, { token: 'csrf-token-that-is-long-enough', headerName: 'X-CSRF-Token' }),
  );
  await page.route('**/api/admin/problems?**', (route) => {
    adminListRequestUrl = route.request().url();
    return success(
      route,
      { items: [] },
      { pagination: { kind: 'page', page: 1, size: 20, totalElements: 0, totalPages: 0 } },
    );
  });
  await page.route('**/api/admin/problems', (route) =>
    route.fulfill({
      status: 201,
      contentType: 'application/json',
      headers: {
        Location: `/api/admin/problems/${problemId}`,
        'X-Request-Id': requestId,
      },
      body: JSON.stringify({ data: problem, meta: { requestId } }),
    }),
  );
  await page.route(`**/api/admin/problems/${problemId}`, (route) => success(route, problem));
  await page.route(`**/api/admin/problems/${problemId}/versions/${versionId}`, (route) =>
    success(route, version),
  );
  await page.route(`**/api/admin/problems/${problemId}/test-data`, (route) =>
    success(route, { items: [] }),
  );

  await page.goto('/admin/problems?page=1&q=&status=ALL');
  await expect(page.getByRole('heading', { name: '题目管理' })).toBeVisible();
  await expect.poll(() => adminListRequestUrl).toBeTruthy();
  const adminListUrl = new URL(adminListRequestUrl!);
  expect(adminListUrl.searchParams.get('q')).toBeNull();
  expect(adminListUrl.searchParams.get('status')).toBeNull();
  expect(adminListUrl.searchParams.get('page')).toBe('1');
  expect(adminListUrl.searchParams.get('size')).toBe('20');
  await page.getByLabel('题目标识').fill('two-sum');
  await page.getByLabel('标题').fill('两数之和');
  await page.getByRole('combobox', { name: '难度' }).click();
  await page.getByRole('option', { name: '简单' }).click();
  await page.getByRole('button', { name: '新建' }).click();

  await expect(page).toHaveURL(
    new RegExp(`/admin/problems/${problemId}/versions/${versionId}(?:\\?|$)`),
  );
  await expect(page.getByRole('heading', { name: '题面与样例' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '测试数据' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '部署与参考程序校准' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '发布与版本' })).toBeVisible();
  await expect(page.locator('.monaco-editor').first()).toBeVisible();

  await page.setViewportSize({ width: 320, height: 720 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
});
