import { readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

import { expect, type Page } from '@playwright/test';

export const directory = process.env['CHERRY_LIVE_PRIVATE'];
if (!directory) throw new Error('Missing private live-test directory');
const privateDirectory: string = directory;
const fixtureDirectory = resolve(
  import.meta.dirname,
  '../../../deploy/sandbox-linux/tests/acceptance',
);

const parsed: unknown = JSON.parse(readFileSync(join(directory, 'live-context.json'), 'utf8'));
if (!parsed || typeof parsed !== 'object') throw new Error('Invalid live context');
const fields = parsed as Record<string, unknown>;
function field(key: string): string {
  const value = fields[key];
  if (typeof value !== 'string' || !value) throw new Error('Missing live context field: ' + key);
  return value;
}
export const context = {
  slug: field('slug'),
  problemId: field('problemId'),
  problemVersionId: field('problemVersionId'),
  username: field('username'),
  password: field('password'),
};
export const source = (name: string) => readFileSync(join(fixtureDirectory, name + '.cpp'), 'utf8');
export const results: Record<string, Record<string, unknown>> = {};
export function record(key: string, data: Record<string, unknown>) {
  if (results[key]) throw new Error('Duplicate live case');
  results[key] = data;
  const pending = join(privateDirectory, 'live.pending');
  writeFileSync(pending, JSON.stringify(results));
  renameSync(pending, join(privateDirectory, 'live.json'));
}
export function observe(key: string) {
  const pending = join(privateDirectory, 'case.pending');
  writeFileSync(pending, key);
  renameSync(pending, join(privateDirectory, 'observed-case'));
}
let problemResponse = 0;
export function observeProblem(page: Page) {
  page.on('response', (response) => {
    if (new URL(response.url()).pathname === '/api/problems/' + context.slug) {
      problemResponse = response.status();
    }
  });
}
export async function replace(page: Page, code: string) {
  const editor = page.getByRole('textbox', { name: /^C\+\+ 代码编辑器/ });
  try {
    await expect(editor).toBeVisible();
  } catch (error) {
    // Fixed facts only; never export DOM, URLs, credentials or the original error text.
    try {
      const state = {
        matches: Math.min(await editor.count(), 16),
        visible: await editor.first().isVisible(),
        loading: await page.getByText('正在加载代码编辑器…', { exact: true }).isVisible(),
        loadError: await page
          .getByRole('button', { name: '重新加载编辑器', exact: true })
          .isVisible(),
        problemResponse,
      };
      writeFileSync(join(privateDirectory, 'editor-state.json'), JSON.stringify(state));
    } catch {
      // A closed page must not replace the original assertion failure.
    }
    throw error;
  }
  await editor.focus();
  await editor.press('ControlOrMeta+a');
  // Use Monaco's paste path: typing multiline text applies indentation on every line.
  await editor.evaluate((element, text) => {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', text);
    element.dispatchEvent(
      new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }),
    );
  }, code);
}
export async function editorCode(page: Page) {
  return (await page.locator('.monaco-editor .view-lines').innerText())
    .replaceAll('\u00a0', ' ')
    .trim();
}
