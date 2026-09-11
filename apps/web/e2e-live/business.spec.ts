import { expect, test } from '@playwright/test';

import { historyResponse, runResponse, submissionResponse } from './schemas.ts';
import { context, editorCode, observe, record, replace, results, source } from './support.ts';

const cases = [
  ['io', 'io', 'COMPLETED', '运行完成（未校验答案）'],
  ['ce', 'compile_error', 'COMPILE_ERROR', '编译失败'],
  ['re', 'runtime_error', 'RUNTIME_ERROR', '程序运行错误'],
  ['signal', 'signal_kill', 'RUNTIME_ERROR', '程序运行错误'],
  ['cpu', 'cpu_limit', 'TIME_LIMIT_EXCEEDED', '超过时间限制'],
  ['memory', 'memory_limit', 'MEMORY_LIMIT_EXCEEDED', '超过内存限制'],
  ['output', 'output_limit', 'OUTPUT_LIMIT_EXCEEDED', '超过输出限制'],
  ['empty', 'empty', 'COMPLETED', '运行完成（未校验答案）'],
] as const;

test('fresh Linux node: custom runs, Kafka submissions and history', async ({ page }) => {
  let customPosts = 0;
  let formalPosts = 0;
  const owners = new Set<string>();
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (request.method() !== 'POST') return;
    if (path === '/api/custom-runs') customPosts++;
    if (path === '/api/submissions') formalPosts++;
    if (path === '/api/custom-runs' || path === '/api/submissions') {
      owners.add(request.headers()['x-expected-user-id'] ?? '');
    }
  });
  await test.step('login', async () => {
    await page.goto('/login');
    await page.getByLabel('用户名').fill(context.username);
    await page.getByLabel('密码', { exact: true }).fill(context.password);
    const login = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/auth/login',
    );
    await page.getByRole('button', { name: '登录', exact: true }).click();
    expect((await login).status()).toBe(200);
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
  });
  await test.step('problem', async () => {
    await page.goto('/problems/' + context.slug);
  });

  for (const [key, fixture, status, label] of cases) {
    await test.step(key, async () => {
      await replace(page, source(fixture));
      await page.getByRole('tab', { name: '自定义输入', exact: true }).click();
      await page
        .getByRole('textbox', { name: '自定义输入数据', exact: true })
        .fill(key === 'io' ? '1 2\n' : '');
      if (key === 'cpu' || key === 'memory') observe(key);
      const started = process.hrtime.bigint();
      const received = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST' &&
          new URL(response.url()).pathname === '/api/custom-runs',
        { timeout: 60_000 },
      );
      await page.getByRole('button', { name: '运行', exact: true }).click();
      const response = await received;
      expect(response.status()).toBe(200);
      const body = await response.body();
      const httpNs = Number(process.hrtime.bigint() - started);
      observe('');
      expect(body.length).toBeLessThan(200_000);
      const { data, meta } = runResponse.parse(JSON.parse(body.toString()));
      expect(data.problemId).toBe(context.problemId);
      expect(data.problemVersionId).toBe(context.problemVersionId);
      expect(data.status).toBe(status);
      expect(data.effectiveLimits.cpuNs).toBe(1_000_000_000);
      expect(data.effectiveLimits.memoryBytes).toBe(268_435_456);
      expect(meta.requestId).toBeTruthy();
      await page.getByRole('tab', { name: '运行结果', exact: true }).click();
      await expect(page.getByText(label, { exact: true })).toBeVisible();
      if (key === 'io') {
        expect(data.stdout?.text).toBe('3\n');
        expect(data.stderr?.text).toBe('work048-stderr\n');
        await expect(page.getByRole('textbox', { name: '标准输出', exact: true })).toContainText(
          '3',
        );
        await expect(page.getByRole('textbox', { name: '错误输出', exact: true })).toContainText(
          'work048-stderr',
        );
      }
      if (key === 'ce') expect(data.compileDiagnostic).toContain('WORK048_EXPECTED_COMPILE_ERROR');
      if (key === 'output') {
        expect(data.stdout?.truncated).toBe(true);
        expect(Buffer.byteLength(data.stdout?.text ?? '', 'utf8')).toBeLessThanOrEqual(16_384);
        expect(data.stdout?.capturedBytes).toBeLessThanOrEqual(1_048_576);
      }
      if (key === 'empty') {
        expect(data.memoryBytes).toBeLessThan(16 * 1024 * 1024);
        expect(data.stdout?.text).toBe('');
        expect(data.stderr?.text).toBe('');
      }
      record(key, {
        status: data.status,
        requestId: meta.requestId,
        httpNs,
        cpuNs: data.cpuNs,
        memoryBytes: data.memoryBytes,
        effectiveLimits: data.effectiveLimits,
        stdoutBytes: data.stdout?.capturedBytes,
        stderrBytes: data.stderr?.capturedBytes,
        stdoutTruncated: data.stdout?.truncated,
        bodyBytes: body.length,
        problemId: data.problemId,
        problemVersionId: data.problemVersionId,
      });
    });
  }
  expect(customPosts).toBe(8);
  for (const [key, fixture, verdict] of [
    ['ac', 'sum', 'AC'],
    ['wa', 'wrong_answer', 'WA'],
  ] as const) {
    await test.step(key, async () => {
      await replace(page, source(fixture));
      const created = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST' &&
          new URL(response.url()).pathname === '/api/submissions',
      );
      await page.getByRole('button', { name: '提交', exact: true }).click();
      const response = await created;
      expect(response.status()).toBe(201);
      const initial = submissionResponse.parse(await response.json());
      let final = initial;
      // Read-only polling observes the single real request; no mutating retry.
      await expect
        .poll(
          async () => {
            const polled = await page.request.get('/api/submissions/' + initial.data.id, {
              timeout: 10_000,
            });
            expect(polled.status()).toBe(200);
            expect((await polled.body()).length).toBeLessThan(65_536);
            final = submissionResponse.parse(await polled.json());
            return final.data.status;
          },
          { timeout: 90_000, intervals: [500] },
        )
        .toBe('DONE');
      expect(final.data.verdict).toBe(verdict);
      expect(final.data.problemVersionId).toBe(context.problemVersionId);
      expect(final.data.totalCount).toBe(6);
      const panel = page.getByRole('region', { name: '本次提交结果' });
      await expect(
        panel.getByText(key === 'ac' ? 'AC · 通过' : 'WA · 答案错误', { exact: true }),
      ).toBeVisible();
      if (key === 'ac') {
        expect(final.data.passedCount).toBe(6);
        expect(final.data.executedCount).toBe(6);
        await expect(panel.getByText('通过 6 / 6', { exact: true })).toBeVisible();
      }
      record(key, {
        submissionId: final.data.id,
        verdict: final.data.verdict,
        requestId: initial.meta.requestId,
        passedCount: final.data.passedCount,
        totalCount: final.data.totalCount,
        problemVersionId: final.data.problemVersionId,
      });
    });
  }
  await test.step('history', async () => {
    await replace(page, '// live CI draft must survive history');
    await page.getByRole('tab', { name: '提交记录', exact: true }).click();
    const historical = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
        '/api/submissions/' + String(results['wa']?.['submissionId']) + '/source',
    );
    await page.getByRole('button', { name: /WA · 答案错误/ }).click();
    const historicalResponse = await historical;
    expect(historicalResponse.status()).toBe(200);
    const history = historyResponse.parse(await historicalResponse.json());
    expect(history.data.source).toBe(source('wrong_answer'));
    await expect(page.getByRole('textbox', { name: '历史提交代码' })).toContainText(
      'WORK048_WRONG_ANSWER',
    );
    await expect.poll(() => editorCode(page)).toBe('// live CI draft must survive history');
    await page.getByRole('button', { name: '载入编辑器', exact: true }).click();
    await page.getByRole('button', { name: '取消', exact: true }).click();
    await expect.poll(() => editorCode(page)).toBe('// live CI draft must survive history');
    await page.getByRole('button', { name: '返回提交记录', exact: true }).click();
    const acceptedSource = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
        '/api/submissions/' + String(results['ac']?.['submissionId']) + '/source',
    );
    await page.getByRole('button', { name: /AC · 通过/ }).click();
    const acceptedResponse = await acceptedSource;
    expect(acceptedResponse.status()).toBe(200);
    expect(historyResponse.parse(await acceptedResponse.json()).data.source).toBe(source('sum'));
    await expect(page.getByRole('textbox', { name: '历史提交代码' })).toContainText(
      'Public A+B reference fixture',
    );
    await expect.poll(() => editorCode(page)).toBe('// live CI draft must survive history');
    expect(customPosts).toBe(8);
    expect(formalPosts).toBe(2);
    expect(owners.size).toBe(1);
    expect([...owners][0]).toMatch(/^[0-9a-f-]{36}$/);
    record('history', {
      submissionId: history.data.submissionId,
      problemVersionId: history.data.problemVersionId,
      requestId: history.meta.requestId,
      draftPreserved: true,
      customPosts,
      formalPosts,
      userId: [...owners][0],
    });
  });
});
