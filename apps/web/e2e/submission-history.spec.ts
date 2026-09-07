import { expect, type Page, type Route, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const userId = 'd0e35399-6487-4ac8-8138-8d5bd60eb003';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const oldVersion = '873d4d76-103e-4978-a592-dd05ba776780';
const submissionId = '273d4d76-103e-4978-a592-dd05ba776781';
const historicalSource = '// historical attempt\nint main() { return 1; }';
const starter = 'int main() { return 0; }';
const row = {
  id: submissionId,
  problemId,
  problemVersionId: oldVersion,
  problemVersionNo: 1,
  problemTitle: '求和练习',
  languageId: 'cpp',
  status: 'DONE',
  createdAt: '2026-09-07T01:00:00Z',
  verdict: 'WA',
  cpuNs: 1200000,
  memoryBytes: 4096,
};
async function success(route: Route, data: unknown, meta: object = {}) {
  await route.fulfill({
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId, ...meta } }),
  });
}
async function setup(page: Page) {
  const state = {
    loggedIn: true,
    sourceRequests: 0,
    judgingReads: 0,
    creates: 0,
    queries: [] as string[],
  };
  await page.route('**/api/auth/session', (route) =>
    success(
      route,
      state.loggedIn
        ? {
            authenticated: true,
            user: {
              id: userId,
              username: 'history-user',
              role: 'USER',
              status: 'ACTIVE',
              passwordChangeRequired: false,
              createdAt: '2026-09-07T01:00:00Z',
              updatedAt: '2026-09-07T01:00:00Z',
              rowVersion: 0,
            },
          }
        : { authenticated: false },
    ),
  );
  await page.route('**/api/problems/history-sum', (route) =>
    success(route, {
      problemId,
      problemVersionId: versionId,
      versionNo: 2,
      slug: 'history-sum',
      codeMode: 'ACM',
      title: '求和练习',
      difficulty: 'EASY',
      tags: ['基础'],
      statementMarkdown: '计算两个数的和。',
      inputDescriptionMarkdown: '两个整数',
      outputDescriptionMarkdown: '输出和',
      constraintsMarkdown: '',
      hintMarkdown: '',
      samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: '' }],
      allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: starter }],
    }),
  );
  await page.route('**/api/submissions**', async (route) => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'POST') {
      state.creates++;
      return success(route, row);
    }
    if (url.pathname.endsWith('/source')) {
      state.sourceRequests++;
      expect(route.request().headers()['x-expected-user-id']).toBe(userId);
      return success(route, {
        submissionId,
        problemId,
        problemVersionId: oldVersion,
        languageId: 'cpp',
        source: historicalSource,
      });
    }
    if (url.pathname === `/api/submissions/${submissionId}`) {
      if (state.judgingReads > 0) {
        state.judgingReads--;
        return success(route, { ...row, status: 'JUDGING', verdict: undefined });
      }
      return success(route, row);
    }
    state.queries.push(url.search);
    expect(url.searchParams.get('problemId')).toBe(problemId);
    expect(route.request().headers()['x-expected-user-id']).toBe(userId);
    const pageNo = Number(url.searchParams.get('page') ?? '1');
    const empty = url.searchParams.get('verdict') === 'AC';
    return success(route, empty ? [] : [row], {
      pagination: {
        kind: 'page',
        page: pageNo,
        size: 20,
        totalElements: empty ? 0 : 21,
        totalPages: empty ? 0 : 2,
      },
    });
  });
  return state;
}
function editor(page: Page) {
  return page.getByRole('textbox', { name: /^C\+\+ 代码编辑器/ });
}
async function code(page: Page) {
  return (await page.locator('.monaco-editor .view-lines').innerText())
    .replaceAll('\u00a0', ' ')
    .trim();
}
async function replace(page: Page, source: string) {
  await editor(page).focus();
  await editor(page).press('Control+a');
  await page.keyboard.insertText(source);
}
async function openHistory(page: Page) {
  await page.getByRole('tab', { name: '提交记录', exact: true }).click();
  await page.getByRole('button', { name: /WA · 答案错误/ }).click();
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).toContainText(
    'historical attempt',
  );
}

test('tabs and history preserve the editor; load requires confirmation and retains URL state', async ({
  page,
}) => {
  const state = await setup(page);
  await page.setViewportSize({ width: 1440, height: 950 });
  await page.goto('/problems/history-sum');
  await expect(editor(page)).toBeVisible();
  await replace(page, '// current draft');
  await openHistory(page);
  await expect.poll(() => code(page)).toBe('// current draft');
  await page.getByRole('button', { name: '载入编辑器', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('历史提交与当前题目版本不同');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect.poll(() => code(page)).toBe('// current draft');
  await page.getByRole('tab', { name: '题目描述', exact: true }).click();
  await page.getByRole('tab', { name: '提交记录', exact: true }).click();
  await expect.poll(() => code(page)).toBe('// current draft');
  await page.getByRole('button', { name: '载入编辑器', exact: true }).click();
  await page.getByRole('button', { name: '确认载入', exact: true }).click();
  await expect.poll(() => code(page)).toBe(historicalSource);
  expect(state.creates).toBe(0);
  await page.getByRole('button', { name: '返回提交记录' }).click();
  await page.getByRole('button', { name: '下一页' }).click();
  await expect(page).toHaveURL(/historyPage=2/);
  await page.reload();
  await expect(page.getByText('第 2 页 · 共 2 页')).toBeVisible();
  await expect.poll(() => code(page)).toBe(historicalSource);
  const measurements = await page
    .locator('[data-slot="data-list-row"]')
    .first()
    .evaluate((el) => {
      const style = getComputedStyle(el);
      const title = el.querySelector('button > span');
      return {
        height: el.getBoundingClientRect().height,
        padding: style.paddingInline,
        gap: style.gap,
        fontSize: title ? getComputedStyle(title).fontSize : null,
        lineHeight: title ? getComputedStyle(title).lineHeight : null,
      };
    });
  await test.info().attach('history-row-measurements', {
    body: JSON.stringify(measurements),
    contentType: 'application/json',
  });
  expect(measurements.height).toBeGreaterThanOrEqual(44);
  expect(measurements.fontSize).toBe('13px');
  await page.screenshot({ path: '/tmp/work043-history-list.png', fullPage: true });
});

test('source is not polled and logout hides historical code', async ({ page }) => {
  const state = await setup(page);
  state.judgingReads = 2;
  await page.goto('/problems/history-sum?tab=submissions');
  await page.getByRole('button', { name: /WA · 答案错误/ }).click();
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).toBeVisible();
  await expect.poll(() => state.sourceRequests).toBe(1);
  await page.screenshot({ path: '/tmp/work043-history-detail.png', fullPage: true });
  await expect(page.getByText('WA · 答案错误', { exact: true })).toBeVisible({ timeout: 10000 });
  expect(state.sourceRequests).toBe(1);
  state.loggedIn = false;
  await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')));
  // Session polling is an existing workbench boundary; don't alter application caches from a test.
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).not.toBeVisible({
    timeout: 20000,
  });
  await expect(page.getByText('登录后查看自己在本题的提交记录。')).toBeVisible();
});

test('mobile nested tabs keep code and visitors can read the statement', async ({ page }) => {
  const state = await setup(page);
  await page.setViewportSize({ width: 320, height: 750 });
  await page.goto('/problems/history-sum');
  await page.getByRole('tab', { name: '提交记录', exact: true }).click();
  await expect(page.getByRole('button', { name: /WA · 答案错误/ })).toBeVisible();
  await page.screenshot({ path: '/tmp/work043-history-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect(editor(page)).toBeVisible();
  await page.getByRole('tab', { name: '题目', exact: true }).click();
  await expect(page.getByRole('button', { name: /WA · 答案错误/ })).toBeVisible();
  state.loggedIn = false;
  await page.reload();
  await expect(page.getByText('登录后查看自己在本题的提交记录。')).toBeVisible();
  await page.getByRole('tab', { name: '题目描述', exact: true }).click();
  await expect(page.getByText('计算两个数的和。')).toBeVisible();
});

test('filters retain recovery links and keyboard tabs work', async ({ page }) => {
  await setup(page);
  await page.goto(`/problems/history-sum?tab=submissions&submissionId=${submissionId}`);
  await page.getByRole('combobox', { name: '判定', exact: true }).click();
  await page.getByRole('option', { name: 'AC · 通过', exact: true }).click();
  await expect(page.getByText('没有符合条件的提交。')).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`submissionId=${submissionId}`));
  await expect(page).toHaveURL(/historyVerdict=AC/);
  await page.getByRole('button', { name: '清除筛选' }).click();
  await expect(page.getByRole('button', { name: /WA · 答案错误/ })).toBeVisible();
  await page.getByRole('tab', { name: '提交记录', exact: true }).focus();
  await page.keyboard.press('ArrowLeft');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('tab', { name: '题目描述', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  );
});

test('history remains reachable at zoom and forced colors', async ({ page }) => {
  await setup(page);
  await page.setViewportSize({ width: 640, height: 475 });
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'light' });
  await page.goto('/problems/history-sum?tab=submissions');
  await page.getByRole('button', { name: /WA · 答案错误/ }).click();
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).toBeVisible();
  await page.getByRole('button', { name: '载入编辑器', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: /^切换到/ }).click();
  await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'light');
  await page.screenshot({ path: '/tmp/work043-history-zoom-light.png', fullPage: true });
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await page.getByRole('button', { name: '返回提交记录' }).click();
  const entry = page.getByRole('button', { name: /WA · 答案错误/ });
  await entry.focus();
  await expect(entry).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '/tmp/work043-history-forced-colors.png', fullPage: true });
});

test('a denied source is hidden and can be retried without overwriting code', async ({ page }) => {
  await setup(page);
  await page.route('**/api/submissions/*/source', (route) =>
    route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      headers: { 'X-Request-Id': requestId },
      body: JSON.stringify({
        type: 'urn:cherry-oj:problem:not-found',
        title: '提交不存在',
        status: 404,
        detail: '提交不存在或不属于当前账号。',
        code: 'SUBMISSION_NOT_FOUND',
        instance: `urn:cherry-oj:request:${requestId}`,
        meta: { requestId },
      }),
    }),
  );
  await page.route(`**/api/submissions/${submissionId}`, (route) =>
    success(route, {
      ...row,
      problemTitle: '这是一段用于验证窄屏换行的很长的中文题目名称'.repeat(8),
    }),
  );
  await page.setViewportSize({ width: 320, height: 750 });
  await page.goto(`/problems/history-sum?tab=submissions&historySubmissionId=${submissionId}`);
  await expect(page.getByRole('button', { name: '重试代码' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).not.toBeVisible();
  await expect(page.getByRole('button', { name: '载入编辑器', exact: true })).not.toBeVisible();
  await page.unroute('**/api/submissions/*/source');
  await page.getByRole('button', { name: '重试代码' }).click();
  await expect(page.getByRole('textbox', { name: '历史提交代码' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('another tab changing the draft invalidates an open load confirmation', async ({
  page,
  context,
}) => {
  await setup(page);
  await page.goto('/problems/history-sum');
  await expect(editor(page)).toBeVisible();
  await replace(page, '// saved draft');
  await expect(page.getByText('已保存到本机', { exact: true })).toBeVisible();
  const other = await context.newPage();
  await setup(other);
  await other.goto('/problems/history-sum');
  await expect(editor(other)).toBeVisible();
  await openHistory(page);
  await page.getByRole('button', { name: '载入编辑器', exact: true }).click();
  await replace(other, '// updated in another tab');
  await expect(other.getByText('已保存到本机', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '确认载入', exact: true })).toBeDisabled();
  await expect(
    page.getByText('草稿已变化或存在多标签冲突，请取消、处理后重新载入。'),
  ).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect.poll(() => code(page)).toBe('// saved draft');
  await expect(page.getByRole('button', { name: '查看恢复副本' })).toBeVisible();
  await other.close();
});
