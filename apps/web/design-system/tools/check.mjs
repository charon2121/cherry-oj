#!/usr/bin/env node
/* global console, process */
/*
 * Modified for Cherry OJ on 2026-08-28.
 * Verifies manifest-driven generation, exact theme completeness, same-theme
 * alias resolution, WCAG contrast combinations, adapter mappings, provenance,
 * and package integrity. See ../NOTICE.md and ../LICENSE.open-design.
 */

import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import {
  cp,
  lstat,
  mkdtemp,
  readFile,
  readdir,
  realpath,
  rm,
  symlink,
  unlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { execPath } from "node:process";
import { promisify } from "node:util";
import { generateOutputs, parseCustomProperties, resolveInputPath, rootDir } from "./build.mjs";

const webDir = path.resolve(rootDir, "..");
const distributionLegalDir = path.join(webDir, "public/legal");
const execFileAsync = promisify(execFile);

const neutralSurfaces = [
  "--ds-canvas",
  "--ds-panel",
  "--ds-surface",
  "--ds-surface-subtle",
  "--ds-surface-raised",
  "--ds-surface-hover"
];
const statuses = ["success", "warning", "danger", "info", "special"];
const requiredNames = [
  ...neutralSurfaces,
  "--ds-surface-translucent",
  "--ds-surface-translucent-hover",
  "--ds-surface-translucent-selected",
  "--ds-fg",
  "--ds-fg-2",
  "--ds-fg-muted",
  "--ds-fg-meta",
  "--ds-fg-disabled",
  "--ds-fg-ghost",
  "--ds-border-soft",
  "--ds-border",
  "--ds-border-solid",
  "--ds-border-strong",
  "--ds-line-tertiary",
  "--ds-brand-surface",
  "--ds-brand-surface-hover",
  "--ds-brand-surface-active",
  "--ds-on-brand",
  "--ds-brand-foreground",
  "--ds-brand-foreground-hover",
  "--ds-brand-soft",
  "--ds-on-brand-soft",
  "--ds-link",
  "--ds-link-hover",
  "--ds-focus",
  "--ds-selection-surface",
  "--ds-selection-foreground",
  ...statuses.flatMap((status) => [
    `--ds-${status}-foreground`,
    `--ds-${status}-surface`,
    `--ds-${status}-border`,
    `--ds-${status}-solid`,
    `--ds-${status}-on-solid`
  ]),
  "--ds-overlay",
  "--ds-elevation-flat",
  "--ds-elevation-subtle",
  "--ds-elevation-ring",
  "--ds-elevation-inset",
  "--ds-elevation-dialog",
  "--ds-elevation-raised"
];

function createExpectedContractShape() {
  const shape = {};
  const define = (name, type, contrastClass, allowedOn = [], opaque = false) => {
    shape[name] = { type, contrastClass, allowedOn, opaque };
  };

  for (const name of neutralSurfaces) define(name, "color", "none", [], true);
  for (const name of [
    "--ds-surface-translucent",
    "--ds-surface-translucent-hover",
    "--ds-surface-translucent-selected"
  ]) {
    define(name, "color", "none");
  }
  for (const name of ["--ds-fg", "--ds-fg-2", "--ds-fg-muted", "--ds-fg-meta", "--ds-fg-disabled"]) {
    define(name, "color", "text", neutralSurfaces);
  }
  define("--ds-fg-ghost", "color", "text", neutralSurfaces);
  define("--ds-border-soft", "color", "decorative", neutralSurfaces);
  define("--ds-border", "color", "decorative", neutralSurfaces);
  define("--ds-border-solid", "color", "decorative", neutralSurfaces);
  define("--ds-border-strong", "color", "nonText", neutralSurfaces);
  define("--ds-line-tertiary", "color", "decorative", neutralSurfaces);

  const brandSurfaces = ["--ds-brand-surface", "--ds-brand-surface-hover", "--ds-brand-surface-active"];
  for (const name of brandSurfaces) define(name, "color", "none", [], true);
  define("--ds-on-brand", "color", "text", brandSurfaces);
  define("--ds-brand-foreground", "color", "text", [...neutralSurfaces, "--ds-brand-soft"]);
  define("--ds-brand-foreground-hover", "color", "text", [...neutralSurfaces, "--ds-brand-soft"]);
  define("--ds-brand-soft", "color", "none", [], true);
  define("--ds-on-brand-soft", "color", "text", ["--ds-brand-soft"]);
  define("--ds-link", "color", "text", neutralSurfaces);
  define("--ds-link-hover", "color", "text", neutralSurfaces);
  define("--ds-focus", "color", "nonText", neutralSurfaces);
  define("--ds-selection-surface", "color", "none", [], true);
  define("--ds-selection-foreground", "color", "text", ["--ds-selection-surface"]);

  for (const status of statuses) {
    const allowed = [...neutralSurfaces, `--ds-${status}-surface`];
    define(`--ds-${status}-foreground`, "color", "text", allowed);
    define(`--ds-${status}-surface`, "color", "none", [], true);
    define(`--ds-${status}-border`, "color", "nonText", allowed);
    define(`--ds-${status}-solid`, "color", "none", [], true);
    define(`--ds-${status}-on-solid`, "color", "text", [`--ds-${status}-solid`]);
  }

  define("--ds-overlay", "color", "none");
  define("--ds-elevation-flat", "shadow", "none");
  define("--ds-elevation-subtle", "shadow", "none");
  define("--ds-elevation-ring", "shadow", "none");
  define("--ds-elevation-inset", "shadow", "none");
  define("--ds-elevation-dialog", "shadow", "none");
  define("--ds-elevation-raised", "shadow", "none");
  return shape;
}

const expectedContractShape = createExpectedContractShape();
const fixedSourceProvenance = {
  designSource: "Claude Design export: Cherry OJ Design System",
  designSourceRootSha256: "68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d",
  designSourceFileCount: 99,
  sourceTokensSha256: "9f99cf1b4b799f1871b742542a56fc9dd8c9a179fc452c1e56e7b6e2cdfd022e",
  sourceDesignSha256: "4c7264d8bc0e26de761c550e9f0445b0e7d92078c1a288f3fdb604b4f6df8fb7",
  sourceLicenseSha256: "9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da"
};

const expectedContractRules = {
  themeMustDeclareEveryRequiredEntry: true,
  aliasesMustResolveWithinTheSameTheme: true,
  opaqueEntriesRejectAlpha: true,
  componentsMayConsumeRawTokens: false,
  necessaryStatusMustAlsoUseTextIconOrShape: true
};

const expectedPackageFiles = [
  "manifest.json",
  "README.md",
  "LICENSE.open-design",
  "LICENSE.fonts",
  "NOTICE.md",
  "tokens.foundation.css",
  "theme-contract.json",
  "themes.manifest.json",
  "tokens.css",
  "design-tokens.json",
  "tailwind-v4.css",
  "tools/build.mjs",
  "tools/check.mjs"
];

const adapterAliases = {
  "--background": "--ds-canvas",
  "--foreground": "--ds-fg",
  "--surface": "--ds-surface",
  "--surface-subtle": "--ds-surface-subtle",
  "--surface-raised": "--ds-surface-raised",
  "--card": "--ds-surface-raised",
  "--card-foreground": "--ds-fg",
  "--popover": "--ds-surface-raised",
  "--popover-foreground": "--ds-fg",
  "--secondary": "--ds-surface-subtle",
  "--secondary-foreground": "--ds-fg",
  "--muted": "--ds-surface-subtle",
  "--muted-foreground": "--ds-fg-muted",
  "--accent": "--ds-surface-hover",
  "--accent-foreground": "--ds-fg",
  "--primary": "--ds-brand-surface",
  "--primary-foreground": "--ds-on-brand",
  "--brand": "--ds-brand-foreground",
  "--brand-soft": "--ds-brand-soft",
  "--border": "--ds-border",
  "--border-strong": "--ds-border-strong",
  "--input": "--ds-border-strong",
  "--input-background": "--ds-surface-subtle",
  "--ring": "--ds-focus",
  "--destructive": "--ds-danger-solid",
  "--destructive-foreground": "--ds-danger-on-solid",
  "--sidebar": "--ds-panel",
  "--sidebar-foreground": "--ds-fg",
  "--sidebar-primary": "--ds-brand-surface",
  "--sidebar-primary-foreground": "--ds-on-brand",
  "--sidebar-accent": "--ds-surface-hover",
  "--sidebar-accent-foreground": "--ds-fg",
  "--sidebar-border": "--ds-border",
  "--sidebar-ring": "--ds-focus",
  "--chart-1": "--ds-brand-foreground",
  "--chart-2": "--ds-success-foreground",
  "--chart-3": "--ds-info-foreground",
  "--chart-4": "--ds-warning-foreground",
  "--chart-5": "--ds-special-foreground",
  "--radius": "--ds-radius-md",
  "--font-sans": "--ds-font-body",
  "--font-display": "--ds-font-display",
  "--font-mono": "--ds-font-mono"
};
for (const status of statuses) {
  adapterAliases[`--${status}`] = `--ds-${status}-foreground`;
  adapterAliases[`--${status}-soft`] = `--ds-${status}-surface`;
  adapterAliases[`--${status}-solid`] = `--ds-${status}-solid`;
  adapterAliases[`--${status}-on-solid`] = `--ds-${status}-on-solid`;
}

const errors = [];
let contrastChecks = 0;
let minimumContrast = { ratio: Number.POSITIVE_INFINITY, pair: "" };
const expectedMinimumContrast = {
  ratio: 3.4035078594052393,
  pair: "pure-white --ds-border-strong on --ds-surface-hover"
};

function fail(message) {
  errors.push(message);
}

function verifyFixedProvenance(label, provenance) {
  if (provenance?.source !== "OpenDesign curated bundled fixture: design-systems/linear-app") {
    fail(`${label} provenance must identify the OpenDesign curated bundled fixture`);
  }
  if (provenance?.officialLinearSource !== false) {
    fail(`${label} provenance must state officialLinearSource=false`);
  }
  for (const [field, expected] of Object.entries(fixedSourceProvenance)) {
    if (provenance?.[field] !== expected) fail(`${label} provenance ${field} must be ${expected}`);
  }
  if (provenance?.license !== "Apache-2.0") fail(`${label} provenance license must be Apache-2.0`);
  if (provenance?.licenseFile !== "LICENSE.open-design") {
    fail(`${label} provenance licenseFile must be LICENSE.open-design`);
  }
}

async function readText(relativePath) {
  return readFile(await resolveInputPath(relativePath), "utf8");
}

async function readJson(relativePath) {
  return JSON.parse(await readText(relativePath));
}

function isInsideRoot(relativePath) {
  return relativePath !== ".." && !relativePath.startsWith(`..${path.sep}`) && !path.isAbsolute(relativePath);
}

async function verifyNoSymlinks(directory, packageRoot = rootDir) {
  const directoryMetadata = await lstat(directory);
  if (directoryMetadata.isSymbolicLink()) {
    throw new Error(`design-system package contains a symbolic link: ${path.relative(packageRoot, directory) || "."}`);
  }

  const entries = await readdir(directory);
  for (const entry of entries) {
    const absolutePath = path.join(directory, entry);
    const metadata = await lstat(absolutePath);
    const relativePath = path.relative(packageRoot, absolutePath).split(path.sep).join("/");
    if (metadata.isSymbolicLink()) {
      throw new Error(`design-system package contains a symbolic link: ${relativePath}`);
    }
    if (metadata.isDirectory()) await verifyNoSymlinks(absolutePath, packageRoot);
  }
}

async function listFiles(directory) {
  const files = [];
  const entries = await readdir(directory);
  for (const entry of entries) {
    const absolutePath = path.join(directory, entry);
    const metadata = await lstat(absolutePath);
    if (metadata.isSymbolicLink()) {
      throw new Error(`design-system package contains a symbolic link: ${path.relative(rootDir, absolutePath)}`);
    }
    if (metadata.isDirectory()) {
      files.push(...(await listFiles(absolutePath)));
    } else if (metadata.isFile()) {
      files.push(path.relative(rootDir, absolutePath).split(path.sep).join("/"));
    }
  }
  return files;
}

async function readDistributionFile(fileName) {
  const legalMetadata = await lstat(distributionLegalDir);
  if (legalMetadata.isSymbolicLink()) throw new Error("public/legal must not be a symbolic link");

  const legalRealPath = await realpath(distributionLegalDir);
  const lexicalPath = path.join(distributionLegalDir, fileName);
  const metadata = await lstat(lexicalPath);
  if (metadata.isSymbolicLink()) throw new Error(`public/legal/${fileName} must not be a symbolic link`);

  const fileRealPath = await realpath(lexicalPath);
  if (!isInsideRoot(path.relative(legalRealPath, fileRealPath))) {
    throw new Error(`public/legal/${fileName} resolves outside public/legal`);
  }
  return readFile(fileRealPath, "utf8");
}

function duplicateCustomProperties(css) {
  const counts = new Map();
  for (const match of css.matchAll(/(--[a-zA-Z0-9-]+)\s*:/g)) {
    counts.set(match[1], (counts.get(match[1]) ?? 0) + 1);
  }
  return [...counts.entries()].filter(([, count]) => count > 1).map(([name]) => name);
}

function resolveValue(name, tokens, stack = []) {
  if (stack.includes(name)) throw new Error(`alias cycle: ${[...stack, name].join(" -> ")}`);
  const value = tokens[name];
  if (value === undefined) throw new Error(`missing alias target ${name}`);
  const alias = value.match(/^var\((--[a-zA-Z0-9-]+)\)$/);
  return alias ? resolveValue(alias[1], tokens, [...stack, name]) : value;
}

function parseColorValue(value) {
  const hex = value.match(/^#([0-9a-f]{6})$/i);
  if (hex) {
    return {
      r: Number.parseInt(hex[1].slice(0, 2), 16) / 255,
      g: Number.parseInt(hex[1].slice(2, 4), 16) / 255,
      b: Number.parseInt(hex[1].slice(4, 6), 16) / 255,
      a: 1
    };
  }
  const rgba = value.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i);
  if (rgba) {
    return {
      r: Number(rgba[1]) / 255,
      g: Number(rgba[2]) / 255,
      b: Number(rgba[3]) / 255,
      a: rgba[4] === undefined ? 1 : Number(rgba[4])
    };
  }
  throw new Error(`unsupported color syntax: ${value}`);
}

function parseResolvedColor(name, tokens) {
  return parseColorValue(resolveValue(name, tokens));
}

function linearChannel(channel) {
  return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
}

function luminance(color) {
  return 0.2126 * linearChannel(color.r) + 0.7152 * linearChannel(color.g) + 0.0722 * linearChannel(color.b);
}

function contrastRatio(foreground, background) {
  if (foreground.a !== 1 || background.a !== 1) throw new Error("contrast colors must be opaque");
  const foregroundLuminance = luminance(foreground);
  const backgroundLuminance = luminance(background);
  return (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) /
    (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
}

function verifyContract(contract) {
  if (contract.schemaVersion !== 1) fail("theme-contract schemaVersion must be 1");
  if (!contract.provenance || !contract.modified) fail("theme-contract must contain provenance and modified metadata");
  if (!Array.isArray(contract.entries)) {
    fail("theme-contract entries must be an array");
    return;
  }
  const names = contract.entries.map((entry) => entry.name);
  const duplicates = names.filter((name, index) => names.indexOf(name) !== index);
  if (duplicates.length > 0) fail(`duplicate contract entries: ${[...new Set(duplicates)].join(", ")}`);
  const actual = new Set(names);
  for (const name of requiredNames) if (!actual.has(name)) fail(`contract missing required entry ${name}`);
  for (const name of actual) if (!requiredNames.includes(name)) fail(`unexpected required contract entry ${name}`);

  const byName = new Map(contract.entries.map((entry) => [entry.name, entry]));
  for (const entry of contract.entries) {
    if (entry.required !== true) fail(`${entry.name} required must be exactly true`);
    if (!["color", "shadow"].includes(entry.type)) fail(`${entry.name} has invalid type ${entry.type}`);
    if (typeof entry.role !== "string" || entry.role.length === 0) fail(`${entry.name} must have a role`);
    if (!Array.isArray(entry.allowedOn)) fail(`${entry.name} allowedOn must be an array`);
    if (!["text", "nonText", "decorative", "none"].includes(entry.contrastClass)) {
      fail(`${entry.name} has invalid contrastClass ${entry.contrastClass}`);
    }
    for (const surface of entry.allowedOn ?? []) {
      if (!byName.has(surface)) fail(`${entry.name} references unknown allowedOn surface ${surface}`);
    }

    const expected = expectedContractShape[entry.name];
    if (!expected) continue;
    if (entry.type !== expected.type) fail(`${entry.name} type must be ${expected.type}`);
    if (entry.contrastClass !== expected.contrastClass) {
      fail(`${entry.name} contrastClass must be ${expected.contrastClass}`);
    }
    const actualAllowed = [...entry.allowedOn].sort();
    const expectedAllowed = [...expected.allowedOn].sort();
    if (actualAllowed.length !== expectedAllowed.length || actualAllowed.some((name, index) => name !== expectedAllowed[index])) {
      fail(`${entry.name} allowedOn must be exactly [${expected.allowedOn.join(", ")}]`);
    }
    if (entry.opaque !== undefined && typeof entry.opaque !== "boolean") {
      fail(`${entry.name} opaque must be a boolean when present`);
    }
    if ((entry.opaque ?? false) !== expected.opaque) {
      fail(`${entry.name} opaque must be ${expected.opaque}`);
    }
  }


  if (contract.contrastThresholds?.text !== 4.5 || contract.contrastThresholds?.nonText !== 3) {
    fail("contract contrast thresholds must remain text=4.5 and nonText=3 without rounding");
  }
  if (contract.contrastThresholds?.decorative !== null || contract.contrastThresholds?.none !== null) {
    fail("decorative and none contrast thresholds must remain null");
  }
  const actualRuleNames = Object.keys(contract.rules ?? {}).sort();
  const expectedRuleNames = Object.keys(expectedContractRules).sort();
  if (
    actualRuleNames.length !== expectedRuleNames.length ||
    actualRuleNames.some((name, index) => name !== expectedRuleNames[index])
  ) {
    fail(`contract rules must be exactly [${expectedRuleNames.join(", ")}]`);
  }
  for (const [rule, expected] of Object.entries(expectedContractRules)) {
    if (contract.rules?.[rule] !== expected) fail(`contract rule ${rule} must be ${expected}`);
  }
}

function stripCssComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, "");
}

function extractThemeSelectorBlock(theme, css) {
  let selectorPattern;
  let selectorError;
  if (theme.id === "cherry-black") {
    selectorPattern = /:root\s*,\s*:root\[data-theme="cherry-black"\]\s*\{([\s\S]*?)\}/g;
    selectorError = "cherry-black must bind its :root fallback and explicit theme selector exactly once";
  } else {
    const escapedId = theme.id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    selectorPattern = new RegExp(`:root\\[data-theme="${escapedId}"\\]\\s*\\{([\\s\\S]*?)\\}`, "g");
    selectorError = `${theme.id} must use its exact html theme selector exactly once`;
  }

  const matches = [...css.matchAll(selectorPattern)];
  if (matches.length !== 1) {
    fail(selectorError);
    return "";
  }
  return matches[0][1];
}

function verifyTheme(theme, contract, css, selectorBlock) {
  const tokens = parseCustomProperties(selectorBlock);
  const duplicateNames = duplicateCustomProperties(css);
  if (duplicateNames.length > 0) fail(`${theme.id} has duplicate declarations: ${duplicateNames.join(", ")}`);
  const allTokens = parseCustomProperties(css);
  const outsideSelector = Object.entries(allTokens).filter(([name, value]) => tokens[name] !== value);
  if (outsideSelector.length > 0 || Object.keys(allTokens).length !== Object.keys(tokens).length) {
    fail(`${theme.id} must declare every custom property inside its exact theme selector`);
  }
  for (const name of requiredNames) {
    if (!(name in tokens)) fail(`${theme.id} does not explicitly declare ${name}`);
  }
  for (const name of requiredNames) {
    const value = tokens[name];
    if (value === undefined) continue;
    for (const alias of value.matchAll(/var\((--[a-zA-Z0-9-]+)\)/g)) {
      if (!(alias[1] in tokens)) fail(`${theme.id} ${name} depends on undeclared same-theme token ${alias[1]}`);
    }
  }

  // Exact theme values are not re-listed here. They are pinned by the committed
  // design-tokens.json snapshot, which build.mjs regenerates from these same files; editing a
  // value makes `build.mjs --check` (and verifyGeneratedOutputs below) report a stale output.
  // A hand-copied table here would be a second, unverifiable transcription of the same values.
  for (const status of statuses) {
    const border = tokens[`--ds-${status}-border`];
    if (border !== `var(--ds-${status}-foreground)`) {
      fail(`${theme.id} ${status} necessary border must alias its foreground`);
    }
  }

  for (const entry of contract.entries) {
    if (!(entry.name in tokens)) continue;
    if (entry.type === "color") {
      try {
        const color = parseResolvedColor(entry.name, tokens);
        if (entry.opaque && color.a !== 1) fail(`${theme.id} ${entry.name} must be opaque`);
      } catch (error) {
        fail(`${theme.id} ${entry.name}: ${error.message}`);
      }
    }
  }

  for (const entry of contract.entries) {
    const threshold = contract.contrastThresholds[entry.contrastClass];
    if (threshold === null || threshold === undefined) continue;
    for (const backgroundName of entry.allowedOn) {
      try {
        const ratio = contrastRatio(parseResolvedColor(entry.name, tokens), parseResolvedColor(backgroundName, tokens));
        contrastChecks += 1;
        if (ratio < minimumContrast.ratio) minimumContrast = { ratio, pair: `${theme.id} ${entry.name} on ${backgroundName}` };
        if (ratio < threshold) {
          fail(`${theme.id} ${entry.name} on ${backgroundName} is ${ratio.toFixed(3)}:1; requires ${threshold}:1`);
        }
      } catch (error) {
        fail(`${theme.id} cannot check ${entry.name} on ${backgroundName}: ${error.message}`);
      }
    }
  }

  return tokens;
}

function verifyAdapter(css) {
  if (/data-theme|cherry-black|pure-white/.test(css)) fail("Tailwind adapter must not know theme ids or data-theme");
  if (!css.includes('@custom-variant dark (&:where([data-color-scheme="dark"], [data-color-scheme="dark"] *));')) {
    fail("Tailwind dark variant must use derived data-color-scheme");
  }
  const rootBlock = css.match(/:root\s*\{([\s\S]*?)\}/)?.[1] ?? "";
  const rootAliases = parseCustomProperties(rootBlock);
  for (const [alias, canonical] of Object.entries(adapterAliases)) {
    const expected = `var(${canonical})`;
    if (rootAliases[alias] !== expected) fail(`adapter ${alias} must map to ${expected}`);
  }

  const themeBlock = css.match(/@theme\s+inline\s*\{([\s\S]*?)\}/)?.[1] ?? "";
  const utilityAliases = parseCustomProperties(themeBlock);
  for (const alias of Object.keys(adapterAliases)) {
    if (["--radius", "--font-sans", "--font-display", "--font-mono"].includes(alias)) continue;
    const utility = `--color-${alias.slice(2)}`;
    if (utilityAliases[utility] !== `var(${alias})`) fail(`@theme must register ${utility} from ${alias}`);
  }
  for (const [utility, canonical] of Object.entries({
    "--radius-sm": "--ds-radius-sm",
    "--radius-md": "--ds-radius-md",
    "--radius-lg": "--ds-radius-lg",
    "--radius-full": "--ds-radius-pill",
    "--font-sans": "--ds-font-body",
    "--font-display": "--ds-font-display",
    "--font-mono": "--ds-font-mono"
  })) {
    if (utilityAliases[utility] !== `var(${canonical})`) fail(`@theme must register ${utility} from ${canonical}`);
  }
}

async function verifyGeneratedOutputs() {
  const expected = await generateOutputs();
  for (const [relativePath, content] of Object.entries(expected)) {
    let actual = "";
    try {
      actual = await readText(relativePath);
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
    }
    if (actual !== content) fail(`${relativePath} is stale; run node tools/build.mjs`);
  }
}

async function verifyPackage(packageManifest, themeManifest) {
  if (!packageManifest.provenance || !packageManifest.modified) fail("manifest.json must contain provenance and modified metadata");
  if (packageManifest.schemaVersion !== 1) fail("manifest.json schemaVersion must be 1");
  if (packageManifest.name !== "cherry-oj-web-design-system") fail("manifest.json name must identify the Web package");
  if (packageManifest.version !== "2.0.0") fail("manifest.json version must be 2.0.0");
  if (!Array.isArray(packageManifest.files)) {
    fail("manifest.json files must be an array");
    return;
  }
  const staticPaths = packageManifest.files.map((file) => file.path);
  if (new Set(staticPaths).size !== staticPaths.length) fail("manifest.json contains duplicate static file paths");
  if (
    staticPaths.length !== expectedPackageFiles.length ||
    staticPaths.some((file, index) => file !== expectedPackageFiles[index])
  ) {
    fail(`manifest.json files must remain exactly [${expectedPackageFiles.join(", ")}]`);
  }
  for (const file of packageManifest.files) {
    if (typeof file.path !== "string" || file.path.length === 0) fail("manifest.json file path must be non-empty");
    if (typeof file.role !== "string" || file.role.length === 0) fail(`${file.path} manifest role must be non-empty`);
    if (file.generated === true && !["tokens.css", "design-tokens.json"].includes(file.path)) {
      fail(`${file.path} must not be marked generated`);
    }
  }
  const expectedEntrypoints = {
    documentation: "README.md",
    tokens: "tokens.css",
    valueAnchor: "design-tokens.json",
    tailwind: "tailwind-v4.css",
    themeContract: "theme-contract.json",
    themeManifest: "themes.manifest.json"
  };
  if (JSON.stringify(packageManifest.entrypoints) !== JSON.stringify(expectedEntrypoints)) {
    fail("manifest.json entrypoints changed");
  }
  const expectedManagedFiles = [
    {
      registry: "themes.manifest.json",
      selector: "themes[].file",
      role: "complete registered theme sources"
    }
  ];
  if (JSON.stringify(packageManifest.managedFiles) !== JSON.stringify(expectedManagedFiles)) {
    fail("manifest.json managedFiles must declare only themes.manifest.json themes[].file");
  }
  const managedPaths = themeManifest.themes.map((theme) => theme.file);
  if (new Set(managedPaths).size !== managedPaths.length) fail("managed theme file paths must be unique");
  for (const relativePath of managedPaths) {
    if (!/^themes\/[a-z0-9-]+\.css$/.test(relativePath)) {
      fail(`managed theme path must stay inside themes/: ${relativePath}`);
    }
    if (staticPaths.includes(relativePath)) fail(`managed theme must not also be statically registered: ${relativePath}`);
  }
  const paths = [...staticPaths, ...managedPaths];
  const actualPaths = (await listFiles(rootDir)).sort();
  const registeredPaths = [...paths].sort();
  for (const relativePath of actualPaths) {
    if (!registeredPaths.includes(relativePath)) fail(`package file is not registered in manifest.json: ${relativePath}`);
  }
  for (const relativePath of registeredPaths) {
    if (!actualPaths.includes(relativePath)) fail(`manifest file is missing: ${relativePath}`);
  }

  for (const relativePath of paths) {
    let content;
    try {
      content = await readText(relativePath);
    } catch (error) {
      fail(`cannot read registered package file ${relativePath}: ${error.message}`);
      continue;
    }
    if (/\/Users\/charon\/Downloads|~\/Downloads/.test(content)) fail(`${relativePath} depends on the local Downloads path`);
    if (relativePath === "LICENSE.open-design" || relativePath === "LICENSE.fonts") continue;
    const extension = path.extname(relativePath);
    if ([".css", ".html", ".md", ".mjs"].includes(extension) && !content.includes("Modified for Cherry OJ")) {
      fail(`${relativePath} lacks its prominent Modified for Cherry OJ notice`);
    }
    if (extension === ".json") {
      try {
        const json = JSON.parse(content);
        if (!json.provenance || !json.modified) fail(`${relativePath} must contain top-level provenance and modified metadata`);
      } catch (error) {
        fail(`${relativePath} is not valid JSON: ${error.message}`);
      }
    }
  }

  const license = await readFile(await resolveInputPath("LICENSE.open-design"));
  const licenseHash = createHash("sha256").update(license).digest("hex");
  if (licenseHash !== "9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da") {
    fail(`LICENSE.open-design is not the fixed verbatim source license: ${licenseHash}`);
  }

  const notice = await readText("NOTICE.md");
  for (const requiredText of [
    "OpenDesign repository",
    "not official Linear source code",
    fixedSourceProvenance.sourceTokensSha256,
    fixedSourceProvenance.sourceDesignSha256,
    fixedSourceProvenance.sourceLicenseSha256,
    "Moved the executable frontend assets into this Web-owned package"
  ]) {
    if (!notice.includes(requiredText)) fail(`NOTICE.md is missing required provenance text: ${requiredText}`);
  }

  const fontLicense = await readText("LICENSE.fonts");
  for (const requiredText of [
    "Copyright 2016 The Inter Project Authors",
    "Copyright 2020 The JetBrains Mono Project Authors",
    "SIL OPEN FONT LICENSE Version 1.1"
  ]) {
    if (!fontLicense.includes(requiredText)) fail(`LICENSE.fonts is missing required text: ${requiredText}`);
  }

  const distributedLicense = await readDistributionFile("LICENSE.open-design");
  const distributedFontLicense = await readDistributionFile("LICENSE.fonts");
  const distributedNotice = await readDistributionFile("NOTICE.md");
  if (distributedLicense !== license.toString("utf8")) {
    fail("public/legal/LICENSE.open-design differs from the package license");
  }
  if (distributedNotice !== notice) {
    fail("public/legal/NOTICE.md differs from the package notice");
  }
  if (distributedFontLicense !== fontLicense) {
    fail("public/legal/LICENSE.fonts differs from the package font license");
  }
}

async function run() {
  await verifyNoSymlinks(rootDir);
  const contract = await readJson("theme-contract.json");
  const themeManifest = await readJson("themes.manifest.json");
  const packageManifest = await readJson("manifest.json");

  verifyContract(contract);
  verifyFixedProvenance("theme-contract.json", contract.provenance);
  if (themeManifest.schemaVersion !== 1) fail("themes.manifest schemaVersion must be 1");
  if (!themeManifest.provenance || !themeManifest.modified) fail("themes.manifest must contain provenance and modified metadata");
  verifyFixedProvenance("themes.manifest.json", themeManifest.provenance);
  if (themeManifest.selectorAttribute !== "data-theme") {
    fail("themes.manifest selectorAttribute must remain data-theme");
  }
  if (themeManifest.defaultTheme !== "cherry-black" || themeManifest.fallbackTheme !== "cherry-black") {
    fail("default and fallback themes must both be cherry-black");
  }
  if (themeManifest.followSystemByDefault !== false) fail("themes must not follow the OS by default");
  const ids = themeManifest.themes.map((theme) => theme.id);
  if (JSON.stringify(ids) !== JSON.stringify(["cherry-black", "pure-white"])) {
    fail("theme ids must remain exactly [cherry-black, pure-white] in that order");
  }
  // id, label, colorScheme, file, version and provenance are carried into design-tokens.json,
  // so a silent edit here shows up as a stale generated output rather than needing a copy.
  if (new Set(ids).size !== ids.length) fail("theme ids must be unique");
  verifyFixedProvenance("manifest.json", packageManifest.provenance);
  if (!ids.includes(themeManifest.defaultTheme) || !ids.includes(themeManifest.fallbackTheme)) {
    fail("default and fallback theme ids must be registered");
  }
  const themeFiles = themeManifest.themes.map((theme) => theme.file);
  if (new Set(themeFiles).size !== themeFiles.length) fail("theme files must be unique");
  const themeLabels = themeManifest.themes.map((theme) => theme.label);
  if (new Set(themeLabels).size !== themeLabels.length) fail("theme labels must be unique");

  for (const theme of themeManifest.themes) {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(theme.id)) fail(`invalid stable theme id: ${theme.id}`);
    if (!["dark", "light"].includes(theme.colorScheme)) fail(`${theme.id} has invalid colorScheme ${theme.colorScheme}`);
    if (!/^themes\/[a-z0-9-]+\.css$/.test(theme.file)) fail(`${theme.id} has unsafe theme file path ${theme.file}`);
    if (theme.file !== `themes/${theme.id}.css`) fail(`${theme.id} file must be themes/${theme.id}.css`);
    if (!Number.isInteger(theme.version) || theme.version < 1) fail(`${theme.id} version must be a positive integer`);
    if (typeof theme.label !== "string" || theme.label.trim().length === 0) fail(`${theme.id} label must be a non-empty string`);
    if (typeof theme.provenance !== "string" || theme.provenance.trim().length === 0) {
      fail(`${theme.id} provenance must be a non-empty string`);
    }
  }

  for (const theme of themeManifest.themes) {
    const css = stripCssComments(await readText(theme.file));
    const selectorBlock = extractThemeSelectorBlock(theme, css);
    const colorSchemeDeclarations = [...selectorBlock.matchAll(/\bcolor-scheme\s*:\s*([^;{}]+)\s*;/g)].map(
      (match) => match[1].trim()
    );
    if (colorSchemeDeclarations.length !== 1 || colorSchemeDeclarations[0] !== theme.colorScheme) {
      fail(`${theme.id} exact selector must declare exactly one color-scheme: ${theme.colorScheme}`);
    }
    verifyTheme(theme, contract, css, selectorBlock);
    if (/#5e6ad2|#828fff|#4752c4|#7170ff|#7a7fad/i.test(css)) {
      fail(`${theme.id} contains a Linear purple value`);
    }
    if (/#de1c4e|#dd2c53|#d7194b|#c01242|#f9667a|#ff8494|#a70f38/i.test(css)) {
      fail(`${theme.id} contains a retired pre-WORK-034 Cherry value`);
    }
  }

  const foundation = await readText("tokens.foundation.css");
  if (!foundation.includes("prefers-reduced-motion: reduce")) fail("Foundation must provide reduced-motion behavior");
  if (!foundation.includes('"cv01", "ss03"')) fail("Foundation must preserve Inter cv01/ss03 features");
  const foundationRoot = foundation.match(/:root\s*\{([\s\S]*?)\}/)?.[1] ?? "";
  const foundationTokens = parseCustomProperties(foundationRoot);
  // Foundation values and the closed token set are both pinned by design-tokens.json:
  // a changed value or an added key makes the committed snapshot stale, which fails above.
  if (Object.keys(foundationTokens).length === 0) fail("Foundation :root block declares no tokens");
  const reducedMotion = foundation.match(/@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{([\s\S]*)\}\s*$/)?.[1] ?? "";
  const reducedTokens = parseCustomProperties(reducedMotion);
  if (
    reducedTokens["--ds-motion-fast"] !== "0ms" ||
    reducedTokens["--ds-motion-base"] !== "0ms" ||
    reducedTokens["--ds-motion-slow"] !== "0ms"
  ) {
    fail("reduced-motion must set every Foundation duration to 0ms");
  }
  verifyAdapter(await readText("tailwind-v4.css"));
  await verifyGeneratedOutputs();
  await verifyPackage(packageManifest, themeManifest);

  const combinationsPerTheme = Object.values(expectedContractShape).reduce((count, entry) => {
    const threshold = contract.contrastThresholds[entry.contrastClass];
    return count + (threshold === null || threshold === undefined ? 0 : entry.allowedOn.length);
  }, 0);
  const expectedContrastChecks = combinationsPerTheme * themeManifest.themes.length;
  if (contrastChecks !== expectedContrastChecks) {
    fail(`expected ${expectedContrastChecks} theme contrast combinations, checked ${contrastChecks}`);
  }
  if (
    minimumContrast.pair !== expectedMinimumContrast.pair ||
    Math.abs(minimumContrast.ratio - expectedMinimumContrast.ratio) > 1e-15
  ) {
    fail(
      `minimum contrast changed: expected ${expectedMinimumContrast.ratio} (${expectedMinimumContrast.pair}), ` +
        `received ${minimumContrast.ratio} (${minimumContrast.pair})`
    );
  }

  if (errors.length > 0) {
    for (const error of errors) console.error(`ERROR: ${error}`);
    console.error(`\n${errors.length} check(s) failed`);
    process.exitCode = 1;
    return;
  }

  console.log(`theme contract: ${requiredNames.length} required keys across ${themeManifest.themes.length} themes`);
  console.log(
    `contrast: ${contrastChecks} allowed combinations; minimum ${minimumContrast.ratio.toPrecision(17)}:1 ` +
      `(${minimumContrast.pair})`
  );
  console.log("adapter, generated outputs, provenance, license, and package manifest: OK");
}

async function runSelfTest() {
  const temporaryDirectory = await mkdtemp(path.join(tmpdir(), "cherry-oj-web-design-system-"));
  const fixtureWebDir = path.join(temporaryDirectory, "apps/web");
  const fixturePackageDir = path.join(fixtureWebDir, "design-system");
  const fixtureLegalDir = path.join(fixtureWebDir, "public/legal");

  const resetFixture = async () => {
    await rm(fixtureWebDir, { recursive: true, force: true });
    await cp(rootDir, fixturePackageDir, { recursive: true });
    await cp(distributionLegalDir, fixtureLegalDir, { recursive: true });
  };

  const runFixtureCheck = () =>
    execFileAsync(execPath, [path.join(fixturePackageDir, "tools/check.mjs")], {
      cwd: fixtureWebDir,
      encoding: "utf8"
    });
  const runFixtureBuild = async () =>
    execFileAsync(execPath, [await realpath(path.join(fixturePackageDir, "tools/build.mjs"))], {
      cwd: fixtureWebDir,
      encoding: "utf8"
    });

  const expectFailure = async (name, mutate, expectedMessage, runCommand = runFixtureCheck) => {
    await resetFixture();
    await mutate();
    try {
      await runCommand();
    } catch (error) {
      const output = `${error?.stdout ?? ""}\n${error?.stderr ?? ""}`;
      if (!output.includes(expectedMessage)) {
        throw new Error(`${name} failed for an unexpected reason; expected ${expectedMessage}\n${output}`);
      }
      return;
    }
    throw new Error(`${name} fixture was accepted`);
  };

  try {
    await resetFixture();
    await runFixtureCheck();

    await expectFailure(
      "wrong but contrast-compliant token",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const themeCss = await readFile(themePath, "utf8");
        const current = themeCss.match(/--ds-canvas:\s*([^;]+);/)?.[1]?.trim();
        if (!current) throw new Error("pure-white fixture does not declare --ds-canvas");
        // Read the current value instead of naming it: the checker must not hold design values.
        await writeFile(themePath, themeCss.replace(`--ds-canvas: ${current};`, "--ds-canvas: #fefefe;"), "utf8");
      },
      "design-tokens.json is stale"
    );

    await expectFailure(
      "retired Cherry palette",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const themeCss = await readFile(themePath, "utf8");
        const current = themeCss.match(/--ds-brand-surface:\s*([^;]+);/)?.[1]?.trim();
        if (!current) throw new Error("pure-white fixture does not declare --ds-brand-surface");
        // #de1c4e is a retired value the checker legitimately blacklists, not a current one.
        await writeFile(themePath, themeCss.replace(`--ds-brand-surface: ${current};`, "--ds-brand-surface: #de1c4e;"), "utf8");
      },
      "contains a retired pre-WORK-034 Cherry value"
    );

    await expectFailure(
      "missing required token",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const themeCss = await readFile(themePath, "utf8");
        const declaration = themeCss.match(/^[ \t]*--ds-fg:[^;]+;\n/m)?.[0];
        if (!declaration) throw new Error("pure-white fixture does not declare --ds-fg");
        await writeFile(themePath, themeCss.replace(declaration, ""), "utf8");
      },
      "pure-white does not explicitly declare --ds-fg"
    );

    await expectFailure(
      "swapped theme color schemes",
      async () => {
        const manifestPath = path.join(fixturePackageDir, "themes.manifest.json");
        const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
        manifest.themes[0].colorScheme = "light";
        manifest.themes[1].colorScheme = "dark";
        await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
      },
      "exact selector must declare exactly one color-scheme"
    );

    await expectFailure(
      "tokens under the wrong selector",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const content = await readFile(themePath, "utf8");
        const moved = content.replace(':root[data-theme="pure-white"] {', ".wrong-theme-selector {");
        await writeFile(
          themePath,
          `/* :root[data-theme="pure-white"] { color-scheme: light; } */\n${moved}`,
          "utf8"
        );
      },
      "pure-white must use its exact html theme selector exactly once"
    );

    await expectFailure(
      "duplicate color-scheme declaration",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const content = await readFile(themePath, "utf8");
        await writeFile(
          themePath,
          content.replace("  color-scheme: light;", "  color-scheme: light;\n  color-scheme: dark;"),
          "utf8"
        );
      },
      "exact selector must declare exactly one color-scheme: light"
    );

    await expectFailure(
      "contract allowedOn replacement",
      async () => {
        const contractPath = path.join(fixturePackageDir, "theme-contract.json");
        const contract = JSON.parse(await readFile(contractPath, "utf8"));
        const foreground = contract.entries.find((entry) => entry.name === "--ds-fg");
        foreground.allowedOn[0] = "--ds-brand-soft";
        await writeFile(contractPath, `${JSON.stringify(contract, null, 2)}\n`, "utf8");
      },
      "allowedOn must be exactly"
    );

    await expectFailure(
      "unexpected contract rule",
      async () => {
        const contractPath = path.join(fixturePackageDir, "theme-contract.json");
        const contract = JSON.parse(await readFile(contractPath, "utf8"));
        contract.rules.inventedRule = true;
        await writeFile(contractPath, `${JSON.stringify(contract, null, 2)}\n`, "utf8");
      },
      "contract rules must be exactly"
    );

    await expectFailure(
      "non-boolean required flag",
      async () => {
        const contractPath = path.join(fixturePackageDir, "theme-contract.json");
        const contract = JSON.parse(await readFile(contractPath, "utf8"));
        contract.entries[0].required = "false";
        await writeFile(contractPath, `${JSON.stringify(contract, null, 2)}\n`, "utf8");
      },
      "required must be exactly true"
    );

    await expectFailure(
      "non-boolean opaque flag",
      async () => {
        const contractPath = path.join(fixturePackageDir, "theme-contract.json");
        const contract = JSON.parse(await readFile(contractPath, "utf8"));
        contract.entries[0].opaque = "false";
        await writeFile(contractPath, `${JSON.stringify(contract, null, 2)}\n`, "utf8");
      },
      "opaque must be a boolean when present"
    );

    await expectFailure(
      "opaque token alpha",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const themeCss = await readFile(themePath, "utf8");
        const current = themeCss.match(/--ds-brand-soft:\s*([^;]+);/)?.[1]?.trim();
        if (!current) throw new Error("pure-white fixture does not declare --ds-brand-soft");
        await writeFile(
          themePath,
          themeCss.replace(`--ds-brand-soft: ${current};`, "--ds-brand-soft: rgba(252, 231, 237, 0.5);"),
          "utf8"
        );
      },
      "--ds-brand-soft must be opaque"
    );

    await expectFailure(
      "low contrast token",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const themeCss = await readFile(themePath, "utf8");
        const current = themeCss.match(/--ds-fg:\s*([^;]+);/)?.[1]?.trim();
        if (!current) throw new Error("pure-white fixture does not declare --ds-fg");
        // pure-white is a light theme, so white-on-white is a guaranteed contrast failure.
        await writeFile(themePath, themeCss.replace(`--ds-fg: ${current};`, "--ds-fg: #ffffff;"), "utf8");
      },
      "requires 4.5:1"
    );

    await expectFailure(
      "adapter mapping",
      async () => {
        const adapterPath = path.join(fixturePackageDir, "tailwind-v4.css");
        const content = await readFile(adapterPath, "utf8");
        await writeFile(
          adapterPath,
          content.replace("--background: var(--ds-canvas);", "--background: var(--ds-panel);"),
          "utf8"
        );
      },
      "adapter --background must map to var(--ds-canvas)"
    );

    await expectFailure(
      "stale generated CSS",
      async () => {
        const generatedPath = path.join(fixturePackageDir, "tokens.css");
        await writeFile(generatedPath, `${await readFile(generatedPath, "utf8")}/* stale fixture */\n`, "utf8");
      },
      "tokens.css is stale"
    );

    await expectFailure(
      "modified source license",
      async () => {
        const licensePath = path.join(fixturePackageDir, "LICENSE.open-design");
        await writeFile(licensePath, `${await readFile(licensePath, "utf8")}modified\n`, "utf8");
      },
      "LICENSE.open-design is not the fixed verbatim source license"
    );

    await expectFailure(
      "documentation preview registered in the code package",
      async () => {
        const previewPath = path.join(fixturePackageDir, "preview.html");
        await writeFile(previewPath, "<!-- Modified for Cherry OJ -->\n", "utf8");
        const manifestPath = path.join(fixturePackageDir, "manifest.json");
        const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
        manifest.files.push({ path: "preview.html", role: "documentation preview" });
        await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
      },
      "manifest.json files must remain exactly"
    );

    await expectFailure(
      "symlink escape",
      async () => {
        const themePath = path.join(fixturePackageDir, "themes/pure-white.css");
        const externalThemePath = path.join(fixtureWebDir, "outside-theme.css");
        await cp(themePath, externalThemePath);
        await unlink(themePath);
        await symlink(path.relative(path.dirname(themePath), externalThemePath), themePath);
      },
      "contains a symbolic link"
    );

    await expectFailure(
      "generated output symlink escape",
      async () => {
        const generatedPath = path.join(fixturePackageDir, "tokens.css");
        const externalGeneratedPath = path.join(fixtureWebDir, "outside-tokens.css");
        await cp(generatedPath, externalGeneratedPath);
        await unlink(generatedPath);
        await symlink(path.relative(path.dirname(generatedPath), externalGeneratedPath), generatedPath);
      },
      "design-system output must not be a symbolic link: tokens.css",
      runFixtureBuild
    );
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true });
  }

  console.log("design-system self-test: 18 negative fixtures rejected and clean fixture restored");
}

const argumentsList = process.argv.slice(2);
if (argumentsList.length > 1 || (argumentsList.length === 1 && argumentsList[0] !== "--self-test")) {
  throw new Error("check.mjs accepts only the optional --self-test argument");
}

const selectedRun = argumentsList[0] === "--self-test" ? runSelfTest : run;
selectedRun().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
