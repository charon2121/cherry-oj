import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const csrfToken = 'csrf-token-that-is-long-enough';
const temporaryPassword = 'Temporary-Password-42!';

type Session = { authenticated: false } | { authenticated: true; user: ReturnType<typeof account> };

function account(
  overrides: Partial<{
    id: string;
    username: string;
    role: 'USER' | 'ADMIN';
    status: 'ACTIVE' | 'DISABLED';
    passwordChangeRequired: boolean;
    rowVersion: number;
  }> = {},
) {
  return {
    id: 'd0e35399-6487-4ac8-8138-8d5bd60eb003',
    username: 'alice',
    role: 'USER' as const,
    status: 'ACTIVE' as const,
    passwordChangeRequired: false,
    createdAt: '2026-08-26T01:00:00Z',
    updatedAt: '2026-08-26T01:00:00Z',
    rowVersion: 0,
    ...overrides,
  };
}

async function apiSuccess(
  route: Route,
  data: object,
  options: { status?: number; headers?: Record<string, string>; meta?: object } = {},
) {
  await route.fulfill({
    status: options.status ?? 200,
    contentType: 'application/json',
    headers: { 'X-Request-Id': requestId, ...options.headers },
    body: JSON.stringify({ data, meta: { requestId, ...options.meta } }),
  });
}

async function apiProblem(route: Route, status: number, code: string, title: string) {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    headers: { 'X-Request-Id': requestId },
    body: JSON.stringify({
      type: `urn:cherry-oj:problem:${code.toLowerCase().replaceAll('_', '-')}`,
      title,
      status,
      code,
      instance: `urn:cherry-oj:request:${requestId}`,
      meta: { requestId },
    }),
  });
}

async function mockSession(page: Page, readSession: () => Session) {
  await page.route('**/api/auth/session', (route) => apiSuccess(route, readSession()));
}

async function mockCsrf(page: Page) {
  await page.route('**/api/auth/csrf', (route) =>
    apiSuccess(route, { token: csrfToken, headerName: 'X-CSRF-Token' }),
  );
}

test('opens an anonymous application shell without protected-content flash', async ({ page }) => {
  await mockSession(page, () => ({ authenticated: false }));
  await page.route('**/api/status', (route) =>
    apiSuccess(route, { service: 'gateway-service', status: 'ready' }),
  );

  await page.goto('/');

  await expect(page.getByRole('heading', { name: '专注练习，清晰看到每一次进步' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
  await expect(page.getByRole('link', { name: '登录' })).toBeVisible();
  await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0);
  await expect(page.getByText('REST API 已连通')).toBeVisible();
});

test('recovers from rate limiting, prevents duplicate login, and keeps secrets out of browser storage', async ({
  page,
}) => {
  let session: Session = { authenticated: false };
  let loginCalls = 0;
  let releaseLogin: () => void = () => undefined;
  const loginGate = new Promise<void>((resolve) => {
    releaseLogin = resolve;
  });
  await mockSession(page, () => session);
  await mockCsrf(page);
  await page.route('**/api/status', (route) =>
    apiSuccess(route, { service: 'gateway-service', status: 'ready' }),
  );
  await page.route('**/api/auth/login', async (route) => {
    loginCalls += 1;
    expect(route.request().headers()['x-csrf-token']).toBe(csrfToken);
    expect(await route.request().postDataJSON()).toEqual({
      username: 'alice',
      password: 'Secret-Password-42!',
    });
    if (loginCalls === 1) {
      await apiProblem(route, 429, 'RATE_LIMITED', '请求过于频繁');
      return;
    }
    await loginGate;
    session = { authenticated: true, user: account() };
    await apiSuccess(route, {
      ...session,
      accessToken: 'server-must-never-send-this',
      loginGrant: 'server-must-never-send-this-either',
    });
  });

  await page.goto('/login?returnTo=https%3A%2F%2Fevil.example%2Fsteal');
  await page.getByLabel('用户名').fill('alice');
  await page.getByLabel('密码').fill('Secret-Password-42!');
  await page.getByRole('button', { name: '登录' }).click();
  const alert = page.getByRole('alert');
  await expect(alert).toHaveText('尝试次数过多，请稍后再试。');
  await expect(alert).toBeFocused();

  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('button', { name: '正在登录…' })).toBeDisabled();
  await page.getByLabel('密码').press('Enter');
  releaseLogin();

  await expect(page).toHaveURL('/');
  expect(loginCalls).toBe(2);
  const storageState = await page.context().storageState();
  const sessionStorageSnapshot = await page.evaluate<string>(
    'JSON.stringify(Object.entries(window.sessionStorage))',
  );
  const persistedState = `${JSON.stringify(storageState)}${sessionStorageSnapshot}`;
  expect(persistedState).not.toContain('Secret-Password-42!');
  expect(persistedState).not.toContain('server-must-never-send-this');
  expect(persistedState).not.toContain(csrfToken);
  expect(page.url()).not.toContain('Secret-Password-42!');
  await expect(page.locator('body')).not.toContainText('Secret-Password-42!');
  await expect(page.locator('body')).not.toContainText('server-must-never-send-this');
});

test('routes a first-login account to password change and logs out every session after success', async ({
  page,
}) => {
  let session: Session = { authenticated: false };
  await mockSession(page, () => session);
  await mockCsrf(page);
  await page.route('**/api/auth/login', async (route) => {
    session = { authenticated: true, user: account({ passwordChangeRequired: true }) };
    await apiSuccess(route, session);
  });
  await page.route('**/api/auth/password/change', async (route) => {
    expect(route.request().headers()['x-csrf-token']).toBe(csrfToken);
    expect(await route.request().postDataJSON()).toEqual({
      currentPassword: temporaryPassword,
      newPassword: 'A-New-Password-42!',
    });
    session = { authenticated: false };
    await route.fulfill({ status: 204, headers: { 'X-Request-Id': requestId } });
  });

  await page.goto('/login');
  await page.getByLabel('用户名').fill('alice');
  await page.getByLabel('密码').fill(temporaryPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL('/account/password');

  await page.getByLabel('当前密码').fill(temporaryPassword);
  await page.getByLabel('新密码（至少 12 位）').fill('A-New-Password-42!');
  await page.getByLabel('确认新密码').fill('different-password');
  await expect(page.getByText('两次输入的新密码不一致。')).toBeVisible();
  await page.getByLabel('确认新密码').fill('A-New-Password-42!');
  await page.getByRole('button', { name: '修改密码并退出' }).click();

  await expect(page).toHaveURL('/login?returnTo=%2F');
  await expect(page.getByRole('heading', { name: '登录 Cherry OJ' })).toBeVisible();
});

test('lets an admin manage users and reveals a temporary password only once', async ({ page }) => {
  const admin = account({ username: 'root-admin', role: 'ADMIN' });
  const disabledUser = account({
    id: 'a8412346-edf1-47d5-8eca-ceb29837b2ac',
    username: 'disabled-user',
    status: 'DISABLED',
    rowVersion: 3,
  });
  let statusChanges = 0;
  let passwordResets = 0;
  await page.setViewportSize({ width: 360, height: 800 });
  await mockSession(page, () => ({ authenticated: true, user: admin }));
  await mockCsrf(page);
  await page.route('**/api/admin/users?page=1&size=20', (route) =>
    apiSuccess(
      route,
      { items: [admin, disabledUser] },
      {
        meta: {
          pagination: { kind: 'page', page: 1, size: 20, totalElements: 2, totalPages: 1 },
        },
      },
    ),
  );
  await page.route('**/api/admin/users', async (route) => {
    expect(route.request().headers()['x-csrf-token']).toBe(csrfToken);
    expect(await route.request().postDataJSON()).toEqual({ username: 'new-user' });
    await apiSuccess(
      route,
      { user: account({ username: 'new-user' }), temporaryPassword },
      { status: 201, headers: { Location: '/api/admin/users/new-user' } },
    );
  });
  await page.route(`**/api/admin/users/${disabledUser.id}/status`, async (route) => {
    statusChanges += 1;
    expect(await route.request().postDataJSON()).toEqual({ status: 'ACTIVE', rowVersion: 3 });
    await apiSuccess(route, { ...disabledUser, status: 'ACTIVE', rowVersion: 4 });
  });
  await page.route(`**/api/admin/users/${disabledUser.id}/password-reset`, async (route) => {
    passwordResets += 1;
    expect(await route.request().postDataJSON()).toEqual({ rowVersion: 3 });
    await apiSuccess(route, { temporaryPassword: 'Reset-Password-42!' });
  });

  await page.goto('/admin/users?page=1');
  await expect(page.getByRole('heading', { name: '用户账号' })).toBeVisible();
  await expect(page.getByText('○ 已停用')).toBeVisible();
  await expect(page.getByRole('heading', { name: '用户账号' })).toBeInViewport();

  const disabledRow = page.getByRole('row').filter({ hasText: 'disabled-user' });
  page.once('dialog', (dialog) => dialog.accept());
  await disabledRow.getByRole('button', { name: '恢复' }).click();
  await expect.poll(() => statusChanges).toBe(1);
  page.once('dialog', (dialog) => dialog.accept());
  await disabledRow.getByRole('button', { name: '重置密码' }).click();
  await expect(page.getByRole('dialog', { name: '一次性临时密码' })).toContainText(
    'Reset-Password-42!',
  );
  expect(passwordResets).toBe(1);
  await page.getByRole('button', { name: '我已保存，关闭' }).click();

  await page.getByLabel('新用户用户名').fill('new-user');
  await page.getByRole('button', { name: '创建用户' }).click();
  const dialog = page.getByRole('dialog', { name: '一次性临时密码' });
  await expect(dialog).toContainText(temporaryPassword);
  await dialog.getByRole('button', { name: '我已保存，关闭' }).click();
  await expect(page.getByText(temporaryPassword)).toHaveCount(0);
});

test('redirects a signed-in non-admin away from user management', async ({ page }) => {
  await mockSession(page, () => ({ authenticated: true, user: account() }));

  await page.goto('/admin/users?page=1');

  await expect(page).toHaveURL('/forbidden');
  await expect(page.getByRole('heading', { name: '当前账号不能打开这个页面' })).toBeVisible();
});
