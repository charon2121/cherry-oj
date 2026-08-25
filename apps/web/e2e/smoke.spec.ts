import { expect, test } from '@playwright/test';

test('opens the application shell', async ({ page }) => {
  await page.route('**/api/status', async (route) => {
    await route.fulfill({
      headers: { 'X-Request-Id': 'req_01K37XZ3MFXBK92WMG67G4XFN0' },
      json: {
        data: { service: 'gateway-service', status: 'ready' },
        meta: { requestId: 'req_01K37XZ3MFXBK92WMG67G4XFN0' },
      },
    });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Cherry OJ 前端骨架已就绪' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
  await expect(page.getByText('REST API 已连通')).toBeVisible();
});
