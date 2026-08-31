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
  await expect(page.getByRole('link', { name: '题库' })).toBeVisible();
  await expect(page.getByRole('link', { name: '提交记录' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /账号菜单/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0);
  await expect(page.getByText('REST API 已连通')).toBeVisible();
  const headerLinkDecorations = await page
    .locator('header a')
    .evaluateAll((links) => links.map((link) => getComputedStyle(link).textDecorationLine));
  expect(headerLinkDecorations).not.toContain('underline');
  const footer = page.getByRole('contentinfo');
  await expect(footer).toContainText('Focused Workspace');
  const userShellSurfaces = await page.evaluate(() => {
    const main = document.querySelector('main');
    const contentInfo = document.querySelector('footer');
    if (main === null || contentInfo === null) throw new Error('User shell landmarks are missing.');
    const mainStyle = getComputedStyle(main);
    const footerStyle = getComputedStyle(contentInfo);
    return {
      footerBackground: footerStyle.backgroundColor,
      footerBorderTopWidth: footerStyle.borderTopWidth,
      footerBoxShadow: footerStyle.boxShadow,
      footerBottom: contentInfo.getBoundingClientRect().bottom,
      mainBackground: mainStyle.backgroundColor,
      viewportHeight: window.innerHeight,
    };
  });
  expect(userShellSurfaces.footerBackground).toBe(userShellSurfaces.mainBackground);
  expect(userShellSurfaces.footerBorderTopWidth).toBe('0px');
  expect(userShellSurfaces.footerBoxShadow).toBe('none');
  expect(
    Math.abs(userShellSurfaces.footerBottom - userShellSurfaces.viewportHeight),
  ).toBeLessThanOrEqual(1);
});

test('renders the redesigned login as a responsive full-page workspace', async ({ page }) => {
  await mockSession(page, () => ({ authenticated: false }));
  await mockCsrf(page);
  await page.route('**/api/auth/login', (route) =>
    apiProblem(route, 401, 'INVALID_CREDENTIALS', '用户名或密码错误'),
  );

  await page.setViewportSize({ width: 1440, height: 1024 });
  await page.goto('/login');

  const heading = page.getByRole('heading', { level: 1, name: '登录 Cherry OJ' });
  const workspaceArt = page.getByTestId('login-workspace-art');
  await expect(heading).toBeVisible();
  await expect(workspaceArt).toBeVisible();
  await expect(page.locator('[data-slot="card"]')).toHaveCount(0);
  await expect(page.locator('main').getByRole('link')).toHaveCount(0);
  const desktopGeometry = await page.evaluate(() => {
    const title = document.querySelector('h1');
    const art = document.querySelector('[data-testid="login-workspace-art"]');
    const form = document.querySelector('form');
    if (title === null || art === null || form === null) {
      throw new Error('Login page regions are missing.');
    }
    return {
      artLeft: art.getBoundingClientRect().left,
      formRight: form.getBoundingClientRect().right,
      pageHeight: document.documentElement.scrollHeight,
      titleLeft: title.getBoundingClientRect().left,
      viewportHeight: window.innerHeight,
    };
  });
  expect(desktopGeometry.titleLeft).toBeLessThan(desktopGeometry.artLeft);
  expect(desktopGeometry.formRight).toBeLessThan(desktopGeometry.artLeft);
  expect(desktopGeometry.pageHeight).toBeLessThanOrEqual(desktopGeometry.viewportHeight);

  await page.setViewportSize({ width: 1280, height: 720 });
  await expect
    .poll(() =>
      page.evaluate(() => ({
        horizontal: document.documentElement.scrollWidth - window.innerWidth,
        vertical: document.documentElement.scrollHeight - window.innerHeight,
      })),
    )
    .toEqual({ horizontal: 0, vertical: 0 });

  await page.getByLabel('用户名').fill('alice');
  await page.getByLabel('密码', { exact: true }).fill(temporaryPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => ({
        horizontal: document.documentElement.scrollWidth - window.innerWidth,
        vertical: document.documentElement.scrollHeight - window.innerHeight,
      })),
    )
    .toEqual({ horizontal: 0, vertical: 0 });

  await page.setViewportSize({ width: 320, height: 720 });
  await expect(workspaceArt).toBeHidden();
  await expect(heading).toBeInViewport();
  await expect(page.getByRole('button', { name: '登录' })).toBeInViewport();
  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(horizontalOverflow).toBeLessThanOrEqual(1);

  await page.setViewportSize({ width: 640, height: 720 });
  await expect(heading).toBeInViewport();
  await expect(page.getByRole('button', { name: '登录' })).toBeInViewport();
  const zoomEquivalentOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(zoomEquivalentOverflow).toBeLessThanOrEqual(1);
});

test('keeps empty login submission in native validation without sending credentials', async ({
  page,
}) => {
  let loginCalls = 0;
  await mockSession(page, () => ({ authenticated: false }));
  await page.route('**/api/auth/login', (route) => {
    loginCalls += 1;
    return route.abort();
  });

  await page.goto('/login');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page.getByLabel('用户名')).toBeFocused();
  expect(
    await page
      .getByLabel('用户名')
      .evaluate((input: HTMLInputElement) => input.validity.valueMissing),
  ).toBe(true);
  expect(loginCalls).toBe(0);
});

test('renders the same empty admin dashboard at both requested routes', async ({ page }) => {
  const admin = account({ username: 'root-admin', role: 'ADMIN' });
  const requestedApiPaths: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/')) requestedApiPaths.push(url.pathname);
  });
  await mockSession(page, () => ({ authenticated: true, user: admin }));

  for (const path of ['/admin', '/admin/dashborad']) {
    await page.goto(path);
    await expect(page).toHaveURL(path);
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await expect(page.getByRole('navigation', { name: '管理侧栏导航' })).toBeVisible();
    await expect(page.getByRole('contentinfo')).toHaveCount(0);
    const headerLinkDecorations = await page
      .locator('header a')
      .evaluateAll((links) => links.map((link) => getComputedStyle(link).textDecorationLine));
    expect(headerLinkDecorations).not.toContain('underline');

    const adminShellGeometry = await page.evaluate(() => {
      const main = document.querySelector('#admin-main');
      if (main === null) throw new Error('Admin shell main is missing.');
      const contentRegion = main.parentElement;
      if (contentRegion === null) throw new Error('Admin shell content region is missing.');
      const shell = contentRegion.parentElement;
      if (shell === null) throw new Error('Admin shell root is missing.');
      return {
        contentBottom: contentRegion.getBoundingClientRect().bottom,
        shellBottom: shell.getBoundingClientRect().bottom,
        templateRows: getComputedStyle(shell).gridTemplateRows.split(' ').length,
        viewportHeight: window.innerHeight,
      };
    });
    expect(adminShellGeometry.templateRows).toBe(2);
    expect(
      Math.abs(adminShellGeometry.contentBottom - adminShellGeometry.viewportHeight),
    ).toBeLessThanOrEqual(1);
    expect(
      Math.abs(adminShellGeometry.shellBottom - adminShellGeometry.viewportHeight),
    ).toBeLessThanOrEqual(1);
  }

  expect(requestedApiPaths).toEqual(['/api/auth/session', '/api/auth/session']);
});

test('keeps authenticated account actions in one recoverable menu', async ({ page }) => {
  const admin = account({ username: 'root-admin', role: 'ADMIN' });
  let session: Session = { authenticated: true, user: admin };
  let logoutCalls = 0;
  await mockSession(page, () => session);
  await mockCsrf(page);
  await page.route('**/api/status', (route) =>
    apiSuccess(route, { service: 'gateway-service', status: 'ready' }),
  );
  await page.route('**/api/auth/logout', async (route) => {
    logoutCalls += 1;
    expect(route.request().headers()['x-csrf-token']).toBe(csrfToken);
    if (logoutCalls === 1) {
      await apiProblem(route, 503, 'SERVICE_UNAVAILABLE', '服务暂时不可用');
      return;
    }
    session = { authenticated: false };
    await route.fulfill({ status: 204, headers: { 'X-Request-Id': requestId } });
  });

  await page.goto('/');
  const accountMenuTrigger = page.getByRole('button', { name: /账号菜单，root-admin/ });
  await expect(accountMenuTrigger).toBeVisible();
  await accountMenuTrigger.click();
  const accountMenu = page.getByRole('menu', { name: /账号菜单，root-admin/ });
  await expect(accountMenu.getByText('root-admin')).toBeVisible();
  await expect(accountMenu.getByRole('menuitem', { name: '修改密码' })).toBeVisible();
  await expect(accountMenu.getByRole('menuitem', { name: '管理中心' })).toBeVisible();

  await accountMenu.getByRole('menuitem', { name: '退出登录' }).click();
  await expect.poll(() => logoutCalls).toBe(1);
  const failedTrigger = page.getByRole('button', { name: /上次退出失败/ });
  await expect(failedTrigger).toContainText('退出失败');
  await failedTrigger.click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('menuitem', { name: '重试退出' }).click();

  await expect.poll(() => logoutCalls).toBe(2);
  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: '登录 Cherry OJ' })).toBeVisible();
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
  await page.getByLabel('密码', { exact: true }).fill('Secret-Password-42!');
  await page.getByRole('button', { name: '登录' }).click();
  const alert = page.getByRole('alert');
  await expect(alert).toContainText('尝试次数过多，请稍后再试。');
  await expect(alert).toBeFocused();

  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('button', { name: '正在登录…' })).toBeDisabled();
  await page.getByLabel('密码', { exact: true }).press('Enter');
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
  await page.getByLabel('密码', { exact: true }).fill(temporaryPassword);
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
  await expect(page.getByRole('contentinfo')).toHaveCount(0);
  await expect(page.getByText('○ 已停用')).toBeVisible();
  await expect(page.getByRole('heading', { name: '用户账号' })).toBeInViewport();
  const navigationTrigger = page.getByRole('button', { name: '打开管理导航' });
  await navigationTrigger.click();
  const mobileNavigation = page.getByRole('navigation', { name: '移动管理导航' });
  await expect(mobileNavigation).toBeVisible();
  await expect(mobileNavigation.getByRole('button', { name: '账号管理' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  await expect(mobileNavigation.getByRole('link', { name: '用户账号' })).toHaveAttribute(
    'aria-current',
    'page',
  );
  await page.getByRole('button', { name: '关闭管理导航' }).click();
  await expect(navigationTrigger).toBeFocused();

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
