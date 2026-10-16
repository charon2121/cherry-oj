import { render, screen, waitFor } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { RichMarkdownEditor } from './rich-markdown-editor';

const source = '## 输入\n\n两个整数 **a** 和 `b`。\n\n- 第一行\n\n```cpp\nint main() {}\n```';

test('renders stored Markdown as rich text and does not report a change on mount', async () => {
  const onChange = vi.fn();
  render(<RichMarkdownEditor aria-label="题目正文" value={source} onChange={onChange} />);

  const editor = await screen.findByRole('textbox', { name: '题目正文' });
  await waitFor(() => expect(editor.querySelector('h2')).toHaveTextContent('输入'));
  expect(editor.querySelector('strong')).toHaveTextContent('a');
  expect(editor.querySelector('pre code')).toHaveTextContent('int main() {}');
  expect(onChange).not.toHaveBeenCalled();
});

test('toggling read-only and replacing the value from outside are not user edits', async () => {
  const onChange = vi.fn();
  const { rerender } = render(
    <RichMarkdownEditor aria-label="题目正文" value="旧内容" onChange={onChange} />,
  );
  const editor = await screen.findByRole('textbox', { name: '题目正文' });
  await waitFor(() => expect(editor).toHaveTextContent('旧内容'));

  rerender(
    <RichMarkdownEditor aria-label="题目正文" value="旧内容" onChange={onChange} disabled />,
  );
  await waitFor(() => expect(editor).toHaveAttribute('contenteditable', 'false'));

  rerender(<RichMarkdownEditor aria-label="题目正文" value="**服务端最新**" onChange={onChange} />);
  await waitFor(() => expect(editor.querySelector('strong')).toHaveTextContent('服务端最新'));
  expect(onChange).not.toHaveBeenCalled();
});
