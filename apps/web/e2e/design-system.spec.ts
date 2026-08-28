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
  await page.route('**/api/status', (route) =>
    apiSuccess(route, { service: 'gateway-service', status: 'ready' }),
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

  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');

  const loginLink = page.getByRole('link', { name: '登录' });
  await expect(loginLink).toBeFocused();
  const focusIndicator = await loginLink.evaluate((element) => {
    const styles = getComputedStyle(element);
    return { style: styles.outlineStyle, width: styles.outlineWidth };
  });
  expect(focusIndicator.style).not.toBe('none');
  expect(focusIndicator.width).not.toBe('0px');
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
    'disabled:border-border! disabled:bg-secondary! disabled:text-[var(--ds-fg-disabled)]!';
  const variantClasses = [
    {
      variant: 'primary',
      className:
        'border border-transparent bg-primary text-primary-foreground aria-pressed:bg-[var(--ds-brand-surface-active)]',
    },
    {
      variant: 'secondary',
      className:
        'border border-border-strong bg-secondary text-secondary-foreground aria-pressed:border-ring aria-pressed:bg-accent',
    },
    {
      variant: 'ghost',
      className:
        'border border-transparent bg-transparent text-[var(--ds-fg-2)] aria-pressed:bg-accent aria-pressed:text-foreground',
    },
    {
      variant: 'danger',
      className:
        'border border-transparent bg-destructive text-destructive-foreground aria-pressed:border-[var(--ds-danger-on-solid)]',
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
  test(`${theme.label} keeps the 320px shell usable`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 720 });
    await installPreferenceAndFirstFrameProbe(page, { value: theme.id });
    await mockAnonymousShell(page);

    await page.goto('/');

    await expectTheme(page, theme.id);
    await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toBeInViewport();
    const horizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(horizontalOverflow).toBeLessThanOrEqual(1);
  });
}
