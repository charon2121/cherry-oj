/// <reference lib="dom" />

import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

import {
  defaultThemeId,
  fallbackThemeId,
  type ThemeId,
  themeRegistry,
  themeStorageKey,
} from '../src/generated/design-system/themes.js';

type FirstFrameSnapshot = Readonly<{
  backgroundColor: string;
  colorScheme: string;
  themeId: string | null;
}>;

type PreferenceSetup = Readonly<{
  failStorageRead?: boolean;
  value?: string | null;
}>;

const darkTheme = themeRegistry.find((theme) => theme.colorScheme === 'dark');
const lightTheme = themeRegistry.find((theme) => theme.colorScheme === 'light');

if (darkTheme === undefined || lightTheme === undefined) {
  throw new Error('The design-system browser matrix requires dark and light themes.');
}

async function apiSuccess(route: Route, data: object) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    headers: { 'X-Request-Id': 'req_design_system_e2e' },
    body: JSON.stringify({ data, meta: { requestId: 'req_design_system_e2e' } }),
  });
}

async function mockAnonymousShell(page: Page) {
  await page.route('**/api/auth/session', (route) => apiSuccess(route, { authenticated: false }));
}

async function mockAdminShell(page: Page) {
  await page.route('**/api/auth/session', (route) =>
    apiSuccess(route, {
      authenticated: true,
      user: {
        id: 'd0e35399-6487-4ac8-8138-8d5bd60eb003',
        username: 'root-admin',
        role: 'ADMIN',
        status: 'ACTIVE',
        passwordChangeRequired: false,
        createdAt: '2026-08-26T01:00:00Z',
        updatedAt: '2026-08-26T01:00:00Z',
        rowVersion: 0,
      },
    }),
  );
}

async function installPreferenceAndFirstFrameProbe(page: Page, setup: PreferenceSetup = {}) {
  await page.addInitScript(
    ({ failStorageRead, storageKey, value }) => {
      if (failStorageRead) {
        Object.defineProperty(window, 'localStorage', {
          configurable: true,
          get() {
            throw new DOMException('Storage access is blocked.', 'SecurityError');
          },
        });
      } else if (value === null || value === undefined) {
        window.localStorage.removeItem(storageKey);
      } else {
        window.localStorage.setItem(storageKey, value);
      }

      const firstFrame = new Promise<FirstFrameSnapshot>((resolve) => {
        window.requestAnimationFrame(() => {
          const root = document.documentElement;
          resolve({
            backgroundColor: getComputedStyle(document.body).backgroundColor,
            colorScheme: getComputedStyle(root).colorScheme,
            themeId: root.getAttribute('data-theme'),
          });
        });
      });

      Object.defineProperty(window, '__cherryOjFirstThemeFrame', {
        configurable: true,
        value: firstFrame,
      });
    },
    {
      failStorageRead: setup.failStorageRead ?? false,
      storageKey: themeStorageKey,
      value: setup.value,
    },
  );
}

async function readFirstFrame(page: Page): Promise<FirstFrameSnapshot> {
  return page.evaluate(() => {
    const browserWindow = window as typeof window & {
      __cherryOjFirstThemeFrame: Promise<FirstFrameSnapshot>;
    };
    return browserWindow.__cherryOjFirstThemeFrame;
  });
}

async function expectTheme(page: Page, themeId: ThemeId) {
  const theme = themeRegistry.find((candidate) => candidate.id === themeId);
  if (theme === undefined) throw new Error('Expected theme is absent from the registry.');

  await expect(page.locator('html')).toHaveAttribute('data-theme', theme.id);
  await expect(page.locator('html')).toHaveAttribute('data-color-scheme', theme.colorScheme);
  await expect
    .poll(() => page.evaluate(() => getComputedStyle(document.documentElement).colorScheme))
    .toBe(theme.colorScheme);
}

const firstPaintCases: ReadonlyArray<
  readonly [name: string, setup: PreferenceSetup, expectedTheme: ThemeId, clearsInvalid: boolean]
> = [
  ['missing preference', {}, defaultThemeId, false],
  ['empty preference', { value: '' }, defaultThemeId, true],
  ['explicit dark preference', { value: darkTheme.id }, darkTheme.id, false],
  ['explicit light preference', { value: lightTheme.id }, lightTheme.id, false],
  ['unknown preference', { value: 'unknown-theme' }, fallbackThemeId, true],
  ['unavailable storage', { failStorageRead: true }, fallbackThemeId, false],
];

for (const [name, setup, expectedTheme, clearsInvalid] of firstPaintCases) {
  test(`applies ${name} before the first rendered frame`, async ({ page }) => {
    const docsRequests: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/docs/')) docsRequests.push(request.url());
    });
    await installPreferenceAndFirstFrameProbe(page, setup);
    await mockAnonymousShell(page);

    await page.goto('/');

    const firstFrame = await readFirstFrame(page);
    const expectedDefinition = themeRegistry.find((theme) => theme.id === expectedTheme);
    if (expectedDefinition === undefined) throw new Error('Expected theme metadata is missing.');
    expect(firstFrame.themeId).toBe(expectedDefinition.id);
    expect(firstFrame.colorScheme).toBe(expectedDefinition.colorScheme);
    expect(firstFrame.backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    await expectTheme(page, expectedTheme);
    expect(docsRequests).toEqual([]);

    if (clearsInvalid) {
      await expect
        .poll(() =>
          page.evaluate((storageKey) => window.localStorage.getItem(storageKey), themeStorageKey),
        )
        .toBeNull();
    }
  });
}

test('the blocking bootstrap applies a saved theme without the React bundle', async ({ page }) => {
  await installPreferenceAndFirstFrameProbe(page, { value: lightTheme.id });
  await page.route('**/assets/*.js', (route) => route.abort());

  await page.goto('/', { waitUntil: 'domcontentloaded' });

  const firstFrame = await readFirstFrame(page);
  expect(firstFrame.themeId).toBe(lightTheme.id);
  expect(firstFrame.colorScheme).toBe(lightTheme.colorScheme);
  await expectTheme(page, lightTheme.id);
  await expect(page.locator('#root')).toBeEmpty();
});

test('theme changes converge across tabs and unknown values fall back safely', async ({
  context,
  page,
}) => {
  await mockAnonymousShell(page);
  await page.goto('/');
  await expectTheme(page, defaultThemeId);

  const otherPage = await context.newPage();
  await mockAnonymousShell(otherPage);
  await otherPage.goto('/');
  await otherPage.evaluate(
    ({ storageKey, value }) => window.localStorage.setItem(storageKey, value),
    { storageKey: themeStorageKey, value: lightTheme.id },
  );
  await expectTheme(page, lightTheme.id);

  await otherPage.evaluate(
    ({ storageKey }) => window.localStorage.setItem(storageKey, 'unknown-theme'),
    { storageKey: themeStorageKey },
  );
  await expectTheme(page, fallbackThemeId);
  await expect
    .poll(() =>
      page.evaluate((storageKey) => window.localStorage.getItem(storageKey), themeStorageKey),
    )
    .toBeNull();
});

test('the global theme switcher persists its choice and synchronizes another tab', async ({
  context,
  page,
}) => {
  await mockAnonymousShell(page);
  await page.goto('/');
  await expectTheme(page, defaultThemeId);

  const switchToLight = page.getByRole('button', { name: `切换到 ${lightTheme.label}` });
  await switchToLight.focus();
  await page.keyboard.press('Enter');

  await expectTheme(page, lightTheme.id);
  await expect(page.getByRole('status')).toHaveText(`已切换到 ${lightTheme.label}`);
  await expect
    .poll(() =>
      page.evaluate((storageKey) => window.localStorage.getItem(storageKey), themeStorageKey),
    )
    .toBe(lightTheme.id);

  await page.reload();
  await expectTheme(page, lightTheme.id);
  await expect(page.getByRole('button', { name: `切换到 ${darkTheme.label}` })).toBeVisible();

  const otherPage = await context.newPage();
  await mockAnonymousShell(otherPage);
  await otherPage.goto('/');
  await expectTheme(otherPage, lightTheme.id);

  await page.getByRole('button', { name: `切换到 ${darkTheme.label}` }).click();
  await expectTheme(page, darkTheme.id);
  await expectTheme(otherPage, darkTheme.id);
});

test('the theme switcher still applies a choice when preference storage rejects writes', async ({
  page,
}) => {
  await page.addInitScript(() => {
    const browserStorage = window.localStorage;
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        clear: browserStorage.clear.bind(browserStorage),
        getItem: browserStorage.getItem.bind(browserStorage),
        get length() {
          return browserStorage.length;
        },
        key: browserStorage.key.bind(browserStorage),
        removeItem: browserStorage.removeItem.bind(browserStorage),
        setItem() {
          throw new DOMException('Storage is full.', 'QuotaExceededError');
        },
      },
    });
  });
  await mockAnonymousShell(page);
  await page.goto('/');

  await page.getByRole('button', { name: `切换到 ${lightTheme.label}` }).click();

  await expectTheme(page, lightTheme.id);
  await expect(page.getByRole('status')).toHaveText(
    `已切换到 ${lightTheme.label}，但浏览器未能记住选择`,
  );
});

test('reduced motion collapses design-system durations without changing theme', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await installPreferenceAndFirstFrameProbe(page, { value: lightTheme.id });
  await mockAnonymousShell(page);

  await page.goto('/');

  await expectTheme(page, lightTheme.id);
  const durations = await page.evaluate(() => {
    const styles = getComputedStyle(document.documentElement);
    return {
      base: styles.getPropertyValue('--ds-motion-base').trim(),
      fast: styles.getPropertyValue('--ds-motion-fast').trim(),
    };
  });
  expect(durations).toEqual({ base: '0s', fast: '0s' });

  await page.goto('/login');
  await expectTheme(page, lightTheme.id);
  await expect(page.getByRole('heading', { level: 1, name: '登录 Cherry OJ' })).toBeVisible();
});

test('forced colors keeps keyboard navigation and theme metadata usable', async ({ page }) => {
  await page.emulateMedia({ forcedColors: 'active' });
  await installPreferenceAndFirstFrameProbe(page, { value: lightTheme.id });
  await mockAnonymousShell(page);

  await page.goto('/');

  await expect(page.locator('html')).toHaveAttribute('data-theme', lightTheme.id);
  await expect(page.locator('html')).toHaveAttribute('data-color-scheme', lightTheme.colorScheme);
  await expect(page.getByRole('link', { name: '登录' })).toBeVisible();
  expect(await page.evaluate(() => window.matchMedia('(forced-colors: active)').matches)).toBe(
    true,
  );

  const loginLink = page.getByRole('link', { name: '登录' });
  for (let index = 0; index < 8; index += 1) {
    if (await loginLink.evaluate((element) => element === document.activeElement)) break;
    await page.keyboard.press('Tab');
  }
  await expect(loginLink).toBeFocused();
  const focusIndicator = await loginLink.evaluate((element) => {
    const styles = getComputedStyle(element);
    return { style: styles.outlineStyle, width: styles.outlineWidth };
  });
  expect(focusIndicator.style).not.toBe('none');
  expect(focusIndicator.width).not.toBe('0px');

  await page.goto('/login');
  const username = page.getByLabel('用户名');
  for (let index = 0; index < 8; index += 1) {
    if (await username.evaluate((element) => element === document.activeElement)) break;
    await page.keyboard.press('Tab');
  }
  await expect(username).toBeFocused();
  const fieldFocusIndicator = await username.evaluate((element) => {
    const styles = getComputedStyle(element);
    return { style: styles.outlineStyle, width: styles.outlineWidth };
  });
  expect(fieldFocusIndicator.style).not.toBe('none');
  expect(fieldFocusIndicator.width).not.toBe('0px');
});

test('the router keeps a semantic not-found recovery path', async ({ page }) => {
  await mockAnonymousShell(page);

  await page.goto('/missing-design-system-route');

  await expect(page.getByRole('heading', { level: 1, name: '页面不存在' })).toBeVisible();
  await expect(page.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/');
});

test('disabled button colors win when a control is also pressed', async ({ page }) => {
  await mockAnonymousShell(page);
  await page.goto('/');

  const disabledClasses =
    'disabled:border-[var(--ds-border)]! disabled:bg-[var(--ds-surface-translucent)]! disabled:text-[var(--ds-fg-disabled)]!';
  const variantClasses = [
    {
      variant: 'primary',
      className:
        'border border-transparent bg-primary text-primary-foreground aria-pressed:bg-[var(--ds-brand-surface-active)]',
    },
    {
      variant: 'secondary',
      className:
        'border border-transparent bg-[var(--ds-surface-translucent-hover)] text-[var(--ds-fg-2)] aria-pressed:bg-[var(--ds-surface-translucent-selected)]',
    },
    {
      variant: 'ghost',
      className:
        'border border-[var(--ds-border-solid)] bg-[var(--ds-surface-translucent)] text-[var(--ds-fg-ghost)] aria-pressed:bg-[var(--ds-surface-translucent-selected)] aria-pressed:text-foreground',
    },
    {
      variant: 'danger',
      className:
        'border border-transparent bg-destructive text-destructive-foreground aria-pressed:outline-[var(--ds-danger-on-solid)]',
    },
  ].map(({ className, variant }) => ({ variant, className: `${className} ${disabledClasses}` }));
  const snapshots = await page.evaluate((classesByVariant) => {
    const snapshot = (className: string, pressed: boolean) => {
      const button = document.createElement('button');
      button.className = className;
      button.disabled = true;
      button.setAttribute('aria-pressed', String(pressed));
      document.body.append(button);
      const styles = getComputedStyle(button);
      const result = {
        backgroundColor: styles.backgroundColor,
        borderColor: styles.borderColor,
        color: styles.color,
      };
      button.remove();
      return result;
    };

    return classesByVariant.map(({ variant, className }) => ({
      variant,
      idle: snapshot(className, false),
      pressed: snapshot(className, true),
    }));
  }, variantClasses);

  for (const { idle, pressed } of snapshots) expect(pressed).toEqual(idle);
});

for (const theme of themeRegistry) {
  test(`${theme.label} keeps the 320px shell and login usable`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 720 });
    await installPreferenceAndFirstFrameProbe(page, { value: theme.id });
    await mockAnonymousShell(page);

    await page.goto('/');

    await expectTheme(page, theme.id);
    const navigationTrigger = page.getByRole('button', { name: '打开主导航' });
    await expect(
      page.getByRole('button', {
        name: `切换到 ${theme.colorScheme === 'dark' ? lightTheme.label : darkTheme.label}`,
      }),
    ).toBeVisible();
    await expect(navigationTrigger).toBeVisible();
    await navigationTrigger.click();
    await expect(page.getByRole('navigation', { name: '移动主导航' })).toBeVisible();
    await page.getByRole('button', { name: '关闭主导航' }).click();
    await expect(page.locator('main')).toBeInViewport();
    const shellSurfaces = await page.evaluate(() => {
      const main = document.querySelector('main');
      const footer = document.querySelector('footer');
      if (main === null || footer === null) throw new Error('User shell landmarks are missing.');
      const mainStyle = getComputedStyle(main);
      const footerStyle = getComputedStyle(footer);
      return {
        footerBackground: footerStyle.backgroundColor,
        footerBorderTopWidth: footerStyle.borderTopWidth,
        footerBoxShadow: footerStyle.boxShadow,
        mainBackground: mainStyle.backgroundColor,
      };
    });
    expect(shellSurfaces.footerBackground).toBe(shellSurfaces.mainBackground);
    expect(shellSurfaces.footerBorderTopWidth).toBe('1px');
    expect(shellSurfaces.footerBoxShadow).toBe('none');

    await page.goto('/login');
    await expectTheme(page, theme.id);
    await expect(page.getByRole('heading', { level: 1, name: '登录 Cherry OJ' })).toBeInViewport();
    await expect(page.getByTestId('login-workspace-art')).toBeHidden();
    await expect(page.getByRole('button', { name: '登录' })).toBeInViewport();
    const horizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(horizontalOverflow).toBeLessThanOrEqual(1);
  });
}

test('the 320px admin header keeps navigation, theme, and account actions usable', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await mockAdminShell(page);

  await page.goto('/admin');

  await expect(page.getByRole('button', { name: '打开管理导航' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Cherry OJ 管理中心' })).toBeVisible();
  await expect(page.locator('header').getByRole('link', { name: /用户端/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: `切换到 ${lightTheme.label}` })).toBeVisible();
  await page.getByRole('button', { name: /账号菜单，root-admin/ }).click();
  await expect(page.getByRole('menuitem', { name: '返回用户端' })).toBeVisible();

  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(horizontalOverflow).toBeLessThanOrEqual(1);
});
