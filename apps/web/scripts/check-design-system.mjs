/* global console */

import { execFile } from 'node:child_process';
import {
  copyFile,
  lstat,
  mkdir,
  mkdtemp,
  readdir,
  readFile,
  rm,
  symlink,
  unlink,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { extname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { argv, execPath } from 'node:process';
import { fileURLToPath, URL } from 'node:url';
import { promisify } from 'node:util';

const webDirectory = fileURLToPath(new URL('..', import.meta.url));
const designSystemDirectory = join(webDirectory, 'design-system');
const themeManifestPath = join(designSystemDirectory, 'themes.manifest.json');
const execFileAsync = promisify(execFile);

const scannedExtensions = new Set([
  '.cjs',
  '.css',
  '.html',
  '.js',
  '.jsx',
  '.json',
  '.mjs',
  '.svg',
  '.ts',
  '.tsx',
]);

const productionDirectoryTargets = ['.storybook', 'e2e', 'public', 'scripts', 'src'];
const productionFileTargets = [
  'components.json',
  'eslint.config.js',
  'index.html',
  'openapi-ts.config.mjs',
  'package.json',
  'playwright.config.ts',
  'prettier.config.mjs',
  'tsconfig.app.json',
  'tsconfig.json',
  'tsconfig.node.json',
  'vite.config.ts',
];

// The checker contains the forbidden spellings as test data. Keep this exact-file exception local.
const fullyAllowedFiles = new Set(['scripts/check-design-system.mjs']);

// Exceptions are rule-specific and exact-path only. There is intentionally no wildcard for SVGs
// or tests; a future legitimate fixture must name both its file and the one rule it exercises.
const ruleAllowlist = new Map([
  [
    'literal-theme-id',
    new Set([
      'index.html',
      'public/generated/theme-init.js',
      'src/generated/design-system/themes.ts',
    ]),
  ],
]);

function normalizePath(path) {
  return path.split(sep).join('/');
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function readThemeIds() {
  const manifest = JSON.parse(await readFile(themeManifestPath, 'utf8'));
  if (manifest === null || typeof manifest !== 'object' || !Array.isArray(manifest.themes)) {
    throw new Error('设计系统源码检查失败：themes.manifest.json 缺少 themes 数组。');
  }

  const themeIds = manifest.themes.map((theme, index) => {
    if (
      theme === null ||
      typeof theme !== 'object' ||
      typeof theme.id !== 'string' ||
      theme.id.length === 0
    ) {
      throw new Error(`设计系统源码检查失败：themes[${index}].id 必须是非空字符串。`);
    }
    return theme.id;
  });

  if (themeIds.length === 0) {
    throw new Error('设计系统源码检查失败：themes.manifest.json 没有登记主题。');
  }

  return themeIds;
}

function createRules(themeIds) {
  const themePattern = themeIds
    .slice()
    .sort((left, right) => right.length - left.length)
    .map(escapeRegExp)
    .join('|');

  return [
    {
      id: 'external-design-system-source',
      message: '禁止从 Web 根目录外读取设计系统文档；请使用本地设计系统包。',
      pattern: new RegExp(String.raw`\bdocs[\\/]+(?:\.[\\/]+)*design-system\b`, 'g'),
    },
    {
      id: 'raw-hex',
      message: '禁止在 Web 源码或配置中写 raw hex；请使用 semantic token。',
      pattern:
        /(?<![A-Za-z0-9_-])#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{4}|[0-9a-f]{3})(?![0-9a-f])/gi,
    },
    {
      id: 'raw-oklch',
      message: '禁止在 Web 源码或配置中写 OKLCH；颜色值只由 canonical 主题提供。',
      pattern: /\boklch\s*\(/gi,
    },
    {
      id: 'legacy-dark-selector',
      message: '禁止旧 .dark 主题选择器；主题只由 data-theme 表达。',
      pattern: /\.dark\b/g,
    },
    {
      id: 'legacy-dark-utility',
      message: '禁止 dark: 主题分支；组件必须只消费 semantic token。',
      pattern: /(?<![A-Za-z0-9_-])dark:/g,
    },
    {
      id: 'literal-theme-id',
      message: '业务源码和配置不得枚举 theme id；请消费生成 registry。',
      pattern: new RegExp(`(?<![A-Za-z0-9_-])(?:${themePattern})(?![A-Za-z0-9_-])`, 'g'),
    },
    {
      id: 'raw-design-token',
      message: '禁止消费 --ds-raw-* primitive；请使用 semantic token。',
      pattern: /--ds-raw-[A-Za-z0-9_-]+/g,
    },
    {
      id: 'dynamic-color-mix',
      message: '禁止用 color-mix() 动态制造必要对比；请使用已审核的 semantic token。',
      pattern: /\bcolor-mix\s*\(/gi,
    },
    {
      id: 'state-opacity-utility',
      message: 'disabled/placeholder 不得通过 Tailwind opacity 或 alpha modifier 降低可读性。',
      pattern:
        /(?:disabled|aria-disabled|aria-\[disabled(?:=[^\]]+)?\]|data-disabled|data-\[disabled(?:=[^\]]+)?\]|placeholder):[^\s"'`<>]*(?:opacity-[^\s"'`<>]+|(?:accent|bg|border|caret|decoration|fill|outline|ring|shadow|stroke|text)-[A-Za-z0-9_()[\].-]+\/(?:\d{1,3}|\[[^\]]+\]))/gi,
    },
    {
      id: 'state-opacity-css',
      message: 'disabled/placeholder 不得声明 opacity；请使用专门的前景 token。',
      pattern:
        /(?::disabled|::placeholder|\[disabled(?:=[^\]]+)?\]|\[aria-disabled(?:=[^\]]+)?\]|\[data-disabled(?:=[^\]]+)?\])[^{}]*\{[^{}]*\bopacity\s*:/gis,
    },
  ];
}

async function collectDirectoryFiles(directory) {
  const directoryMetadata = await lstat(directory);
  if (directoryMetadata.isSymbolicLink()) {
    throw new Error(
      `设计系统源码检查失败：扫描树不得包含符号链接：${normalizePath(relative(webDirectory, directory))}。`,
    );
  }
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const path = join(directory, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error(
        `设计系统源码检查失败：扫描树不得包含符号链接：${normalizePath(relative(webDirectory, path))}。`,
      );
    }
    if (entry.isDirectory()) {
      files.push(...(await collectDirectoryFiles(path)));
    } else if (entry.isFile() && scannedExtensions.has(extname(entry.name))) {
      files.push(path);
    }
  }

  return files;
}

async function collectProductionFiles() {
  const files = [];

  for (const target of productionDirectoryTargets) {
    files.push(...(await collectDirectoryFiles(join(webDirectory, target))));
  }
  for (const target of productionFileTargets) {
    files.push(join(webDirectory, target));
  }

  return [...new Set(files)].sort();
}

function positionAt(content, index) {
  const before = content.slice(0, index);
  const lines = before.split('\n');
  return { line: lines.length, column: (lines.at(-1)?.length ?? 0) + 1 };
}

function isAllowed(relativePath, ruleId) {
  return (
    fullyAllowedFiles.has(relativePath) || ruleAllowlist.get(ruleId)?.has(relativePath) === true
  );
}

async function scanFiles(files, rootDirectory, rules) {
  const violations = [];

  for (const path of files) {
    const relativePath = normalizePath(relative(rootDirectory, path));
    const metadata = await lstat(path);
    if (metadata.isSymbolicLink()) {
      throw new Error(`设计系统源码检查失败：扫描文件不得是符号链接：${relativePath}。`);
    }
    const content = await readFile(path, 'utf8');

    for (const rule of rules) {
      if (isAllowed(relativePath, rule.id)) continue;

      for (const match of content.matchAll(rule.pattern)) {
        const index = match.index ?? 0;
        violations.push({
          ...positionAt(content, index),
          path: relativePath,
          ruleId: rule.id,
          message: rule.message,
          excerpt: match[0].replaceAll(/\s+/g, ' ').slice(0, 100),
        });
      }
    }
  }

  return violations.sort(
    (left, right) =>
      left.path.localeCompare(right.path) ||
      left.line - right.line ||
      left.column - right.column ||
      left.ruleId.localeCompare(right.ruleId),
  );
}

function printViolations(violations) {
  for (const violation of violations) {
    console.error(
      `${violation.path}:${violation.line}:${violation.column} [${violation.ruleId}] ${violation.message}`,
    );
    console.error(`  ${violation.excerpt}`);
  }
}

async function runProductionScan(rules) {
  const violations = await scanFiles(await collectProductionFiles(), webDirectory, rules);
  const externalSourceRule = rules.find((rule) => rule.id === 'external-design-system-source');
  if (externalSourceRule === undefined) {
    throw new Error('设计系统源码检查失败：缺少外部设计系统源规则。');
  }
  violations.push(
    ...(await scanFiles(await collectDirectoryFiles(designSystemDirectory), webDirectory, [
      externalSourceRule,
    ])),
  );
  violations.sort(
    (left, right) =>
      left.path.localeCompare(right.path) ||
      left.line - right.line ||
      left.column - right.column ||
      left.ruleId.localeCompare(right.ruleId),
  );
  if (violations.length > 0) {
    printViolations(violations);
    throw new Error(`设计系统源码检查失败：发现 ${violations.length} 处违规。`);
  }
  console.log('设计系统源码检查通过。');
}

async function runGeneratorDriftSelfTest(temporaryDirectory) {
  const fixtureRepository = join(temporaryDirectory, 'stale-generated-repository');
  const fixtureWebDirectory = join(fixtureRepository, 'apps/web');
  const fixtureDesignSystemDirectory = join(fixtureWebDirectory, 'design-system');
  const fixtureGenerator = join(fixtureWebDirectory, 'scripts/generate-design-system.mjs');
  const fixtureManifestPath = join(fixtureDesignSystemDirectory, 'themes.manifest.json');

  await mkdir(resolve(fixtureGenerator, '..'), { recursive: true });
  await mkdir(fixtureDesignSystemDirectory, { recursive: true });
  await copyFile(join(webDirectory, 'scripts/generate-design-system.mjs'), fixtureGenerator);
  await copyFile(themeManifestPath, fixtureManifestPath);

  const manifest = JSON.parse(await readFile(themeManifestPath, 'utf8'));
  if (manifest === null || typeof manifest !== 'object' || !Array.isArray(manifest.themes)) {
    throw new Error('设计系统生成漂移自测试失败：themes.manifest.json 缺少 themes 数组。');
  }

  for (const [index, theme] of manifest.themes.entries()) {
    if (theme === null || typeof theme !== 'object' || typeof theme.file !== 'string') {
      throw new Error(`设计系统生成漂移自测试失败：themes[${index}].file 必须是字符串。`);
    }

    const sourceTheme = resolve(designSystemDirectory, theme.file);
    const fixtureTheme = resolve(fixtureDesignSystemDirectory, theme.file);
    const relativeTheme = relative(fixtureDesignSystemDirectory, fixtureTheme);
    if (
      relativeTheme === '..' ||
      relativeTheme.startsWith(`..${sep}`) ||
      isAbsolute(relativeTheme)
    ) {
      throw new Error(`设计系统生成漂移自测试失败：主题文件越界：${theme.file}。`);
    }
    await mkdir(resolve(fixtureTheme, '..'), { recursive: true });
    await copyFile(sourceTheme, fixtureTheme);
  }

  const runGenerator = (...argumentsList) =>
    execFileAsync(execPath, [fixtureGenerator, ...argumentsList], {
      cwd: fixtureWebDirectory,
      encoding: 'utf8',
    });
  const expectGeneratorFailure = async (name, expectedMessage) => {
    try {
      await runGenerator('--check');
    } catch (error) {
      const output = `${error?.stdout ?? ''}\n${error?.stderr ?? ''}`;
      if (!output.includes(expectedMessage)) {
        throw new Error(
          `设计系统生成漂移自测试失败：${name} 以非预期原因失败；期望 ${expectedMessage}。\n${output}`,
        );
      }
      return;
    }
    throw new Error(`设计系统生成漂移自测试失败：${name} 未被拒绝。`);
  };

  await runGenerator();
  const fixtureRegistry = join(fixtureWebDirectory, 'src/generated/design-system/themes.ts');
  await writeFile(
    fixtureRegistry,
    `${await readFile(fixtureRegistry, 'utf8')}/* stale generated fixture */\n`,
    'utf8',
  );

  let rejectedStaleOutput = false;
  try {
    await runGenerator('--check');
  } catch (error) {
    const stderr =
      error !== null && typeof error === 'object' && 'stderr' in error
        ? String(error.stderr)
        : String(error);
    if (!stderr.includes('生成文件已漂移')) {
      throw new Error(`设计系统生成漂移自测试失败：检查以非预期原因失败。\n${stderr}`);
    }
    rejectedStaleOutput = true;
  }

  if (!rejectedStaleOutput) {
    throw new Error('设计系统生成漂移自测试失败：篡改临时生成物后检查仍通过。');
  }

  await runGenerator();
  await runGenerator('--check');

  const manifestContent = await readFile(fixtureManifestPath, 'utf8');
  const externalManifestPath = join(fixtureWebDirectory, 'outside-themes.manifest.json');
  await writeFile(externalManifestPath, manifestContent, 'utf8');
  await unlink(fixtureManifestPath);
  await symlink(
    relative(resolve(fixtureManifestPath, '..'), externalManifestPath),
    fixtureManifestPath,
  );
  await expectGeneratorFailure('manifest 符号链接', 'themes.manifest.json 不得是符号链接');
  await unlink(fixtureManifestPath);
  await writeFile(fixtureManifestPath, manifestContent, 'utf8');
  await unlink(externalManifestPath);

  const firstTheme = manifest.themes[0];
  const firstThemePath = resolve(fixtureDesignSystemDirectory, firstTheme.file);
  const firstThemeContent = await readFile(firstThemePath, 'utf8');
  const externalThemePath = join(fixtureWebDirectory, 'outside-theme.css');
  await writeFile(externalThemePath, firstThemeContent, 'utf8');
  await unlink(firstThemePath);
  await symlink(relative(resolve(firstThemePath, '..'), externalThemePath), firstThemePath);
  await expectGeneratorFailure('theme 符号链接', `主题 ${firstTheme.id} 的 file 不得是符号链接`);
  await unlink(firstThemePath);
  await writeFile(firstThemePath, firstThemeContent, 'utf8');
  await unlink(externalThemePath);
  await runGenerator('--check');

  const registryContent = await readFile(fixtureRegistry, 'utf8');
  const externalRegistryPath = join(fixtureWebDirectory, 'outside-themes.ts');
  await writeFile(externalRegistryPath, registryContent, 'utf8');
  await unlink(fixtureRegistry);
  await symlink(relative(resolve(fixtureRegistry, '..'), externalRegistryPath), fixtureRegistry);
  await expectGeneratorFailure('生成输出符号链接', '生成文件不得是符号链接');
  await unlink(fixtureRegistry);
  await writeFile(fixtureRegistry, registryContent, 'utf8');
  await unlink(externalRegistryPath);
  await runGenerator('--check');
}

async function runSelfTest(rules) {
  const themeRule = rules.find((rule) => rule.id === 'literal-theme-id');
  const firstThemeId = (await readThemeIds())[0];
  if (themeRule === undefined || firstThemeId === undefined) {
    throw new Error('设计系统源码检查自测试失败：无法建立 theme id fixture。');
  }

  const fixtures = [
    [
      'external design-system source',
      'external-design-system-source',
      `@import '${['docs', 'design-system', 'tokens.css'].join('/')}';\n`,
      '.css',
    ],
    [
      'external source with duplicate separators',
      'external-design-system-source',
      `@import '${['docs', '', 'design-system', 'tokens.css'].join('/')}';\n`,
      '.css',
    ],
    [
      'external source with a current-directory segment',
      'external-design-system-source',
      `@import '${['docs', '.', 'design-system', 'tokens.css'].join('/')}';\n`,
      '.css',
    ],
    ['raw hex', 'raw-hex', "export const color = '#123456';\n", '.ts'],
    ['OKLCH', 'raw-oklch', '.fixture { color: oklch(0.5 0.2 40); }\n', '.css'],
    ['.dark selector', 'legacy-dark-selector', '.dark .fixture { color: inherit; }\n', '.css'],
    ['dark: utility', 'legacy-dark-utility', "export const classes = 'dark:bg-surface';\n", '.ts'],
    ['literal theme id', themeRule.id, `export const theme = '${firstThemeId}';\n`, '.ts'],
    ['raw design token', 'raw-design-token', '.fixture { color: var(--ds-raw-gray-1); }\n', '.css'],
    [
      'color-mix',
      'dynamic-color-mix',
      '.fixture { color: color-mix(in srgb, currentColor 50%, transparent); }\n',
      '.css',
    ],
    [
      'disabled Tailwind opacity',
      'state-opacity-utility',
      "export const classes = 'disabled:opacity-50';\n",
      '.ts',
    ],
    [
      'placeholder Tailwind alpha',
      'state-opacity-utility',
      "export const classes = 'placeholder:text-foreground/50';\n",
      '.ts',
    ],
    ['disabled CSS opacity', 'state-opacity-css', 'button:disabled { opacity: 0.5; }\n', '.css'],
    [
      'placeholder CSS opacity',
      'state-opacity-css',
      'input::placeholder { opacity: 0.5; }\n',
      '.css',
    ],
  ];

  const allowedFixtures = [
    ['disabled width fraction', "export const width = 'disabled:w-1/2';\n", '.ts'],
    [
      'semantic placeholder foreground',
      "export const placeholder = 'placeholder:text-muted-foreground';\n",
      '.ts',
    ],
    [
      'non-disabled transition state',
      "export const animation = 'data-[starting-style]:opacity-0';\n",
      '.ts',
    ],
    ['ordinary decorative opacity', '.fixture { opacity: 0.5; }\n', '.css'],
  ];

  const temporaryDirectory = await mkdtemp(join(tmpdir(), 'cherry-oj-design-system-check-'));
  try {
    for (const [name, expectedRuleId, content, extension] of fixtures) {
      const fixturePath = join(temporaryDirectory, `fixture${extension}`);
      await writeFile(fixturePath, content, 'utf8');

      const violations = await scanFiles([fixturePath], temporaryDirectory, rules);
      if (!violations.some((violation) => violation.ruleId === expectedRuleId)) {
        throw new Error(`设计系统源码检查自测试失败：${name} 没有触发 ${expectedRuleId}。`);
      }

      await unlink(fixturePath);
      const recovered = await scanFiles(
        await collectDirectoryFiles(temporaryDirectory),
        temporaryDirectory,
        rules,
      );
      if (recovered.length !== 0) {
        throw new Error(`设计系统源码检查自测试失败：移除 ${name} fixture 后未恢复。`);
      }
    }

    for (const [index, [name, content, extension]] of allowedFixtures.entries()) {
      const fixturePath = join(temporaryDirectory, `allowed-${index}${extension}`);
      await writeFile(fixturePath, content, 'utf8');
      const violations = await scanFiles([fixturePath], temporaryDirectory, rules);
      if (violations.length !== 0) {
        printViolations(violations);
        throw new Error(`设计系统源码检查自测试失败：合法 fixture ${name} 被误报。`);
      }
      await unlink(fixturePath);
    }

    const symlinkTarget = join(temporaryDirectory, 'symlink-target.ts');
    const symlinkFixture = join(temporaryDirectory, 'symlink-fixture.ts');
    await writeFile(symlinkTarget, 'export const safe = true;\n', 'utf8');
    await symlink('symlink-target.ts', symlinkFixture);
    let rejectedSymlink = false;
    try {
      await collectDirectoryFiles(temporaryDirectory);
    } catch (error) {
      if (!String(error).includes('扫描树不得包含符号链接')) throw error;
      rejectedSymlink = true;
    }
    if (!rejectedSymlink) {
      throw new Error('设计系统源码检查自测试失败：扫描树符号链接未被拒绝。');
    }
    await unlink(symlinkFixture);
    await unlink(symlinkTarget);
    if ((await collectDirectoryFiles(temporaryDirectory)).length !== 0) {
      throw new Error('设计系统源码检查自测试失败：移除扫描树符号链接 fixture 后未恢复。');
    }

    await runGeneratorDriftSelfTest(temporaryDirectory);
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true });
  }

  console.log(
    `设计系统源码检查自测试通过：${fixtures.length} 个源码负向 fixture、1 个扫描树符号链接 fixture、1 个生成漂移 fixture 与 3 个生成路径符号链接 fixture 均失败且移除后恢复，${allowedFixtures.length} 个合法 fixture 无误报。`,
  );
}

const argumentsList = argv.slice(2);
if (
  argumentsList.length > 1 ||
  (argumentsList.length === 1 && argumentsList[0] !== '--self-test')
) {
  throw new Error('设计系统源码检查只接受可选参数 --self-test。');
}

const rules = createRules(await readThemeIds());
if (argumentsList[0] === '--self-test') {
  await runSelfTest(rules);
} else {
  await runProductionScan(rules);
}
