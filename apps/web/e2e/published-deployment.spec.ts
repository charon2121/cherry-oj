import { expect, test } from '@playwright/test';

const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const dataId = '454ef3b0-082e-4de6-a3d0-0f75d9a81138';
const environmentId = '454ef3b0-082e-4de6-a3d0-0f75d9a81139';
const time = '2026-09-08T01:00:00Z';
const sha = 'a'.repeat(64);

for (const status of ['PUBLISHED', 'ARCHIVED'] as const) {
  test(`${status} version deployment keeps content immutable`, async ({ page }) => {
    let deployed = false;
    const data = {
      id: dataId,
      problemId,
      status: 'READY',
      sourceType: 'MANUAL_UPLOAD',
      contentSha256: sha,
      caseCount: 1,
      totalBytes: 6,
      manifest: { caseCount: 1, totalBytes: 6, files: [] },
      createdAt: time,
      readyAt: time,
      errorMessage: null,
    };
    const version = {
      id: versionId,
      problemId,
      versionNo: 1,
      status,
      codeMode: 'ACM',
      title: '已发布题目',
      statementMarkdown: '题面',
      inputDescriptionMarkdown: '输入',
      outputDescriptionMarkdown: '输出',
      constraintsMarkdown: null,
      hintMarkdown: null,
      difficulty: 'EASY',
      tags: [],
      samples: [],
      allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: 'int main() {}' }],
      testDataVersion: data,
      changeSummary: null,
      createdAt: time,
      updatedAt: time,
      publishedAt: time,
      rowVersion: 4,
    };
    await page.route('**/api/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      let value: object;
      if (path === '/api/auth/session')
        value = {
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
        };
      else if (path === '/api/auth/csrf')
        value = { token: 'csrf-token-that-is-long-enough', headerName: 'X-CSRF-Token' };
      else if (path.endsWith('/deployment')) {
        expect(status).toBe('PUBLISHED');
        expect(route.request().postDataJSON()).toEqual({
          testDataVersionId: dataId,
          expectedSha256: sha,
          rowVersion: 4,
        });
        deployed = true;
        value = {
          testDataVersionId: dataId,
          environmentId,
          environmentName: 'node',
          expectedSha256: sha,
          status: 'READY',
          deployedSha256: sha,
          deployedAt: time,
          errorMessage: null,
          updatedAt: time,
          rowVersion: 0,
        };
      } else if (path.endsWith('/publish-check'))
        value = {
          ready: deployed,
          environmentId,
          checks: ['ACTIVE_ENVIRONMENT', 'ONLINE_JUDGE_NODE', 'CALIBRATION', 'DEPLOYMENT'].map(
            (code) => ({ code, passed: code !== 'DEPLOYMENT' || deployed, message: code }),
          ),
        };
      else if (path.endsWith('/test-data')) value = { items: [data] };
      else if (path.endsWith(`/versions/${versionId}`)) value = version;
      else if (path === `/api/admin/problems/${problemId}`)
        value = {
          id: problemId,
          slug: 'published',
          status: 'ACTIVE',
          visibility: 'PUBLIC',
          currentPublishedVersionId: versionId,
          versions: [version],
          createdAt: time,
          updatedAt: time,
          rowVersion: 0,
        };
      else throw new Error(`Unexpected API ${path}`);
      const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
      await route.fulfill({
        contentType: 'application/json',
        headers: { 'X-Request-Id': requestId },
        body: JSON.stringify({ data: value, meta: { requestId } }),
      });
    });
    await page.goto(`/admin/problems/${problemId}/versions/${versionId}?step=test-and-calibrate`);
    const deploy = page.getByRole('button', { name: '部署测试数据', exact: true });
    if (status === 'PUBLISHED') {
      await expect(deploy).toBeEnabled();
      await deploy.focus();
      await page.keyboard.press('Enter');
      await expect.poll(() => deployed).toBe(true);
      await expect(page.getByRole('status').filter({ hasText: '部署可用' })).toBeVisible();
    } else await expect(deploy).toBeDisabled();
    await expect(page.getByRole('button', { name: '测试数据 ZIP', exact: true })).toBeDisabled();
    await expect(page.getByRole('button', { name: '用于此版本', exact: true })).toBeDisabled();
    await expect(
      page.getByRole('button', { name: '运行参考程序校准', exact: true }),
    ).toBeDisabled();
    await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeDisabled();
  });
}
