import { createClient } from '@hey-api/openapi-ts';
import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, relative } from 'node:path';
import { fileURLToPath, URL } from 'node:url';

import config from '../openapi-ts.config.mjs';

const projectDirectory = fileURLToPath(new URL('..', import.meta.url));
const generatedDirectory = join(projectDirectory, 'src/generated/api');
const temporaryDirectory = await mkdtemp(join(tmpdir(), 'cherry-oj-web-api-'));

async function readTree(directory, root = directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = new Map();

  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      for (const [name, content] of await readTree(path, root)) {
        files.set(name, content);
      }
    } else if (entry.isFile()) {
      files.set(relative(root, path), await readFile(path, 'utf8'));
    }
  }

  return files;
}

try {
  await createClient({ ...config, output: temporaryDirectory });

  const expected = await readTree(temporaryDirectory);
  const actual = await readTree(generatedDirectory);
  const expectedNames = [...expected.keys()].sort();
  const actualNames = [...actual.keys()].sort();

  if (JSON.stringify(expectedNames) !== JSON.stringify(actualNames)) {
    throw new Error('OpenAPI 生成文件集合已漂移，请运行 npm run generate:api。');
  }

  for (const name of expectedNames) {
    if (expected.get(name) !== actual.get(name)) {
      throw new Error(`OpenAPI 生成文件已漂移：${name}，请运行 npm run generate:api。`);
    }
  }
} finally {
  await rm(temporaryDirectory, { recursive: true, force: true });
}
