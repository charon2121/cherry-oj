import { expect, type Page, type Route, test } from '@playwright/test';

import type { AdminProblem, ProblemLanguageInput } from '../src/generated/api/types.gen.js';

const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const userId = 'd0e35399-6487-4ac8-8138-8d5bd60eb003';
const calibrationId = '273d4d76-103e-4978-a592-dd05ba776781';
const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const time = '2026-11-10T00:00:00Z';
const cpp = {
  id: 'cpp',
  displayName: 'C++',
  starterCode: 'int main() { return 0; }',
  judgeTemplate: null,
  solutionPrefix: null,
};
const c = {
  id: 'c',
  displayName: 'C',
  starterCode: 'int main(void) { return 0; }',
  judgeTemplate: null,
  solutionPrefix: null,
};

async function success(route: Route, data: unknown) {
  await route.fulfill({
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({ data, meta: { requestId } }),
  });
}

async function backend(page: Page, role: 'ADMIN' | 'USER') {
  const problem: AdminProblem = {
    id: problemId,
    slug: 'multi-sum',
    title: '同题多语言求和',
    visibility: 'PRIVATE',
    status: 'ACTIVE',
    codeMode: 'ACM',
    statementMarkdown: '计算两数的和。',
    inputDescriptionMarkdown: '两个整数',
    outputDescriptionMarkdown: '输出和',
    constraintsMarkdown: null,
    hintMarkdown: null,
    difficulty: 'EASY',
    tags: [],
    samples: [{ ordinal: 1, input: '1 2', output: '3', explanationMarkdown: null }],
    allowedLanguages: [cpp],
    testData: {
      digest: 'a'.repeat(64),
      testcaseCount: 1,
      totalBytes: 6,
      updatedAt: time,
      manifest: { testcaseCount: 1, totalBytes: 6, files: [] },
    },
    createdAt: time,
    updatedAt: time,
    publishedAt: null,
    rowVersion: 0,
  };
  const state = {
    problem,
    ready: ['cpp', 'c'],
    calibration: { languageId: '', kind: '' },
    submissions: [] as { languageId: string; source: string }[],
    runs: [] as { languageId: string; source: string }[],
    failReadiness: false,
  };
  const row = (languageId: string) => ({
    id: calibrationId,
    problemId,
    problemTitle: problem.title,
    languageId,
    status: 'DONE',
    createdAt: time,
    verdict: 'AC',
    cpuNs: 1000000,
    memoryBytes: 4096,
    passedTestcaseCount: 1,
    executedTestcaseCount: 1,
    testcaseCount: 1,
  });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path === '/api/auth/session')
      return success(route, {
        authenticated: true,
        user: {
          id: userId,
          username: 'multi-user',
          role,
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
    if (path === '/api/admin/languages')
      return success(route, {
        items: [c, cpp].map((language) => ({
          id: language.id,
          displayName: language.displayName,
          solutionPrefix: '#include <stdio.h>\n',
          updatedAt: time,
        })),
      });
    if (path === `/api/admin/problems/${problemId}/languages`) {
      const body = route.request().postDataJSON() as {
        allowedLanguages: ProblemLanguageInput[];
        rowVersion: number;
      };
      expect(body.rowVersion).toBe(state.problem.rowVersion);
      state.problem = {
        ...state.problem,
        rowVersion: body.rowVersion + 1,
        allowedLanguages: body.allowedLanguages.map((input) => ({
          ...input,
          displayName: input.id === 'c' ? 'C' : 'C++',
          solutionPrefix: null,
        })),
      };
      return success(route, state.problem);
    }
    if (path === `/api/admin/problems/${problemId}/limits`)
      return success(route, {
        languages: state.problem.allowedLanguages.map((language) => ({
          languageId: language.id,
          limitSource:
            language.id === 'cpp'
              ? 'BASE'
              : state.calibration.kind === 'OVERRIDE'
                ? 'OVERRIDE'
                : 'DERIVED',
          cpuNs: 1000000000,
          memoryBytes: 268435456,
          clockNs: null,
          calibrationId,
          cpuCapped: false,
          memoryCapped: false,
          unavailableReason: null,
        })),
      });
    if (path === `/api/admin/problems/${problemId}/calibration`) {
      const body = route.request().postDataJSON() as {
        languageId: string;
        kind: string;
        cpuNs: number;
        memoryBytes: number;
        clockNs: number | null;
      };
      state.calibration = body;
      return success(route, {
        id: calibrationId,
        problemId,
        languageId: body.languageId,
        kind: body.kind,
        status: 'VALID',
        cpuNs: body.cpuNs,
        memoryBytes: body.memoryBytes,
        clockNs: body.clockNs,
        testDataDigest: 'a'.repeat(64),
        benchmarkSummary: null,
        errorMessage: null,
        createdAt: time,
        updatedAt: time,
        rowVersion: 0,
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
          'ONLINE_JUDGE_NODE',
          'CALIBRATION',
        ].map((code) => ({ code, passed: true, message: `${code} 已通过` })),
        languages: state.problem.allowedLanguages.map((language) => ({
          languageId: language.id,
          ready: state.ready.includes(language.id),
          checks: [
            { code: 'LANGUAGE', passed: state.ready.includes(language.id), message: '语言状态' },
            { code: 'ONLINE_JUDGE_NODE', passed: true, message: '节点状态' },
            { code: 'CALIBRATION', passed: true, message: '标定状态' },
          ],
        })),
      });
    if (path === `/api/admin/problems/${problemId}/publish`) {
      state.problem = {
        ...state.problem,
        visibility: 'PUBLIC',
        publishedAt: time,
        rowVersion: state.problem.rowVersion + 1,
      };
      return success(route, state.problem);
    }
    if (path === `/api/admin/problems/${problemId}` && method === 'PATCH') {
      const body = route.request().postDataJSON() as { title: string; rowVersion: number };
      expect(body.rowVersion).toBe(state.problem.rowVersion);
      state.problem = { ...state.problem, title: body.title, rowVersion: body.rowVersion + 1 };
      return success(route, state.problem);
    }
    if (path === `/api/admin/problems/${problemId}`) return success(route, state.problem);
    if (path === '/api/problems/multi-sum/languages') {
      if (state.failReadiness)
        return route.fulfill({
          status: 503,
          contentType: 'application/problem+json',
          headers: { 'X-Request-Id': requestId },
          body: JSON.stringify({
            type: 'about:blank',
            title: '服务不可用',
            status: 503,
            code: 'SERVICE_UNAVAILABLE',
            meta: { requestId },
          }),
        });
      return success(route, {
        items: state.problem.allowedLanguages
          .filter((language) => state.ready.includes(language.id))
          .map(({ id, displayName, starterCode }) => ({ id, displayName, starterCode })),
      });
    }
    if (path === '/api/problems/multi-sum') return success(route, { ...state.problem, problemId });
    if (path === '/api/custom-runs') {
      const body = route.request().postDataJSON() as { languageId: string; source: string };
      state.runs.push(body);
      return success(route, {
        problemId,
        languageId: body.languageId,
        status: 'COMPLETED',
        stdout: { text: '3', capturedBytes: 1, truncated: false },
        stderr: { text: '', capturedBytes: 0, truncated: false },
        effectiveLimits: { cpuNs: 1000000000, memoryBytes: 268435456, clockNs: 10000000000 },
      });
    }
    if (path === '/api/submissions' && method === 'POST') {
      const body = route.request().postDataJSON() as { languageId: string; source: string };
      state.submissions.push(body);
      return success(route, row(body.languageId));
    }
    if (path === `/api/submissions/${calibrationId}`)
      return success(route, row(state.submissions.at(-1)?.languageId ?? 'cpp'));
    throw new Error(`Unexpected API ${method} ${path}`);
  });
  return state;
}

async function choose(page: Page, label: string, name: string) {
  await page.getByRole('combobox', { name: label, exact: true }).click();
  await page.getByRole('option', { name, exact: true }).click();
}

async function replaceCode(page: Page, name: string, source: string) {
  const editor = page.getByRole('textbox', {
    name: new RegExp(`^${name.replaceAll('+', '\\+')} 代码编辑器`),
  });
  await editor.focus();
  await editor.press('Control+a');
  await page.keyboard.insertText(source);
}

test('admin adds C, overrides its derived limits and publishes with one ready language', async ({
  page,
}) => {
  const state = await backend(page, 'ADMIN');
  await page.goto(`/admin/problems/${problemId}`);
  await choose(page, '添加语言', 'C');
  await page.getByRole('textbox', { name: 'C 起始代码', exact: true }).fill(c.starterCode);
  await expect
    .poll(() => state.problem.allowedLanguages.map((item) => item.id))
    .toEqual(['cpp', 'c']);
  await expect(page.getByRole('status').filter({ hasText: '语言配置已保存' })).toBeVisible();
  await expect(page.getByText('公式推导', { exact: true })).toBeVisible();
  await page.goto(`/admin/problems/${problemId}?panel=calibration`);
  await choose(page, '标定语言', 'C');
  await choose(page, '标定方式', '覆盖标定');
  await page.getByRole('textbox', { name: 'C 参考程序编辑器', exact: true }).fill(c.starterCode);
  await page.getByRole('button', { name: '运行参考程序校准', exact: true }).click();
  await expect.poll(() => state.calibration).toMatchObject({ languageId: 'c', kind: 'OVERRIDE' });
  await expect(page.getByRole('status').filter({ hasText: '校准状态' })).toContainText('VALID');
  state.ready = ['c'];
  await page.goto(`/admin/problems/${problemId}?panel=publish`);
  await expect(page.getByText('c · 可作答', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '公开题目', exact: true }).click();
  await expect.poll(() => state.problem.visibility).toBe('PUBLIC');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await choose(page, '编辑语言', 'C');
  const updatedStarter = c.starterCode + '\n// published edit';
  const starterEditor = page.getByRole('textbox', { name: 'C 起始代码', exact: true });
  await starterEditor.fill(updatedStarter);
  await page
    .getByRole('textbox', { name: '题目标题', exact: true })
    .fill('同题多语言求和 · 已编辑');
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled();
  await starterEditor.press('Control+s');
  await expect.poll(() => state.problem.title).toBe('同题多语言求和 · 已编辑');
  await expect
    .poll(() => state.problem.allowedLanguages.find((item) => item.id === 'c')?.starterCode)
    .toBe(updatedStarter);

  await page.getByRole('region', { name: '题目语言配置', exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: '/private/tmp/cherry-multilang-admin-dark.png' });
  await page.getByRole('button', { name: /^切换到/ }).click();
  await page.screenshot({ path: '/private/tmp/cherry-multilang-admin-light.png' });
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('region', { name: '题目语言配置', exact: true }).scrollIntoViewIfNeeded();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.getByRole('combobox', { name: '编辑语言', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('option', { name: 'C', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await expect(page.getByRole('button', { name: '保存语言', exact: true })).toBeVisible();
  await page.screenshot({ path: '/private/tmp/cherry-multilang-admin-320-forced.png' });
});

test('language switching restores separate drafts and sends the selected language to run and submit', async ({
  page,
}) => {
  const state = await backend(page, 'USER');
  state.problem = { ...state.problem, visibility: 'PUBLIC', allowedLanguages: [cpp, c] };
  state.ready = ['cpp'];
  await page.goto('/problems/multi-sum');
  await page.getByRole('combobox', { name: '作答语言' }).click();
  await expect(page.getByRole('option', { name: 'C', exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');
  state.ready = ['cpp', 'c'];
  await page.reload();
  await replaceCode(page, 'C++', 'int cppDraft = 11;');
  await choose(page, '作答语言', 'C');
  await replaceCode(page, 'C', 'int cDraft = 22;');
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect
    .poll(() => state.runs[0])
    .toMatchObject({ languageId: 'c', source: 'int cDraft = 22;' });
  await choose(page, '作答语言', 'C++');
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('int cppDraft = 11;');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect
    .poll(() => state.submissions[0])
    .toMatchObject({ languageId: 'cpp', source: 'int cppDraft = 11;' });
  await choose(page, '作答语言', 'C');
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('int cDraft = 22;');
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '作答语言' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: '作答语言' })).toContainText('C');
  await expect(page.getByRole('combobox', { name: '作答语言' })).not.toContainText('C++');
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('int cDraft = 22;');
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.screenshot({ path: '/private/tmp/cherry-multilang-student-320.png' });
});

test('no ready language remains readable and a readiness outage is distinguishable and retryable', async ({
  page,
}) => {
  const state = await backend(page, 'USER');
  state.ready = [];
  await page.goto('/problems/multi-sum');
  await expect(page.getByText('此题目暂时没有就绪的作答语言。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '提交', exact: true })).toHaveCount(0);
  state.failReadiness = true;
  await page.reload();
  await expect(page.getByRole('button', { name: '重试可用语言' })).toBeVisible();
  state.failReadiness = false;
  state.ready = ['cpp'];
  await page.getByRole('button', { name: '重试可用语言' }).click();
  await expect(page.getByRole('button', { name: '提交', exact: true })).toBeEnabled();
});
