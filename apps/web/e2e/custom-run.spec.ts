import { expect, type Page, type Route, test } from '@playwright/test';
const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const userId = 'd0e35399-6487-4ac8-8138-8d5bd60eb003';
const starterCode = 'int main() { return 0; }';
async function success(route: Route, data: object, meta: object = {}) {
  await route.fulfill({
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId, ...meta } }),
  });
}

function session(role: 'USER' | 'ADMIN' = 'USER', id = userId, passwordChangeRequired = false) {
  return {
    authenticated: true,
    user: {
      id,
      username: role === 'ADMIN' ? 'workspace-admin' : 'workspace-user',
      role,
      status: 'ACTIVE',
      passwordChangeRequired,
      createdAt: '2026-09-07T01:00:00Z',
      updatedAt: '2026-09-07T01:00:00Z',
      rowVersion: 0,
    },
  };
}

function problem(problemVersionId = versionId, versionNo = 1, source = starterCode) {
  return {
    problemId,
    problemVersionId,
    versionNo,
    slug: 'workspace-sum',
    codeMode: 'ACM',
    title: '求和练习：用于验证左右读题与编码的长中文题目标题',
    difficulty: 'EASY',
    tags: ['基础', '输入输出'],
    statementMarkdown: '# 题意\n\n计算两个整数的和。',
    inputDescriptionMarkdown: '输入两个整数 a 和 b。',
    outputDescriptionMarkdown: '输出 a 与 b 的和。',
    constraintsMarkdown: '每个整数的绝对值不超过一千。',
    hintMarkdown: '使用标准输入与标准输出。',
    samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: '一加二等于三。' }],
    allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: source }],
  };
}

const result = {
  problemId,
  problemVersionId: versionId,
  problemVersionNo: 1,
  languageId: 'cpp',
  status: 'COMPLETED',
  cpuNs: 1000000,
  memoryBytes: 4096,
  stdout: { text: '3\n', capturedBytes: 2, truncated: false },
  stderr: { text: 'debug\n', capturedBytes: 6, truncated: false },
  effectiveLimits: { cpuNs: 1000000000, memoryBytes: 268435456, clockNs: 3000000000 },
};
async function setup(page: Page) {
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.route('**/api/auth/csrf', (route) =>
    success(route, { token: 'test-csrf-token-0123456789', headerName: 'X-CSRF-Token' }),
  );
  await page.route('**/api/problems/workspace-sum', (route) => success(route, problem()));
  await page.goto('/problems/workspace-sum');
  await expect(page.getByRole('button', { name: '运行', exact: true })).toBeEnabled();
}
const input = (page: Page) => page.getByRole('textbox', { name: '自定义输入数据', exact: true });
test('empty input snapshot, edits during execution and tabs never resubmit or steal focus', async ({
  page,
}) => {
  let count = 0;
  let release: () => void = () => {};
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/custom-runs', async (route) => {
    count++;
    expect(route.request().postDataJSON()).toMatchObject({
      inputText: '',
      source: starterCode,
      expectedProblemVersionId: versionId,
    });
    expect(route.request().headers()['x-expected-user-id']).toBe(userId);
    await pending;
    await success(route, result);
  });
  await setup(page);
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect.poll(() => count).toBe(1);
  await expect(page.getByRole('button', { name: '正在运行…', exact: true })).toBeDisabled();
  await page.getByRole('tab', { name: '自定义输入', exact: true }).click();
  await input(page).fill('changed');
  release();
  await expect(page.getByRole('button', { name: '运行', exact: true })).toBeEnabled();
  await expect(input(page)).toBeVisible();
  await page.getByRole('tab', { name: '运行结果', exact: true }).click();
  await expect(page.getByText('运行完成（未校验答案）', { exact: true })).toBeVisible();
  await expect(page.getByText('代码或输入已修改，这些修改尚未运行。')).toBeVisible();
  await expect(page.getByRole('textbox', { name: '错误输出', exact: true })).toContainText('debug');
  expect(count).toBe(1);
  const codeEditor = page.getByRole('textbox', { name: /^C\+\+ 代码编辑器/ });
  await expect(codeEditor).toBeVisible();
  const editorBox = await page.locator('.monaco-editor').boundingBox();
  expect(editorBox?.height ?? 0).toBeGreaterThan(100);
  await page.screenshot({ path: '/tmp/cherry-work044-desktop.png' });
  await page.getByRole('button', { name: /^切换到/ }).click();
  await page.screenshot({ path: '/tmp/cherry-work044-light.png', animations: 'disabled' });
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await expect(page.getByRole('tab', { name: '运行结果', exact: true })).toBeVisible();
  await page.emulateMedia({ forcedColors: 'none' });
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.screenshot({ path: '/tmp/cherry-work044-mobile.png' });
  await page.reload();
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect(input(page)).toHaveText('');
});
test('replace and clear require confirmation; errors show retry delay without automatic POST', async ({
  page,
}) => {
  let count = 0;
  await page.route('**/api/custom-runs', (route) => {
    count++;
    return route.fulfill({
      status: 429,
      contentType: 'application/problem+json',
      headers: { 'Retry-After': '12', 'X-Request-Id': requestId },
      body: JSON.stringify({
        type: 'urn:cherry-oj:problem:too-many-requests',
        title: '请求过于频繁',
        detail: '请稍后重试。',
        status: 429,
        code: 'TOO_MANY_REQUESTS',
        instance: `urn:cherry-oj:request:${requestId}`,
        meta: { requestId },
      }),
    });
  });
  await setup(page);
  await input(page).fill('keep me');
  await page.getByRole('button', { name: '清空输入', exact: true }).click();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(input(page)).toContainText('keep me');
  await page.getByRole('combobox', { name: '使用样例' }).click();
  await page.getByRole('option', { name: '样例 1', exact: true }).click();
  await page.getByRole('button', { name: '确认替换', exact: true }).click();
  await expect(input(page)).toContainText('1 2');
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect(
    page.getByRole('tabpanel', { name: '运行结果', exact: true }).getByRole('alert'),
  ).toContainText('12 秒');
  expect(count).toBe(1);
});

test('switching accounts clears private input and discards a late result', async ({ page }) => {
  let release: () => void = () => {};
  let received = false;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/custom-runs', async (route) => {
    received = true;
    await pending;
    await success(route, result);
  });
  await setup(page);
  await input(page).fill('private input');
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect.poll(() => received).toBe(true);
  await page.route('**/api/auth/session', (route) =>
    success(route, session('USER', 'c8fa9ec9-0709-445c-9e72-185c690e2eba')),
  );
  await page.clock.setFixedTime(Date.now() + 60000);
  await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')));
  await expect(input(page)).toBeVisible();
  await expect(input(page)).toHaveText('');
  release();
  await page.getByRole('tab', { name: '运行结果', exact: true }).click();
  await expect(page.getByText('填写输入后点击运行，结果会显示在这里。')).toBeVisible();
  await expect(page.getByText('运行完成（未校验答案）', { exact: true })).toHaveCount(0);
});
