import type { editor } from 'monaco-editor/editor/editor.api';

import type { ThemeSnapshot } from '@/lib/theme/theme-runtime';

type ColorScheme = ThemeSnapshot['colorScheme'];

/** Monaco accepts resolved colors, so CSS remains the only authored color source. */
function readColor(host: HTMLElement, token: string, forcedColor?: string): string {
  const probe = document.createElement('span');
  probe.hidden = true;
  probe.style.forcedColorAdjust = 'none';
  probe.style.color = forcedColor ?? `var(${token})`;
  host.append(probe);
  const value = getComputedStyle(probe).color;
  probe.remove();
  const parts = value
    .match(/^rgba?\(([^)]+)\)$/)?.[1]
    ?.split(/[,\s/]+/)
    .map(Number);
  if (!parts || parts.length < 3 || parts.some((part) => !Number.isFinite(part))) {
    throw new Error(`Cannot resolve editor semantic color: ${token}`);
  }
  return `#${parts
    .map((part, index) =>
      Math.round(index === 3 ? part * 255 : part)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;
}

function readNumber(style: CSSStyleDeclaration, token: string): number {
  const value = Number.parseFloat(style.getPropertyValue(token));
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`Cannot resolve editor semantic dimension: ${token}`);
  }
  return value;
}

export function readCodeEditorTheme(host: HTMLElement, colorScheme: ColorScheme) {
  const forced = window.matchMedia('(forced-colors: active)').matches;
  const color = (token: string, system = 'CanvasText') =>
    readColor(host, token, forced ? system : undefined);
  const foreground = color('--ds-fg');
  const background = color('--ds-canvas', 'Canvas');
  const muted = color('--ds-fg-muted', 'GrayText');
  const panel = color('--ds-panel', 'Canvas');
  const surface = color('--ds-surface-raised', 'Canvas');
  const border = color('--ds-border-strong');
  const focus = color('--ds-focus', 'Highlight');
  const selection = color('--ds-selection-surface', 'Highlight');
  const selectedText = color('--ds-selection-foreground', 'HighlightText');
  const secondary = color('--ds-fg-2');

  const theme: editor.IStandaloneThemeData = {
    base: colorScheme === 'dark' ? 'vs-dark' : 'vs',
    inherit: true,
    rules: [
      { token: '', foreground },
      { token: 'keyword', foreground: color('--ds-brand-foreground') },
      { token: 'type', foreground: color('--ds-brand-foreground') },
      { token: 'string', foreground: color('--ds-success-foreground') },
      { token: 'number', foreground: color('--ds-special-foreground') },
      { token: 'comment', foreground: muted, fontStyle: 'italic' },
      { token: 'metatag', foreground: muted },
      { token: 'delimiter', foreground: secondary },
      { token: 'invalid', foreground: color('--ds-danger-foreground') },
    ],
    colors: {
      focusBorder: focus,
      foreground,
      'editor.foreground': foreground,
      'editor.background': background,
      'editorLineNumber.foreground': muted,
      'editorLineNumber.activeForeground': foreground,
      'editorCursor.foreground': focus,
      'editor.selectionBackground': selection,
      'editor.selectionForeground': selectedText,
      'editor.inactiveSelectionBackground': selection,
      'editor.lineHighlightBackground': panel,
      'editor.lineHighlightBorder': panel,
      'editor.findMatchBackground': selection,
      'editor.findMatchBorder': focus,
      'editor.findMatchHighlightBackground': panel,
      'editor.findMatchHighlightBorder': border,
      'editorBracketMatch.background': panel,
      'editorBracketMatch.border': border,
      'editorGutter.background': background,
      'editorWhitespace.foreground': muted,
      'editorIndentGuide.background1': panel,
      'editorIndentGuide.activeBackground1': border,
      'editorWidget.background': surface,
      'editorWidget.foreground': foreground,
      'editorWidget.border': border,
      'editorWidget.resizeBorder': border,
      'editorHoverWidget.background': surface,
      'editorHoverWidget.foreground': foreground,
      'editorHoverWidget.border': border,
      'editorSuggestWidget.background': surface,
      'editorSuggestWidget.foreground': foreground,
      'editorSuggestWidget.border': border,
      'editorSuggestWidget.selectedBackground': selection,
      'editorSuggestWidget.selectedForeground': selectedText,
      'editorSuggestWidget.highlightForeground': color('--ds-brand-foreground'),
      'input.background': background,
      'input.foreground': foreground,
      'input.border': border,
      'input.placeholderForeground': muted,
      'inputOption.activeBackground': selection,
      'inputOption.activeForeground': selectedText,
      'inputOption.activeBorder': focus,
      'inputValidation.errorBackground': color('--ds-danger-surface', 'Canvas'),
      'inputValidation.errorForeground': color('--ds-danger-foreground'),
      'inputValidation.errorBorder': color('--ds-danger-border'),
      'button.background': color('--ds-brand-surface', 'ButtonFace'),
      'button.foreground': color('--ds-on-brand', 'ButtonText'),
      'button.hoverBackground': color('--ds-brand-surface-hover', 'Highlight'),
      'toolbar.hoverBackground': panel,
      'toolbar.activeBackground': selection,
      'scrollbarSlider.background': color('--ds-border-solid'),
      'scrollbarSlider.hoverBackground': muted,
      'scrollbarSlider.activeBackground': secondary,
      'scrollbar.shadow': background,
      'widget.shadow': background,
    },
  };

  const style = getComputedStyle(host);
  const fontSize = readNumber(style, '--ds-text-sm');
  return {
    theme,
    options: {
      fontFamily: style.getPropertyValue('--ds-font-mono').trim(),
      fontSize,
      lineHeight: Math.round(fontSize * readNumber(style, '--ds-leading-body')),
      padding: {
        top: readNumber(style, '--ds-space-3'),
        bottom: readNumber(style, '--ds-space-3'),
      },
    },
  };
}
