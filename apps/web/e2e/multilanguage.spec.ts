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
const pypy = {
  id: 'pypy',
  displayName: 'Python 3 (PyPy)',
  starterCode: 'a, b = map(int, input().split())\nprint(a + b)\n',
  judgeTemplate: null,
  solutionPrefix: null,
};
const java = {
  id: 'java',
  displayName: 'Java 21',
  starterCode: 'public class Main { public static void main(String[] args) {} }\n',
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
        items: [c, cpp, pypy, java].map((language) => ({
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
          displayName:
            [c, cpp, pypy, java].find((language) => language.id === input.id)?.displayName ??
            input.id,
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
        cpuNs: 1_000_000,
        memoryBytes: 28 << 20,
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
    name: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} 代码编辑器`),
  });
  await editor.focus();
  await editor.press('Control+a');
  if (source.includes('\n')) {
    // 多行按粘贴处理；Input.insertText 会被当成逐行键入，额外触发自动缩进。
    await editor.evaluate((target, text) => {
      const clipboardData = new DataTransfer();
      clipboardData.setData('text/plain', text);
      target.dispatchEvent(
        new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData }),
      );
    }, source);
  } else {
    await page.keyboard.insertText(source);
  }
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
  await page.getByRole('button', { name: '运行参考程序标定', exact: true }).click();
  await expect.poll(() => state.calibration).toMatchObject({ languageId: 'c', kind: 'OVERRIDE' });
  await expect(page.getByRole('status').filter({ hasText: '标定状态' })).toContainText('VALID');
  state.ready = ['c'];
  await page.goto(`/admin/problems/${problemId}?panel=publish`);
  await expect(page.getByText('c · 可作答', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '公开题目', exact: true }).click();
  await expect.poll(() => state.problem.visibility).toBe('PUBLIC');
  await expect(page.getByRole('dialog')).toBeHidden();
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
  await page.screenshot({ path: '/tmp/cherry-multilang-admin-dark.png' });
  await page.getByRole('button', { name: /^切换到/ }).click();

  await page.screenshot({ path: '/tmp/cherry-multilang-admin-light.png' });
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
  await page.screenshot({ path: '/tmp/cherry-multilang-admin-320-forced.png' });
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
  await page.screenshot({ path: '/tmp/cherry-multilang-student-320.png' });
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

test('PyPy has Python syntax, independent drafts and selected-language run and submission', async ({
  page,
}) => {
  const state = await backend(page, 'USER');
  state.problem = { ...state.problem, visibility: 'PUBLIC', allowedLanguages: [cpp, c, pypy] };
  state.ready = ['cpp', 'c', 'pypy'];
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/problems/multi-sum');
  await choose(page, '作答语言', pypy.displayName);
  await expect(page.locator('[data-slot="code-editor-host"][data-mode-id="python"]')).toBeVisible();
  const source = 'class Solution:\n    def add(self, a, b):\n        return a + b\n';
  await replaceCode(page, pypy.displayName, source);
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('class');
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载代码', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('Main.py');
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect.poll(() => state.runs[0]).toMatchObject({ languageId: 'pypy', source });
  await expect(page.getByText('样例全部通过', { exact: true })).toBeVisible();
  await choose(page, '作答语言', 'C++');
  await expect(page.locator('[data-slot="code-editor-host"][data-mode-id="cpp"]')).toBeVisible();
  await expect(page.locator('.monaco-editor .view-lines')).toContainText(cpp.starterCode);
  await choose(page, '作答语言', pypy.displayName);
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('class');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect.poll(() => state.submissions[0]).toMatchObject({ languageId: 'pypy', source });
  const keyword = page
    .locator('.monaco-editor .view-line')
    .first()
    .locator('span')
    .filter({ hasText: /^class$/ });
  await expect(keyword).toBeVisible();
  await page.screenshot({ path: '/tmp/cherry-pypy-student-dark.png' });
  await page.getByRole('button', { name: /^切换到/ }).click();
  const expectedKeywordColor = await page.evaluate(() => {
    const probe = document.createElement('span');
    probe.style.color = 'var(--ds-brand-foreground)';
    document.body.append(probe);
    const color = getComputedStyle(probe).color;
    probe.remove();
    return color;
  });
  await expect(keyword).toHaveCSS('color', expectedKeywordColor);
  await page.screenshot({ path: '/tmp/cherry-pypy-student-light.png' });
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '作答语言' })).toContainText(pypy.displayName);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  const languageBox = await page.getByRole('combobox', { name: '作答语言' }).boundingBox();
  const codeBox = await page.locator('[data-slot="code-editor-host"]').boundingBox();
  if (!languageBox || !codeBox) throw new Error('Language selector or editor is not visible.');
  expect(languageBox.y + languageBox.height).toBeLessThanOrEqual(codeBox.y);
  await page.screenshot({ path: '/tmp/cherry-pypy-student-320-forced.png' });
});

test('PyPy remains editable with Python indentation on a touch screen', async ({ browser }) => {
  const context = await browser.newContext({
    viewport: { width: 320, height: 720 },
    hasTouch: true,
    isMobile: true,
  });
  try {
    const page = await context.newPage();
    const state = await backend(page, 'USER');
    state.problem = { ...state.problem, visibility: 'PUBLIC', allowedLanguages: [cpp, pypy] };
    state.ready = ['cpp', 'pypy'];
    await page.goto('/problems/multi-sum');
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    await choose(page, '作答语言', pypy.displayName);
    const editor = page.getByRole('textbox', {
      name: `${pypy.displayName} 代码编辑器`,
      exact: true,
    });
    await expect(page.locator('.cm-editor').filter({ has: editor })).toBeVisible();
    await expect(page.locator('.monaco-editor')).toHaveCount(0);
    await editor.fill('class Solution:');
    await editor.press('Enter');
    await page.keyboard.insertText('pass');
    await page.getByRole('button', { name: '运行', exact: true }).click();
    await expect
      .poll(() => state.runs[0])
      .toMatchObject({ languageId: 'pypy', source: 'class Solution:\n    pass' });
    await expect(page.getByText('样例全部通过', { exact: true })).toBeVisible();
    await editor.focus();
    await editor.press('Tab');
    await expect(editor).not.toBeFocused();
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
      .toBeLessThanOrEqual(1);
    await page.screenshot({ path: '/tmp/cherry-pypy-touch-320.png' });
  } finally {
    await context.close();
  }
});

test('Java syntax, drafts, file names and selected-language requests survive layout changes', async ({
  page,
}) => {
  const state = await backend(page, 'USER');
  state.problem = { ...state.problem, visibility: 'PUBLIC', allowedLanguages: [cpp, java] };
  state.ready = ['cpp', 'java'];
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/problems/multi-sum');
  await choose(page, '作答语言', java.displayName);
  await expect(page.locator('[data-slot="code-editor-host"][data-mode-id="java"]')).toBeVisible();
  const source =
    'public class Main {\n    public static void main(String[] args) { System.out.println("中文结果"); }\n}\n';
  await replaceCode(page, java.displayName, source);
  const keyword = page
    .locator('.monaco-editor .view-line')
    .first()
    .locator('span')
    .filter({ hasText: /^public$/ })
    .first();
  await expect(keyword).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载代码', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('Main.java');
  await page.getByRole('button', { name: '运行', exact: true }).click();
  await expect.poll(() => state.runs[0]).toMatchObject({ languageId: 'java', source });
  await expect(page.getByText('样例全部通过', { exact: true })).toBeVisible();
  await choose(page, '作答语言', 'C++');
  await expect(page.locator('.monaco-editor .view-lines')).toContainText(cpp.starterCode);
  await choose(page, '作答语言', java.displayName);
  await expect(page.locator('.monaco-editor .view-lines')).toContainText('中文结果');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect.poll(() => state.submissions[0]).toMatchObject({ languageId: 'java', source });
  await page.screenshot({ path: '/tmp/cherry-java-student-dark.png' });
  await page.getByRole('button', { name: /^切换到/ }).click();
  const expectedKeywordColor = await page.evaluate(() => {
    const probe = document.createElement('span');
    probe.style.color = 'var(--ds-brand-foreground)';
    document.body.append(probe);
    const color = getComputedStyle(probe).color;
    probe.remove();
    return color;
  });
  await expect(keyword).toHaveCSS('color', expectedKeywordColor);
  await page.screenshot({ path: '/tmp/cherry-java-student-light.png' });
  await page.setViewportSize({ width: 640, height: 450 });
  await page.getByRole('tab', { name: '代码', exact: true }).click();
  await expect(page.getByRole('button', { name: '运行', exact: true })).toBeVisible();
  await page.setViewportSize({ width: 320, height: 720 });
  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' });
  await replaceCode(page, java.displayName, source + '// ' + '较长的中文代码说明'.repeat(40));
  await page.getByRole('textbox', { name: /^Java 21 代码编辑器/ }).press('Control+Home');
  await expect(page.getByRole('combobox', { name: '作答语言' })).toContainText(java.displayName);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1);
  await page.getByRole('combobox', { name: '作答语言' }).focus();
  await page.keyboard.press('Tab');
  await page.screenshot({ path: '/tmp/cherry-java-student-320-forced.png' });
});

test('Java uses the official grammar and four-space indentation on touch screens', async ({
  browser,
}) => {
  const context = await browser.newContext({
    viewport: { width: 320, height: 720 },
    hasTouch: true,
    isMobile: true,
  });
  try {
    const page = await context.newPage();
    const state = await backend(page, 'USER');
    state.problem = {
      ...state.problem,
      visibility: 'PUBLIC',
      codeMode: 'CORE',
      allowedLanguages: [java],
    };
    state.ready = ['java'];
    await page.goto('/problems/multi-sum');
    await page.getByRole('tab', { name: '代码', exact: true }).click();
    const editor = page.getByRole('textbox', {
      name: `${java.displayName} 代码编辑器`,
      exact: true,
    });
    await expect(page.locator('.cm-editor').filter({ has: editor })).toBeVisible();
    await expect(page.locator('.monaco-editor')).toHaveCount(0);
    await editor.fill('class Solution {');
    await editor.press('Enter');
    await page.keyboard.insertText('int value;');
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: '下载代码', exact: true }).click();
    expect((await download).suggestedFilename()).toBe('Solution.java');
    await page.getByRole('button', { name: '运行', exact: true }).click();
    await expect
      .poll(() => state.runs[0])
      .toMatchObject({ languageId: 'java', source: 'class Solution {\n    int value;' });
    await editor.focus();
    await editor.press('Tab');
    await expect(editor).not.toBeFocused();
    await page.screenshot({ path: '/tmp/cherry-java-touch-320.png' });
  } finally {
    await context.close();
  }
});
