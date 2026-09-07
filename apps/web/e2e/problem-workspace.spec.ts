import type { BrowserContext, Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const nextVersionId = '873d4d76-103e-4978-a592-dd05ba776780';
const userId = 'd0e35399-6487-4ac8-8138-8d5bd60eb003';
const secondUserId = 'c8fa9ec9-0709-445c-9e72-185c690e2eba';
const starterCode = 'int main() { return 0; }';
const draftPrefix = 'cherry-oj.code-draft.v1:';

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

async function mockProblem(target: Page | BrowserContext) {
  await target.route('**/api/problems/workspace-sum', (route) => success(route, problem()));
}

function editor(page: Page) {
  return page.getByRole('textbox', { name: /^C\+\+ 代码编辑器/ });
}

async function pressEditorShortcut(page: Page, key: string) {
  // Desktop Chrome fixture advertises Windows even when Playwright runs on macOS.
  // Monaco uses the user agent and CodeMirror uses platform for Cmd/Ctrl.
  const usesMonaco = (await page.locator('.monaco-editor').count()) > 0;
  const modifier = await page.evaluate(
    (monaco) =>
      /Mac|iPhone|iPad|iPod/.test(monaco ? navigator.userAgent : navigator.platform)
        ? 'Meta'
        : 'Control',
    usesMonaco,
  );
  await editor(page).press(`${modifier}+${key}`);
}

async function replaceCode(page: Page, source: string) {
  await expect(editor(page)).toBeVisible();
  if (await page.locator('.monaco-editor .view-lines').isVisible()) {
    await page.locator('.monaco-editor .view-lines').click({ position: { x: 5, y: 5 } });
  } else {
    await editor(page).click();
  }
  await pressEditorShortcut(page, 'a');
  if (source === '') await page.keyboard.press('Backspace');
  else await page.keyboard.insertText(source);
}

async function renderedCode(page: Page) {
  return page.locator('.monaco-editor .view-lines').innerText();
}

async function expectCode(page: Page, source: string) {
  await expect
    .poll(async () => (await renderedCode(page)).replaceAll('\u00a0', ' ').trim())
    .toBe(source.trim());
}

async function draftSources(page: Page, owner = userId, version = versionId) {
  return page.evaluate(
    ({ prefix, owner, problemId, version }) =>
      Object.entries(localStorage)
        .filter(([key]) => key.startsWith(`${prefix}${owner}:${problemId}:${version}:cpp:`))
        .map(([, serialized]) => {
          if (typeof serialized !== 'string') return null;
          const record: unknown = JSON.parse(serialized);
          return typeof record === 'object' && record !== null && 'source' in record
            ? record.source
            : null;
        }),
    { prefix: draftPrefix, owner, problemId, version },
  );
}

async function expectSaved(page: Page, source: string, owner = userId, version = versionId) {
  await expect.poll(() => draftSources(page, owner, version)).toContain(source);
  await expect(page.getByText('已保存到本机', { exact: true })).toBeVisible();
}

async function refocusAfterStaleTime(page: Page, nextTime: number) {
  // Query 的真实 focus manager 监听 visibilitychange；只推进时钟和发浏览器事件，
  // 不访问应用的 Query cache 或调用组件内部方法。
  await page.clock.setFixedTime(nextTime);
  await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')));
}

for (const role of ['USER', 'ADMIN'] as const) {
  test(`${role} can edit and restore drafts without automatically submitting`, async ({ page }) => {
    const requests: string[] = [];
    page.on('request', (request) => requests.push(request.url()));
    await mockProblem(page);
    await page.route('**/api/auth/session', (route) => success(route, session(role)));
    await page.goto('/problems/workspace-sum');

    await expect(page.getByRole('heading', { name: '题意' })).toBeVisible();
    await expect(editor(page)).toBeEditable();
    await expectCode(page, starterCode);
    await expect(page.getByRole('link', { name: /登录后.*(答题|编写)/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '运行', exact: true })).toBeDisabled();
    await expect(page.getByRole('button', { name: '提交', exact: true })).toBeEnabled();
    await expect(page.getByText(/^自定义运行暂未开放。/)).toBeVisible();

    const source = 'int answer = 42;';
    await replaceCode(page, source);
    await expectSaved(page, source);
    await page.reload();
    await expectCode(page, source);
    await replaceCode(page, '');
    await expectSaved(page, '');
    await page.reload();
    await expectCode(page, '');

    await pressEditorShortcut(page, 'Enter');
    await editor(page).press('F5');
    await expect(page.getByText(/^自定义运行暂未开放。/)).toBeVisible();
    expect(
      requests.filter((url) => /\/(?:run|submissions?|judge|sandbox|blobs)(?:[/?]|$)/.test(url)),
    ).toEqual([]);
    expect(requests.filter((url) => new URL(url).origin !== new URL(page.url()).origin)).toEqual(
      [],
    );
  });
}

test('a guest sees a read-only starter and can return to the original problem after login', async ({
  page,
}) => {
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, { authenticated: false }));
  await page.goto('/problems/workspace-sum');
  await expectCode(page, starterCode);
  await expect(editor(page)).not.toBeEditable();
  await replaceCode(page, 'this must not replace the starter');
  await expectCode(page, starterCode);
  const login = page.getByRole('link', { name: /登录后.*(答题|编写)/ });
  await expect(login).toBeVisible();
  await login.click();
  const loginUrl = new URL(page.url());
  expect(loginUrl.pathname).toBe('/login');
  expect(loginUrl.searchParams.get('returnTo')).toBe('/problems/workspace-sum');
  expect(
    await page.evaluate(
      (prefix) => Object.keys(localStorage).filter((key) => key.startsWith(prefix)),
      draftPrefix,
    ),
  ).toEqual([]);
});

test('pending and failed session lookup never claim that the visitor is logged out', async ({
  page,
}) => {
  let releaseSession: () => void = () => {};
  const pending = new Promise<void>((resolve) => {
    releaseSession = resolve;
  });
  await mockProblem(page);
  await page.route('**/api/auth/session', async (route) => {
    await pending;
    await route.abort('failed');
  });
  await page.goto('/problems/workspace-sum');
  const codePane = page.getByRole('region', { name: '代码', exact: true });
  await expect(codePane.getByText('正在确认登录状态…', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /登录后.*(答题|编写)/ })).toHaveCount(0);
  releaseSession();
  await expect(codePane.getByText(/(无法|未能).*登录状态|登录状态.*(失败|无法)/)).toBeVisible();
  await expect(page.getByRole('link', { name: /登录后.*(答题|编写)/ })).toHaveCount(0);
  await expect(editor(page)).not.toBeEditable();
  await replaceCode(page, 'this must not replace the starter');
  await expectCode(page, starterCode);
});

test('a password-change-required account receives the password flow before editing', async ({
  page,
}) => {
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session('USER', userId, true)));
  await page.goto('/problems/workspace-sum');
  await expect(page.getByRole('link', { name: /修改密码/ })).toHaveAttribute(
    'href',
    '/account/password',
  );
  await expect(editor(page)).not.toBeEditable();
  await replaceCode(page, 'this must not replace the starter');
  await expectCode(page, starterCode);
  await expect(page.getByRole('link', { name: /登录后.*(答题|编写)/ })).toHaveCount(0);
});

test('theme, search, undo and narrow-pane switching preserve the same code', async ({ page }) => {
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.goto('/problems/workspace-sum');
  const source = 'int answer = 41;';
  await replaceCode(page, source);
  await expectSaved(page, source);
  const previousScheme = await page.locator('html').getAttribute('data-color-scheme');
  await page.getByRole('button', { name: /^切换到/ }).click();
  await expect(page.locator('html')).not.toHaveAttribute('data-color-scheme', previousScheme ?? '');
  await pressEditorShortcut(page, 'z');
  // Monaco may group a typed replacement by word; the meaningful contract is that
  // switching the theme retains undo history and redo restores the complete code.
  await expect
    .poll(async () => (await renderedCode(page)).replaceAll('\u00a0', ' ').trim())
    .not.toBe(source);
  await pressEditorShortcut(page, 'Shift+z');
  await expectCode(page, source);
  await pressEditorShortcut(page, 'f');
  await expect(page.locator('.monaco-editor .find-widget')).toBeVisible();
  await page.keyboard.press('Escape');
  await editor(page).press('Tab');
  await expect(editor(page)).not.toBeFocused();

  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expectCode(page, source);
  await page.getByRole('tab', { name: '题目', exact: true }).click();
  await expect(page.getByRole('heading', { name: '题意' })).toBeVisible();
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expectCode(page, source);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await expect(editor(page)).toBeVisible();
  await expect(page.getByText(/^自定义运行暂未开放。/)).toBeVisible();
});

test('a touch phone uses the lightweight editor and keeps its draft between panes', async ({
  browser,
}) => {
  const context = await browser.newContext({
    baseURL: 'http://127.0.0.1:4173',
    viewport: { width: 390, height: 844 },
    hasTouch: true,
    isMobile: true,
  });
  try {
    await mockProblem(context);
    await context.route('**/api/auth/session', (route) => success(route, session()));
    const page = await context.newPage();
    await page.goto('/problems/workspace-sum');
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    await expect(page.locator('.cm-editor')).toBeVisible();
    await expect(page.locator('.monaco-editor')).toHaveCount(0);
    const source = 'int mobileAnswer = 3;';
    await replaceCode(page, source);
    await expectSaved(page, source);
    await page.getByRole('tab', { name: '题目', exact: true }).click();
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    await expect(page.locator('.cm-content')).toHaveText(source);
    await page.reload();
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    await expect(page.locator('.cm-content')).toHaveText(source);
  } finally {
    await context.close();
  }
});

test('refreshing a published version never replaces an open buffer without an explicit switch', async ({
  page,
}) => {
  let currentProblem = problem();
  await page.route('**/api/problems/workspace-sum', (route) => success(route, currentProblem));
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.goto('/problems/workspace-sum');
  const source = 'int oldVersionAnswer = 7;';
  await replaceCode(page, source);
  await expectSaved(page, source);

  currentProblem = problem(nextVersionId, 2, 'int main() { return 2; }');
  await refocusAfterStaleTime(page, Date.now() + 60_000);
  await expect(page.getByRole('button', { name: '打开新版本' })).toBeVisible();
  await expectCode(page, source);
  await page.getByRole('button', { name: '打开新版本' }).click();
  await expectCode(page, currentProblem.allowedLanguages[0]?.starterCode ?? '');
  await replaceCode(page, 'int newVersionAnswer = 8;');
  await expectSaved(page, 'int newVersionAnswer = 8;', userId, nextVersionId);
  expect(await draftSources(page)).toContain(source);
});

test('session expiry hides the previous account draft and a different account starts independently', async ({
  page,
}) => {
  let currentSession: object = session();
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, currentSession));
  await page.goto('/problems/workspace-sum');
  const source = 'int privateAnswer = 23;';
  await replaceCode(page, source);
  await expectSaved(page, source);
  const now = Date.now();

  currentSession = { authenticated: false };
  await refocusAfterStaleTime(page, now + 60_000);
  await expect(page.getByRole('link', { name: /登录后.*(答题|编写)/ })).toBeVisible();
  await expectCode(page, starterCode);
  await expect(editor(page)).not.toBeEditable();
  await replaceCode(page, 'this must not replace the starter');
  await expectCode(page, starterCode);
  expect(await draftSources(page)).toContain(source);

  currentSession = session('USER', secondUserId);
  await refocusAfterStaleTime(page, now + 120_000);
  await expect(editor(page)).toBeEditable();
  await expectCode(page, starterCode);
  await replaceCode(page, 'int secondAccountAnswer = 24;');
  await expectSaved(page, 'int secondAccountAnswer = 24;', secondUserId);

  currentSession = session();
  await refocusAfterStaleTime(page, now + 180_000);
  await expectCode(page, source);
});

test('reloading after a failed editor chunk restores saved code and starts a local worker', async ({
  page,
}) => {
  let rejectEditorChunk = false;
  const workerUrls: string[] = [];
  page.on('worker', (worker) => workerUrls.push(worker.url()));
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.route(/\/assets\/code-editor-runtime-[^/]+\.js(?:\?.*)?$/, async (route) => {
    if (rejectEditorChunk) await route.abort('failed');
    else await route.continue();
  });
  await page.goto('/problems/workspace-sum');
  const source = 'int answer = 42;';
  await replaceCode(page, source);
  await expectSaved(page, source);
  rejectEditorChunk = true;
  await page.reload();
  await expect(page.getByRole('button', { name: '刷新页面重试' })).toBeVisible();
  expect(await draftSources(page)).toContain(source);
  rejectEditorChunk = false;
  await page.getByRole('button', { name: '刷新页面重试' }).click();
  await expectCode(page, source);
  await editor(page).press('Control+Space');
  await expect.poll(() => workerUrls.length).toBeGreaterThan(0);
  expect(workerUrls.every((url) => new URL(url).origin === new URL(page.url()).origin)).toBe(true);
});

test('restoring starter code requires a decision and preserves the draft when cancelled', async ({
  page,
}) => {
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.goto('/problems/workspace-sum');
  const source = 'int keepUntilConfirmed = 99;';
  await replaceCode(page, source);
  await expectSaved(page, source);
  await page.getByRole('button', { name: '恢复起始代码', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '恢复起始代码？' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: '取消', exact: true }).click();
  await expectCode(page, source);
  await page.getByRole('button', { name: '恢复起始代码', exact: true }).click();
  await dialog.getByRole('button', { name: '确认恢复', exact: true }).click();
  await expectCode(page, starterCode);
  await expectSaved(page, starterCode);
  await page.reload();
  await expectCode(page, starterCode);
});

test('storage failure leaves code available and protects navigation until it is saved', async ({
  page,
}) => {
  await page.addInitScript((prefix) => {
    // The fault injector restores the original Storage receiver with call(this, ...).
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const originalWrite = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key: string, value: string) {
      if (key.startsWith(prefix))
        throw new DOMException('Storage quota test', 'QuotaExceededError');
      originalWrite.call(this, key, value);
    };
  }, draftPrefix);
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.goto('/problems/workspace-sum');
  const source = 'int unsavedButAvailable = 11;';
  await replaceCode(page, source);
  await expect(page.getByText('保存失败，请复制备份', { exact: true })).toBeVisible();
  await expectCode(page, source);
  await expect(page.getByText('已保存到本机', { exact: true })).toHaveCount(0);
  expect(await draftSources(page)).toEqual([]);
  await page.getByRole('link', { name: '题库', exact: true }).last().click();
  const dialog = page.getByRole('dialog', { name: '代码还未保存到本机' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: '返回编辑', exact: true }).click();
  await expect(page).toHaveURL(/\/problems\/workspace-sum$/);
  await expectCode(page, source);
  await expect(page.getByRole('button', { name: '复制代码', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '下载代码', exact: true })).toBeVisible();
});

test('the public library does not request Monaco before opening a coding workspace', async ({
  page,
}) => {
  const editorRequests: string[] = [];
  page.on('request', (request) => {
    if (
      /\/(?:code-editor-runtime|editor\.worker|cpp)-[^/]+\.(?:js|css)(?:\?.*)?$/.test(request.url())
    ) {
      editorRequests.push(request.url());
    }
  });
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.route('**/api/problems?**', (route) =>
    success(
      route,
      { items: [] },
      {
        pagination: { kind: 'cursor', nextCursor: null, hasMore: false },
      },
    ),
  );
  await page.goto('/problems?sort=UPDATED_DESC&size=20');
  await expect(page.getByText('题库还没有公开题目')).toBeVisible();
  expect(editorRequests).toEqual([]);
});

test('a 200-percent equivalent desktop viewport keeps editing and lower controls reachable', async ({
  browser,
}) => {
  // A 1280 × 720 desktop at 200% zoom has a 640 × 360 CSS viewport. This models
  // that layout with DPR 2; it does not change the browser's actual zoom setting.
  const context = await browser.newContext({
    baseURL: 'http://127.0.0.1:4173',
    viewport: { width: 640, height: 360 },
    deviceScaleFactor: 2,
    hasTouch: false,
    isMobile: false,
  });
  try {
    await mockProblem(context);
    await context.route('**/api/auth/session', (route) => success(route, session()));
    const page = await context.newPage();
    await page.goto('/problems/workspace-sum');
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    await expect(editor(page)).toBeInViewport({ ratio: 1 });
    await expect(editor(page)).toBeEditable();
    const lineHeight = await page
      .locator('.monaco-editor .view-line')
      .first()
      .evaluate((line) => line.getBoundingClientRect().height);
    // Being focusable is insufficient when stacked toolbars leave only half a
    // line: retain room to read multiple lines and scroll the surrounding pane.
    await expect
      .poll(
        async () =>
          (await page.locator('[data-slot="code-editor-host"]').boundingBox())?.height ?? 0,
      )
      .toBeGreaterThanOrEqual(lineHeight * 3);
    const source = 'int zoomedAnswer = 200;';
    await replaceCode(page, source);
    await expectSaved(page, source);
    await expectCode(page, source);
    await page.screenshot({ path: '/tmp/cherry-work041-visual/zoom-equivalent-editor.png' });

    // Wheel near the pane edge rather than programmatically scrolling hidden
    // containers: footer actions must be reachable by the user as well.
    const pane = page.getByRole('tabpanel', { name: '代码', exact: true });
    const box = await pane.boundingBox();
    if (!box) throw new Error('The code pane has no visible layout box.');
    await page.mouse.move(box.x + box.width - 4, Math.min(box.y + 8, 348));
    await expect(async () => {
      await page.mouse.wheel(0, 240);
      await expect(page.getByRole('button', { name: '提交', exact: true })).toBeInViewport({
        ratio: 1,
        timeout: 250,
      });
    }).toPass({ timeout: 5000 });
    await expect(page.getByRole('button', { name: '运行', exact: true })).toBeInViewport({
      ratio: 1,
    });
    await expect(page.getByText('已保存到本机', { exact: true })).toBeInViewport({ ratio: 1 });
    await expect(page.getByText(/^自定义运行暂未开放。/)).toBeInViewport({ ratio: 1 });
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
      .toBeLessThanOrEqual(1);
    await page.screenshot({ path: '/tmp/cherry-work041-visual/zoom-equivalent.png' });
  } finally {
    await context.close();
  }
});

const submissionId = '01a079c1-c530-7962-a5ad-f09adb0a0261';
function submission(status: 'PENDING' | 'DONE' = 'DONE') {
  return {
    id: submissionId,
    problemId,
    problemVersionId: versionId,
    problemVersionNo: 1,
    problemTitle: '求和练习',
    languageId: 'cpp',
    status,
    createdAt: '2026-09-07T01:00:00Z',
    ...(status === 'DONE'
      ? {
          verdict: 'WA',
          cpuNs: 1230000,
          memoryBytes: 1024,
          passedCount: 1,
          executedCount: 2,
          totalCount: 3,
        }
      : {}),
  };
}
async function missingSubmission(route: Route) {
  await route.fulfill({
    status: 404,
    contentType: 'application/problem+json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({
      type: 'about:blank',
      title: '提交不存在',
      status: 404,
      code: 'SUBMISSION_NOT_FOUND',
      meta: { requestId },
    }),
  });
}
async function submissionSetup(page: Page) {
  await mockProblem(page);
  await page.route('**/api/auth/session', (route) => success(route, session()));
  await page.route('**/api/auth/csrf', (route) =>
    success(route, { token: 'test-csrf-token-long-enough', headerName: 'X-CSRF-Token' }),
  );
  await page.route('**/api/submission-requests/*', missingSubmission);
}

test('formal submission freezes code, restores a result on refresh, and stops at DONE', async ({
  page,
}) => {
  await submissionSetup(page);
  const posts: { key: string | undefined; body: unknown }[] = [];
  let reads = 0;
  await page.route('**/api/submissions', async (route) => {
    posts.push({
      key: route.request().headers()['idempotency-key'],
      body: route.request().postDataJSON() as unknown,
    });
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      headers: { 'X-Request-Id': requestId, Location: `/api/submissions/${submissionId}` },
      body: JSON.stringify({ data: submission('PENDING'), meta: { requestId } }),
    });
  });
  await page.route(`**/api/submissions/${submissionId}`, (route) => {
    reads++;
    return success(route, submission());
  });
  await page.goto('/problems/workspace-sum');
  await replaceCode(page, 'int answer = 42;');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect(page.getByText('WA · 答案错误', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`submissionId=${submissionId}`));
  expect(posts).toHaveLength(1);
  expect(posts[0]?.body).toEqual({
    problemId,
    expectedProblemVersionId: versionId,
    languageId: 'cpp',
    source: 'int answer = 42;',
  });
  await replaceCode(page, 'int answer = 99;');
  await expectSaved(page, 'int answer = 99;');
  await page.reload();
  await expect(page.getByText('通过 1 / 3', { exact: true })).toBeVisible();
  await expectCode(page, 'int answer = 99;');
  expect(posts).toHaveLength(1);
  const doneReads = reads;
  await page.waitForTimeout(2400);
  expect(reads).toBe(doneReads);
  for (let theme = 0; theme < 2; theme++) {
    const scheme = await page.locator('html').getAttribute('data-color-scheme');
    await page.screenshot({ path: `/tmp/work002-visual/result-${scheme}.png` });
    await page.getByRole('button', { name: /^切换到/ }).click();
  }
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await page.getByRole('region', { name: '本次提交结果' }).scrollIntoViewIfNeeded();
  await expect(page.getByText('WA · 答案错误', { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await page.screenshot({ path: '/tmp/work002-visual/result-narrow-forced.png' });
});

test('a lost response survives a published version change and retries the original code and key', async ({
  page,
}) => {
  await submissionSetup(page);
  const posts: { key: string | undefined; body: unknown }[] = [];
  await page.route('**/api/submissions', async (route) => {
    posts.push({
      key: route.request().headers()['idempotency-key'],
      body: route.request().postDataJSON() as unknown,
    });
    if (posts.length === 1) return route.abort('failed');
    return success(route, submission());
  });
  await page.route(`**/api/submissions/${submissionId}`, (route) => success(route, submission()));
  await page.goto('/problems/workspace-sum');
  await replaceCode(page, 'int original = 1;');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原代码重试同一次提交' })).toBeEnabled();
  await replaceCode(page, 'int edited = 2;');
  await expectSaved(page, 'int edited = 2;');
  await page.route('**/api/problems/workspace-sum', (route) =>
    success(route, problem(nextVersionId, 2)),
  );
  await page.reload();
  await expect(page.getByRole('button', { name: '用原代码重试同一次提交' })).toBeEnabled();
  expect(posts).toHaveLength(1);
  await page.getByRole('button', { name: '用原代码重试同一次提交' }).click();
  await expect(page.getByText('WA · 答案错误', { exact: true })).toBeVisible();
  expect(posts).toHaveLength(2);
  expect(posts[1]).toEqual(posts[0]);
  await expectCode(page, starterCode);
});

test('CSRF refresh keeps the editor owner precondition after another tab switches account', async ({
  page,
}) => {
  await submissionSetup(page);
  let posts = 0;
  await page.route('**/api/submissions', async (route) => {
    posts++;
    expect(route.request().headers()['x-expected-user-id']).toBe(userId);
    const csrf = posts === 1;
    await route.fulfill({
      status: csrf ? 403 : 409,
      contentType: 'application/problem+json',
      headers: { 'X-Request-Id': requestId },
      body: JSON.stringify({
        type: 'about:blank',
        status: csrf ? 403 : 409,
        code: csrf ? 'CSRF_REJECTED' : 'SESSION_CHANGED',
        title: csrf ? 'CSRF 已过期' : '登录账号已切换',
        detail: csrf ? '请刷新 CSRF' : '当前登录账号与编辑器所属账号不同。',
        meta: { requestId },
      }),
    });
  });
  await page.goto('/problems/workspace-sum');
  await expectCode(page, starterCode);
  // The server session now belongs to B while this editor still displays A's buffer.
  await page.route('**/api/auth/session', (route) => success(route, session('USER', secondUserId)));
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect(page.getByText(/当前登录账号与编辑器所属账号不同。/)).toBeVisible();
  expect(posts).toBe(2);
  await expect(page).not.toHaveURL(/submissionId=/);
});
