/** Keep Monaco and its worker out of list/admin chunks. Network module failures may require reload. */
export function loadCodeEditor() {
  return import('./code-editor-runtime');
}
