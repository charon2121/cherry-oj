import '../../../node_modules/monaco-editor/esm/vs/base/browser/ui/codicons/codicon/codicon.css';
import 'monaco-editor/editor/browser/coreCommands';
import 'monaco-editor/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/editor/contrib/clipboard/browser/clipboard';
import 'monaco-editor/editor/contrib/comment/browser/comment';
import 'monaco-editor/editor/contrib/indentation/browser/indentation';
import 'monaco-editor/editor/contrib/linesOperations/browser/linesOperations';
import 'monaco-editor/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/editor/contrib/wordOperations/browser/wordOperations';
import 'monaco-editor/features/find/register';

import * as monaco from 'monaco-editor/editor/editor.api';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import {
  conf as cppConfiguration,
  language as cppLanguage,
} from 'monaco-editor/languages/definitions/cpp/cpp';

import type { ThemeSnapshot } from '@/lib/theme/theme-runtime';

import { readCodeEditorTheme } from './code-editor-theme';

type CodeEditorSettings = Readonly<{
  colorScheme: ThemeSnapshot['colorScheme'];
  readOnly: boolean;
  ariaLabel: string;
}>;

export type CodeEditorHandle = Readonly<{
  setValue: (value: string) => void;
  updateSettings: (settings: CodeEditorSettings) => void;
  dispose: () => void;
}>;

// The Vite worker constructor resolves to this deployment's hashed static asset.
globalThis.MonacoEnvironment = { getWorker: () => new EditorWorker() };

// The official grammar ships inside this already-lazy runtime. Monaco's convenience
// register entry caches a rejected grammar import forever, so it cannot support retry
// after a language chunk network failure. Register the same grammar through public APIs.
monaco.languages.register({
  id: 'cpp',
  extensions: ['.cpp', '.cc', '.cxx', '.hpp', '.hh', '.hxx'],
  aliases: ['C++', 'Cpp', 'cpp'],
});
monaco.languages.setLanguageConfiguration('cpp', cppConfiguration);
monaco.languages.setMonarchTokensProvider('cpp', cppLanguage);

export async function createCodeEditor(
  host: HTMLElement,
  value: string,
  onChange: (value: string) => void,
  settings: CodeEditorSettings,
): Promise<CodeEditorHandle> {
  // Initialize the locally registered tokenizer before reporting a usable editor.
  await monaco.editor.colorize('', 'cpp', {});
  const initialTheme = readCodeEditorTheme(host, settings.colorScheme);
  const themeName = 'cherry-semantic-code';
  monaco.editor.defineTheme(themeName, initialTheme.theme);
  const model = monaco.editor.createModel(value, 'cpp');
  model.updateOptions({ insertSpaces: true, tabSize: 4 });
  let view: monaco.editor.IStandaloneCodeEditor;
  try {
    view = monaco.editor.create(host, {
      model,
      theme: themeName,
      ...initialTheme.options,
      ariaLabel: settings.ariaLabel,
      readOnly: settings.readOnly,
      domReadOnly: settings.readOnly,
      automaticLayout: false,
      autoDetectHighContrast: false,
      tabFocusMode: true,
      editContext: false,
      minimap: { enabled: false },
      lineNumbers: 'on',
      glyphMargin: false,
      folding: false,
      stickyScroll: { enabled: false },
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      renderLineHighlight: 'line',
      scrollBeyondLastLine: false,
      smoothScrolling: false,
      cursorBlinking: 'solid',
      cursorSmoothCaretAnimation: 'off',
      contextmenu: false,
      bracketPairColorization: { enabled: false },
      guides: { indentation: false, bracketPairs: false },
      wordWrap: 'off',
      wordBasedSuggestions: 'currentDocument',
      quickSuggestions: false,
      suggestOnTriggerCharacters: false,
      unicodeHighlight: { ambiguousCharacters: false, invisibleCharacters: false },
    });
  } catch (error) {
    model.dispose();
    throw error;
  }

  let composing = false;
  let applyingExternalValue = false;
  let disposed = false;
  let currentSettings = settings;
  const changes = view.onDidChangeModelContent(() => {
    if (!composing && !applyingExternalValue) onChange(model.getValue());
  });
  const compositionStart = view.onDidCompositionStart(() => {
    composing = true;
  });
  const compositionEnd = view.onDidCompositionEnd(() => {
    composing = false;
    onChange(model.getValue());
  });
  const observer = new ResizeObserver(() => view.layout());
  observer.observe(host);
  const contrast = window.matchMedia('(forced-colors: active)');
  const refreshTheme = () => {
    const resolved = readCodeEditorTheme(host, currentSettings.colorScheme);
    monaco.editor.defineTheme(themeName, resolved.theme);
    view.updateOptions({
      ...resolved.options,
      theme: themeName,
      readOnly: currentSettings.readOnly,
      domReadOnly: currentSettings.readOnly,
      ariaLabel: currentSettings.ariaLabel,
    });
  };
  contrast.addEventListener('change', refreshTheme);
  void document.fonts?.ready.then(() => {
    if (!disposed) {
      monaco.editor.remeasureFonts();
      view.layout();
    }
  });

  return {
    setValue(nextValue) {
      // An ordinary React echo must never clear Monaco's undo history. During IME
      // composition the editor owns its in-progress text until compositionEnd.
      if (composing || model.getValue() === nextValue) return;
      applyingExternalValue = true;
      try {
        view.pushUndoStop();
        view.executeEdits('controlled-value', [
          { range: model.getFullModelRange(), text: nextValue },
        ]);
        view.pushUndoStop();
      } finally {
        applyingExternalValue = false;
      }
    },
    updateSettings(nextSettings) {
      currentSettings = nextSettings;
      refreshTheme();
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      observer.disconnect();
      contrast.removeEventListener('change', refreshTheme);
      changes.dispose();
      compositionStart.dispose();
      compositionEnd.dispose();
      view.dispose();
      model.dispose();
    },
  };
}
