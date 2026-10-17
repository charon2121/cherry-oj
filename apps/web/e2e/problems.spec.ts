import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
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
  // 题面里不可信的 <img> 不得渲染；页头的品牌标志不算。
  await expect(page.locator('main img')).toHaveCount(0);
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

test('an admin creates a problem and restores the complete workbench', async ({ page }) => {
  let adminListRequestUrl: string | undefined;
  let savedTitle: string | undefined;
  let savedStatement: string | undefined;
  let saveRequestCount = 0;
  let releaseFirstSave: () => void = () => {};
  const firstSaveResponse = new Promise<void>((resolve) => {
    releaseFirstSave = resolve;
  });
  const timestamp = '2026-08-30T01:00:00Z';
  let problem = {
    id: problemId,
    slug: 'two-sum',
    visibility: 'PRIVATE',
    status: 'ACTIVE',
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
    testData: null,
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
  await page.route(`**/api/admin/problems/${problemId}`, async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as {
        title: string;
        statementMarkdown: string;
        difficulty: string;
        tags: string[];
      };
      savedTitle = body.title;
      savedStatement = body.statementMarkdown;
      // ACM 题目没有驱动代码，保存时不能带这个字段。
      expect(body).not.toHaveProperty('judgeTemplate');
      saveRequestCount += 1;
      if (saveRequestCount === 1) await firstSaveResponse;
      // 与服务端一致：保存后返回整道题的最新内容。
      problem = {
        ...problem,
        title: body.title,
        statementMarkdown: body.statementMarkdown,
        difficulty: body.difficulty,
        tags: body.tags,
        rowVersion: problem.rowVersion + 1,
      };
    }
    await success(route, problem);
  });
  await page.route(`**/api/admin/problems/${problemId}/publish-check`, (route) =>
    success(route, {
      ready: false,
      checks: [
        { code: 'CONTENT', passed: true, message: '题面完整' },
        { code: 'SAMPLES', passed: true, message: '样例完整' },
        { code: 'LANGUAGE', passed: true, message: '语言配置完整' },
        { code: 'TEST_DATA', passed: false, message: '还没有测试数据' },
        { code: 'CALIBRATION', passed: false, message: '尚未校准' },
        { code: 'ONLINE_JUDGE_NODE', passed: true, message: '在线判题节点可用' },
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
  await expect(page.getByRole('dialog', { name: '新建题目' })).toBeVisible();
  await page.getByLabel('题目标题').fill('两数之和');
  await page.getByLabel('题目标识').fill('two-sum');
  await page.getByRole('combobox', { name: '初始难度' }).click();
  await page.getByRole('option', { name: '简单' }).click();
  await page.getByRole('button', { name: '创建题目' }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/problems/${problemId}(?:\\?.*)?$`));
  // 一页到底：标题、题面、样例、起始代码都在同一页，没有步骤切换。
  await expect(page.getByRole('textbox', { name: '题目正文' })).toBeVisible();
  await expect(page.getByRole('region', { name: '样例' })).toBeVisible();
  await expect(page.getByText('修改会自动保存')).toBeVisible();

  // 私有题目自动保存；保存进行中继续编辑，等这次完成后接着保存最新内容，不丢输入。
  await page.getByLabel('题目标题').fill('两数之和（已更新）');
  await expect.poll(() => savedTitle).toBe('两数之和（已更新）');
  await expect(page.getByText('正在保存…')).toBeVisible();
  await page.getByLabel('题目标题').fill('两数之和（保存期间继续编辑）');
  releaseFirstSave();
  await expect(page.getByLabel('题目标题')).toHaveValue('两数之和（保存期间继续编辑）');
  await expect.poll(() => savedTitle).toBe('两数之和（保存期间继续编辑）');
  await expect(page.getByText(/已保存/)).toBeVisible();

  // 题面是所见即所得的 Markdown：输入 ## 即成标题，保存的仍是 Markdown 原文。
  const statementEditor = page.getByRole('textbox', { name: '题目正文' });
  await statementEditor.click();
  await page.keyboard.press('ControlOrMeta+End');
  await page.keyboard.press('Enter');
  await page.keyboard.type('## 补充说明');
  await page.keyboard.press('Enter');
  await page.keyboard.type('补充中文段落');
  await expect(statementEditor.getByRole('heading', { name: '补充说明' })).toBeVisible();
  await expect.poll(() => savedStatement).toContain('## 补充说明\n\n补充中文段落');
  await expect(page.locator('.monaco-editor')).toHaveCount(0);

  // 自动保存不改写正在编辑的内容：服务端返回的题目（这里不回写提示）不会冲掉输入，光标也不回跳。
  await page.getByRole('textbox', { name: '提示' }).click();
  await expect(page.getByRole('textbox', { name: '提示' })).toBeFocused();
  await page.keyboard.type('第一行');
  await page.keyboard.press('Enter');
  const savesBeforePause = saveRequestCount;
  await expect.poll(() => saveRequestCount).toBeGreaterThan(savesBeforePause);
  await expect(page.getByText(/已保存/)).toBeVisible();
  await page.keyboard.type('第二行');
  await expect(page.getByRole('textbox', { name: '提示' }).locator('p')).toHaveText([
    '第一行',
    '第二行',
  ]);

  const savesBeforeClear = saveRequestCount;
  await page.getByLabel('题目标题').fill('');
  await expect(page.getByText('标题不能为空，填写之前修改不会保存。')).toBeVisible();
  await page.waitForTimeout(1500);
  expect(saveRequestCount).toBe(savesBeforeClear);
  await page.getByLabel('题目标题').fill('两数之和');
  await expect.poll(() => savedTitle).toBe('两数之和');

  // 属性在右栏原地修改：难度是点开即选的菜单。
  await page.getByRole('button', { name: '难度：简单' }).click();
  await page.getByRole('menuitem', { name: '中等' }).click();
  await expect(page.getByRole('button', { name: '难度：中等' })).toBeVisible();

  // 公开检查里没通过的项直接带到对应的对话框。
  await page.getByRole('button', { name: /公开检查/ }).click();
  await page
    .getByRole('dialog')
    .getByText('还没有测试数据')
    .locator('xpath=..')
    .getByRole('button', { name: '去处理' })
    .click();
  await expect(page).toHaveURL(/panel=test-data/);
  await expect(page.getByRole('dialog', { name: '测试数据' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.setViewportSize({ width: 320, height: 720 });
  await expect(page.getByRole('textbox', { name: '题目正文' })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
});

test('an admin creates a core problem, writes its driver and is told to recalibrate', async ({
  page,
}) => {
  const timestamp = '2026-10-18T01:00:00Z';
  let createdCodeMode: string | undefined;
  let savedDriver: string | undefined;
  let problem = {
    id: problemId,
    slug: 'core-add',
    visibility: 'PRIVATE',
    status: 'ACTIVE',
    codeMode: 'CORE',
    title: '两数相加',
    statementMarkdown: '实现 add。',
    inputDescriptionMarkdown: '两个整数。',
    outputDescriptionMarkdown: '它们的和。',
    constraintsMarkdown: null,
    hintMarkdown: null,
    difficulty: 'EASY',
    tags: [],
    samples: [],
    allowedLanguages: [
      {
        id: 'cpp',
        displayName: 'C++',
        starterCode: 'class Solution {};',
        judgeTemplate: null as string | null,
      },
    ],
    testData: null,
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
  await page.route('**/api/admin/problems?**', (route) =>
    success(
      route,
      { items: [] },
      { pagination: { kind: 'page', page: 1, size: 20, totalElements: 0, totalPages: 0 } },
    ),
  );
  await page.route('**/api/admin/problems', (route) => {
    createdCodeMode = (route.request().postDataJSON() as { codeMode: string }).codeMode;
    return route.fulfill({
      status: 201,
      contentType: 'application/json',
      headers: { Location: `/api/admin/problems/${problemId}`, 'X-Request-Id': requestId },
      body: JSON.stringify({ data: problem, meta: { requestId } }),
    });
  });
  await page.route(`**/api/admin/problems/${problemId}`, async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as { judgeTemplate?: string };
      savedDriver = body.judgeTemplate;
      problem = {
        ...problem,
        allowedLanguages: [
          { ...problem.allowedLanguages[0]!, judgeTemplate: body.judgeTemplate ?? null },
        ],
        rowVersion: problem.rowVersion + 1,
      };
    }
    await success(route, problem);
  });
  await page.route(`**/api/admin/problems/${problemId}/publish-check`, (route) =>
    success(route, {
      ready: false,
      checks: [
        { code: 'CONTENT', passed: true, message: '题面完整' },
        { code: 'SAMPLES', passed: false, message: '至少需要一个样例。' },
        { code: 'LANGUAGE', passed: false, message: '核心代码题需要填写驱动代码。' },
        { code: 'TEST_DATA', passed: false, message: '还没有测试数据' },
        { code: 'CALIBRATION', passed: false, message: '尚未校准' },
        { code: 'ONLINE_JUDGE_NODE', passed: true, message: '在线判题节点可用' },
      ],
    }),
  );

  await page.goto('/admin/problems?page=1&q=&status=ALL');
  await page.getByRole('button', { name: '新建题目' }).click();
  await page.getByLabel('题目标题').fill('两数相加');
  await page.getByLabel('题目标识').fill('core-add');
  await page.getByRole('combobox', { name: '代码模式' }).click();
  await page.getByRole('option', { name: '核心代码' }).click();
  await page.getByRole('button', { name: '创建题目' }).click();
  await expect.poll(() => createdCodeMode).toBe('CORE');

  await expect(page).toHaveURL(new RegExp(`/admin/problems/${problemId}(?:\\?.*)?$`));
  await expect(page.getByRole('button', { name: '核心代码 · C++' })).toBeDisabled();
  const driverSection = page.getByRole('region', { name: '驱动代码' });
  await expect(driverSection).toBeVisible();

  // 驱动代码为空时给出骨架；填入后自动保存，并提示改过的驱动代码需要重新标定。
  await driverSection.getByRole('button', { name: '填入骨架' }).click();
  await expect.poll(() => savedDriver).toContain('#include "solution.cpp"');
  await expect(driverSection.getByRole('button', { name: '填入骨架' })).toHaveCount(0);
  await expect(driverSection.getByRole('status')).toHaveText('已修改，建议重新标定');

  await driverSection.getByRole('button', { name: '重新标定' }).click();
  await expect(page).toHaveURL(/panel=calibration/);
  await expect(page.getByRole('dialog', { name: '运行参考核心代码校准' })).toBeVisible();
});
