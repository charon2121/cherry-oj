import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { SafeMarkdown } from './safe-markdown';

test('renders GFM and code while refusing raw HTML and unsafe URLs', () => {
  const { container } = render(
    <SafeMarkdown
      value={
        '## 标题\n\n| 输入 | 输出 |\n| --- | --- |\n| `1` | `2` |\n\n<img src=x onerror="alert(1)">\n\n[危险](javascript:alert(1))'
      }
    />,
  );

  expect(screen.getByRole('heading', { name: '标题' })).toBeVisible();
  expect(screen.getByRole('table')).toBeVisible();
  expect(container.querySelector('img')).toBeNull();
  expect(screen.getByText('危险').closest('a')).not.toHaveAttribute('href');
  expect(container.innerHTML).not.toContain('onerror');
});
