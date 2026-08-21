import { expect, test } from '@playwright/test';

test('opens the application shell', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Cherry OJ 前端骨架已就绪' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
});
