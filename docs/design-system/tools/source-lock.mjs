#!/usr/bin/env node
/*
 * Modified for Cherry OJ on 2026-09-03.
 * Generates a deterministic integrity lock for the imported Claude Design
 * source snapshot. The source directory is evidence only and never a Web input.
 */

import { createHash } from "node:crypto";
import { lstat, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptFile = fileURLToPath(import.meta.url);
const rootDir = path.resolve(path.dirname(scriptFile), "..");
const sourceDirectory = path.join(rootDir, "source/claude-design-v1");
const outputPath = path.join(rootDir, "source-lock.json");

async function collectFiles(directory) {
  const metadata = await lstat(directory);
  if (metadata.isSymbolicLink()) throw new Error("source snapshot must not contain symbolic links");
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`source snapshot contains symbolic link: ${entry.name}`);
    if (entry.isDirectory()) files.push(...(await collectFiles(absolutePath)));
    if (entry.isFile()) files.push(absolutePath);
  }
  return files;
}

async function generateLock() {
  const absoluteFiles = await collectFiles(sourceDirectory);
  const files = [];
  let totalBytes = 0;

  for (const absolutePath of absoluteFiles) {
    const content = await readFile(absolutePath);
    const relativePath = path.relative(sourceDirectory, absolutePath).split(path.sep).join("/");
    totalBytes += content.byteLength;
    files.push({
      path: relativePath,
      bytes: content.byteLength,
      sha256: createHash("sha256").update(content).digest("hex")
    });
  }

  const rootMaterial = files.map((file) => `${file.sha256}  ${file.path}\n`).join("");
  return {
    schemaVersion: 1,
    provenance: {
      source: "Claude Design export supplied by the Cherry OJ project owner",
      importedFrom: "local download; absolute path intentionally not persisted",
      importedAt: "2026-09-03"
    },
    modified: {
      for: "Cherry OJ",
      date: "2026-09-03",
      summary: "Records the unmodified 99-file visual source snapshot used by WORK-034."
    },
    sourceDirectory: "source/claude-design-v1",
    fileCount: files.length,
    totalBytes,
    rootSha256: createHash("sha256").update(rootMaterial).digest("hex"),
    files
  };
}

async function run() {
  const argumentsList = process.argv.slice(2);
  if (argumentsList.length > 1 || (argumentsList.length === 1 && argumentsList[0] !== "--check")) {
    throw new Error("source-lock.mjs accepts only the optional --check argument");
  }
  const expected = `${JSON.stringify(await generateLock(), null, 2)}\n`;
  if (argumentsList[0] === "--check") {
    const actual = await readFile(outputPath, "utf8");
    if (actual !== expected) throw new Error("source-lock.json is stale; run node tools/source-lock.mjs");
    console.log("source snapshot lock is current");
    return;
  }
  await writeFile(outputPath, expected, "utf8");
  console.log("generated source-lock.json");
}

run().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
