import { argv } from 'node:process';
import { lstat, mkdir, mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, URL } from 'node:url';

const webDirectory = fileURLToPath(new URL('..', import.meta.url));
const designSystemDirectory = join(webDirectory, 'design-system');
const manifestPath = join(designSystemDirectory, 'themes.manifest.json');
const storageKey = 'cherry-oj.theme';
const colorSchemeAttribute = 'data-color-scheme';
const outputs = {
  registry: join(webDirectory, 'src/generated/design-system/themes.ts'),
  bootstrap: join(webDirectory, 'public/generated/theme-init.js'),
};

function fail(message) {
  throw new Error('设计系统主题生成失败：' + message);
}

function requireObject(value, label) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    fail(label + ' 必须是对象。');
  }
  return value;
}

function requireString(value, label) {
  if (typeof value !== 'string' || value.length === 0) {
    fail(label + ' 必须是非空字符串。');
  }
  return value;
}

function isInsideDirectory(root, candidate) {
  const relativePath = relative(root, candidate);
  return relativePath !== '..' && !relativePath.startsWith('..' + sep) && !isAbsolute(relativePath);
}

async function resolveDesignSystemInput(file, label) {
  if (typeof file !== 'string' || file.length === 0 || isAbsolute(file)) {
    fail(label + ' 必须是设计系统包内的非空相对路径。');
  }

  const absoluteFile = resolve(designSystemDirectory, file);
  const relativeFile = relative(designSystemDirectory, absoluteFile);
  if (relativeFile === '..' || relativeFile.startsWith('..' + sep) || isAbsolute(relativeFile)) {
    fail(label + ' 越出 Web 设计系统包：' + file);
  }

  const rootMetadata = await lstat(designSystemDirectory);
  if (rootMetadata.isSymbolicLink()) {
    fail('design-system 根目录不得是符号链接。');
  }
  const metadata = await lstat(absoluteFile);
  if (metadata.isSymbolicLink()) {
    fail(label + ' 不得是符号链接：' + file);
  }
  if (!metadata.isFile()) {
    fail(label + ' 必须是普通文件：' + file);
  }

  const [rootRealPath, fileRealPath] = await Promise.all([
    realpath(designSystemDirectory),
    realpath(absoluteFile),
  ]);
  if (!isInsideDirectory(rootRealPath, fileRealPath)) {
    fail(label + ' 的真实路径越出 Web 设计系统包：' + file);
  }
  return fileRealPath;
}

async function resolveWebOutput(outputPath) {
  const relativeOutput = relative(webDirectory, outputPath);
  if (!isInsideDirectory(webDirectory, outputPath)) {
    fail('生成文件越出 Web 根目录：' + relativeOutput);
  }

  const webRootMetadata = await lstat(webDirectory);
  if (webRootMetadata.isSymbolicLink()) {
    fail('Web 根目录不得是符号链接。');
  }
  const webRealPath = await realpath(webDirectory);

  let metadata;
  try {
    metadata = await lstat(outputPath);
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
    const parentRealPath = await realpath(resolve(outputPath, '..'));
    if (!isInsideDirectory(webRealPath, parentRealPath)) {
      fail('生成文件父目录的真实路径越出 Web 根目录：' + relativeOutput);
    }
    return outputPath;
  }

  if (metadata.isSymbolicLink()) {
    fail('生成文件不得是符号链接：' + relativeOutput);
  }
  if (!metadata.isFile()) {
    fail('生成文件必须是普通文件：' + relativeOutput);
  }
  const outputRealPath = await realpath(outputPath);
  if (!isInsideDirectory(webRealPath, outputRealPath)) {
    fail('生成文件的真实路径越出 Web 根目录：' + relativeOutput);
  }
  return outputRealPath;
}

function quoteForJavaScript(value) {
  return (
    "'" +
    value
      .replaceAll('\\', '\\\\')
      .replaceAll("'", "\\'")
      .replaceAll('\r', '\\r')
      .replaceAll('\n', '\\n')
      .replaceAll('\u2028', '\\u2028')
      .replaceAll('\u2029', '\\u2029') +
    "'"
  );
}

async function loadManifest() {
  let parsed;
  try {
    const safeManifestPath = await resolveDesignSystemInput(
      relative(designSystemDirectory, manifestPath),
      'themes.manifest.json',
    );
    parsed = JSON.parse(await readFile(safeManifestPath, 'utf8'));
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    fail('无法读取 themes.manifest.json：' + detail);
  }

  const manifest = requireObject(parsed, 'themes.manifest.json');
  if (manifest.schemaVersion !== 1) {
    fail('只支持 schemaVersion=1。');
  }
  if (manifest.selectorAttribute !== 'data-theme') {
    fail('selectorAttribute 必须是 data-theme。');
  }
  if (manifest.followSystemByDefault !== false) {
    fail('followSystemByDefault 必须为 false。');
  }
  if (!Array.isArray(manifest.themes) || manifest.themes.length === 0) {
    fail('themes 必须是非空数组。');
  }

  const seenIds = new Set();
  const themes = [];
  for (const [index, candidate] of manifest.themes.entries()) {
    const theme = requireObject(candidate, 'themes[' + index + ']');
    const id = requireString(theme.id, 'themes[' + index + '].id');
    if (!/^[a-z][a-z0-9-]*$/.test(id)) {
      fail('主题 id 只能使用小写字母、数字和连字符：' + id);
    }
    if (seenIds.has(id)) {
      fail('主题 id 重复：' + id);
    }
    seenIds.add(id);

    const label = requireString(theme.label, 'themes[' + index + '].label');
    const colorScheme = requireString(theme.colorScheme, 'themes[' + index + '].colorScheme');
    if (colorScheme !== 'dark' && colorScheme !== 'light') {
      fail('主题 ' + id + ' 的 colorScheme 必须是 dark 或 light。');
    }
    const file = requireString(theme.file, 'themes[' + index + '].file');
    if (!file.endsWith('.css')) {
      fail('主题 ' + id + ' 的 file 必须指向 CSS。');
    }
    await resolveDesignSystemInput(file, '主题 ' + id + ' 的 file');
    if (!Number.isInteger(theme.version) || theme.version < 1) {
      fail('主题 ' + id + ' 的 version 必须是正整数。');
    }

    themes.push({ id, label, colorScheme, file, version: theme.version });
  }

  const defaultTheme = requireString(manifest.defaultTheme, 'defaultTheme');
  const fallbackTheme = requireString(manifest.fallbackTheme, 'fallbackTheme');
  if (!seenIds.has(defaultTheme)) {
    fail('defaultTheme 未在 themes 中注册：' + defaultTheme);
  }
  if (!seenIds.has(fallbackTheme)) {
    fail('fallbackTheme 未在 themes 中注册：' + fallbackTheme);
  }

  return {
    selectorAttribute: manifest.selectorAttribute,
    defaultTheme,
    fallbackTheme,
    followSystemByDefault: manifest.followSystemByDefault,
    themes,
  };
}

function renderRegistry(manifest) {
  const themeLines = manifest.themes.flatMap((theme) => [
    '  {',
    '    id: ' + quoteForJavaScript(theme.id) + ',',
    '    label: ' + quoteForJavaScript(theme.label) + ',',
    '    colorScheme: ' + quoteForJavaScript(theme.colorScheme) + ',',
    '    file: ' + quoteForJavaScript(theme.file) + ',',
    '    version: ' + theme.version + ',',
    '  },',
  ]);

  return [
    '/* This file is generated by scripts/generate-design-system.mjs. Do not edit. */',
    '',
    'export const themeRegistry = [',
    ...themeLines,
    '] as const;',
    '',
    'export type ThemeDefinition = (typeof themeRegistry)[number];',
    "export type ThemeId = ThemeDefinition['id'];",
    "export type ThemeColorScheme = ThemeDefinition['colorScheme'];",
    '',
    'export const themeSelectorAttribute = ' + quoteForJavaScript(manifest.selectorAttribute) + ';',
    'export const colorSchemeAttribute = ' + quoteForJavaScript(colorSchemeAttribute) + ';',
    'export const defaultThemeId: ThemeId = ' + quoteForJavaScript(manifest.defaultTheme) + ';',
    'export const fallbackThemeId: ThemeId = ' + quoteForJavaScript(manifest.fallbackTheme) + ';',
    'export const themeStorageKey = ' + quoteForJavaScript(storageKey) + ';',
    'export const followSystemByDefault = ' + manifest.followSystemByDefault + ';',
    '',
  ].join('\n');
}

function renderBootstrap(manifest) {
  const colorSchemeLines = manifest.themes.map(
    (theme) => "    '" + theme.id + "': '" + theme.colorScheme + "',",
  );

  return [
    '/* This file is generated by scripts/generate-design-system.mjs. Do not edit. */',
    '/* global document, window */',
    '(function initializeTheme() {',
    "  'use strict';",
    '',
    '  const themeColorSchemes = Object.freeze({',
    ...colorSchemeLines,
    '  });',
    "  const defaultTheme = '" + manifest.defaultTheme + "';",
    "  const fallbackTheme = '" + manifest.fallbackTheme + "';",
    "  const storageKey = '" + storageKey + "';",
    '  const root = document.documentElement;',
    '  let storedTheme = null;',
    '  let storageReadable = true;',
    '',
    '  try {',
    '    storedTheme = window.localStorage.getItem(storageKey);',
    '  } catch {',
    '    storageReadable = false;',
    '  }',
    '',
    '  let theme = defaultTheme;',
    "  if (typeof storedTheme === 'string' && storedTheme.trim().length > 0) {",
    '    theme = Object.prototype.hasOwnProperty.call(themeColorSchemes, storedTheme)',
    '      ? storedTheme',
    '      : fallbackTheme;',
    '  }',
    '',
    '  const invalidStoredTheme =',
    "    typeof storedTheme === 'string' &&",
    '    (storedTheme.trim().length === 0 ||',
    '      !Object.prototype.hasOwnProperty.call(themeColorSchemes, storedTheme));',
    '  if (storageReadable && invalidStoredTheme) {',
    '    try {',
    '      window.localStorage.removeItem(storageKey);',
    '    } catch {',
    '      // Storage cleanup is best-effort; rendering must continue.',
    '    }',
    '  }',
    '',
    "  root.setAttribute('" + manifest.selectorAttribute + "', theme);",
    "  root.setAttribute('" + colorSchemeAttribute + "', themeColorSchemes[theme]);",
    '})();',
    '',
  ].join('\n');
}

async function expectedOutputs() {
  const manifest = await loadManifest();
  return new Map([
    [outputs.registry, renderRegistry(manifest)],
    [outputs.bootstrap, renderBootstrap(manifest)],
  ]);
}

async function checkGenerated(expected) {
  const temporaryDirectory = await mkdtemp(join(tmpdir(), 'cherry-oj-design-system-'));
  try {
    for (const [path, content] of expected) {
      const relativePath = relative(webDirectory, path);
      const rebuiltPath = join(temporaryDirectory, relativePath);
      await mkdir(resolve(rebuiltPath, '..'), { recursive: true });
      await writeFile(rebuiltPath, content, 'utf8');

      let actual;
      try {
        actual = await readFile(await resolveWebOutput(path), 'utf8');
      } catch (error) {
        if (error?.code === 'ENOENT') fail('缺少生成文件：' + relativePath);
        throw error;
      }
      if (actual !== (await readFile(rebuiltPath, 'utf8'))) {
        fail('生成文件已漂移：' + relativePath);
      }
    }
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true });
  }
}

async function writeGenerated(expected) {
  for (const [path, content] of expected) {
    await mkdir(resolve(path, '..'), { recursive: true });
    await writeFile(await resolveWebOutput(path), content, 'utf8');
  }
}

const argumentsList = argv.slice(2);
if (argumentsList.length > 1 || (argumentsList.length === 1 && argumentsList[0] !== '--check')) {
  fail('只接受可选参数 --check。');
}

const expected = await expectedOutputs();
if (argumentsList[0] === '--check') {
  await checkGenerated(expected);
} else {
  await writeGenerated(expected);
}
