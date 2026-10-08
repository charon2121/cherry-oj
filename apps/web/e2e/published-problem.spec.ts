import { expect, type Page, type Route, test } from '@playwright/test';

const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const time = '2026-10-07T01:00:00Z';
const digest = '6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4';
const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';

const testData = {
  digest,
  testcaseCount: 2,
  totalBytes: 16,
  updatedAt: time,
  manifest: { testcaseCount: 2, totalBytes: 16, files: [] },
};

function adminProblem(overrides: object = {}) {
  return {
    id: problemId,
    slug: 'published',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    codeMode: 'ACM',
    title: '已公开题目',
    statementMarkdown: '题面',
    inputDescriptionMarkdown: '输入',
    outputDescriptionMarkdown: '输出',
    constraintsMarkdown: null,
    hintMarkdown: null,
    difficulty: 'EASY',
    tags: [],
    samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: null }],
    allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: 'int main() {}' }],
    testData,
    createdAt: time,
    updatedAt: time,
    publishedAt: time,
    rowVersion: 4,
    ...overrides,
  };
}

async function success(route: Route, data: object) {
  await route.fulfill({
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId } }),
  });
}

/** 一个按路径分发的假后端：题目只有一份内容，没有版本、草稿或部署。 */
async function mockBackend(
  page: Page,
  state: { problem: object; requests: { method: string; path: string; body: unknown }[] },
) {
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/session')
      return success(route, {
        authenticated: true,
        user: {
          id: problemId,
          username: 'admin',
          role: 'ADMIN',
          status: 'ACTIVE',
          passwordChangeRequired: false,
          createdAt: time,
          updatedAt: time,
          rowVersion: 0,
        },
      });
    if (path === '/api/auth/csrf')
      return success(route, {
        token: 'csrf-token-that-is-long-enough',
        headerName: 'X-CSRF-Token',
      });
    if (request.method() !== 'GET') {
      state.requests.push({
        method: request.method(),
        path,
        body: path.endsWith('/test-data') ? undefined : (request.postDataJSON() as unknown),
      });
    }
    if (path === `/api/admin/problems/${problemId}/publish-check`)
      return success(route, {
        ready: true,
        checks: [
          'CONTENT',
          'SAMPLES',
          'LANGUAGE',
          'TEST_DATA',
          'CALIBRATION',
          'ONLINE_JUDGE_NODE',
        ].map((code) => ({ code, passed: true, message: code })),
      });
    if (path === `/api/admin/problems/${problemId}/test-data` && request.method() === 'PUT')
      return success(route, { ...testData, digest: 'b'.repeat(64), testcaseCount: 3 });
    if (path === `/api/admin/problems/${problemId}/unpublish`) {
      state.problem = adminProblem({ visibility: 'PRIVATE', rowVersion: 5 });
      return success(route, state.problem);
    }
    if (path === `/api/admin/problems/${problemId}` && request.method() === 'PATCH') {
      const body = request.postDataJSON() as { title: string; rowVersion: number };
      state.problem = { ...state.problem, title: body.title, rowVersion: body.rowVersion + 1 };
      return success(route, state.problem);
    }
    if (path === `/api/admin/problems/${problemId}`) return success(route, state.problem);
    throw new Error(`Unexpected API ${request.method()} ${path}`);
  });
}

test('a public problem is edited in place and the change is saved without any draft step', async ({
  page,
}) => {
  const state = {
    problem: adminProblem(),
    requests: [] as { method: string; path: string; body: unknown }[],
  };
  await mockBackend(page, state);
  await page.goto(`/admin/problems/${problemId}`);

  await expect(page.getByText('这道题已公开')).toBeVisible();
  await expect(page.getByText('保存会立即对学生生效')).toBeVisible();
  const title = page.getByLabel('题目标题');
  await expect(title).toBeEnabled();
  await title.fill('已公开题目（改了就是改了）');
  await page.getByRole('button', { name: '保存', exact: true }).click();

  await expect.poll(() => state.requests.find((item) => item.method === 'PATCH')).toBeTruthy();
  const patch = state.requests.find((item) => item.method === 'PATCH');
  expect(patch?.path).toBe(`/api/admin/problems/${problemId}`);
  expect(patch?.body).toMatchObject({
    slug: 'published',
    title: '已公开题目（改了就是改了）',
    rowVersion: 4,
  });
  expect(patch?.body).not.toHaveProperty('changeSummary');
  await expect(page.getByText(/已保存/)).toBeVisible();
});

test('an admin can take a public problem back to private', async ({ page }) => {
  const state = {
    problem: adminProblem(),
    requests: [] as { method: string; path: string; body: unknown }[],
  };
  await mockBackend(page, state);
  await page.goto(`/admin/problems/${problemId}?step=publish`);

  await expect(page.getByText('题目已公开。取消公开后变回私有')).toBeVisible();
  await page.getByRole('button', { name: '取消公开', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: '取消公开', exact: true }).click();

  await expect
    .poll(() => state.requests.find((item) => item.path.endsWith('/unpublish')))
    .toBeTruthy();
  expect(state.requests.find((item) => item.path.endsWith('/unpublish'))?.body).toEqual({
    rowVersion: 4,
  });
  await expect(
    page
      .getByRole('region', { name: '检查与公开编辑' })
      .getByRole('button', { name: '公开题目', exact: true }),
  ).toBeVisible();
});

test('replacing the test data uses PUT and warns that the old calibration is stale', async ({
  page,
}) => {
  const state = {
    problem: adminProblem(),
    requests: [] as { method: string; path: string; body: unknown }[],
  };
  await mockBackend(page, state);
  await page.goto(`/admin/problems/${problemId}?step=test-and-calibrate`);

  await expect(page.getByText('当前测试数据', { exact: true })).toBeVisible();
  await page.getByLabel('测试数据 ZIP').setInputFiles({
    name: 'data.zip',
    mimeType: 'application/zip',
    buffer: Buffer.from('PK'),
  });

  await expect.poll(() => state.requests.find((item) => item.method === 'PUT')).toBeTruthy();
  expect(state.requests.find((item) => item.method === 'PUT')?.path).toBe(
    `/api/admin/problems/${problemId}/test-data`,
  );
  await expect(page.getByText('旧的校准已过期，请重新校准')).toBeVisible();
});

test('an archived problem is read-only', async ({ page }) => {
  const state = {
    problem: adminProblem({ status: 'ARCHIVED', visibility: 'PRIVATE' }),
    requests: [] as { method: string; path: string; body: unknown }[],
  };
  await mockBackend(page, state);
  await page.goto(`/admin/problems/${problemId}?step=test-and-calibrate`);

  await expect(page.getByText('这道题已归档，当前为只读状态')).toBeVisible();
  await expect(page.getByLabel('测试数据 ZIP')).toBeDisabled();
  await expect(page.getByRole('button', { name: '运行参考程序校准', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled();
});
