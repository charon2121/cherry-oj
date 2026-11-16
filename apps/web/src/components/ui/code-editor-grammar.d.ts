// Monaco 0.56 exposes the official grammar modules without their own declarations.
declare module 'monaco-editor/languages/definitions/cpp/cpp' {
  import type { languages } from 'monaco-editor/editor/editor.api';

  export const conf: languages.LanguageConfiguration;
  export const language: languages.IMonarchLanguage;
}

declare module 'monaco-editor/languages/definitions/python/python' {
  import type { languages } from 'monaco-editor/editor/editor.api';

  export const conf: languages.LanguageConfiguration;
  export const language: languages.IMonarchLanguage;
}

declare module 'monaco-editor/languages/definitions/java/java' {
  import type { languages } from 'monaco-editor/editor/editor.api';

  export const conf: languages.LanguageConfiguration;
  export const language: languages.IMonarchLanguage;
}
