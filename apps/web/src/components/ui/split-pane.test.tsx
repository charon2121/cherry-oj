import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { SplitPane } from './split-pane';

// 测试环境没有 localStorage，换成内存实现。
beforeEach(() => {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
  });
});
afterEach(() => vi.unstubAllGlobals());

test('the separator is keyboard adjustable within its bounds and remembers the ratio', () => {
  const { unmount } = render(
    <SplitPane
      orientation="horizontal"
      label="调整宽度"
      storageKey="split-test"
      first={<p>左</p>}
      second={<p>右</p>}
    />,
  );
  const separator = screen.getByRole('separator', { name: '调整宽度' });
  expect(separator).toHaveAttribute('aria-orientation', 'vertical');
  expect(separator).toHaveAttribute('aria-valuenow', '50');

  fireEvent.keyDown(separator, { key: 'ArrowRight' });
  expect(separator).toHaveAttribute('aria-valuenow', '52');
  fireEvent.keyDown(separator, { key: 'End' });
  expect(separator).toHaveAttribute('aria-valuenow', '75');
  fireEvent.keyDown(separator, { key: 'ArrowRight' });
  expect(separator).toHaveAttribute('aria-valuenow', '75');

  unmount();
  render(
    <SplitPane
      orientation="horizontal"
      label="调整宽度"
      storageKey="split-test"
      first={<p>左</p>}
      second={<p>右</p>}
    />,
  );
  expect(screen.getByRole('separator', { name: '调整宽度' })).toHaveAttribute(
    'aria-valuenow',
    '75',
  );
});
