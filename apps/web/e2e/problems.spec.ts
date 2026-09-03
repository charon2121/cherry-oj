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
  let savedTitle: string | undefined;
  let savedStatement: string | undefined;
  let saveRequestCount = 0;
  let releaseFirstSave: () => void = () => {};
  const firstSaveResponse = new Promise<void>((resolve) => {
    releaseFirstSave = resolve;
  });
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
  let version = {
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
    samples: [],
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
  await page.route(`**/api/admin/problems/${problemId}/versions/${versionId}`, async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as {
        title: string;
        statementMarkdown: string;
      };
      savedTitle = body.title;
      savedStatement = body.statementMarkdown;
      saveRequestCount += 1;
      if (saveRequestCount === 1) await firstSaveResponse;
      version = { ...version, title: body.title, rowVersion: version.rowVersion + 1 };
    }
    await success(route, version);
  });
  await page.route(`**/api/admin/problems/${problemId}/test-data`, (route) =>
    success(route, { items: [] }),
  );
  await page.route(
    `**/api/admin/problems/${problemId}/versions/${versionId}/publish-check`,
    (route) =>
      success(route, {
        ready: false,
        environmentId: null,
        checks: [
          { code: 'CONTENT', passed: true, message: '题面完整' },
          { code: 'SAMPLES', passed: true, message: '样例完整' },
          { code: 'LANGUAGE', passed: true, message: '语言配置完整' },
          { code: 'TEST_DATA', passed: false, message: '尚未绑定测试数据' },
          { code: 'DEPLOYMENT', passed: false, message: '尚未部署测试数据' },
          { code: 'CALIBRATION', passed: false, message: '尚未校准' },
        ],
      }),
  );

  await page.goto('/admin/problems?page=1&q=&status=ALL');
  await expect(page.getByRole('heading', { name: '题目管理' })).toBeVisible();
  await expect.poll(() => adminListRequestUrl).toBeTruthy();
  const adminListUrl = new URL(adminListRequestUrl!);
  expect(adminListUrl.searchParams.get('q')).toBeNull();
  expect(adminListUrl.searchParams.get('status')).toBeNull();
  expect(adminListUrl.searchParams.get('page')).toBe('1');
  expect(adminListUrl.searchParams.get('size')).toBe('20');
  await page.getByRole('button', { name: '新建题目' }).click();
  await expect(page.getByRole('dialog', { name: '新建题目草稿' })).toBeVisible();
  await page.getByLabel('题目标题').fill('两数之和');
  await page.getByLabel('题目标识').fill('two-sum');
  await page.getByRole('combobox', { name: '初始难度' }).click();
  await page.getByRole('option', { name: '简单' }).click();
  await page.getByRole('button', { name: '创建草稿' }).click();

  await expect(page).toHaveURL(
    new RegExp(`/admin/problems/${problemId}/versions/${versionId}(?:\\?.*)?$`),
  );
  await expect(page.getByRole('region', { name: '基本信息编辑' })).toBeVisible();
  await expect(page.getByText('没有未保存修改')).toBeVisible();

  await page.getByLabel('题目标题').fill('两数之和（已更新）');
  await expect(page.getByText('有未保存内容')).toBeVisible();
  await page.keyboard.press('Control+s');
  await expect.poll(() => savedTitle).toBe('两数之和（已更新）');
  await page.getByLabel('题目标题').fill('两数之和（保存期间继续编辑）');
  releaseFirstSave();
  await expect(page.getByLabel('题目标题')).toHaveValue('两数之和（保存期间继续编辑）');
  await expect(page.getByText('有未保存内容')).toBeVisible();
  await page.keyboard.press('Control+s');
  await expect.poll(() => savedTitle).toBe('两数之和（保存期间继续编辑）');
  await expect(page.getByText(/已保存/)).toBeVisible();

  await page.getByLabel('题目标题').fill('两数之和（跨步骤保留）');
  await page.getByRole('button', { name: /2\. 题面/ }).click();
  await expect(page).toHaveURL(/step=statement/);
  await expect(page.getByRole('region', { name: '题面编辑' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: '题目正文 Markdown 编辑器' })).toBeVisible();
  await expect(page.locator('.cm-editor').first()).toBeVisible();
  await expect(page.locator('.monaco-editor')).toHaveCount(0);
  await page.getByRole('button', { name: /1\. 基本信息/ }).click();
  await expect(page.getByLabel('题目标题')).toHaveValue('两数之和（跨步骤保留）');
  await page.getByRole('button', { name: /2\. 题面/ }).click();
  const statementEditor = page.getByRole('textbox', { name: '题目正文 Markdown 编辑器' });
  await statementEditor.press('End');
  await statementEditor.pressSequentially('\n补充中文段落');
  await expect(page.getByText('有未保存内容')).toBeVisible();
  await statementEditor.press('Control+s');
  await expect.poll(() => savedStatement).toContain('补充中文段落');
  await expect(page.getByText(/已保存/)).toBeVisible();

  const adminMain = page.locator('#admin-main');
  const beforeStepChange = await adminMain.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
    return {
      scrollTop: element.scrollTop,
      scrollRange: element.scrollHeight - element.clientHeight,
    };
  });
  expect(beforeStepChange.scrollRange).toBeGreaterThan(200);
  expect(beforeStepChange.scrollTop).toBeGreaterThan(200);

  await page.getByRole('button', { name: /3\. 样例/ }).click();
  await expect(page).toHaveURL(/step=samples/);
  await expect(page.getByText('还没有样例')).toBeVisible();
  const stepOffset = await page.getByRole('region', { name: '样例编辑' }).evaluate((element) => {
    const main = document.querySelector<HTMLElement>('#admin-main');
    if (!main) throw new Error('admin main region is missing');
    return element.getBoundingClientRect().top - main.getBoundingClientRect().top;
  });
  expect(stepOffset).toBeGreaterThanOrEqual(76);
  expect(stepOffset).toBeLessThanOrEqual(84);

  await page.setViewportSize({ width: 320, height: 720 });
  await expect(page.getByRole('combobox', { name: '当前步骤' })).toContainText('样例');
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
});
