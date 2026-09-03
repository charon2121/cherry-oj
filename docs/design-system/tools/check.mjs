#!/usr/bin/env node
/*
 * Modified for Cherry OJ on 2026-08-27.
 * Verifies manifest-driven generation, exact theme completeness, same-theme
 * alias resolution, WCAG contrast combinations, adapter mappings, provenance,
 * and package integrity. See ../NOTICE.md and ../LICENSE.open-design.
 */

import { createHash } from "node:crypto";
import { lstat, readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { generateOutputs, parseCustomProperties, rootDir } from "./build.mjs";

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
const expectedFoundationTokens = {
  "--ds-font-display": '"Inter Variable", "Inter", "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "SF Pro Display", -apple-system, BlinkMacSystemFont, system-ui, "Segoe UI", Roboto, sans-serif',
  "--ds-font-body": '"Inter Variable", "Inter", "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "SF Pro Display", -apple-system, BlinkMacSystemFont, system-ui, "Segoe UI", Roboto, sans-serif',
  "--ds-font-mono": '"Berkeley Mono", "JetBrains Mono Variable", "JetBrains Mono", ui-monospace, "SFMono-Regular", "SF Mono", Menlo, Monaco, Consolas, "Liberation Mono", monospace',
  "--ds-font-features": '"cv01", "ss03"',
  "--ds-weight-light": "300",
  "--ds-weight-regular": "400",
  "--ds-weight-body": "510",
  "--ds-weight-heading": "590",
  "--ds-text-tiny": "10px",
  "--ds-text-micro": "11px",
  "--ds-text-xs": "12px",
  "--ds-text-cap": "13px",
  "--ds-text-sm": "14px",
  "--ds-text-15": "15px",
  "--ds-text-base": "16px",
  "--ds-text-17": "17px",
  "--ds-text-lg": "18px",
  "--ds-text-h3": "20px",
  "--ds-text-xl": "24px",
  "--ds-text-2xl": "32px",
  "--ds-text-3xl": "48px",
  "--ds-text-display-lg": "64px",
  "--ds-text-4xl": "72px",
  "--ds-leading-body": "1.5",
  "--ds-leading-tight": "1",
  "--ds-leading-heading": "1.13",
  "--ds-leading-h2": "1.33",
  "--ds-leading-label": "1.4",
  "--ds-leading-relaxed": "1.6",
  "--ds-tracking-display": "-0.022em",
  "--ds-tracking-heading": "-0.012em",
  "--ds-tracking-body": "-0.009em",
  "--ds-tracking-caption": "-0.01em",
  "--ds-tracking-eyebrow": "0.08em",
  "--ds-space-px": "1px",
  "--ds-space-1": "4px",
  "--ds-space-1x": "7px",
  "--ds-space-2": "8px",
  "--ds-space-2x": "11px",
  "--ds-space-3": "12px",
  "--ds-space-4": "16px",
  "--ds-space-4x": "19px",
  "--ds-space-5": "20px",
  "--ds-space-5x": "22px",
  "--ds-space-6": "24px",
  "--ds-space-7": "28px",
  "--ds-space-8": "32px",
  "--ds-space-9": "35px",
  "--ds-space-12": "48px",
  "--ds-section-y-desktop": "80px",
  "--ds-section-y-tablet": "48px",
  "--ds-section-y-phone": "32px",
  "--ds-radius-micro": "2px",
  "--ds-radius-xs": "4px",
  "--ds-radius-sm": "6px",
  "--ds-radius-md": "8px",
  "--ds-radius-lg": "12px",
  "--ds-radius-xl": "22px",
  "--ds-radius-pill": "9999px",
  "--ds-radius-circle": "50%",
  "--ds-radius-image-top": "12px 12px 0 0",
  "--ds-focus-width": "2px",
  "--ds-focus-offset": "2px",
  "--ds-motion-fast": "150ms",
  "--ds-motion-base": "200ms",
  "--ds-motion-slow": "320ms",
  "--ds-ease-standard": "cubic-bezier(0.2, 0, 0, 1)",
  "--ds-container-max": "1200px",
  "--ds-container-gutter-desktop": "24px",
  "--ds-container-gutter-tablet": "16px",
  "--ds-container-gutter-phone": "12px",
  "--ds-breakpoint-mobile-sm": "600px",
  "--ds-breakpoint-mobile": "640px",
  "--ds-breakpoint-tablet": "768px",
  "--ds-breakpoint-desktop-sm": "1024px",
  "--ds-breakpoint-desktop": "1280px",
  "--ds-sidebar-width": "220px",
  "--ds-header-height": "56px"
};

const exactThemeValues = {
  "cherry-black": {
    "--ds-raw-linear-canvas": "#08090a",
    "--ds-raw-linear-surface": "#191a1b",
    "--ds-raw-linear-fg": "#f7f8f8",
    "--ds-raw-linear-fg-2": "#d0d6e0",
    "--ds-raw-linear-muted": "#8a8f98",
    "--ds-raw-linear-meta": "#62666d",
    "--ds-raw-linear-success": "#27a644",
    "--ds-raw-linear-warning": "#eab308",
    "--ds-raw-linear-danger": "#dc2626",
    "--ds-canvas": "#08090a",
    "--ds-panel": "#0f1011",
    "--ds-surface": "#191a1b",
    "--ds-surface-subtle": "#141516",
    "--ds-surface-raised": "#191a1b",
    "--ds-surface-hover": "#28282c",
    "--ds-surface-translucent": "rgba(255, 255, 255, 0.02)",
    "--ds-surface-translucent-hover": "rgba(255, 255, 255, 0.04)",
    "--ds-surface-translucent-selected": "rgba(255, 255, 255, 0.05)",
    "--ds-fg": "#f7f8f8",
    "--ds-fg-2": "#d0d6e0",
    "--ds-fg-muted": "#8a8f98",
    "--ds-fg-meta": "#8a8f98",
    "--ds-fg-disabled": "#8a8f98",
    "--ds-fg-ghost": "#e2e4e7",
    "--ds-border-soft": "rgba(255, 255, 255, 0.05)",
    "--ds-border": "rgba(255, 255, 255, 0.08)",
    "--ds-border-solid": "#23252a",
    "--ds-border-strong": "#80848d",
    "--ds-line-tertiary": "#18191a",
    "--ds-brand-surface": "#d2042d",
    "--ds-brand-surface-hover": "#a80324",
    "--ds-brand-surface-active": "#7d0219",
    "--ds-on-brand": "#ffffff",
    "--ds-brand-foreground": "#ff4d67",
    "--ds-brand-foreground-hover": "#ff7088",
    "--ds-brand-soft": "#32141d",
    "--ds-on-brand-soft": "#ff4d67",
    "--ds-link": "var(--ds-brand-foreground)",
    "--ds-link-hover": "var(--ds-brand-foreground-hover)",
    "--ds-focus": "var(--ds-brand-foreground)",
    "--ds-selection-surface": "var(--ds-brand-soft)",
    "--ds-selection-foreground": "var(--ds-fg)",
    "--ds-success-foreground": "#27a644",
    "--ds-success-surface": "#14271a",
    "--ds-success-border": "var(--ds-success-foreground)",
    "--ds-success-solid": "#187a34",
    "--ds-success-on-solid": "#ffffff",
    "--ds-warning-foreground": "#eab308",
    "--ds-warning-surface": "#2c2410",
    "--ds-warning-border": "var(--ds-warning-foreground)",
    "--ds-warning-solid": "#eab308",
    "--ds-warning-on-solid": "#08090a",
    "--ds-danger-foreground": "#f97066",
    "--ds-danger-surface": "#321619",
    "--ds-danger-border": "var(--ds-danger-foreground)",
    "--ds-danger-solid": "#dc2626",
    "--ds-danger-on-solid": "#ffffff",
    "--ds-info-foreground": "#60a5fa",
    "--ds-info-surface": "#142236",
    "--ds-info-border": "var(--ds-info-foreground)",
    "--ds-info-solid": "#245ea8",
    "--ds-info-on-solid": "#ffffff",
    "--ds-special-foreground": "#c084fc",
    "--ds-special-surface": "#281a35",
    "--ds-special-border": "var(--ds-special-foreground)",
    "--ds-special-solid": "#6941c6",
    "--ds-special-on-solid": "#ffffff",
    "--ds-overlay": "rgba(0, 0, 0, 0.85)",
    "--ds-elevation-flat": "none",
    "--ds-elevation-subtle": "rgba(0, 0, 0, 0.03) 0 1.2px 0 0",
    "--ds-elevation-ring": "0 0 0 1px var(--ds-border)",
    "--ds-elevation-inset": "rgba(0, 0, 0, 0.2) 0 0 12px 0 inset",
    "--ds-elevation-dialog": "rgba(0, 0, 0, 0) 0 8px 2px, rgba(0, 0, 0, 0.01) 0 5px 2px, rgba(0, 0, 0, 0.04) 0 3px 2px, rgba(0, 0, 0, 0.07) 0 1px 1px, rgba(0, 0, 0, 0.08) 0 0 1px",
    "--ds-elevation-raised": "rgba(0, 0, 0, 0.4) 0 2px 4px, 0 0 0 1px rgba(255, 255, 255, 0.05)"
  },
  "pure-white": {
    "--ds-canvas": "#ffffff",
    "--ds-panel": "#f7f8f8",
    "--ds-surface": "#ffffff",
    "--ds-surface-subtle": "#f5f6f7",
    "--ds-surface-raised": "#ffffff",
    "--ds-surface-hover": "#f3f4f5",
    "--ds-surface-translucent": "rgba(8, 9, 10, 0.02)",
    "--ds-surface-translucent-hover": "rgba(8, 9, 10, 0.04)",
    "--ds-surface-translucent-selected": "rgba(8, 9, 10, 0.05)",
    "--ds-fg": "#191a1b",
    "--ds-fg-2": "#34343a",
    "--ds-fg-muted": "#62666d",
    "--ds-fg-meta": "#676b73",
    "--ds-fg-disabled": "#676b73",
    "--ds-fg-ghost": "#34343a",
    "--ds-border-soft": "#e6e6e6",
    "--ds-border": "#d0d6e0",
    "--ds-border-solid": "#d0d6e0",
    "--ds-border-strong": "#80848d",
    "--ds-line-tertiary": "#e6e6e6",
    "--ds-brand-surface": "#d2042d",
    "--ds-brand-surface-hover": "#a80324",
    "--ds-brand-surface-active": "#7d0219",
    "--ds-on-brand": "#ffffff",
    "--ds-brand-foreground": "#a80324",
    "--ds-brand-foreground-hover": "#7d0219",
    "--ds-brand-soft": "#fce7ed",
    "--ds-on-brand-soft": "#a80324",
    "--ds-link": "var(--ds-brand-foreground)",
    "--ds-link-hover": "var(--ds-brand-foreground-hover)",
    "--ds-focus": "var(--ds-brand-foreground)",
    "--ds-selection-surface": "var(--ds-brand-soft)",
    "--ds-selection-foreground": "var(--ds-fg)",
    "--ds-success-foreground": "#087c2f",
    "--ds-success-surface": "#ecfdf3",
    "--ds-success-border": "var(--ds-success-foreground)",
    "--ds-success-solid": "#087c2f",
    "--ds-success-on-solid": "#ffffff",
    "--ds-warning-foreground": "#8a5a00",
    "--ds-warning-surface": "#fff8db",
    "--ds-warning-border": "var(--ds-warning-foreground)",
    "--ds-warning-solid": "#eab308",
    "--ds-warning-on-solid": "#08090a",
    "--ds-danger-foreground": "#b42318",
    "--ds-danger-surface": "#fef3f2",
    "--ds-danger-border": "var(--ds-danger-foreground)",
    "--ds-danger-solid": "#b42318",
    "--ds-danger-on-solid": "#ffffff",
    "--ds-info-foreground": "#175cd3",
    "--ds-info-surface": "#eff8ff",
    "--ds-info-border": "var(--ds-info-foreground)",
    "--ds-info-solid": "#175cd3",
    "--ds-info-on-solid": "#ffffff",
    "--ds-special-foreground": "#6941c6",
    "--ds-special-surface": "#f4f3ff",
    "--ds-special-border": "var(--ds-special-foreground)",
    "--ds-special-solid": "#6941c6",
    "--ds-special-on-solid": "#ffffff",
    "--ds-overlay": "rgba(8, 9, 10, 0.56)",
    "--ds-elevation-flat": "none",
    "--ds-elevation-subtle": "rgba(8, 9, 10, 0.04) 0 1.2px 0 0",
    "--ds-elevation-ring": "0 0 0 1px var(--ds-border)",
    "--ds-elevation-inset": "rgba(8, 9, 10, 0.06) 0 0 12px 0 inset",
    "--ds-elevation-dialog": "rgba(8, 9, 10, 0.04) 0 8px 2px, rgba(8, 9, 10, 0.05) 0 5px 2px, rgba(8, 9, 10, 0.07) 0 3px 2px, rgba(8, 9, 10, 0.1) 0 1px 1px, rgba(8, 9, 10, 0.12) 0 0 1px",
    "--ds-elevation-raised": "0 1px 2px rgba(8, 9, 10, 0.08), 0 8px 24px rgba(8, 9, 10, 0.08), 0 0 0 1px rgba(8, 9, 10, 0.08)"
  }
};

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
}

async function readText(relativePath) {
  return readFile(path.join(rootDir, relativePath), "utf8");
}

async function readJson(relativePath) {
  return JSON.parse(await readText(relativePath));
}

async function listFiles(directory) {
  const files = [];
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(absolutePath)));
    } else {
      files.push(path.relative(rootDir, absolutePath).split(path.sep).join("/"));
    }
  }
  return files;
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
    if (!entry.required) fail(`${entry.name} must be required`);
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
    if (Boolean(entry.opaque) !== expected.opaque) fail(`${entry.name} opaque must be ${expected.opaque}`);
  }


  if (contract.contrastThresholds?.text !== 4.5 || contract.contrastThresholds?.nonText !== 3) {
    fail("contract contrast thresholds must remain text=4.5 and nonText=3 without rounding");
  }
  if (contract.contrastThresholds?.decorative !== null || contract.contrastThresholds?.none !== null) {
    fail("decorative and none contrast thresholds must remain null");
  }
  for (const [rule, expected] of Object.entries({
    themeMustDeclareEveryRequiredEntry: true,
    aliasesMustResolveWithinTheSameTheme: true,
    opaqueEntriesRejectAlpha: true,
    componentsMayConsumeRawTokens: false,
    necessaryStatusMustAlsoUseTextIconOrShape: true
  })) {
    if (contract.rules?.[rule] !== expected) fail(`contract rule ${rule} must be ${expected}`);
  }
}

function verifyTheme(theme, contract, css) {
  const tokens = parseCustomProperties(css);
  const duplicateNames = duplicateCustomProperties(css);
  if (duplicateNames.length > 0) fail(`${theme.id} has duplicate declarations: ${duplicateNames.join(", ")}`);
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

  const expected = exactThemeValues[theme.id];
  for (const [name, value] of Object.entries(expected ?? {})) {
    if (tokens[name] !== value) fail(`${theme.id} ${name} changed: expected ${value}, received ${tokens[name]}`);
  }
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

async function verifyReferenceArtifacts() {
  const allowedTokens = new Set([...requiredNames, ...Object.keys(expectedFoundationTokens)]);
  const componentManifest = await readJson("components.manifest.json");
  if (componentManifest.schemaVersion !== 1) fail("components.manifest schemaVersion must be 1");
  if (componentManifest.contractVersion !== "2.0.0") fail("components.manifest contractVersion must be 2.0.0");
  if (componentManifest.themeCoverage !== "all-registered") {
    fail("components.manifest themeCoverage must remain all-registered");
  }
  if (componentManifest.provenance?.sourceFixture !== "OpenDesign curated bundled fixture: design-systems/linear-app") {
    fail("components.manifest must identify the OpenDesign curated bundled fixture");
  }
  if (componentManifest.provenance?.officialLinearArtifact !== false) {
    fail("components.manifest must state officialLinearArtifact=false");
  }
  if (componentManifest.provenance?.license !== "Apache-2.0") {
    fail("components.manifest provenance license must be Apache-2.0");
  }
  const sourceFiles = componentManifest.provenance?.sourceFiles;
  if (JSON.stringify(sourceFiles) !== JSON.stringify(["source/claude-design-v1/components"])) {
    fail("components.manifest provenance sourceFiles changed");
  }
  if (
    componentManifest.provenance?.designSource !== fixedSourceProvenance.designSource ||
    componentManifest.provenance?.designSourceRootSha256 !==
      fixedSourceProvenance.designSourceRootSha256
  ) {
    fail("components.manifest must identify the frozen Claude Design source");
  }
  if (!componentManifest.modified || !componentManifest.globalRules) {
    fail("components.manifest must contain modified metadata and globalRules");
  }
  if (componentManifest.globalRules.themeBranching !== "forbidden" || componentManifest.globalRules.rawColors !== "forbidden") {
    fail("components.manifest must forbid theme branching and raw colors");
  }
  if (!Array.isArray(componentManifest.components) || componentManifest.components.length === 0) {
    fail("components.manifest components must be a non-empty array");
  } else {
    const componentIds = componentManifest.components.map((component) => component.id);
    if (new Set(componentIds).size !== componentIds.length) fail("component ids must be unique");
    for (const component of componentManifest.components) {
      if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(component.id ?? "")) fail(`invalid component id: ${component.id}`);
      if (typeof component.category !== "string" || component.category.length === 0) fail(`${component.id} category must be non-empty`);
      for (const field of ["anatomy", "sizes", "variants", "states", "tokenReferences", "keyboard", "constraints"]) {
        if (!Array.isArray(component[field])) fail(`${component.id} ${field} must be an array`);
      }
      for (const field of ["anatomy", "sizes", "variants", "states", "tokenReferences", "constraints"]) {
        if (Array.isArray(component[field]) && component[field].length === 0) fail(`${component.id} ${field} must not be empty`);
      }
      const references = Array.isArray(component.tokenReferences) ? component.tokenReferences : [];
      if (new Set(references).size !== references.length) fail(`${component.id} has duplicate tokenReferences`);
      for (const token of references) {
        if (!allowedTokens.has(token)) fail(`${component.id} references unknown or raw token ${token}`);
      }
    }
  }

  const manifestText = JSON.stringify(componentManifest);
  if (/data-theme|cherry-black|pure-white|--ds-raw-/.test(manifestText)) {
    fail("component manifest must consume semantic tokens without theme-id or raw-token branches");
  }

  // 组件参考页已删除：静态 HTML 无法引用 React 组件，只能手抄 class，必然与真实组件漂移。
  // 组件的视觉参考由 Storybook 承担，这里只校验主题/颜色/字体/间距的静态检查页。
  const referenceFiles = [
    "preview/themes.html",
    "preview/colors.html",
    "preview/typography.html",
    "preview/spacing.html"
  ];
  for (const relativePath of referenceFiles) {
    const html = await readText(relativePath);
    if (/data-theme|cherry-black|pure-white|--ds-raw-/.test(html)) {
      fail(`${relativePath} must not contain a theme-id or raw-token branch`);
    }
    const expectedTokenHref = 'href="../tokens.css"';
    if (!html.includes(expectedTokenHref)) fail(`${relativePath} must load the stable tokens.css entrypoint`);

    const cssSources = [
      ...[...html.matchAll(/<style(?:\s[^>]*)?>([\s\S]*?)<\/style>/gi)].map((match) => match[1]),
      ...[...html.matchAll(/\sstyle=(?:"([^"]*)"|'([^']*)')/gi)].map((match) => match[1] ?? match[2] ?? "")
    ];
    for (const css of cssSources) {
      if (/(?:#[0-9a-f]{3,4}\b|#[0-9a-f]{6}(?:[0-9a-f]{2})?\b|rgba?\s*\(|hsla?\s*\(|oklch\s*\(|color-mix\s*\()/i.test(css)) {
        fail(`${relativePath} contains a raw CSS color in a reference style`);
      }
    }
    if (/(?:fill|stroke)\s*=\s*["']\s*(?:#|rgba?\s*\(|hsla?\s*\(|oklch\s*\()/i.test(html)) {
      fail(`${relativePath} contains a raw SVG color attribute`);
    }
    for (const match of html.matchAll(/var\((--ds-[a-zA-Z0-9-]+)\)/g)) {
      if (!allowedTokens.has(match[1])) fail(`${relativePath} references unknown design token ${match[1]}`);
    }
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

async function verifySourceLock(sourceLock) {
  if (sourceLock.schemaVersion !== 1 || !sourceLock.provenance || !sourceLock.modified) {
    fail("source-lock.json must contain schemaVersion, provenance, and modified metadata");
  }
  if (sourceLock.sourceDirectory !== "source/claude-design-v1") {
    fail("source-lock.json sourceDirectory must remain source/claude-design-v1");
  }
  if (!Array.isArray(sourceLock.files) || sourceLock.files.length !== 99 || sourceLock.fileCount !== 99) {
    fail("source-lock.json must describe the complete 99-file Claude Design export");
    return [];
  }

  const registered = [];
  const rootMaterial = [];
  let totalBytes = 0;
  const seen = new Set();
  for (const file of sourceLock.files) {
    if (
      typeof file.path !== "string" ||
      file.path.length === 0 ||
      path.isAbsolute(file.path) ||
      file.path.split("/").includes("..") ||
      seen.has(file.path)
    ) {
      fail(`source-lock.json contains an unsafe or duplicate path: ${file.path}`);
      continue;
    }
    seen.add(file.path);
    const relativePath = `${sourceLock.sourceDirectory}/${file.path}`;
    const absolutePath = path.join(rootDir, relativePath);
    try {
      const metadata = await lstat(absolutePath);
      if (metadata.isSymbolicLink() || !metadata.isFile()) {
        fail(`source snapshot entry must be a regular file: ${relativePath}`);
        continue;
      }
      const content = await readFile(absolutePath);
      const sha256 = createHash("sha256").update(content).digest("hex");
      if (content.byteLength !== file.bytes || sha256 !== file.sha256) {
        fail(`source snapshot entry does not match its lock: ${relativePath}`);
      }
      totalBytes += content.byteLength;
      rootMaterial.push(`${sha256}  ${file.path}\n`);
      registered.push(relativePath);
    } catch (error) {
      fail(`cannot verify source snapshot entry ${relativePath}: ${error.message}`);
    }
  }
  const rootSha256 = createHash("sha256").update(rootMaterial.join("")).digest("hex");
  if (totalBytes !== sourceLock.totalBytes || rootSha256 !== sourceLock.rootSha256) {
    fail("source-lock.json aggregate size or root hash does not match the snapshot");
  }
  return registered;
}

async function verifyPackage(packageManifest, themeManifest, sourcePaths) {
  if (!packageManifest.provenance || !packageManifest.modified) fail("manifest.json must contain provenance and modified metadata");
  const staticPaths = packageManifest.files.map((file) => file.path);
  if (new Set(staticPaths).size !== staticPaths.length) fail("manifest.json contains duplicate static file paths");
  const expectedManagedFiles = [
    {
      registry: "themes.manifest.json",
      selector: "themes[].file",
      role: "complete registered theme sources"
    },
    {
      registry: "source-lock.json",
      selector: "files[].path",
      base: "source/claude-design-v1",
      role: "immutable Claude Design source snapshot"
    }
  ];
  if (JSON.stringify(packageManifest.managedFiles) !== JSON.stringify(expectedManagedFiles)) {
    fail("manifest.json managedFiles must declare theme sources and the locked Claude Design snapshot");
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
  const registeredPaths = [...paths, ...sourcePaths].sort();
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
    if (relativePath === "LICENSE.open-design" || relativePath === "LICENSE.lucide") continue;
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

  const license = await readFile(path.join(rootDir, "LICENSE.open-design"));
  const licenseHash = createHash("sha256").update(license).digest("hex");
  if (licenseHash !== "9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da") {
    fail(`LICENSE.open-design is not the fixed verbatim source license: ${licenseHash}`);
  }
  const lucideLicense = await readFile(path.join(rootDir, "LICENSE.lucide"));
  const lucideLicenseHash = createHash("sha256").update(lucideLicense).digest("hex");
  if (lucideLicenseHash !== "b495047bd93a9b06913511076f504daba17d5bbeb3e0650f3bb53a4220329c57") {
    fail(`LICENSE.lucide is not the fixed verbatim Lucide license: ${lucideLicenseHash}`);
  }
}

async function run() {
  const contract = await readJson("theme-contract.json");
  const themeManifest = await readJson("themes.manifest.json");
  const packageManifest = await readJson("manifest.json");
  const sourceLock = await readJson("source-lock.json");

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
  if (ids[0] !== "cherry-black" || !ids.includes("pure-white")) {
    fail("cherry-black must be first/default and pure-white must remain registered");
  }
  if (new Set(ids).size !== ids.length) fail("theme ids must be unique");
  verifyFixedProvenance("manifest.json", packageManifest.provenance);
  if (packageManifest.version !== "2.0.0") fail("manifest.json version must be 2.0.0");
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
    const css = await readText(theme.file);
    if (theme.id === "cherry-black") {
      if (!/:root\s*,\s*:root\[data-theme="cherry-black"\]/.test(css)) {
        fail("cherry-black must bind both :root fallback and its explicit theme selector");
      }
      if (!css.includes("color-scheme: dark")) fail("cherry-black must declare color-scheme: dark");
    } else if (theme.id === "pure-white") {
      if (!/:root\[data-theme="pure-white"\]\s*\{/.test(css)) fail("pure-white must use its exact html theme selector");
      if (!css.includes("color-scheme: light")) fail("pure-white must declare color-scheme: light");
    } else {
      const escapedId = theme.id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      if (!new RegExp(`:root\\[data-theme="${escapedId}"\\]\\s*\\{`).test(css)) {
        fail(`${theme.id} must use its exact html theme selector`);
      }
      if (!css.includes(`color-scheme: ${theme.colorScheme}`)) {
        fail(`${theme.id} must declare manifest color-scheme: ${theme.colorScheme}`);
      }
    }
    verifyTheme(theme, contract, css);
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
  for (const [name, value] of Object.entries(expectedFoundationTokens)) {
    if (foundationTokens[name] !== value) fail(`Foundation ${name} changed: expected ${value}, received ${foundationTokens[name]}`);
  }
  for (const name of Object.keys(foundationTokens)) {
    if (!(name in expectedFoundationTokens)) fail(`Foundation contains unapproved token ${name}`);
  }
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
  const sourcePaths = await verifySourceLock(sourceLock);
  await verifyPackage(packageManifest, themeManifest, sourcePaths);
  await verifyReferenceArtifacts();

  const combinationsPerTheme = Object.values(expectedContractShape).reduce((count, entry) => {
    const threshold = contract.contrastThresholds[entry.contrastClass];
    return count + (threshold === null || threshold === undefined ? 0 : entry.allowedOn.length);
  }, 0);
  const expectedContrastChecks = combinationsPerTheme * themeManifest.themes.length;
  if (contrastChecks !== expectedContrastChecks) {
    fail(`expected ${expectedContrastChecks} theme contrast combinations, checked ${contrastChecks}`);
  }

  if (errors.length > 0) {
    for (const error of errors) console.error(`ERROR: ${error}`);
    console.error(`\n${errors.length} check(s) failed`);
    process.exitCode = 1;
    return;
  }

  console.log(`theme contract: ${requiredNames.length} required keys across ${themeManifest.themes.length} themes`);
  console.log(`contrast: ${contrastChecks} allowed combinations; minimum ${minimumContrast.ratio.toFixed(3)}:1 (${minimumContrast.pair})`);
  console.log("adapter, generated outputs, provenance, license, and package manifest: OK");
}

run().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
