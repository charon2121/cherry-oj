import { fireEvent, render, screen } from '@testing-library/react';
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
