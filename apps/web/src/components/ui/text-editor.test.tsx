import { EditorView } from '@codemirror/view';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ThemeProvider } from '@/lib/theme';

import { FormField } from './field';
import { TextEditor } from './text-editor';

function renderEditor(onChange = vi.fn()) {
  render(
    <ThemeProvider>
      <FormField label="题目正文" description="支持 Markdown">
        <TextEditor
          value="初始内容"
          onChange={onChange}
          language="markdown"
          size="compact"
          aria-label="题目正文 Markdown"
        />
      </FormField>
    </ThemeProvider>,
  );
  return onChange;
}

describe('TextEditor', () => {
  it('holds IME candidates and publishes only the committed source', async () => {
    const onChange = renderEditor();
    const editor = screen.getByRole('textbox', { name: '题目正文 Markdown' });
    const view = EditorView.findFromDOM(editor);
    if (!view) throw new Error('CodeMirror view was not mounted');

    fireEvent.compositionStart(editor);
    act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: 'zhong' } }));
    // Longer than the caller's draft debounce: no candidate may reach onChange.
    await act(() => new Promise<void>((resolve) => setTimeout(resolve, 550)));
    expect(onChange).not.toHaveBeenCalled();
    act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: '中文' } }));
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.compositionEnd(editor, { data: '中文' });
    await waitFor(() => expect(onChange).toHaveBeenCalledExactlyOnceWith('中文'));
  });

  it('does not overwrite an ongoing composition with a controlled value', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <ThemeProvider>
        <TextEditor value="初始内容" onChange={onChange} aria-label="输入法编辑器" />
      </ThemeProvider>,
    );
    const editor = screen.getByRole('textbox', { name: '输入法编辑器' });
    const view = EditorView.findFromDOM(editor);
    if (!view) throw new Error('CodeMirror view was not mounted');
    fireEvent.compositionStart(editor);
    act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: 'pin' } }));
    rerender(
      <ThemeProvider>
        <TextEditor value="父级旧快照" onChange={onChange} aria-label="输入法编辑器" />
      </ThemeProvider>,
    );
    expect(view.state.doc.toString()).toBe('pin');
    expect(onChange).not.toHaveBeenCalled();
    act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: '拼音' } }));
    fireEvent.compositionEnd(editor, { data: '拼音' });
    await waitFor(() => expect(onChange).toHaveBeenCalledExactlyOnceWith('拼音'));

    rerender(
      <ThemeProvider>
        <TextEditor value="明确重置" onChange={onChange} aria-label="输入法编辑器" />
      </ThemeProvider>,
    );
    expect(view.state.doc.toString()).toBe('明确重置');
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('cancels a pending composition notification when the editor unmounts', async () => {
    const onChange = vi.fn();
    const { unmount } = render(
      <ThemeProvider>
        <TextEditor value="" onChange={onChange} aria-label="卸载编辑器" />
      </ThemeProvider>,
    );
    const editor = screen.getByRole('textbox', { name: '卸载编辑器' });
    const view = EditorView.findFromDOM(editor);
    if (!view) throw new Error('CodeMirror view was not mounted');
    fireEvent.compositionStart(editor);
    act(() => view.dispatch({ changes: { from: 0, insert: '中文' } }));
    fireEvent.compositionEnd(editor, { data: '中文' });
    unmount();
    await act(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('also observes compositions delivered by a native EditContext', async () => {
    const previous = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'editContext');
    const context = new EventTarget();
    Object.defineProperty(HTMLElement.prototype, 'editContext', {
      configurable: true,
      writable: true,
      value: context,
    });
    try {
      const onChange = vi.fn();
      const { unmount } = render(
        <ThemeProvider>
          <TextEditor value="" onChange={onChange} aria-label="原生输入法编辑器" />
        </ThemeProvider>,
      );
      const view = EditorView.findFromDOM(
        screen.getByRole('textbox', { name: '原生输入法编辑器' }),
      );
      if (!view) throw new Error('CodeMirror view was not mounted');
      context.dispatchEvent(new Event('compositionstart'));
      act(() => view.dispatch({ changes: { from: 0, insert: 'hou' } }));
      expect(onChange).not.toHaveBeenCalled();
      act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: '后' } }));
      context.dispatchEvent(new Event('compositionend'));
      await waitFor(() => expect(onChange).toHaveBeenCalledExactlyOnceWith('后'));
      unmount();
    } finally {
      if (previous) Object.defineProperty(HTMLElement.prototype, 'editContext', previous);
      else Reflect.deleteProperty(HTMLElement.prototype, 'editContext');
    }
  });

  it('connects the visible label and description to the editable surface', () => {
    renderEditor();
    const editor = screen.getByRole('textbox', { name: '题目正文 Markdown' });
    expect(editor).toHaveTextContent('初始内容');
    expect(editor).toHaveAccessibleDescription('支持 Markdown');
  });

  it('accepts controlled updates without trapping Tab navigation', () => {
    renderEditor();
    const editor = screen.getByRole('textbox', { name: '题目正文 Markdown' });
    expect(fireEvent.keyDown(editor, { key: 'Tab', code: 'Tab' })).toBe(true);
  });

  it('applies an external value without reporting it as a user edit', () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <ThemeProvider>
        <TextEditor value="初始内容" onChange={onChange} aria-label="受控编辑器" />
      </ThemeProvider>,
    );

    rerender(
      <ThemeProvider>
        <TextEditor value="服务端最新内容" onChange={onChange} aria-label="受控编辑器" />
      </ThemeProvider>,
    );

    expect(screen.getByRole('textbox', { name: '受控编辑器' })).toHaveTextContent('服务端最新内容');
    expect(onChange).not.toHaveBeenCalled();
  });
});
