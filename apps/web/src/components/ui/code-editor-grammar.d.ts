// Monaco 0.56 exposes the official C++ grammar module without its own declaration.
declare module 'monaco-editor/languages/definitions/cpp/cpp' {
  import type { languages } from 'monaco-editor/editor/editor.api';

  export const conf: languages.LanguageConfiguration;
  export const language: languages.IMonarchLanguage;
}
